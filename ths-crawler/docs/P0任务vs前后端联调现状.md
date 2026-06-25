# P0 任务 vs 前后端联调现状

> **生成场景**：2026-06-25，盘点拆解书12项P0任务的实际前后端联调匹配情况，明确已完成/部分完成/未开发的边界，指导后续开发优先级。

---

## 总览

| 状态 | 数量 | 任务 |
|------|------|------|
| ✅ 已完成 | 7 | P0-02, P0-03, P0-04, P0-06, P0-07, P0-08, P0-12 |
| ⚠️ 部分完成 | 2 | P0-01（IP封禁绕过）, P0-09（缺增强项） |
| ❌ 未开发 | 3 | P0-05, P0-10, P0-11 |

---

## 逐任务匹配详情

### P0-01：Playwright浏览器自动化采集器 — ⚠️ 部分完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端代码 | ✅ 完成 | PlaywrightIndustryFetcher + PlaywrightConfig + 反检测 + 懒加载 |
| TDD测试 | ✅ 完成 | 5个Mock契约测试+23个测试全通过 |
| 真实采集 | ❌ 阻塞 | 云电脑IP 49.233.146.84 被同花顺Nginx封禁 |
| 前端联动 | N/A | 采集成功后 latest 接口数据量自动变化 |
| 绕过方案 | ✅ 生效 | 500条模拟数据（25行业×20交易日），其他P0任务可正常推进 |

### P0-02：采集器接口抽象层 — ✅ 完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端代码 | ✅ 完成 | @ConditionalOnProperty 切换 playwright/okhttp |
| 前端联动 | 无 | 配置级变更，无前端交互 |

### P0-03：定时调度 — ✅ 完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端代码 | ✅ 完成 | IndustryFlowJob 采集→计算链式调用 |
| 事务边界 | ✅ 确认 | 编排方法无@Transactional，采集入库和趋势计算各持独立短事务 |
| 前端联动 | 无 | 定时任务，无前端交互 |

### P0-04：交易日历 — ✅ 后端完成，缺前端UI

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端API | ✅ 3个全通 | GET /api/trade-calendar, /check, /latest |
| TDD测试 | ✅ 完成 | TradeCalendarServiceTest |
| 前端页面 | ❌ 缺失 | 无交易日历UI页面 |
| 前端联动 | ❌ 缺失 | 日期选择器非交易日不可选（未实现） |

### P0-05：异常重试 — ❌ 未开发

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端代码 | ❌ 未开发 | 采集失败重试机制 |
| 前端联动 | 无 | |

### P0-06：数据去重 — ✅ 完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 去重机制 | ✅ 已满足 | batchInsertOrUpdate + 唯一键(trade_date, industry_name) |
| 前端联动 | 无 | 数据层保障，无前端交互 |
| 备注 | 仅验证即可，无需额外开发 |

### P0-07：趋势修复 — ✅ 完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端修复 | ✅ 完成 | lookback窗口改为交易日×2 + 三级降级null防御 |
| 前端联调 | ✅ 通 | TrendAnalysis 页面趋势数据正常展示 |
| API验证 | ✅ 通过 | GET /api/industry-flow/trend |

### P0-08：共振修复 — ✅ 完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端修复 | ✅ 完成 | R²阈值0.1 + parallelStream改普通for循环 |
| 前端联调 | ✅ 通 | ResonanceSignal 页面共振信号正常展示 |
| API验证 | ✅ 通过 | GET /api/industry-flow/resonance |
| 备注 | R²=0.1 是模拟数据适配值，真实数据应回调到0.3-0.4 |

### P0-09：排行看板增强 — ⚠️ 部分完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 排序白名单 | ✅ 完成 | Controller白名单+Service防御校验+Mapper XML枚举兜底 |
| SQL注入防护 | ✅ 完成 | 三层防御体系 |
| 基础排行 | ✅ 联调通 | FlowRanking 页面 + latest 接口 |
| 趋势计算按钮 | ❌ 缺失 | 前端api层定义了 triggerTrendCalculate() 但无页面调用 |
| 补采入口 | ❌ 缺失 | 无前端按钮触发 collect |
| 概念板块页面 | ❌ 缺失 | SectorFlow 4个接口完全没前端对接 |

### P0-10：统一响应格式 — ❌ 未开发

| 维度 | 状态 | 说明 |
|------|------|------|
| ApiResponse类 | ❌ 未开发 | 统一 success/data/count/timestamp/message |
| Controller改造 | ❌ 未开发 | 所有方法返回 String → ApiResponse<T> |
| 前端适配 | ❌ 未开发 | api/index.ts 统一用 ApiResponse<T> 类型 |

### P0-11：采集链路埋点 — ❌ 未开发

| 维度 | 状态 | 说明 |
|------|------|------|
| traceId贯穿 | ❌ 未开发 | Controller→Service→Fetcher 共享 traceId |
| 分阶段计时 | ❌ 未开发 | 抓取耗时/入库耗时/总耗时 |
| Flyway V5 | ❌ 未开发 | ths_crawl_log 新增 trace_id/phase/detail/retry_count |
| 前端联动 | 无 | |

### P0-12：采集层解耦 — ✅ 完成

| 维度 | 状态 | 说明 |
|------|------|------|
| 后端代码 | ✅ 完成 | 与P0-02合并开发，DataFetcher接口+配置切换 |
| 前端联动 | 无 | 架构级变更，无前端交互 |

---

## 前后端接口联调全景

### 已联调通（5个）

| 接口 | 前端页面 | 状态 |
|------|---------|------|
| GET /api/industry-flow/latest | FlowRanking 排行看板 | ✅ |
| GET /api/industry-flow/trend | TrendAnalysis 趋势分析 | ✅ |
| GET /api/industry-flow/resonance | ResonanceSignal 共振信号 | ✅ |
| GET /api/industry-flow/industries | TrendAnalysis 行业下拉 | ✅ 2026-06-25 新增 |
| GET /api/industry-flow/history | TrendAnalysis 折线图 | ✅ 2026-06-25 新增 |

### 后端有但前端没接（5个）

| 接口 | 前端状态 | 说明 |
|------|---------|------|
| POST /api/industry-flow/trend/calculate | api层定义了，无页面调用 | 需加"计算趋势"按钮 |
| GET /api/trade-calendar | 无前端页面 | 需加交易日历UI |
| GET /api/trade-calendar/check | 无前端页面 | 同上 |
| GET /api/trade-calendar/latest | 无前端页面 | 同上 |
| POST /api/industry-flow/collect/{date} | 无前端入口 | 需加补采按钮 |

### 采集相关（阻塞中）

| 接口 | 状态 | 说明 |
|------|------|------|
| POST /api/industry-flow/collect | ❌ IP被封 | 云电脑无法访问同花顺 |
| GET /api/sector-flow/industry-top3 | ❌ 无数据 | SectorFlow无数据源 |
| GET /api/sector-flow/concept-top3 | ❌ 无数据 | 同上 |
| GET /api/sector-flow/fetch | ❌ AKShare源损坏 | |
| POST /api/sector-flow/daily | ❌ AKShare源损坏 | |

---

## 后续开发优先级

| 优先级 | 任务 | 类型 | 预估工时 | 收益 |
|--------|------|------|---------|------|
| **P1** | 前端加趋势计算按钮 | 前端联调 | 30min | 排行看板功能闭环 |
| **P1** | P0-10 统一响应格式 | 后端+前端 | 1.5h | API标准化，错误处理统一 |
| **P2** | 交易日历前端页面 | 前端 | 2h | 3个后端API盘活 |
| **P2** | P0-05 异常重试 | 后端 | 2h | 采集链路健壮性 |
| **P3** | P0-11 采集链路埋点 | 后端 | 1.5h | 可观测性 |
| **P3** | 概念板块页面 | 前端 | 视数据源 | 依赖数据源修复 |

---

## 关键阻塞项

1. **云电脑IP被封**：49.233.146.84 返回 "Nginx forbidden"，Playwright真实采集无法执行。方案：HTTP代理/Mac本地采集/换云服务器
2. **AKShare源损坏**：sector-flow 4个接口无数据源，概念板块页面无法推进
3. **R²阈值临时值**：当前0.1适配模拟数据，真实数据接入后需回调到0.3-0.4
