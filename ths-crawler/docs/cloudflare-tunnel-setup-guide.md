---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/cloudflare-tunnel-setup-guide.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782198104795
    ReservedCode2: ""
---
<!--
  诞生背景：
  用户问："我看Cloudflare域名比较贵，我想用阿里云买域名，Cloudflare开通道，是否可行，
  并给出我具体步骤梳理"
  
  场景：用户决定用阿里云买域名（便宜）+ Cloudflare Tunnel（免费隧道）的组合方案，
  需要一份清晰的分步操作指南，明确哪些步骤用户做、哪些Agent做。
  
  本文档梳理了从注册到上线的完整流程，三条线并行，最终交汇于DNS切换。
-->

# Cloudflare 固定隧道配置指南（阿里云域名 + Cloudflare Tunnel）

## 方案总览

```
线A：阿里云买域名（用户操作）
线B：Cloudflare开隧道（Agent操作）
最后交汇：域名DNS切到Cloudflare
```

**费用：** 域名 ≈ ¥7-9/年（.xyz/.top），Cloudflare Tunnel 免费

## 第一步：Cloudflare注册（用户操作）

1. 打开 https://dash.cloudflare.com/sign-up
2. 邮箱注册，设密码
3. 登录后点 **Add a site** → 输入你选的域名（如 thsflow.xyz）→ 选 **Free计划**
4. Cloudflare会给你**两个NS地址**，类似：
   ```
   vera.ns.cloudflare.com
   zod.ns.cloudflare.com
   ```
   ⚠️ **先别关这个页面，记下这两个NS**

## 第二步：阿里云买域名（用户操作）

1. 打开 https://wanwang.aliyun.com/domain/
2. 搜索 `thsflow.xyz`，如果可注册就下单（≈¥7-9/年）
3. 实名认证（国内买域名必须，一般几小时通过）
4. 认证通过后，进入 **域名控制台** → 找到刚买的域名 → 点 **管理**
5. 找到 **DNS修改** → 把NS服务器从阿里云默认的改成Cloudflare给的两个：
   ```
   原：dns1.hichina.com / dns2.hichina.com
   改：vera.ns.cloudflare.com / zod.ns.cloudflare.com
   ```
6. 确认保存

## 第三步：Cloudflare侧确认（用户操作）

1. 回到Cloudflare面板，点 **Check nameservers**
2. 等待DNS生效（通常几分钟到24小时，大部分1小时内）
3. 显示 **Active** 就说明域名已托管到Cloudflare

## 第四步：云电脑配置隧道（Agent操作）

用户通知"域名已Active"后，Agent执行以下命令：

```bash
# 1. 认证cloudflared到Cloudflare账号
cloudflared tunnel login    # 会生成一个URL，用户在浏览器打开授权

# 2. 创建命名隧道
cloudflared tunnel create ths-crawler   # 得到隧道ID

# 3. 配置隧道路由
# 写入 ~/.cloudflared/config.yml

# 4. 配置DNS CNAME
cloudflared tunnel route dns ths-crawler ths.你的域名.xyz
# 自动在Cloudflare添加CNAME记录

# 5. 启动隧道
cloudflared tunnel run ths-crawler

# 6. 更新开机自启脚本
# startup.sh改为用固定隧道而非quick tunnel
```

### config.yml 模板

```yaml
tunnel: <隧道ID>
credentials-file: /root/.cloudflared/<隧道ID>.json

ingress:
  - hostname: ths.你的域名.xyz
    service: http://localhost:8080
  - service: http_status:404
```

## 最终效果

```
手机浏览器输入 → https://ths.你的域名.xyz
       │
       ▼
  Cloudflare CDN（自动HTTPS、加速、防攻击）
       │
       ▼ 隧道
  云电脑 Nginx :8080
       │
       ├── /api/* → Spring Boot :8100
       └── /*     → 前端静态文件
  
  永久地址，重启不变 🎉
```

## 用户操作清单

| 步骤 | 操作 | 预计时间 |
|---|---|---|
| ① Cloudflare注册 + 添加域名 | 拿到两个NS | 5分钟 |
| ② 阿里云买域名 + 改NS | 等实名+改DNS | 1-24小时 |
| ③ cloudflared login授权 | 浏览器点同意 | 1分钟 |

①②可以同时做，③等DNS生效后Agent触发。

## 当前状态

- [ ] Cloudflare注册 + 添加域名 + 拿到NS
- [ ] 阿里云买域名 + 实名认证
- [ ] 阿里云DNS修改为Cloudflare NS
- [ ] Cloudflare面板显示Active
- [ ] cloudflared tunnel login 认证
- [ ] 创建命名隧道 ths-crawler
- [ ] 配置DNS CNAME
- [ ] 启动固定隧道
- [ ] 更新 startup.sh 使用固定隧道
- [ ] 验证 https://ths.域名.xyz 可访问

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
