---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/cloudflare-tunnel-explained.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782198102318
    ReservedCode2: ""
---
<!--
  诞生背景：
  用户问："你先给我深度解释下，我们云电脑部署，为啥要用Cloudflare，
  或者说为啥用域名，给我一个形象的图例说明"
  
  场景：云电脑安全组封锁所有入站端口，quick tunnel每次重启URL随机变，
  用户不理解为什么需要Cloudflare+域名这套方案，要求用形象图例说明原理。
  
  本文档从"孤岛"比喻出发，解释Cloudflare Tunnel的"里面往外钻"原理，
  以及域名的"门牌号"作用，作为项目技术决策参考。
-->

# Cloudflare Tunnel 固定域名方案说明

## 问题的本质：云电脑是个"孤岛"

```
                    🌐 互联网
                       │
        ┌──────────────┼──────────────┐
        │              │              │
   🏠 你家WiFi     📱 手机4G      🏢 公司网络
        │              │              │
        └──────┬───────┴──────┬───────┘
               │              │
          "我想访问        "我也想看
           我的看板"        板块数据"
               │              │
               ▼              ▼
          ??? 怎么走到云电脑 ???
```

### 云电脑的真实处境

```
┌─────────────────────────────────────────────────┐
│  腾讯云服务器 49.233.146.84                       │
│                                                   │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐         │
│  │ MySQL   │  │ Spring  │  │  Nginx   │         │
│  │ :3306   │  │ Boot    │  │  :8080   │         │
│  │         │  │ :8100   │  │          │         │
│  └─────────┘  └─────────┘  └──────────┘         │
│                                                   │
│  🔒 安全组（腾讯云防火墙）                         │
│  ┌─────────────────────────────────────┐         │
│  │ 80  → ❌ 封了                        │         │
│  │ 443 → ❌ 封了                        │         │
│  │ 8080→ ❌ 封了                        │         │
│  │ 8100→ ❌ 封了                        │         │
│  │ 所有入站端口全封！                    │         │
│  └─────────────────────────────────────┘         │
│                                                   │
│  = 一座孤岛，外面谁也进不来                        │
└─────────────────────────────────────────────────┘
```

**为什么封？** 扣子买给你的云电脑，腾讯云默认安全组把所有入站端口封了。你没有腾讯云控制台权限，改不了。

## 没有Cloudflare时，试过的方案

| 方案 | 结果 | 原因 |
|---|---|---|
| 直接访问IP `http://49.233.146.84:8080` | ❌ | 安全组拦截 |
| localtunnel `xxx.loca.lt` | ❌ | 腾讯云ICP备案拦截 |
| 联系扣子开放端口 | ⏳ | 需要扣子客服配合，不可控 |

## Cloudflare Tunnel 怎么破局

**核心思路：不是"外面往里打"，而是"里面往外钻"！**

```
                🔒安全组只管"进来"，不管"出去"
                        ↓
┌──────────────────────────────────────────────────┐
│  云电脑（里面主动往外连）                          │
│                                                    │
│  cloudflared ──────────────────────────→ Cloudflare边缘服务器
│  "我是49.233.146.84，               (全球节点，开放443端口)
│   我要建一条隧道，                     ↑
│   帮我把流量转进来"               隧道建立！✅
│                                                    │
└──────────────────────────────────────────────────┘
```

### 流量走向

```
  📱 你的手机                    🌐 Cloudflare              🖥️ 云电脑
  
  访问 ths.example.com  ──→  Cloudflare收到请求
                              "这个域名指向我的隧道"
                              找到对应的隧道 ──────→  隧道传到云电脑
                                                    Nginx :8080
                                                    Spring Boot :8100
                                                    返回页面数据
                              ←────── 隧道原路返回  ←──  响应回来了
  看到看板了 🎉     ←─────  返回给你的手机
```

**一句话总结：安全组封了门，Cloudflare帮你从里面挖了条地道。**

## 为什么还要域名？

### 没有域名（quick tunnel）

每次重启cloudflared → URL随机变：
- 第1次：`https://jazz-rays-profits-earthquake.trycloudflare.com`
- 第2次：`https://purple-rivers-dance-moon.trycloudflare.com`
- 第3次：`https://happy-trees-sing-weather.trycloudflare.com`

❌ 你永远不知道今天的URL是什么
❌ 手机书签每次要改
❌ 分享给别人马上就过期

### 有域名（固定隧道）

永远是：`https://ths.yourdomain.com`

✅ 重启cloudflared也不变
✅ 手机书签一次设好永久用
✅ 随时分享

**域名就是你给这条地道挂的门牌号，没有门牌号，别人找不到你。**

## 最终效果

```
  📱 手机浏览器输入 ths.yourdomain.com
       │
       ▼
  Cloudflare CDN（自动HTTPS、加速、防攻击）
       │
       ▼ 隧道
  云电脑 Nginx :8080
       │
       ├── /api/* → Spring Boot :8100
       └── /*     → 前端静态文件
  
  = 和访问百度一样简单
```

## 固定隧道配置流程

```
1. 注册Cloudflare账号（免费）
2. 购买域名（推荐 .xyz ≈ ¥7/年，.top ≈ ¥9/年）
3. 域名托管到Cloudflare DNS（Cloudflare自动引导）
4. 云电脑执行认证：cloudflared tunnel login
5. 创建命名隧道：cloudflared tunnel create ths-crawler
6. 配置DNS CNAME：ths.yourdomain.com → 隧道ID.cfargotunnel.com
7. 启动隧道：cloudflared tunnel run ths-crawler
8. 永久访问：https://ths.yourdomain.com ✅
```

## 当前状态

- [x] Cloudflare账号注册
- [ ] 域名购买
- [ ] 域名托管到Cloudflare
- [ ] cloudflared tunnel login 认证
- [ ] 创建命名隧道
- [ ] 配置DNS CNAME
- [ ] 启动固定隧道
- [ ] 更新 startup.sh 使用固定隧道

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
