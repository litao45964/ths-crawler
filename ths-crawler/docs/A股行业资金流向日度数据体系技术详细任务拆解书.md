---
AIGC:
    Label: "1"
    ContentProducer: 001191110102MACQD9K64018705
    ProduceID: 1782727546514105_0/project_7652034661715165474-files/ths-crawler/docs/A股行业资金流向日度数据体系技术详细任务拆解书.md
    ReservedCode1: ""
    ContentPropagator: 001191110102MACQD9K64028705
    PropagateID: 1782727546514105#1782315750282
    ReservedCode2: ""
---
# A股行业资金流向日度数据体系技术详细任务拆解书

> **生成场景**：2026-06-24，基于需求清单P0任务，结合现有代码架构（MyBatis-Plus+Spring Boot+React+AntD），逐项拆解到类名、方法、SQL和前端联动，颗粒度可直接用于开发任务分配。

---

## 现有代码架构速览

### 后端包结构
```
com.ths.crawler
├── config/          # MyBatisPlusConfig, AsyncConfig
├── controller/      # IndustryFlowController, SectorFlowController
├── core/            # DataFetcher<T>, DataProcessor<I,O>, FetchContext, FetchResult
├── fetcher/
│   ├── akshare/     # AkshareSectorFlowFetcher（已损坏）
│   └── okhttp/      # OkHttpSectorFlowFetcher, ThsIndustryPageFetcher（401不通）
├── mapper/          # IndustryCapitalFlowMapper, IndustryTrendStatMapper, CrawlLogMapper, SectorCapitalFlowMapper
├── model/
│   ├── dto/         # IndustryFlowDTO, RegressionResult, TrendResonanceDTO, SectorCapitalFlowDTO
│   └── entity/      # IndustryCapitalFlowEntity, IndustryTrendStatEntity, CrawlLogEntity, SectorCapitalFlowEntity
├── processor/       # SectorCapitalFlowProcessor
├── scheduler/       # IndustryFlowJob, SectorCapitalFlowJob
├── service/         # IndustryFlowService, TrendStatService
└── storage/         # DualWriteService
```

### 前端结构
```
ths-crawler-ui/src
├── api/index.ts     # axios封装 + 类型定义 + API调用函数
├── pages/
│   ├── FlowRanking.tsx    # 排行看板
│   ├── TrendAnalysis.tsx  # 趋势看板
│   └── ResonanceSignal.tsx # 共振看板
└── App.tsx          # 路由+深色主题+响应式布局
```

### 现有数据库表
- `industry_capital_flow` — 14字段+3个V3字段，唯一键(trade_date, industry_name)
- `industry_trend_stat` — 13字段，唯一键(trade_date, industry_name, stat_period)
- `ths_sector_capital_flow` — V1遗留，0条数据
- `ths_crawl_log` — 抓取日志
- `flyway_schema_history` — Flyway版本管控

---

## P0-01：Playwright浏览器自动化采集器（1.1.1）

### 目标
实现通过Playwright Java API控制Chrome浏览器，自动抓取同花顺行业资金流向页面1-4页全量数据（约90个行业），替代已失效的OkHttp+Jsoup方案。

### 新增类清单

| 类名 | 包路径 | 职责 |
|------|--------|------|
| `PlaywrightIndustryFetcher` | `com.ths.crawler.fetcher.playwright` | 实现`DataFetcher<IndustryFlowData>`，核心采集器 |
| `PlaywrightConfig` | `com.ths.crawler.config` | Playwright Browser/Context生命周期管理 |

> **不设HexinVGenerator**：浏览器自动化天然绕过所有前端鉴权（hexin-v由浏览器JS自动生成），无需逆向加密逻辑。这是Playwright方案的核心ROI优势。

### 关键方法

```java
// PlaywrightIndustryFetcher
public FetchResult<List<IndustryFlowData>> fetch(FetchContext context)     // 采集入口
private List<IndustryFlowData> fetchAllPages(Page page)                    // 循环翻页+提取
private List<IndustryFlowData> extractTableData(Page page)                // 单页表格DOM提取
private void clickNextPage(Page page, int pageNum)                        // 点击分页按钮+等待渲染
private boolean waitForDataReady(Page page)                               // 等待DOM渲染完成

// PlaywrightConfig
@Bean Browser playwrightBrowser()                                         // 创建Browser实例（单例）
@Bean BrowserContext playwrightContext()                                   // 创建Context（带UA等配置）
@PreDestroy void close()                                                   // 优雅关闭
```

### 数据库操作

**读取**：无

**写入**：通过`IndustryCapitalFlowMapper.batchInsertOrUpdate()`写入`industry_capital_flow`表（已有，无需改表）

```sql
-- 已有SQL（IndustryCapitalFlowMapper.xml）
INSERT INTO industry_capital_flow
    (trade_date, industry_code, industry_name, industry_link, net_amount, inflow_amount,
     outflow_amount, industry_change_pct, leading_stock, leading_stock_code,
     leading_stock_link, leading_stock_pct)
VALUES (...) ON DUPLICATE KEY UPDATE ...
```

### 配置项（application.yml）

```yaml
ths:
  playwright:
    headless: false              # 非headless模式，避免反爬
    timeout: 30000               # 页面加载超时(ms)
    page-wait-ms: 2000           # 翻页后等待DOM渲染(ms)
    max-pages: 4                 # 最大翻页数
    concurrency: 1               # 并发度(先1后优化)
    url: "http://data.10jqka.com.cn/funds/hyzjl/"
```

### 前端联动
- 采集成功后`GET /api/industry-flow/latest`返回数据量从部分行业→全量90行业
- 前端FlowRanking页面展示行业数从~40→~90

### 重点实现思路

**方案选型理由**：Playwright浏览器自动化是ROI最高的方案。hexin-v逆向门槛极高且同花顺不定期更新加密逻辑，维护成本大；浏览器自动化天然绕过所有前端鉴权，跟着页面走就行，页面改版只需改选择器。预期30分钟写脚本，单次可稳定爬50-100页。

1. **导航+等渲染**：`page.navigate(url)` → `page.waitForSelector("table tbody tr")` → 浏览器自动执行所有JS（含hexin-v生成），无需任何逆向。这是本方案最核心的简化点
2. **分页点击+等待**：点击分页按钮后`page.waitForTimeout(2000)`等待DOM重渲染，每页2s延时兼顾稳定性和IP风控
3. **表格DOM提取**：`page.querySelectorAll("table tbody tr")`逐行提取文本，`<a>`标签用`element.attr("href")`提取industry_link，`Pattern.compile("\\d{6}")`从href提取股票代码
4. **资源控制**：Playwright Browser全局单例，每次采集创建新Page，采集完关闭Page；应用关闭时`@PreDestroy`销毁Browser

### Maven依赖

```xml
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.44.0</version>
</dependency>
```

---

## P0-02：采集器接口抽象层保持（1.1.2）

### 目标
Playwright实现类通过`@ConditionalOnProperty`切换，不影响现有AKShare/OkHttp代码。

### 新增/修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `PlaywrightIndustryFetcher` | 新增 | 实现`DataFetcher<List<IndustryFlowData>>`，加`@ConditionalOnProperty(name="ths.fetcher.impl", havingValue="playwright")` |
| `ThsIndustryPageFetcher` | 不改 | 加`@ConditionalOnProperty(name="ths.fetcher.impl", havingValue="okhttp", matchIfMissing=false)` |
| `IndustryFlowService` | 改 | 注入`DataFetcher<List<IndustryFlowData>>`替代直接依赖`ThsIndustryPageFetcher` |

### 关键方法改动

```java
// IndustryFlowService — 改依赖注入
// 之前：private final ThsIndustryPageFetcher pageFetcher;
// 之后：private final DataFetcher<List<IndustryFlowData>> industryFetcher;

public CollectResult collectDailyData() {
    FetchResult<List<IndustryFlowData>> fetchResult = industryFetcher.fetch(
        FetchContext.builder().source("industry_capital_flow").build()
    );
    // ... 后续转换入库逻辑不变
}
```

### 数据库操作
无新增SQL

### 配置项

```yaml
ths:
  fetcher:
    impl: playwright    # 切换为playwright | okhttp | akshare
```

### 前端联动
无直接联动，但切换后采集数据量变化影响排行页展示

### 重点实现思路
1. 现有`DataFetcher<T>`接口已足够抽象，Playwright实现类只需`implements DataFetcher<List<IndustryFlowData>>`
2. `FetchContext`传递`source`标识，Playwright实现从context获取配置参数
3. `FetchResult<T>`统一包装采集结果（success/empty/error），已有类不需改

---

## P0-03：定时调度（1.2.1）

### 目标
每个交易日15:30自动触发全量采集+趋势计算。

### 修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `IndustryFlowJob` | 改 | 合并采集+趋势计算为链式调用 |

### 关键方法

```java
// IndustryFlowJob — 已有，需增强
@Scheduled(cron = "${ths.cron.industry-flow-collect}")
public void dailyCollectAndCalc() {
    if (!tradeCalendarService.isTradeDay(LocalDate.now())) return;  // 1.2.2联动

    log.info("[traceId={}] 定时采集开始", traceId);
    // Step1: 采集
    CollectResult collectResult = flowService.collectDailyData();
    // Step2: 采集成功则触发趋势计算
    if (collectResult.isSuccess()) {
        trendService.calculateDailyTrendStat();
    }
    log.info("[traceId={}] 定时采集+计算完成: {}", traceId, collectResult);
}
```

### 数据库操作
无新增SQL

### 配置项

```yaml
ths:
  cron:
    industry-flow-collect: "0 30 15 ? * MON-FRI"  # 已有
```

### 前端联动
无直接联动

### 事务边界设计（关键⚠️）

**禁止在`dailyCollectAndCalc()`上加`@Transactional`**。原因：
- Playwright浏览器操作属网络IO，单次采集可能30s-2min，若持有DB连接会导致连接池耗尽→Connection Timeout→服务不可用
- 计算失败不应回滚已入库的采集数据——采集数据是"原始事实"，入库即保留；计算是"派生数据"，随时可重跑

**正确的事务拆分：**
```
dailyCollectAndCalc()           ← 无@Transactional，纯编排方法
  ├── collectDailyData()        ← 事务1（@Transactional）：仅覆盖"转换+入库"部分
  │     fetch() → 无事务（Playwright IO）→ 转换+batchInsertOrUpdate（事务边界）
  └── calculateDailyTrendStat() ← 事务2（@Transactional）：独立事务，失败不影响采集数据
```

- 采集事务边界：`collectDailyData()`内部将Playwright抓取（无事务）和数据入库（`@Transactional`）分开，事务仅包裹入库操作
- 计算事务边界：独立事务，即使失败采集数据已安全入库
- 失败处理：计算失败时记录日志+ths_crawl_log，后续可手动/自动重跑计算

### 重点实现思路
1. 采集→计算链式调用，保证采集成功才计算
2. **事务隔离**：编排方法无事务，采集入库和趋势计算各持独立短事务，避免长IO持有连接
3. 后续引入Spring Event解耦：采集完发布`CollectDoneEvent`，`TrendStatListener`监听后触发计算（为2.3.2预留）

---

## P0-04：交易日历判定（1.2.2）

### 目标
非交易日（周末/法定节假日）不触发采集，前端日期选择器非交易日不可选。

### 新增类

| 类名 | 包路径 | 职责 |
|------|--------|------|
| `TradeCalendarService` | `com.ths.crawler.service` | 交易日判定+缓存 |
| `TradeCalendarEntity` | `com.ths.crawler.model.entity` | 交易日历表实体 |
| `TradeCalendarMapper` | `com.ths.crawler.mapper` | 交易日历Mapper |
| `TradeCalendarController` | `com.ths.crawler.controller` | 前端查询交易日历API |

### 新增表DDL（Flyway V4）

```sql
CREATE TABLE trade_calendar (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  trade_date   DATE         NOT NULL COMMENT '交易日期',
  is_open      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否交易日: 1=是 0=否',
  year         INT          NOT NULL COMMENT '年份',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_trade_date (trade_date),
  KEY idx_year (year)
) COMMENT='A股交易日历表';
```

### 关键方法

```java
// TradeCalendarService
public boolean isTradeDay(LocalDate date)                           // 判定是否交易日
public List<LocalDate> getTradeDays(int year)                      // 获取全年交易日列表
public LocalDate getLatestTradeDay(LocalDate date)                  // 获取最近交易日(含当日或往前)
public LocalDate getPreviousTradeDay(LocalDate date)                // 获取前一交易日
public void initCalendar(int year)                                  // 初始化年度交易日历(手动触发)

// TradeCalendarController
@GetMapping("/api/trade-calendar")                                  // 前端日历控件查询
public ApiResponse<List<LocalDate>> getTradeDays(@RequestParam int year)
```

### 数据库操作SQL

```sql
-- 查询某日是否交易日
SELECT is_open FROM trade_calendar WHERE trade_date = #{date};

-- 查询全年交易日列表
SELECT trade_date FROM trade_calendar WHERE year = #{year} AND is_open = 1 ORDER BY trade_date;

-- 查询最近交易日
SELECT MAX(trade_date) FROM trade_calendar WHERE trade_date <= #{date} AND is_open = 1;

-- 查询前一交易日
SELECT MAX(trade_date) FROM trade_calendar WHERE trade_date < #{date} AND is_open = 1;
```

### 前端联动

```typescript
// api/index.ts 新增
export async function fetchTradeCalendar(year: number) {
  const res = await api.get<{ success: boolean; data: string[] }>(
    '/api/trade-calendar', { params: { year } }
  );
  return res.data;
}

// 日期选择器组件：灰置非交易日，兜底自动回退最近交易日
// 联动3.2.1的全局日期选择器
```

### 重点实现思路
1. **初始化**：首次启动时通过`initCalendar()`加载年度交易日历，数据来源可用公开A股交易日历CSV或API
2. **缓存**：全年交易日列表缓存在内存`ConcurrentHashMap<Integer, List<LocalDate>>`，按年缓存
3. **兜底**：若trade_calendar表无数据，退化为`dayOfWeek != SATURDAY && dayOfWeek != SUNDAY`（现有逻辑）

---

## P0-05：异常重试（1.2.3）

### 目标
单页抓取失败自动重试3次，全量采集部分失败记录并继续。

### 修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `PlaywrightIndustryFetcher` | 改 | 增加单页重试逻辑 |
| `IndustryFlowService` | 改 | 采集结果记录部分失败信息 |

### 关键方法

```java
// PlaywrightIndustryFetcher — 单页重试
private List<IndustryFlowData> fetchPageWithRetry(Page page, int pageNum, int maxRetry) {
    for (int attempt = 1; attempt <= maxRetry; attempt++) {
        try {
            clickNextPage(page, pageNum);
            waitForDataReady(page);
            List<IndustryFlowData> data = extractTableData(page);
            if (!data.isEmpty()) return data;
        } catch (Exception e) {
            log.warn("[traceId={}] 第{}页第{}次抓取失败: {}", traceId, pageNum, attempt, e.getMessage());
            if (attempt < maxRetry) {
                int delay = (int) Math.pow(2, attempt) * 1000;  // 指数退避: 2s/4s
                Thread.sleep(delay);
            }
        }
    }
    log.error("[traceId={}] 第{}页重试{}次后仍失败", traceId, pageNum, maxRetry);
    return Collections.emptyList();  // 单页失败不阻塞整体
}

// IndustryFlowService — 采集结果增强
public static class CollectResult {
    private boolean success;
    private int totalRows;
    private int insertedRows;
    private int failedPages;      // 新增：失败页数
    private String failedDetail;  // 新增：失败详情
    private long costMs;
}
```

### 数据库操作

```sql
-- ths_crawl_log 记录重试（4.1.2联动，此处先简易记录）
INSERT INTO ths_crawl_log (source, status, record_count, cost_ms, error_msg, trade_date)
VALUES ('industry_capital_flow', 'PARTIAL_SUCCESS', 80, 45000, '第3页3次重试失败', '2026-06-24');
```

### 配置项

```yaml
ths:
  playwright:
    max-retry: 3              # 单页最大重试次数
    retry-delay-base: 1000    # 退避基数(ms)
```

### 前端联动
无直接联动

### 重点实现思路
1. **指数退避**：2s→4s，避免短间隔重试触发反爬
2. **单页失败不阻塞**：第2页失败继续抓第3、4页，最终合并已成功页的数据入库
3. **CollectResult增强**：`failedPages>0`时status记为`PARTIAL_SUCCESS`，写入ths_crawl_log

---

## P0-06：数据去重（1.2.4）

### 目标
同一交易日同一行业不会产生重复记录。

### 现状
已有保障：`industry_capital_flow`唯一键`(trade_date, industry_name)` + `batchInsertOrUpdate`使用`ON DUPLICATE KEY UPDATE`

### 验证SQL

```sql
-- 验证无重复
SELECT trade_date, industry_name, COUNT(*) as cnt
FROM industry_capital_flow
GROUP BY trade_date, industry_name
HAVING cnt > 1;
-- 预期：空结果

-- 验证唯一键存在
SHOW INDEX FROM industry_capital_flow WHERE Non_unique = 0;
```

### 前端联动
无

### 结论
**已满足，无需额外开发**。仅验证唯一键和ON DUPLICATE KEY UPDATE生效。

---

## P0-07：趋势研判指标修复（2.1.1）

### 目标
修复`GET /api/industry-flow/trend`接口500 bug，数据库有5条记录但查询逻辑有问题。

### 问题定位

```java
// IndustryFlowController.getTrend() — 现有bug
LocalDate latestDate = trendMapper.selectByIndustryAndDateRange(industry,
        LocalDate.now().minusDays(1), LocalDate.now())  // ← 问题1：只查最近2天，数据可能是更早日期
        .stream().findFirst()
        .map(IndustryTrendStatEntity::getTradeDate)
        .orElse(null);
if (latestDate == null) {
    latestDate = LocalDate.now();  // ← 问题2：fallback到今天，但今天可能没数据
}
IndustryTrendStatEntity stat = trendMapper.selectByIndustryDatePeriod(
        industry, latestDate, period);  // ← 问题3：用industryName查，但入库可能用的是中文全名vs简称不匹配
```

### 修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `IndustryFlowController` | 改 | 修复getTrend查询逻辑 |
| `IndustryTrendStatMapper` | 改 | 新增selectLatestDateByIndustry方法 |
| `IndustryTrendStatMapper.xml` | 改 | 新增SQL |

### 关键方法

```java
// IndustryFlowController.getTrend() — 修复后
@GetMapping("/trend")
public ApiResponse<IndustryTrendStatEntity> getTrend(
        @RequestParam String industry,
        @RequestParam(defaultValue = "22") Integer period) {
    // 1. 从trend_stat表查该行业最新日期（不限最近2天）
    LocalDate latestDate = trendMapper.selectLatestDateByIndustry(industry);
    if (latestDate == null) {
        return ApiResponse.fail("未找到行业[" + industry + "]的趋势数据");
    }
    // 2. 用最新日期+周期查趋势
    IndustryTrendStatEntity stat = trendMapper.selectByIndustryDatePeriod(industry, latestDate, period);
    if (stat == null) {
        return ApiResponse.fail("未找到行业[" + industry + "]在" + period + "日周期的趋势数据");
    }
    return ApiResponse.success(stat);
}
```

### 新增SQL

```xml
<!-- IndustryTrendStatMapper.xml 新增 -->
<select id="selectLatestDateByIndustry" resultType="java.time.LocalDate">
    SELECT MAX(trade_date)
    FROM industry_trend_stat
    WHERE industry_name = #{industryName}
</select>
```

### 前端联动

```typescript
// TrendAnalysis.tsx — 无需改API调用方式，但需处理新的ApiResponse格式(3.3.1联动)
// 现有错误：trend接口500导致趋势页无数据
// 修复后：正确返回趋势数据，ECharts折线图可渲染
```

### 重点实现思路
1. **根本原因**：原代码限死查最近2天，但测试数据trade_date可能是更早日期；fallback到`LocalDate.now()`更是查不到
2. **修复策略**：用`MAX(trade_date)`取该行业最新统计日期，不限死天数范围
3. **行业名称匹配**：确认入库的industry_name与前端传入一致（中文全名，如"半导体"而非"881121"）

---

## P0-08：长短周期共振信号修复（2.1.5）

### 目标
修复`GET /api/industry-flow/resonance`返回空数组。

### 问题定位

```java
// TrendStatService.calculateResonance() — 空数据原因推断
// 该方法需要从industry_trend_stat查数据，但：
// 1. trend_stat只有5条记录（4个行业×1个周期？），可能没有同时覆盖短+长周期
// 2. industry_name匹配问题：trend_stat里存的名称与flow表可能不一致
// 3. 采集端瘫痪 → 没有新数据写入trend_stat → 共振计算找不到数据
```

### 修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `TrendStatService` | 改 | 修复共振计算逻辑+增加日志 |
| `IndustryFlowController` | 改 | 共振接口增加调试日志 |

### 关键方法

```java
// TrendStatService.calculateResonance() — 增加诊断
public List<TrendResonanceDTO> calculateResonance(int shortPeriod, int longPeriod) {
    // 1. 查询所有有数据的行业
    List<String> industries = trendMapper.selectDistinctIndustries();
    log.info("共振计算：行业列表 size={}", industries.size());
    if (industries.isEmpty()) {
        log.warn("industry_trend_stat无数据，无法计算共振");
        return Collections.emptyList();
    }

    // 2. 查最新交易日
    LocalDate latestDate = flowMapper.selectLatestTradeDate();
    log.info("共振计算：最新交易日={}", latestDate);

    // 3. 逐行业查短+长周期趋势
    List<TrendResonanceDTO> result = new ArrayList<>();
    for (String industry : industries) {
        IndustryTrendStatEntity shortStat = trendMapper.selectByIndustryDatePeriod(
                industry, latestDate, shortPeriod);
        IndustryTrendStatEntity longStat = trendMapper.selectByIndustryDatePeriod(
                industry, latestDate, longPeriod);

        if (shortStat == null || longStat == null) {
            log.debug("行业[{}]缺少短周期({})或长周期({})数据", industry, shortPeriod, longPeriod);
            continue;  // 跳过不完整数据
        }
        // ... 构建TrendResonanceDTO
    }
    return result;
}
```

### 数据验证SQL

```sql
-- 检查trend_stat有哪些行业和周期
SELECT industry_name, stat_period, trade_date
FROM industry_trend_stat
ORDER BY industry_name, stat_period;

-- 检查是否同时有5日和22日数据
SELECT industry_name,
       GROUP_CONCAT(stat_period ORDER BY stat_period) as periods
FROM industry_trend_stat
GROUP BY industry_name
HAVING periods LIKE '%5%' AND periods LIKE '%22%';
```

### 前端联动
- 共振页面`ResonanceSignal.tsx`：修复后散点图有数据点可渲染
- 当前空数组导致页面显示"暂无共振信号"

### 重点实现思路
1. **核心问题**：trend_stat数据不完整（只有5条），可能只有单周期。Playwright采集器上线后数据会自然增长
2. **短期修复**：确保`trend/calculate`接口能正确为所有行业×所有周期生成trend_stat记录
3. **诊断先行**：先跑上面验证SQL确认数据缺口，再决定是修代码还是等数据

---

## P0-09：行业资金排行看板增强（3.1.2）

### 目标
前端排行看板支持多维度排序+TOP N可调，后端API增加排序字段和分页。

### 后端修改

| 类名 | 变更 | 职责 |
|------|------|------|
| `IndustryFlowController` | 改 | latest接口增加分页+排序校验 |
| `IndustryFlowService` | 改 | getLatestFlow增加分页支持 |

### 关键方法

```java
// IndustryFlowController — 增强latest接口
@GetMapping("/latest")
public ApiResponse<List<IndustryFlowDTO>> getLatest(
        @RequestParam(defaultValue = "10") Integer topN,
        @RequestParam(defaultValue = "net_amount") String orderBy,
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize) {
    // orderBy白名单校验
    Set<String> allowedOrderBy = Set.of("net_amount", "inflow_amount", "outflow_amount", "industry_change_pct");
    if (!allowedOrderBy.contains(orderBy)) {
        return ApiResponse.fail("不支持的排序字段: " + orderBy);
    }
    List<IndustryFlowDTO> list = flowService.getLatestFlow(topN, orderBy, page, pageSize);
    return ApiResponse.success(list, list.size());
}
```

### 数据库操作

```xml
<!-- IndustryCapitalFlowMapper.xml — 排序SQL（防注入⚠️） -->
<!-- 禁止使用 ORDER BY ${orderBy}，${}是直接拼接存在SQL注入高危漏洞 -->
<!-- 即使Controller层做了白名单校验，Service层复用该SQL时可能绕过校验 -->
<!-- 正确做法：Mapper XML层用<choose>枚举所有合法值，非法值fallback到默认排序 -->
<select id="selectLatestWithOrder" resultType="IndustryCapitalFlowEntity">
    SELECT * FROM industry_capital_flow
    WHERE trade_date = (SELECT MAX(trade_date) FROM industry_capital_flow)
    ORDER BY
    <choose>
        <when test="orderBy == 'net_amount'">net_amount</when>
        <when test="orderBy == 'inflow_amount'">inflow_amount</when>
        <when test="orderBy == 'outflow_amount'">outflow_amount</when>
        <when test="orderBy == 'industry_change_pct'">industry_change_pct</when>
        <otherwise>net_amount</otherwise>
    </choose>
    DESC
    LIMIT #{pageSize} OFFSET #{offset}
</select>
```

**三层防注入体系：**
1. Controller层白名单校验（用户体验，返回友好错误提示）
2. Service层防御性校验（逻辑兜底，防止内部调用绕过Controller）
3. Mapper XML层`<choose>`枚举（最终防线，即使前两层都漏了也不可能注入）

### 前端联动

```typescript
// FlowRanking.tsx — 增强
// 1. 排序字段切换：Ant Design Table的column sorter
// 2. TOP N可调：Radio.Group (5/10/20/全量)
// 3. 点击行业行→跳转趋势页（联动3.1.3）

// api/index.ts — fetchLatestFlow增加参数
export async function fetchLatestFlow(topN = 10, orderBy = 'net_amount', page = 1, pageSize = 20) {
  const res = await api.get('/api/industry-flow/latest', {
    params: { topN, orderBy, page, pageSize },
  });
  return res.data;
}
```

### 重点实现思路
1. **90条数据前端排序即可**，无需后端分页；但预留分页参数
2. **排序白名单**：防止SQL注入，只允许4个预定义字段
3. **三层防注入**：Controller白名单→Service防御性校验→Mapper XML `<choose>`枚举（最终防线），杜绝`${orderBy}`拼接注入
4. **行业名可点击**：`Table.Column.render`包裹`<Link to={`/trend?industry=${record.industryName}`}>`

---

## P0-10：统一响应格式（3.3.1）

### 目标
所有API返回`{success, data, count, timestamp}`标准结构，替代当前混用String+JSON。

### 新增类

| 类名 | 包路径 | 职责 |
|------|--------|------|
| `ApiResponse<T>` | `com.ths.crawler.model.dto` | 统一响应封装 |

### 关键方法

```java
@Data
@Builder
public class ApiResponse<T> {
    private boolean success;
    private T data;
    private Integer count;
    private Long timestamp;
    private String message;  // 失败时的错误信息

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true).data(data)
                .count(data instanceof List ? ((List<?>) data).size() : 1)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> success(T data, int count) {
        return ApiResponse.<T>builder()
                .success(true).data(data).count(count)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> fail(String message) {
        return ApiResponse.<T>builder()
                .success(false).message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}
```

### 修改类（逐Controller替换）

| 类名 | 变更 |
|------|------|
| `IndustryFlowController` | 所有方法返回类型从`String`→`ApiResponse<T>` |
| `SectorFlowController` | 同上 |

### Controller改动示例

```java
// 之前
@GetMapping("/latest")
public String getLatest(...) {
    List<IndustryFlowDTO> list = flowService.getLatestFlow(topN, orderBy);
    return JSON.toJSONString(Map.of("success", true, "data", list, "count", list.size()));
}

// 之后
@GetMapping("/latest")
public ApiResponse<List<IndustryFlowDTO>> getLatest(...) {
    List<IndustryFlowDTO> list = flowService.getLatestFlow(topN, orderBy);
    return ApiResponse.success(list);
}
```

### 前端联动

```typescript
// api/index.ts — 所有API调用适配新格式
interface ApiResponse<T> {
  success: boolean;
  data: T;
  count: number;
  timestamp: number;
  message?: string;
}

// 之前
export async function fetchLatestFlow(topN: number, orderBy: string) {
  const res = await api.get<{ success: boolean; data: IndustryFlowItem[]; count: number }>(
    '/api/industry-flow/latest', { params: { topN, orderBy } }
  );
  return res.data;
}

// 之后
export async function fetchLatestFlow(topN: number, orderBy: string) {
  const res = await api.get<ApiResponse<IndustryFlowItem[]>>(
    '/api/industry-flow/latest', { params: { topN, orderBy } }
  );
  if (!res.data.success) throw new Error(res.data.message);
  return res.data;
}
```

### 重点实现思路
1. **一次性改完**：3个Controller方法量不大，一次性全改比渐进式更干净
2. **前端适配**：前端`api/index.ts`统一用`ApiResponse<T>`类型，各页面组件无需改
3. **错误处理**：`ApiResponse.fail()`返回的message前端用`message.error()`展示

---

## P0-11：采集链路核心埋点（4.1.2）

### 目标
采集全流程关键节点有日志覆盖，每个节点输出traceId。

### 修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `IndustryFlowService` | 改 | 采集流程增加traceId+分阶段计时 |
| `PlaywrightIndustryFetcher` | 改 | 每页采集输出traceId日志 |
| `CrawlLogEntity` | 改 | 新增traceId/phase/detail/retryCount字段（4.1.4联动，此处先用已有字段简易实现） |

### 关键方法

```java
// IndustryFlowService.collectDailyData() — 增加埋点
public CollectResult collectDailyData() {
    String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    long start = System.currentTimeMillis();

    log.info("[traceId={}] 采集开始", traceId);

    // Step1: 抓取
    long fetchStart = System.currentTimeMillis();
    FetchResult<List<IndustryFlowData>> fetchResult = industryFetcher.fetch(context);
    long fetchCost = System.currentTimeMillis() - fetchStart;
    log.info("[traceId={}] 抓取完成: count={}, cost={}ms", traceId, fetchResult.getData().size(), fetchCost);

    // Step2: 转换+入库
    long saveStart = System.currentTimeMillis();
    int inserted = flowMapper.batchInsertOrUpdate(entities);
    long saveCost = System.currentTimeMillis() - saveStart;
    log.info("[traceId={}] 入库完成: inserted={}, cost={}ms", traceId, inserted, saveCost);

    // Step3: 记录日志
    crawlLogService.log(traceId, "industry_capital_flow", "SUCCESS",
            fetchResult.getData().size(), System.currentTimeMillis() - start, null);

    return CollectResult.success(fetchResult.getData().size(), inserted, System.currentTimeMillis() - start);
}
```

### 数据库操作

```sql
-- 现有ths_crawl_log写入（简易版，4.1.4升级后再加新字段）
INSERT INTO ths_crawl_log (source, status, record_count, cost_ms, error_msg, trade_date)
VALUES ('industry_capital_flow', 'SUCCESS', 90, 45000, NULL, '2026-06-24');
```

### 前端联动
无直接联动

### 重点实现思路
1. **traceId贯穿**：从Controller→Service→Fetcher，同一次采集共享traceId
2. **分阶段计时**：抓取耗时/入库耗时/总耗时，便于定位瓶颈
3. **日志分级**：采集成功INFO，重试WARN，失败ERROR

---

## P0-12：采集层与存储层解耦（4.3.1）

### 目标
新增数据源只需新增Fetcher实现类，通过配置切换。

### 修改类

| 类名 | 变更 | 职责 |
|------|------|------|
| `IndustryFlowService` | 改 | 依赖`DataFetcher<List<IndustryFlowData>>`接口而非具体实现 |
| `PlaywrightIndustryFetcher` | 新增 | `@ConditionalOnProperty`注册 |
| `ThsIndustryPageFetcher` | 改 | 加`@ConditionalOnProperty`注解 |

### 关键方法

```java
// IndustryFlowService — 接口化注入
@Service
@RequiredArgsConstructor
public class IndustryFlowService {
    private final DataFetcher<List<IndustryFlowData>> industryFetcher;  // 接口注入
    private final IndustryCapitalFlowMapper flowMapper;
    // ...
}

// PlaywrightIndustryFetcher — 条件注册
@Component
@ConditionalOnProperty(name = "ths.fetcher.impl", havingValue = "playwright")
public class PlaywrightIndustryFetcher implements DataFetcher<List<IndustryFlowData>> {
    // ...
}

// ThsIndustryPageFetcher — 条件注册
@Component
@ConditionalOnProperty(name = "ths.fetcher.impl", havingValue = "okhttp", matchIfMissing = false)
public class ThsIndustryPageFetcher {
    // ... 保留但默认不激活
}
```

### 数据库操作
无

### 前端联动
无

### 重点实现思路
1. **Spring自动注入**：同一接口只有一个实现类被Spring容器加载，不会冲突
2. **配置切换**：改`application.yml`的`ths.fetcher.impl`即可切换，无需改代码
3. **后续扩展**：新增"东方财富"数据源只需加`EastMoneyIndustryFetcher implements DataFetcher`+`@ConditionalOnProperty(havingValue="eastmoney")`

---

## P0任务依赖关系与开发顺序

```
P0-04 交易日历 ─────────────────────────┐
                                         │
P0-01 Playwright采集器 ← P0-02 接口抽象  │
        │                                │
        ├── P0-03 定时调度 ←─────────────┘
        ├── P0-05 异常重试
        ├── P0-06 数据去重（已满足，仅验证）
        ├── P0-11 采集链路埋点
        └── P0-12 采集层解耦（与P0-02合并）

P0-10 统一响应格式 ← P0-09 排行看板增强
                         │
P0-07 趋势修复 ←─────────┘
P0-08 共振修复
```

### 建议开发顺序

| 批次 | 任务 | 预估工时 | 说明 |
|------|------|---------|------|
| 1 | P0-04 交易日历 | 2h | 基础设施，其他任务依赖 |
| 2 | P0-01 + P0-02 + P0-12 | 8h | 采集核心链路，三者强耦合合并开发 |
| 3 | P0-03 + P0-05 + P0-06 | 2h | 调度+重试+去重验证，采集器完成后串联 |
| 4 | P0-10 统一响应格式 | 1.5h | 后端API标准化，前端适配 |
| 5 | P0-07 + P0-08 | 2h | 修复现有bug，数据到位后验证 |
| 6 | P0-09 排行看板增强 | 2h | 前端增强，依赖统一响应格式 |
| 7 | P0-11 采集链路埋点 | 1.5h | 可穿插在各批中逐步完善 |

**总预估：约19h（纯编码时间，不含联调调试）**

---

## Flyway迁移规划

| 版本 | 内容 | 关联P0任务 |
|------|------|-----------|
| V4 | `CREATE TABLE trade_calendar` | P0-04 |
| V5 | `ALTER TABLE ths_crawl_log ADD trace_id VARCHAR(32), ADD phase VARCHAR(20), ADD detail JSON, ADD retry_count INT` | P0-11（4.1.4联动） |

---

## TDD测试驱动开发规范

> **策略**：分层TDD——核心业务逻辑严格TDD（红→绿→重构），数据层和接口层先实现后补测试，采集层Mock契约测试。

### 测试框架

| 框架 | 用途 | 来源 |
|------|------|------|
| JUnit 5 | 测试引擎 | spring-boot-starter-test自带 |
| Mockito 5 | Mock依赖 | spring-boot-starter-test自带 |
| AssertJ | 流式断言（可读性优于JUnit原生） | spring-boot-starter-test自带 |
| H2 Database | Mapper层集成测试内存库 | 新增test依赖 |
| Spring Boot Test | @MybatisPlusTest / @WebMvcTest | spring-boot-starter-test自带 |

### 测试目录结构

```
src/test/java/com/ths/crawler/
├── unit/                          ← 纯逻辑，零Spring容器，毫秒级
│   ├── service/
│   │   ├── TradeCalendarServiceTest.java
│   │   ├── TrendStatServiceTest.java
│   │   └── AnomalyDetectionServiceTest.java
│   └── fetcher/
│       └── PlaywrightIndustryFetcherTest.java   ← Mock Page
│
├── integration/                   ← 启动部分Spring容器，秒级
│   ├── mapper/
│   │   ├── IndustryCapitalFlowMapperTest.java   ← @MybatisPlusTest + H2
│   │   └── TradeCalendarMapperTest.java
│   └── service/
│       └── IndustryFlowServiceTest.java         ← Mock Fetcher + 真实Mapper
│
└── contract/                      ← 接口契约，验证输入输出格式
    └── api/
        └── IndustryFlowControllerTest.java      ← @WebMvcTest
```

### 测试数据管理

```
src/test/resources/
├── schema-h2.sql          ← H2建表DDL（从Flyway V1/V2复制，兼容H2语法）
├── data-trade-calendar.sql ← 预置交易日历测试数据（2026年）
└── data-industry-flow.sql  ← 预置行业资金流测试数据（5条，复现现有数据状态）
```

### Mock边界原则

```
Mock这条线：Controller → Service → Fetcher/Mapper
            ↑          ↑          ↑
         可Mock      可Mock     尽量不Mock（用H2真跑）

1. Mapper层用H2内存库真跑SQL，不Mock——SQL写错Mock测不出来
2. Service层Mock Mapper返回——专注业务逻辑
3. Fetcher层Mock Playwright的Page接口——不启动浏览器
4. Controller层@WebMvcTest + Mock Service——专注HTTP契约
5. 跨层集成用@SpringBootTest + H2——验证事务边界
```

### 逐P0任务的TDD方案

#### P0-04 交易日历 — 严格TDD（红→绿→重构）

```java
// 红灯阶段：先写测试用例
@Test void 周六不是交易日()
@Test void 周日不是交易日()
@Test void 国庆节不是交易日()
@Test void 调休补班是交易日()              // 如周六补班
@Test void 普通工作日是交易日()
@Test void getLatestTradeDay_当日是交易日_返回当日()
@Test void getLatestTradeDay_当日是周末_返回上周五()
@Test void getLatestTradeDay_当日是国庆_返回9月30日()
@Test void getPreviousTradeDay_正常情况()
@Test void 缓存命中_不查数据库()           // 验证ConcurrentHashMap缓存

// 绿灯阶段：写TradeCalendarService实现让测试通过
// 重构阶段：抽取日期查询模板方法，消除重复代码

// Mock策略：Mock TradeCalendarMapper，返回预设日期列表
```

#### P0-01 Playwright采集器 — Mock契约测试

```java
// 不是传统TDD，而是"接口契约测试"

@Test void fetch_正常页面_返回90条数据()
  // mock Page: 模拟4页×25行表格数据
  // 验证: fetchAllPages被调用1次, extractTableData被调用4次
  // 验证: 返回数据size=90

@Test void fetch_第2页超时_重试3次后跳过()
  // mock clickNextPage第2页抛TimeoutError
  // 验证: 返回数据size=65(跳过25), failedPages=1

@Test void fetch_页面无数据_返回empty()
  // mock extractTableData返回空列表
  // 验证: FetchResult.isSuccess()=false

@Test void extractTableData_解析a标签_提取industry_link和股票代码()
  // mock ElementHandle, 验证href解析逻辑

// Mock策略：Mock Playwright的Page/BrowserContext（接口层Mock，不启动真浏览器）
```

#### P0-03 调度+事务 — 集成验证（关键边界测试）

```java
// 不适合TDD，但必须验证事务边界

@Test void dailyCollectAndCalc_编排方法无Transactional注解()
  // 反射检查方法上无@Transactional

@Test void collectDailyData_入库失败_不回滚Playwright已抓取数据()
  // mock batchInsertOrUpdate抛Exception
  // 验证: Playwright fetch()被调用了（数据已抓到内存）

@Test void calculateDailyTrendStat_计算失败_采集数据仍在库中()
  // mock calculateDailyTrendStat抛Exception
  // 验证: industry_capital_flow表数据仍在

// Mock策略：@SpringBootTest + Mock Fetcher + H2真实DB
```

#### P0-07 趋势bug修复 — 经典TDD（先复现后修复）

```java
@Test void getTrend_数据库只有3天前数据_应返回3天前的趋势()
  // 模拟: trade_date=3天前, 非今天非昨天
  // 期望: 返回该条数据（当前bug: 返回500/404）

@Test void getTrend_行业不存在_返回fail提示()
  // 期望: ApiResponse.fail("未找到行业[xxx]的趋势数据")

// 绿灯阶段：修复selectLatestDateByIndustry逻辑
// 验证：原来500的测试通过
```

#### P0-09 排序白名单 — 安全TDD（攻击驱动测试）

```java
@Test void orderBy合法值_net_amount_正常排序()
@Test void orderBy合法值_inflow_amount_正常排序()
@Test void orderBy注入攻击_DROP_TABLE_白名单拦截()
@Test void orderBy注入攻击_1OR1_白名单拦截()
@Test void orderBy注入攻击_空字符串_fallback默认排序()
@Test void mapper层_choose枚举_非法值_fallback到net_amount()
  // 直接测Mapper XML的<choose>逻辑
```

#### P0-08 共振修复 — 诊断驱动

```java
// 第一步：跑诊断SQL确认数据缺口（排查）
// 第二步：确认问题后写测试

@Test void calculateResonance_短周期5长周期22数据齐全_返回共振结果()
@Test void calculateResonance_某行业只有短周期_跳过该行业()
@Test void calculateResonance_无任何趋势数据_返回空列表()
```

### TDD执行节奏

| P0任务 | TDD策略 | 测试先行？ | 说明 |
|--------|---------|-----------|------|
| P0-04 交易日历 | 严格TDD | ✅ 先写测试 | 纯逻辑，零外部依赖，TDD最佳切入点 |
| P0-01 采集器 | Mock契约测试 | ✅ 先定义契约 | Mock Page接口，不启动浏览器 |
| P0-03 调度+事务 | 集成验证 | ❌ 先实现后测关键边界 | 事务边界必须验证 |
| P0-05 异常重试 | 先实现后补测试 | ❌ | 骨架代码，逻辑简单 |
| P0-06 数据去重 | 仅验证 | N/A | 已满足，跑验证SQL即可 |
| P0-07 趋势修复 | 经典TDD | ✅ 先复现bug | 红灯=复现bug，绿灯=修复 |
| P0-08 共振修复 | 诊断驱动 | ❌ 先诊断后补测试 | 数据缺口未明，先排查 |
| P0-09 排行增强 | 安全TDD | ✅ 攻击驱动 | 注入用例先行 |
| P0-10 统一响应 | 先实现后补测试 | ❌ | 格式化代码，逻辑简单 |
| P0-11 采集埋点 | 先实现后补测试 | ❌ | 可穿插在各批中逐步完善 |
| P0-12 采集层解耦 | 先实现后补测试 | ❌ | 与P0-02合并开发 |

---

> 本内容由 Coze AI 生成，请遵循相关法律法规及《人工智能生成合成内容标识办法》使用与传播。
