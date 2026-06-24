---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/A股行业资金流向日度数据体系建设需求清单.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782315747059
    ReservedCode2: ""
---
# A股行业资金流向日度数据体系建设需求清单

> **场景**：2026-06-24，项目V3 Flyway里程碑已完成，现有4张业务表+105条日度明细数据，但采集端瘫痪（AKShare接口损坏）、趋势计算接口500、共振信号空、前端仅有基础排行。用户要求从采集/计算/看板/架构四大维度输出可落地的完整需求清单，颗粒度可直接用于开发任务拆解。

---

## 一、日度数据采集落地方向

### 1.1 合规数据源选型与抓取实现路径

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 1.1.1 | Playwright浏览器自动化采集器 | 可稳定抓取同花顺行业资金流向页面1-4页（约90个行业），单次全量采集耗时<3min，成功率>95% | **核心链路**：①浏览器自动执行JS生成hexin-v反爬cookie（拦截同花顺反爬机制的核心环节，通过`page.evaluate()`注入JS计算逻辑或从cookie/localStorage中提取）→ ②打开行业资金流向页面，等待首屏DOM渲染完成（`page.waitForSelector`定位表格关键元素）→ ③循环点击分页按钮（1→2→3→4页），每次点击后等待DOM重新渲染（`waitForSelector`+网络idle双重判定）→ ④逐页提取表格数据（`page.querySelectorAll`解析`<tr><td>`）→ ⑤从`<a>`标签href提取industry_link和leading_stock_link，正则`\\d{6}`提取leading_stock_code；**反检测**：随机UserAgent+页面停留间隔1-3s+非headless模式+模拟人类滚动行为 | ☐ |
| 1.1.2 | 采集器接口抽象层保持 | 新增Playwright实现类，通过`@ConditionalOnProperty`切换，不影响现有AKShare/OkHttp代码 | `DataFetcher<T>`接口新增`playwright`实现；application.yml配置`ths.fetcher.impl=playwright` | ☐ |

### 1.2 采集任务调度、异常重试、数据去重与校验

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 1.2.1 | Spring @Scheduled定时调度 | 每个交易日15:30自动触发全量采集（收盘后数据稳定） | `@Scheduled(cron = "0 30 15 * * MON-FRI")`；结合交易日历判断是否执行 | ☐ |
| 1.2.2 | 交易日历判定 | 非交易日（周末/法定节假日）不触发采集 | 方案A：内置A股交易日历表（年度预加载）；方案B：采集前调接口判断（需备用） | ☐ |
| 1.2.3 | 异常重试机制 | 单页抓取失败自动重试3次，全量采集部分失败记录并继续 | 指数退避重试（1s/2s/4s）；单行业失败不阻塞整体流程；重试耗尽记录到ths_crawl_log | ☐ |
| 1.2.4 | 数据去重保障 | 同一交易日同一行业不会产生重复记录 | `industry_capital_flow`唯一键`(trade_date, industry_name)` + INSERT ON DUPLICATE KEY UPDATE | ☐ |
| 1.2.5 | 补采与重采机制 | 支持指定日期补采；支持当日全量重采（覆盖旧数据） | `POST /api/industry-flow/collect/{date}`已有接口；Playwright实现类需支持日期参数切换 | ☐ |

### 1.3 原始层数据落库规范

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 1.3.1 | 行业明细表字段补齐 | industry_capital_flow覆盖全量90行业采集需求 | 当前105条仅4个交易日部分行业；Playwright上线后预期日增90行；字段已满足，无需新增 | ☐ |
| 1.3.2 | 成分股明细表`industry_stock_daily`（TODO） | 存储板块成分股日度行情数据，支撑下钻查询 | 字段来源于同花顺成分股涨跌排行页面：现价/涨跌幅/涨跌额/涨速/换手率/量比/振幅/成交额/流通股/流通市值/市盈率/排名；唯一键(trade_date, industry_code, stock_code)；详见附录A | ☐ |

### 1.4 数据完整性、口径一致性保障

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 1.4.1 | 采集完整性校验 | 每次全量采集后校验：行业总数是否=90（或预期值），缺失行业名单记录到日志 | 采集后与行业基准名单（industry_code 881xxx全量）比对；偏差>5%触发告警日志 | ☐ |
| 1.4.2 | 口径一致性文档 | 明确各数据源的单位、时间戳、字段含义差异 | 同花顺页面金额单位=元→落库统一为万元（÷10000）；涨跌幅取页面展示值（已含%）；文档化到技术设计文档 | ☐ |
| 1.4.3 | 跨日数据连续性检查 | 交易日T有数据、T+1无数据时，标记为采集异常 | 定时任务每日16:00检查：最近交易日数据是否到达；缺失时记录到ths_crawl_log（status=MISSING） | ☐ |

---

## 二、数据指标计算与价值挖掘方向

### 2.1 核心指标清单与标准计算口径

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 2.1.1 | 趋势研判指标 | 线性回归斜率+trend_strength信号输出正常 | 修复现有trend接口500 bug；slope>0且R²>0.5→上升趋势；补充trend_strength=slope/avg_net_amount归一化 | ☐ |
| 2.1.2 | 强度排序指标 | 净额排名+净额占流入比+资金集中度可查询 | 净额占比=net_amount/inflow_amount；资金集中度=行业净额/全市场净额；实时计算（无需预存） | ☐ |
| 2.1.3 | 结构分化指标 | 行业间资金分化程度可量化展示 | 分化度=全行业净额标准差/均值（变异系数）；极差=top1净额-bottom1净额；与历史分化度对比 | ☐ |
| 2.1.4 | 异动识别指标 | 资金异动行业自动识别+分级告警 | **双维度异动标准**（替代原σ标准，原因：金融时序非正态分布，绝对值偏离≠有意义异动，排名跃升阈值过于粗放）→ ①**百分位排名维度**：当日净额在近N日历史中的百分位排名，percentile≥95→P0，≥90→P1，≥80→P2；percentile=COUNT(历史日净额<当日净额)/总历史日数×100；②**趋势反转维度**：N日连续同向（连涨/连跌）后首日方向翻转，且翻转幅度>前日净额绝对值的50%，标记为反转异动（reversal_flag=1）；任一维度触发即记入异动表 | ☐ |
| 2.1.5 | 长短周期共振信号 | 短周期5日+长周期22日同向信号输出 | 修复现有resonance空数据bug；短期slope>0且长期slope>0→多头共振；反向→空头共振 | ☐ |
| 2.1.6 | 动量与反转指标 | 行业资金动量排名+反转信号识别 | 动量=近N日净额累积变化率；反转=N日连续同向后首日净额方向翻转；动量衰减=连续3日净额递减 | ☐ |

### 2.2 多周期聚合统计

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 2.2.1 | 标准周期聚合 | 支持5/10/14/22/30/60日周期，结果预存industry_trend_stat | 扩展现有trend计算逻辑，补齐6个标准周期；每次采集后自动触发计算 | ☐ |
| 2.2.2 | 周聚合（5个交易日） | 按自然周聚合资金流数据 | 新建`industry_weekly_stat`表或复用trend_stat增加period_type字段；周起始=周一 | ☐ |
| 2.2.3 | 月聚合（约22个交易日） | 按自然月聚合资金流数据 | 同上，period_type=monthly；月度汇总含累计净额/月均净额/月内最大单日净额 | ☐ |
| 2.2.4 | 自定义区间查询 | 前端可选任意起止日期，后端实时计算区间统计 | 无需预存，基于industry_capital_flow原始数据实时聚合；SQL窗口函数实现；响应<500ms | ☐ |

### 2.3 指标分层存储策略

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 2.3.1 | 三层存储架构落地 | L0原始层/L1指标层/L2聚合层物理分离 | L0=industry_capital_flow(原始日度)；L1=industry_trend_stat(趋势指标)+industry_anomaly_stat(异动指标)；L2=industry_weekly_stat+industry_monthly_stat(周期聚合) | ☐ |
| 2.3.2 | L0→L1计算触发机制 | 采集完成自动触发L1计算，无需人工干预 | Spring事件机制：采集完成→发布CollectDoneEvent→TrendStatListener→计算趋势+异动 | ☐ |
| 2.3.3 | L1→L2定时聚合 | 每周一/每月首个交易日触发L2聚合 | `@Scheduled` + 交易日历判定；聚合结果写入L2表 | ☐ |
| 2.3.4 | 历史回算能力 | 新增指标后，可对历史L0数据批量回算L1 | 提供`POST /api/industry-flow/backfill?startDate=2026-01-01&endDate=2026-06-01`；按日循环调用L1计算逻辑 | ☐ |

### 2.4 数据复用设计

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 2.4.1 | 统一行业主数据 | 行业代码与名称映射关系唯一可信源 | 新建`industry_master`表：industry_code(PK)/industry_name/category(申万一级)/sub_category/enable_flag；与industry_capital_flow通过industry_code关联 | ☐ |
| 2.4.2 | 计算引擎抽象 | 指标计算逻辑与存储解耦，新增指标=新增计算类 | 定义`IndicatorCalculator<Input, Output>`泛型接口；每个指标一个实现类；Spring Bean自动注册 | ☐ |
| 2.4.3 | 指标注册表 | 所有计算指标可枚举、可配置启用/禁用 | 新建`indicator_registry`表或application.yml配置；包含指标名/描述/依赖周期/启用状态 | ☐ |

---

## 三、前端看板设计与前后端协同方向

### 3.1 看板维度划分与展示形式

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 3.1.1 | 全市场概览看板 | 一屏展示：今日资金净流入总额、行业涨跌比、分化度、异动行业数，含环比变化与资金持续性标注 | 顶部统计卡片区（4个KPI，每个KPI附带vs昨日环比箭头+变化值，例：净流入-50亿↑+150亿）；中部行业资金流向热力图（90行业×涨跌幅映射颜色，支持左右滑动查看近5日热力图变化，板块轮动一目了然）；底部净额TOP5/BOTTOM5柱状图（每个行业标注净额连续流入/流出天数，例："证券 连续3日净流入↑"）；环比数据从industry_capital_flow取昨日汇总对比，连续天数从industry_capital_flow按行业往前连续统计同向交易日数 | ☐ |
| 3.1.2 | 行业资金排行看板 | 支持按净额/流入/流出/涨跌幅多维排序，展示TOP N行业 | Ant Design Table + 排序切换；每行展示：排名/行业名/涨跌幅/净额/流入/流出/领涨股；点击行跳转行业详情 | ☐ |
| 3.1.3 | 单行业趋势对比看板 | 选定1-3个行业，展示多周期趋势线对比，含资金-价格背离识别 | ECharts双Y轴折线图：左Y轴=净额，右Y轴=涨跌幅(%)；叠加5日/22日均线；下方展示趋势斜率+R²数值；行业选择器支持搜索（最多3个）；**资金-价格背离标识**：净额连涨N日+涨跌幅连跌/横盘→标注"吸筹背离"；净额连跌+涨跌幅连涨→标注"诱多背离"；数据来源=industry_capital_flow已有字段(net_amount+change_pct)，无需额外数据源 | ☐ |
| 3.1.4 | 长短周期共振看板 | 展示当前多头共振/空头共振行业列表，长短周期可配置 | 散点图：X轴=短期slope，Y轴=长期slope，四个象限分别=多头共振/短期回调/空头共振/短期反弹；颜色映射R²（过滤噪声：方向对但R²低=趋势不稳定）；**周期可配置**：默认5日/22日（传统技术分析周/月周期），但对资金流数据3日/10日可能更灵敏，通过指标注册表(2.4.3)或前端周期选择器让用户自定义短/长周期参数 | ☐ |
| 3.1.5 | 异动监控看板 | 实时展示资金异动行业，百分位+反转双维度分级告警 | 异动列表：行业名/当日净额/百分位排名/异动等级(P0红/P1橙/P2黄)/是否反转标记；时间轴展示近30日异动频次；支持筛选"仅看反转异动" | ☐ |
| 3.1.6 | 板块成分股下钻看板 | 点击行业→展开成分股Top15涨跌排行 | 行业内页：顶部行业概览卡片；中部成分股表格（股票代码/名称/涨跌幅/净额）；底部成分股涨跌分布直方图 | ☐ |

### 3.2 看板核心指标、交互逻辑与数据粒度

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 3.2.1 | 日期选择器全局联动+非交易日过滤 | 所有看板日期联动，切换日期后全部刷新，非交易日不可选 | 全局状态管理（React Context）；默认=最近交易日；支持日历选择+快捷按钮（近5日/近22日/近60日）；**非交易日处理**：日历上非交易日灰置不可选（主路径），兜底：若URL参数或快捷按钮指向非交易日则自动回退到最近前交易日；**交易日历数据来源**：后端提供`GET /api/trade-calendar?year=2026`返回全年交易日列表（依赖1.2.2交易日历表），前端按年缓存；未采集年份可用公开A股交易日历初始化 | ☐ |
| 3.2.2 | 排行看板交互 | 排序字段切换→无刷新重排；TOP N可调（5/10/20/全量） | 前端排序（已加载全量数据时）或后端排序（数据量大时）；分页加载 | ☐ |
| 3.2.3 | 趋势看板交互 | 行业选择器支持搜索+多选（最多3个）；周期切换（5/10/22/60日） | Ant Design Select多选模式；ECharts动态系列增减；周期切换调不同API参数 | ☐ |
| 3.2.4 | 移动端响应式适配 | 所有看板在手机端可正常浏览和交互 | 已有基础适配，需补充：卡片横向滑动、表格缩略模式（核心列优先）、图表touch缩放 | ☐ |

### 3.3 前后端接口设计

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 3.3.1 | 统一响应格式 | 所有API返回 `{success, data, count, timestamp}` 标准结构 | 定义`ApiResponse<T>`泛型类；现有Controller逐步统一（当前混用String+JSON） | ☐ |
| 3.3.2 | 全市场概览API | `GET /api/industry-flow/overview` 返回当日汇总指标 | 聚合计算：全市场净额总和/行业涨跌比/分化度/异动数；数据粒度=日 | ☐ |
| 3.3.3 | 异动监控API | `GET /api/industry-flow/anomaly?level=P0&date=2026-06-24&reversal=1` | 查询`industry_anomaly_stat`表；支持按异动等级+日期+是否反转筛选；返回百分位排名+趋势反转双维度数据 | ☐ |
| 3.3.4 | 成分股下钻API | `GET /api/industry-flow/stocks?industryCode=881157&date=2026-06-24` | 查询`industry_stock_daily`表；支持topN参数；返回成分股涨跌排行 | ☐ |
| 3.3.5 | 多行业趋势对比API | `GET /api/industry-flow/trend-compare?industries=半导体,证券&period=22` | 批量查询industry_trend_stat；返回多行业趋势数据用于前端叠加展示 | ☐ |
| 3.3.6 | 接口性能要求 | 单表查询<200ms，聚合查询<500ms，列表接口支持分页 | 索引优化（已有trade_date+net_amount索引）；大结果集分页（offset+limit）；聚合结果预计算存L2 | ☐ |

---

## 四、系统架构与非功能设计方向

### 4.1 全链路日志体系

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 4.1.1 | 日志分级标准 | ERROR/WARN/INFO/DEBUG四级，生产环境默认INFO | ERROR=采集失败/数据校验失败/服务异常；WARN=重试成功/数据缺失/性能慢查询；INFO=采集完成/计算完成/API调用；DEBUG=详细抓取过程（仅调试时开启） | ☐ |
| 4.1.2 | 采集链路核心埋点 | 采集全流程关键节点有日志覆盖 | 埋点：采集开始(耗时开始计时)→单页抓取成功/失败→校验通过/失败→落库成功/跳过(重复)→采集完成(总耗时+总数)；每个节点输出traceId | ☐ |
| 4.1.3 | 计算链路核心埋点 | 指标计算全流程关键节点有日志覆盖 | 埋点：计算开始→逐行业计算成功/失败→落库成功→计算完成(总耗时+指标数) | ☐ |
| 4.1.4 | ths_crawl_log表结构升级 | 现有字段不足以支撑链路追踪 | 新增字段：trace_id(VARCHAR 32)/phase(采集阶段)/detail(JSON)/retry_count；Flyway V4迁移脚本 | ☐ |

### 4.2 性能优化方案

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 4.2.1 | 批量数据计算优化 | 90行业×6周期=540条趋势统计，单次计算<10s | MyBatis-Plus批量思路：`IService.saveBatch()`/`updateBatchById()`批量写入，单次batchSize=500；计算逻辑纯内存完成最后批量写入；搭配`SqlSessionFactory`设置`executorType=BATCH`进一步减少SQL交互次数 | ☐ |
| 4.2.2 | 接口查询优化 | latest排行接口响应<100ms（数据量<10万行） | 现有索引已覆盖trade_date+net_amount；后续数据量增长时考虑：Redis缓存最新日排行（TTL=1h） | ☐ |
| 4.2.3 | 前端数据加载优化 | 首屏加载<2s，看板切换<500ms | API响应压缩(gzip)；前端路由懒加载；ECharts按需引入（已有）；列表虚拟滚动（数据量大时） | ☐ |
| 4.2.4 | 采集并发优化 | Playwright多Tab并行采集，全量90行业<2min | Playwright BrowserContext多Page并行；控制并发度=3（避免触发反爬）；失败页签重试而非重开 | ☐ |

### 4.3 分层解耦设计

| # | 需求项 | 交付标准 | 核心实现要点 | 状态 |
|---|---|---|---|---|
| 4.3.1 | 采集层与存储层解耦 | 新增数据源（如东方财富）只需新增Fetcher实现类 | 现有`DataFetcher<T>`接口已抽象；Playwright实现类需同样实现该接口；采集结果统一转为`IndustryFlowDTO` | ☐ |
| 4.3.2 | 存储层与计算层解耦 | 新增指标不需要修改采集/存储代码 | 计算层独立Service，通过Spring Event监听采集完成事件；计算类实现`IndicatorCalculator`接口，Bean自动注册 | ☐ |
| 4.3.3 | 计算层与接口层解耦 | 新增看板无需修改计算逻辑 | 接口层只做查询+格式化，不包含计算逻辑；计算结果存L1/L2表，接口直接读；需实时计算的轻量逻辑放接口层 | ☐ |
| 4.3.4 | 接口层与展示层解耦 | 前端可独立迭代，不依赖后端接口字段名变更 | 后端API版本化（/api/v1/ /api/v2/）；响应字段驼峰命名+文档化；前端TypeScript接口类型与API契约同步 | ☐ |
| 4.3.5 | 数据库变更管控 | 所有DDL走Flyway迁移脚本，纳入Git版本控制 | 已落地：V1基线+V2增量已执行；后续V3/V4...按需添加；详见《数据库变更管控指南》 | ✅ |
| 4.3.6 | 配置外部化 | 采集参数/调度时间/计算周期均可通过application.yml配置 | 已有基础：`ths.fetcher.impl`切换采集器；需补充：采集并发度/重试次数/校验阈值/异动σ倍数等配置项 | ☐ |

---

## 附录：新增表结构设计

### A. `industry_stock_daily` — 行业成分股日度明细表（TODO）

> 字段来源：同花顺行业成分股涨跌排行页面，共13列数据

```sql
CREATE TABLE industry_stock_daily (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trade_date       DATE         NOT NULL COMMENT '交易日期',
  industry_code    VARCHAR(20)  NOT NULL COMMENT '行业代码(881xxx)',
  stock_code       VARCHAR(10)  NOT NULL COMMENT '股票代码',
  stock_name       VARCHAR(50)  NOT NULL COMMENT '股票名称',
  current_price    DECIMAL(12,2) DEFAULT NULL COMMENT '现价(元)',
  change_pct       DECIMAL(10,4) DEFAULT NULL COMMENT '涨跌幅%',
  change_amount    DECIMAL(12,2) DEFAULT NULL COMMENT '涨跌额(元)',
  change_speed     DECIMAL(10,4) DEFAULT NULL COMMENT '涨速%',
  turnover_rate    DECIMAL(10,4) DEFAULT NULL COMMENT '换手率%',
  volume_ratio     DECIMAL(10,4) DEFAULT NULL COMMENT '量比',
  amplitude        DECIMAL(10,4) DEFAULT NULL COMMENT '振幅%',
  turnover         DECIMAL(20,4) DEFAULT NULL COMMENT '成交额(万元)',
  float_shares     DECIMAL(20,4) DEFAULT NULL COMMENT '流通股(亿股)',
  float_market_cap DECIMAL(20,4) DEFAULT NULL COMMENT '流通市值(亿元)',
  pe_ratio         DECIMAL(10,2) DEFAULT NULL COMMENT '市盈率(静态,--表示无数据)',
  rank_in_industry INT          DEFAULT NULL COMMENT '行业内排名(序号)',
  created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_date_industry_stock (trade_date, industry_code, stock_code),
  KEY idx_trade_date (trade_date),
  KEY idx_industry_code (industry_code),
  KEY idx_stock_code (stock_code)
) COMMENT='行业成分股日度明细表(来源:同花顺成分股涨跌排行页)';
```

### B. `industry_anomaly_stat` — 行业异动指标表（双维度：百分位排名+趋势反转）

```sql
CREATE TABLE industry_anomaly_stat (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trade_date       DATE         NOT NULL COMMENT '交易日期',
  industry_code    VARCHAR(20)  NOT NULL COMMENT '行业代码',
  industry_name    VARCHAR(50)  NOT NULL COMMENT '行业名称',
  period           INT          NOT NULL DEFAULT 22 COMMENT '参考周期(日)',
  net_amount       DECIMAL(20,4) NOT NULL COMMENT '当日净额(万元)',
  -- 百分位排名维度
  percentile       DECIMAL(5,2) DEFAULT NULL COMMENT '当日净额在近N日中的百分位排名(0-100)',
  hist_count       INT          DEFAULT NULL COMMENT '参考周期内历史数据条数',
  anomaly_level    VARCHAR(5)   DEFAULT NULL COMMENT '异动等级(百分位维度): P0(≥95)/P1(≥90)/P2(≥80)',
  -- 趋势反转维度
  consecutive_days INT          DEFAULT NULL COMMENT '此前连续同向天数(正=连涨,负=连跌)',
  prev_net_amount  DECIMAL(20,4) DEFAULT NULL COMMENT '前日净额(万元)',
  reversal_ratio   DECIMAL(10,4) DEFAULT NULL COMMENT '反转幅度比(当日净额/前日净额绝对值)',
  reversal_flag    TINYINT(1)   DEFAULT 0 COMMENT '是否趋势反转: 1=是 0=否',
  --
  created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_date_industry_period (trade_date, industry_code, period),
  KEY idx_trade_date (trade_date),
  KEY idx_anomaly_level (anomaly_level),
  KEY idx_reversal (reversal_flag)
) COMMENT='行业异动指标表(百分位排名+趋势反转双维度)';
```

### C. `industry_master` — 行业主数据表

```sql
CREATE TABLE industry_master (
  industry_code  VARCHAR(20)  NOT NULL COMMENT '行业代码(881xxx)',
  industry_name  VARCHAR(50)  NOT NULL COMMENT '行业名称',
  category       VARCHAR(30)  DEFAULT NULL COMMENT '申万一级行业分类',
  enable_flag    TINYINT(1)   DEFAULT 1 COMMENT '是否启用: 1=是 0=否',
  created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (industry_code),
  KEY idx_category (category)
) COMMENT='行业主数据表';
```

### D. `industry_weekly_stat` / `industry_monthly_stat` — 周期聚合表

```sql
CREATE TABLE industry_weekly_stat (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  week_start_date DATE         NOT NULL COMMENT '周起始日(周一)',
  week_end_date   DATE         NOT NULL COMMENT '周结束日(周五)',
  industry_code   VARCHAR(20)  NOT NULL COMMENT '行业代码',
  industry_name   VARCHAR(50)  NOT NULL COMMENT '行业名称',
  total_net_amount  DECIMAL(20,4) DEFAULT NULL COMMENT '周累计净额',
  avg_net_amount    DECIMAL(20,4) DEFAULT NULL COMMENT '周均净额',
  max_daily_net     DECIMAL(20,4) DEFAULT NULL COMMENT '周内最大单日净额',
  min_daily_net     DECIMAL(20,4) DEFAULT NULL COMMENT '周内最小单日净额',
  trading_days      INT          DEFAULT 0 COMMENT '实际交易日数',
  avg_change_pct    DECIMAL(10,4) DEFAULT NULL COMMENT '周均涨跌幅',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_week_industry (week_start_date, industry_code),
  KEY idx_week_start (week_start_date)
) COMMENT='行业资金周度聚合表';

CREATE TABLE industry_monthly_stat (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  month_date      DATE         NOT NULL COMMENT '月份(每月1日)',
  industry_code   VARCHAR(20)  NOT NULL COMMENT '行业代码',
  industry_name   VARCHAR(50)  NOT NULL COMMENT '行业名称',
  total_net_amount  DECIMAL(20,4) DEFAULT NULL COMMENT '月累计净额',
  avg_net_amount    DECIMAL(20,4) DEFAULT NULL COMMENT '月均净额',
  max_daily_net     DECIMAL(20,4) DEFAULT NULL COMMENT '月内最大单日净额',
  min_daily_net     DECIMAL(20,4) DEFAULT NULL COMMENT '月内最小单日净额',
  trading_days      INT          DEFAULT 0 COMMENT '实际交易日数',
  avg_change_pct    DECIMAL(10,4) DEFAULT NULL COMMENT '月均涨跌幅',
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_month_industry (month_date, industry_code),
  KEY idx_month_date (month_date)
) COMMENT='行业资金月度聚合表';
```

---

## 需求优先级与分期建议

### P0 — 核心链路打通（优先开发）
- [ ] 1.1.1 Playwright浏览器自动化采集器
- [ ] 1.1.2 采集器接口抽象层保持
- [ ] 1.2.1 定时调度
- [ ] 1.2.2 交易日历判定
- [ ] 1.2.3 异常重试
- [ ] 1.2.4 数据去重
- [ ] 2.1.1 趋势研判指标（修复500 bug）
- [ ] 2.1.5 长短周期共振信号（修复空数据）
- [ ] 3.1.2 行业资金排行看板
- [ ] 3.3.1 统一响应格式
- [ ] 4.1.2 采集链路核心埋点
- [ ] 4.3.1 采集层与存储层解耦

### P1 — 数据价值挖掘（P0完成后推进）
- [ ] 1.3.2 成分股明细表（TODO，字段已设计，待采集链路延伸）
- [ ] 1.4.1 采集完整性校验
- [ ] 1.4.2 口径一致性文档
- [ ] 2.1.2 强度排序指标
- [ ] 2.1.4 异动识别指标
- [ ] 2.1.6 动量与反转指标
- [ ] 2.2.1 标准周期聚合
- [ ] 2.3.1 三层存储架构
- [ ] 3.1.1 全市场概览看板
- [ ] 3.1.5 异动监控看板
- [ ] 3.1.6 成分股下钻看板
- [ ] 3.3.2~3.3.5 新增API
- [ ] 4.3.2~4.3.4 计算层/接口层/展示层解耦

### P2 — 架构成熟度（持续完善）
- [ ] 1.2.5 补采与重采
- [ ] 1.4.3 跨日数据连续性检查
- [ ] 2.1.3 结构分化指标
- [ ] 2.2.2~2.2.3 周月聚合
- [ ] 2.2.4 自定义区间查询
- [ ] 2.3.2~2.3.4 计算触发/定时聚合/历史回算
- [ ] 2.4.1~2.4.3 行业主数据/计算引擎抽象/指标注册
- [ ] 3.1.3 趋势对比看板
- [ ] 3.1.4 共振看板
- [ ] 3.2.1~3.2.4 看板交互优化
- [ ] 3.3.6 接口性能优化
- [ ] 4.1.1 日志分级
- [ ] 4.1.3 计算链路埋点
- [ ] 4.2.1~4.2.4 性能优化
- [ ] 4.3.5 数据库变更管控（已落地✅）
- [ ] 4.3.6 配置外部化

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
