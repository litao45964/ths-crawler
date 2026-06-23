---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/deploy-retrospective.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782198107282
    ReservedCode2: ""
---
# 云电脑前后端联调复盘 & 优化建议

> 日期：2026-06-23
> 场景：项目空间代码 → 云电脑部署 → 公网访问

---

## 一、联调全链路回顾（实际耗时约 40 分钟）

| 步骤 | 操作 | 耗时 | 坑点 |
|------|------|------|------|
| 1 | 递归下载项目文件到云电脑 | ~5min | coze CLI无clone命令，写Python脚本逐个下载 |
| 2 | 修复目录嵌套 | ~3min | download后多了一层同名目录 |
| 3 | Maven编译后端 | ~2min | ✅ 一次通过 |
| 4 | 初始化数据库（schema+测试数据） | ~1min | ✅ 顺利 |
| 5 | 启动Spring Boot | ~1min | ✅ 2.7秒启动 |
| 6 | npm install + vite build | ~2min | ✅ 顺利 |
| 7 | 配置Nginx | ~3min | /root目录权限问题→改到/var/www |
| 8 | 公网访问 | ~15min | 🔴 安全组封端口→localtunnel被ICP拦截→cloudflare tunnel |
| 9 | 验证前后端联通 | ~2min | ✅ API+前端全部正常 |

**核心发现：技术联调本身很快（步骤3-7共约12分钟），公网访问才是最大耗时（步骤8花了15分钟）。**

---

## 二、踩过的坑逐一分析

### 坑1：coze CLI 没有批量下载/克隆能力

**现象：** `coze agent file list` 不支持 depth 递归展开，`coze agent file download` 只能逐文件下载且下载到当前目录的 basename（不保留目录结构）

**实际影响：**
- 写了 Python 脚本递归 list→download，耗时 5 分钟才下完 110 个文件
- download 后文件名只有 basename，目录结构丢失，需要手动修复

**优化方案：**
- **短期**：保留 `download_project.py` 脚本，后续新项目直接复用
- **中期**：项目文件空间支持 `coze agent file clone --project-dir /ths-crawler --local-dir /root/ths-crawler` 一键克隆
- **替代思路**：如果项目已同步到 GitHub，直接 `git clone` 最快（1秒 vs 5分钟）

### 坑2：下载后目录多嵌套一层

**现象：** `coze agent file download --project-file-path /ths-crawler/src/main/java/.../App.java` 下载后文件在 `/root/ths-crawler/ths-crawler/src/...`，多了一层 `/ths-crawler/`

**根因：** download 脚本用 `BASE_LOCAL + os.path.dirname(rel_path)` 拼路径，rel_path 以 `/ths-crawler/` 开头，又拼到了 `/root/ths-crawler/` 下

**优化方案：** 下载脚本需要加一个 `strip_prefix` 参数，去掉远程路径的前缀层级

### 坑3：Nginx 默认无法读取 /root 目录

**现象：** 前端页面 500 Internal Server Error

**根因：** Nginx worker 进程以 www-data 用户运行，无权限读取 /root 下的文件

**修复：** `cp -r /root/ths-crawler-ui/dist /var/www/ths-crawler && chown -R www-data:www-data /var/www/ths-crawler`

**优化方案：** 部署脚本直接把构建产物输出到 /var/www，不要放在 /root 下

### 坑4：云电脑公网端口全部被封

**现象：** 服务在 localhost 正常，但公网 IP + 端口无法访问

**根因：** 扣子云电脑底层是腾讯云 CVM，安全组默认只开放 SSH（22）端口，HTTP/HTTPS 和自定义端口全部被拦。用户无法直接登录腾讯云控制台修改安全组

**尝试链路：**
1. ❌ 直接访问公网IP:80 → 安全组拦截
2. ❌ 改用8080高位端口 → 同样被拦
3. ❌ localtunnel 内网穿透 → 腾讯云 ICP 备案拦截
4. ✅ Cloudflare Tunnel → 成功，但URL临时

**优化方案：**
- **方案A（推荐）**：联系扣子客服开放安全组80/443端口，一劳永逸
- **方案B**：注册 Cloudflare 账号，配置固定域名隧道（免费，域名不变）
- **方案C**：使用 frp 自建穿透服务器（需要另一台有公网IP的机器）

### 坑5：Cloudflare quick tunnel URL 是临时的

**现象：** 每次重启 cloudflared 进程，URL 会变

**影响：** 无法分享固定链接，手机书签会失效

**优化方案：**
- 注册 Cloudflare 账号 → `cloudflared tunnel login` → 创建命名隧道 → 绑定自己的域名
- 或者等扣子平台开放安全组端口后直接用 IP 访问

---

## 三、流程优化建议

### 3.1 一键部署脚本

当前是手动一步步执行，应该做成幂等脚本：

```bash
#!/bin/bash
# ths-cloud-deploy.sh - 云电脑一键部署脚本
# 用法: bash ths-cloud-deploy.sh

set -e

# ===== 1. 环境检查 =====
echo ">>> 检查环境..."
mysql -uroot -proot -e "SELECT 1" >/dev/null 2>&1 || { echo "MySQL未就绪"; exit 1; }
redis-cli ping >/dev/null 2>&1 || { echo "Redis未就绪"; exit 1; }
java -version 2>&1 | grep -q "17" || { echo "JDK17未安装"; exit 1; }

# ===== 2. 下载项目文件（如已有则跳过）=====
if [ ! -f /root/ths-crawler/pom.xml ]; then
    echo ">>> 下载项目文件..."
    python3 /root/download_project.py
fi

# ===== 3. 修复目录嵌套 =====
if [ -d /root/ths-crawler/ths-crawler ]; then
    echo ">>> 修复目录嵌套..."
    cd /root/ths-crawler
    cp -rn ths-crawler/* . 2>/dev/null
    rm -rf ths-crawler
fi

# ===== 4. 初始化数据库 =====
echo ">>> 初始化数据库..."
mysql -uroot -proot ths_crawler < /root/ths-crawler/src/main/resources/db/schema.sql 2>/dev/null
mysql -uroot -proot ths_crawler < /root/ths-crawler/src/main/resources/db/schema_v2.sql 2>/dev/null

# ===== 5. 编译后端 =====
echo ">>> 编译后端..."
cd /root/ths-crawler
mvn clean compile -q

# ===== 6. 启动后端（如已运行则跳过）=====
if ! curl -s http://localhost:8100/api/industry-flow/industries >/dev/null 2>&1; then
    echo ">>> 启动后端..."
    nohup mvn spring-boot:run -q > /root/backend.log 2>&1 &
    sleep 15
fi

# ===== 7. 构建前端 =====
echo ">>> 构建前端..."
cd /root/ths-crawler-ui
npm install --silent 2>/dev/null
npx vite build

# ===== 8. 部署前端到 Nginx =====
echo ">>> 部署前端..."
mkdir -p /var/www/ths-crawler
cp -r /root/ths-crawler-ui/dist/* /var/www/ths-crawler/
chown -R www-data:www-data /var/www/ths-crawler

# 配置 Nginx
cat > /etc/nginx/sites-available/ths-crawler << 'NGINX'
server {
    listen 8080;
    server_name _;
    root /var/www/ths-crawler;
    index index.html;
    
    location / {
        try_files $uri $uri/ /index.html;
    }
    
    location /api/ {
        proxy_pass http://127.0.0.1:8100/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
NGINX

ln -sf /etc/nginx/sites-available/ths-crawler /etc/nginx/sites-enabled/
nginx -s reload 2>/dev/null || nginx

# ===== 9. 启动 Cloudflare Tunnel =====
if ! pgrep -f cloudflared >/dev/null; then
    echo ">>> 启动 Cloudflare Tunnel..."
    nohup cloudflared tunnel --url http://localhost:8080 > /root/cf.log 2>&1 &
    sleep 10
    URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /root/cf.log | head -1)
    echo ">>> 公网地址: $URL"
else
    echo ">>> Cloudflare Tunnel 已运行"
    URL=$(grep -o 'https://[a-z0-9-]*\.trycloudflare\.com' /root/cf.log | head -1)
    echo ">>> 公网地址: $URL"
fi

# ===== 10. 验证 =====
echo ""
echo "===== 验证结果 ====="
echo "后端API: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8100/api/industry-flow/industries)"
echo "前端页面: $(curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/)"
echo "公网访问: $URL"
```

### 3.2 服务持久化（开机自启）

当前 MySQL/Redis/Spring Boot/Nginx/Cloudflare Tunnel 都是前台或 nohup 启动，**云电脑重启后全部丢失**。

**解决方案：写 systemd user service 或 crontab @reboot**

```bash
# 方案1: crontab（最简单）
(crontab -l 2>/dev/null; cat << 'CRON'
@reboot mysqld --user=mysql --daemonize
@reboot redis-server --daemonize yes
@reboot sleep 10 && cd /root/ths-crawler && mvn spring-boot:run -q > /root/backend.log 2>&1
@reboot sleep 5 && nginx
@reboot sleep 15 && cloudflared tunnel --url http://localhost:8080 > /root/cf.log 2>&1
CRON
) | crontab -
```

### 3.3 项目文件同步策略

| 方式 | 速度 | 可靠性 | 适合场景 |
|------|------|--------|----------|
| coze CLI download | 慢（5min/100文件） | 中（目录嵌套） | 无GitHub时 |
| git clone | 快（<10秒） | 高 | ✅ 首选 |
| 本地重新生成代码 | 中（2-3min） | 高 | 无版本历史时 |

**建议**：优先走 GitHub 同步，git clone 到云电脑最快最稳。

---

## 四、公网访问方案对比

| 方案 | 成本 | 稳定性 | URL固定 | 配置难度 | 推荐度 |
|------|------|--------|---------|----------|--------|
| 开放安全组80端口 | 免费 | ⭐⭐⭐⭐⭐ | ✅ IP固定 | 需联系扣子 | ⭐⭐⭐⭐⭐ |
| Cloudflare 固定隧道 | 免费 | ⭐⭐⭐⭐ | ✅ 域名固定 | 注册+配置 | ⭐⭐⭐⭐ |
| Cloudflare quick tunnel | 免费 | ⭐⭐⭐ | ❌ 随机 | 零配置 | ⭐⭐⭐ |
| frp 自建穿透 | 需VPS | ⭐⭐⭐⭐ | ✅ 自定义 | 中等 | ⭐⭐⭐ |
| localtunnel | 免费 | ⭐⭐ | ❌ 随机 | 零配置 | ⭐（ICP拦截） |

---

## 五、下次联调的理想流程（5分钟版）

1. `git clone https://github.com/xxx/ths-crawler.git` → 10秒
2. `mysql -uroot -proot ths_crawler < schema.sql` → 3秒
3. `cd ths-crawler && mvn spring-boot:run &` → 后台启动
4. `cd ths-crawler-ui && npm install && npx vite build` → 30秒
5. `cp -r dist/* /var/www/ths-crawler/ && nginx -s reload` → 2秒
6. `cloudflared tunnel run ths-crawler` → 固定域名

**从 40 分钟压缩到 5 分钟，核心是 git clone 替代逐文件下载 + 一键部署脚本。**

---

## 六、待办清单

- [ ] 向扣子客服申请开放云电脑 80/443 端口安全组
- [ ] 注册 Cloudflare 账号，配置固定域名隧道
- [ ] 收到 GitHub 仓库地址+PAT后首次 push，后续用 git clone 部署
- [ ] 编写一键部署脚本 `ths-cloud-deploy.sh` 并上传项目空间
- [ ] 配置 crontab @reboot 实现服务开机自启
- [ ] 修复 download_project.py 的目录嵌套 bug

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
