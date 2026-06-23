# ths-crawler — 同花顺行业资金流向爬虫系统

每日低频抓取A股行业板块资金净流入数据，支持趋势分析与共振信号检测。

## 项目结构

```
ths-crawler/
├── backend/          # Spring Boot 3.2 + JdbcTemplate + MySQL + Redis
│   ├── src/          # Java源码（35个类）
│   ├── docs/         # 设计文档、API示例、部署指南
│   └── scripts/      # 启动脚本、Python辅助脚本、测试数据SQL
├── frontend/         # React 18 + Vite 5 + TypeScript + Ant Design 5 + ECharts 5
│   └── src/          # 页面组件（资金排行/趋势分析/共振信号）
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

## 快速启动

```bash
# 后端
cd backend
mvn spring-boot:run

# 前端
cd frontend
npm install && npm run dev
```

## 部署文档

- [云电脑环境搭建指南](backend/docs/cloud-computer-setup-guide.md)
- [联调复盘与一键部署](backend/docs/deploy-retrospective.md)
- [Gap分析与开发计划](backend/docs/gap-analysis-v3.md)
- [GitHub同步方案](backend/docs/github-sync-guide.md)

## License

MIT
