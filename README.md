# ths-crawler — 同花顺行业资金流向爬虫系统

每日低频抓取A股行业板块资金净流入数据，支持趋势分析与共振信号检测。

## 一键部署

```bash
git clone git@github.com:litao45964/ths-crawler.git
cd ths-crawler && chmod +x deploy.sh && ./deploy.sh
```

> 约5分钟：环境检测 → MySQL初始化 → Redis启动 → 后端编译 → 前端构建 → Nginx部署 → Cloudflare隧道

## 项目结构

```
ths-crawler/
├── ths-crawler/          # 后端 Spring Boot 3.2 + JdbcTemplate + MySQL + Redis
│   ├── src/              # Java源码（35个类）
│   ├── docs/             # 设计文档、API示例、部署指南
│   └── scripts/          # 启动脚本、Python辅助脚本、测试数据SQL
├── ths-crawler-ui/       # 前端 React 18 + Vite 5 + TypeScript + Ant Design 5
│   └── src/              # 页面组件（资金排行/趋势分析/共振信号）
├── schema.sql            # 数据库建表脚本
├── nginx.conf            # Nginx站点配置
├── cloudflared.yml       # Cloudflare隧道配置模板
├── deploy.sh             # 一键部署脚本
├── .env.example          # 环境变量模板
└── README.md
```

## 核心功能

- 📊 **行业资金流排行**：每日抓取A股行业板块资金净流入数据（Top N）
- 📈 **趋势分析**：线性回归计算行业资金流趋势（斜率/R²/标准差）
- 🔔 **共振信号**：长短周期趋势共振检测，识别资金持续流入行业
- 📱 **移动端适配**：响应式布局，手机端自动切换卡片模式

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Java 17 + Spring Boot 3.2 + JdbcTemplate + MySQL 8.0 + Redis |
| 前端 | React 18 + Vite 5 + TypeScript + Ant Design 5 + ECharts 5 |
| 数据源 | AKShare（Python桥接） / OkHttp直连（预留） |
| 部署 | 云电脑 + Nginx + Cloudflare Tunnel |

## API端点

| Method | Path | 说明 |
|--------|------|------|
| POST | /api/industry-flow/collect | 手动触发日度采集 |
| GET | /api/industry-flow/latest?topN=10 | 最新日度排行 |
| GET | /api/industry-flow/industries | 行业名称列表 |
| GET | /api/industry-flow/trend?industry=半导体&period=22 | 单行业趋势 |
| GET | /api/industry-flow/history?industry=半导体&days=60 | 历史净额序列 |
| GET | /api/industry-flow/resonance?shortPeriod=5&longPeriod=22 | 共振信号 |
| POST | /api/industry-flow/trend/calculate | 手动触发趋势计算 |

## 手动部署

<details>
<summary>点击展开手动部署步骤</summary>

```bash
# 1. MySQL
mysql -uroot -proot < schema.sql

# 2. Redis
redis-server --daemonize yes

# 3. 后端
cd ths-crawler && mvn spring-boot:run &

# 4. 前端
cd ths-crawler-ui && npm install && npx vite build
mkdir -p /var/www/ths-crawler && cp -r dist/* /var/www/ths-crawler/

# 5. Nginx
cp nginx.conf /etc/nginx/sites-available/ths-crawler
ln -s /etc/nginx/sites-available/ths-crawler /etc/nginx/sites-enabled/
nginx -s reload

# 6. 公网访问
cloudflared tunnel --url http://localhost:8080
```

</details>

## 文档

- [开发协作链路与踩坑总结](ths-crawler/docs/dev-workflow-guide.md)
- [云电脑环境搭建指南](ths-crawler/docs/cloud-computer-setup-guide.md)
- [联调复盘与一键部署](ths-crawler/docs/deploy-retrospective.md)
- [Gap分析与开发计划](ths-crawler/docs/gap-analysis-v3.md)
- [GitHub同步方案](ths-crawler/docs/github-sync-guide.md)

## License

MIT
