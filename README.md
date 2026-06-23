# ths-crawler

同花顺行业资金流向爬虫系统 — A股行业板块资金净流入监控看板

## 快速部署

```bash
git clone git@github.com:litao45964/ths-crawler.git
cd ths-crawler && chmod +x deploy.sh && ./deploy.sh
```

## 在线访问

- **固定域名：** https://ths.thsflow.xyz
- 后端API：`https://ths.thsflow.xyz/api/industry-flow/latest?topN=5`

## 技术栈

- **后端：** Java 17 + Spring Boot 3 + JdbcTemplate + MySQL + Redis
- **前端：** React 18 + Vite 5 + TypeScript + Ant Design 5 + ECharts 5
- **部署：** Nginx + Cloudflare Tunnel + crontab自启

## 项目结构

```
ths-crawler/          # 后端Java项目
ths-crawler-ui/       # 前端React项目
deploy.sh             # 一键部署脚本
schema.sql            # 数据库建表+测试数据
nginx.conf            # Nginx配置
cloudflared.yml       # Cloudflare固定隧道配置
.env.example          # 环境变量模板
```
