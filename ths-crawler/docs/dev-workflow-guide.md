---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/dev-workflow-guide.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782198112033
    ReservedCode2: ""
---
# ths-crawler 开发协作链路与踩坑总结

> 2026-06-23 春风 & 小伍 共同整理

---

## 一、四套环境定位

| 环境 | 角色 | 网络 | 持久化 | 操作方式 |
|------|------|------|--------|----------|
| **沙箱** | 代码生产车间：写代码、编译、验证 | 能访问GitHub，不能访问同花顺 | ❌ 重启丢失 | bash(默认) |
| **项目空间** | 持久仓库：所有文件的中转站 | N/A（Coze内部） | ✅ 永久保存 | coze CLI upload/download |
| **云电脑** | 运行车间：编译、运行、部署、对外服务 | 能访问GitHub（偶发波动）、能访问同花顺 | ✅ 永久保存 | bash(desktop_name="cloud") |
| **GitHub** | 代码仓库：版本控制、协作、备份 | 公网 | ✅ 永久保存 | git push/pull |

---

## 二、协作链条（开发新功能的完整流程）

```
┌─────────┐    写代码     ┌─────────┐   upload    ┌──────────┐
│  沙箱    │ ──────────→ │ 项目空间 │ ──────────→ │ 云电脑   │
│(写+编译) │              │(中转站)  │  download   │(运行+部署)│
└─────────┘              └──────────┘              └──────────┘
     │                        │                         │
     │  git push (沙箱网络通)  │                         │ git push
     │  或 云电脑 push        │                         │ (云电脑网络通时)
     ↓                        ↓                         ↓
┌──────────────────────────────────────────────────────────────┐
│                        GitHub 仓库                            │
│              https://github.com/litao45964/ths-crawler        │
└──────────────────────────────────────────────────────────────┘
```

### 推荐开发流程

1. **沙箱写代码** → 编译验证通过
2. **沙箱上传到项目空间** → `coze agent file upload --project-id 7652034661715165474`
3. **云电脑下载** → `python3 /root/download_project.py` 或 `coze agent file download`
4. **云电脑编译运行** → 验证功能
5. **推送到GitHub** → 说了"上传到GitHub"自动执行

---

## 三、这次遇到的问题

### 问题1：云电脑访问GitHub超时（最耗时）

**现象**：`git push` 卡住 60s+ 无响应，`curl github.com` 返回 000

**根因**：国内服务器访问GitHub受GFW影响，网络不稳定。有时能通（返回200），有时超时

**解决**：等网络恢复后重试。本次等了约5分钟后重新push成功

**教训**：不要在云电脑网络不通时死磕，先做别的事，过会儿再试

### 问题2：沙箱从项目空间下载文件太慢

**现象**：`coze agent file download` 单个文件要 10s+，80个文件要十几分钟；Python递归下载脚本超时

**根因**：coze CLI每次下载都是独立HTTP请求，无并发，延迟叠加

**解决**：不在沙箱做批量下载。云电脑已有完整代码，直接在云电脑操作

### 问题3：沙箱↔云电脑无法直接传文件

**现象**：想把沙箱代码传到云电脑（或反过来），没有直接通道

**根因**：两套环境文件系统完全隔离，没有共享存储或SCP通道

**变通方案**：
- 方案A：沙箱上传到项目空间 → 云电脑从项目空间下载（慢但可靠）
- 方案B：沙箱先git push到GitHub → 云电脑git pull（需要两边网络都通）
- 方案C：云电脑本地直接改代码（不走沙箱中转）

---

## 四、各环境操作铁律

### 沙箱铁律
- ✅ 写代码、编译验证
- ✅ 上传文件到项目空间（coze CLI upload）
- ✅ 访问GitHub（网络稳定）
- ❌ 不要跑长期服务（重启丢失）
- ❌ 不要从项目空间批量下载（太慢）

### 云电脑铁律
- ✅ 编译运行、部署服务、对外暴露
- ✅ 访问同花顺（爬虫核心能力）
- ✅ git push到GitHub（网络偶发波动，失败重试）
- ❌ sudo只能跑apt/dpkg，其他被禁
- ❌ systemctl/service被禁，服务需nohup启动

### 项目空间铁律
- ✅ 所有产出文件的中转站和持久仓库
- ✅ Agent工作目录的文件不跟项目走
- ❌ `coze agent file list` 不支持depth递归，需Python脚本

### GitHub铁律
- ✅ 版本控制、协作、备份
- ✅ PAT存SECRET.md，自动读取推送
- ⚠️ 云电脑push偶尔网络波动，失败等1-2分钟重试

---

## 五、未来开发新功能的推荐路径

### 场景A：纯后端Java功能（如新增API、修改Service）

```
沙箱写Java代码 → 沙箱mvn编译验证 → 上传到项目空间 → 
云电脑download → 云电脑mvn编译 → 云电脑重启Spring Boot → 验证API → 
"上传到GitHub" → 自动push
```

### 场景B：纯前端功能（如新增页面、修改组件）

```
沙箱写React代码 → 上传到项目空间 → 
云电脑download → 云电脑npm build → 云电脑复制dist到/var/www/ths-crawler → 
验证页面 → "上传到GitHub" → 自动push
```

### 场景C：全栈功能（前后端都改）

```
沙箱写Java+React代码 → 分别编译验证 → 分别上传到项目空间 →
云电脑download → 后端mvn编译+前端npm build → 重启服务+刷新前端 →
验证联调 → "上传到GitHub" → 自动push
```

### 场景D：紧急hotfix（云电脑直改）

```
云电脑vim改代码 → 编译验证 → 重启服务 → 
"上传到GitHub" → 自动push
（跳过沙箱和项目空间，最快但无备份）
```

---

## 六、关键命令速查

```bash
# === 项目空间操作 ===
coze agent file list --project-id 7652034661715165474 --project-dir /ths-crawler
coze agent file upload --project-id 7652034661715165474 --local-file-path xxx --project-dir /ths-crawler/src/xxx
coze agent file download --project-id 7652034661715165474 --project-file-path /ths-crawler/pom.xml

# === 云电脑下载全量项目 ===
python3 /root/download_project.py

# === 云电脑启动服务 ===
cd /root/ths-crawler && mvn spring-boot:run -DskipTests &
cd /root/ths-crawler-ui && npm run build && cp -r dist/* /var/www/ths-crawler/
nginx -s reload

# === GitHub推送 ===
cd /root/ths-crawler-repo
git add -A && git commit -m "feat: xxx" && git push

# === Cloudflare隧道 ===
cloudflared tunnel --url http://localhost:8080
```

---

## 七、待优化项

1. **云电脑→沙箱文件通道**：目前没有直接通道，只能通过项目空间中转或GitHub
2. **一键部署脚本**：deploy-retrospective.md中有模板，待提取为独立可执行脚本
3. **crontab @reboot**：所有服务开机自启未配置，云电脑重启后需手动拉起
4. **Cloudflare固定隧道**：临时URL每次重启会变，需注册账号+域名配置固定隧道
5. **云电脑GitHub网络稳定性**：偶发超时，可考虑配置代理或SSH over 443端口
6. **download_project.py优化**：coze CLI不支持depth递归，每次全量遍历太慢，应改为增量同步

---

*文档生成时间：2026-06-23 09:38*

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
