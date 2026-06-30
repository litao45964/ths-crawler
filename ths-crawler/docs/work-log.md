<!--
  诞生背景：
  用户说："给我创建个工作日志MD吧，并上传到项目文件，
  我把每天我们的里程碑内容，以内容追加的形式，写入这个文件"
  
  场景：项目已运行一周，积累了大量里程碑事件，用户希望有一个统一的日志文件，
  以日期为维度追加记录每天的关键进展，方便回溯和复盘。
  
  本文件按时间倒序排列（最新在前），每天一个章节，追加写入。
  
  写入安全强制规范：
  1. 沙箱内维护的 worklog 完整版本为唯一可信源（source of truth）
  2. 更新时追加当日新增内容，历史记录100%原样保留
  3. 校验完整性后，全量写入项目空间同步
  4. 严禁仅提取增量片段直接写入（全量覆盖会丢历史）
  5. 严禁依赖「读取项目空间→修改→回写」链路更新
  6. 严禁生成副版本、备份文件、独立日志文件
  
  标注规则：
  - 类别标签：【里程碑】/【卡点突破】/【思辨探索】，一条一个主标签不强求叠加
  - 重要性等级：P0核心关键/P1重要推进，不要P2
  - 格式：`- [时间] 【类别·等级】 内容`
-->

# ths-crawler 项目工作日志

> 按时间倒序排列，最新在前。格式：`- [时间] 【类别·等级】 内容`
> 【里程碑】/【卡点突破】/【思辨探索】 · P0核心关键/P1重要推进

---

## 2026-06-30（周二）

### 核心提炼

1. iFinD金融数据技能全功能梳理：系统产出13操作全景分类（行情4+基本面2+选股基金3+工具4）+8个CLI调用模板范例（行情/K线/高频/财务指标/估值序列/智能选股/公告/代码转换）+4条使用铁律，建立项目数据源能力知识体系
2. 同花顺金融数据手册五合一归档：5本iFinD参数手册（operations/parameters-basic/parameters-common/parameters-market/parameters-services）上传到项目空间 `/同花顺金融数据手册/`，为数据采集链路提供官方参数参考
3. iFinD技能鉴权缺口确认：定位THS_API_KEY（JWT Token）为技能启用必要条件，待用户提供Token后解锁申万行业分类、智能选股等高级查询能力

### 工作明细

- [20:32] 【里程碑·P1】iFinD金融数据技能全功能梳理：13个操作分四类（行情类real-time-quotation/history-quotation/high-frequency/snap-shot；基本面类basic-data/date-sequence；选股基金类smart-stock-picking/fund-valuation/final-fund-valuation；工具类get-thscode/report-query/get-trade-dates/offset-trade-date/get-error-message），配套8个CLI调用模板范例覆盖完整投资分析场景，建立4条使用铁律（统一_cli_wrapper.py调用/指标名精确/JSON参数整体传/未收录指标不猜）
- [21:13] 【里程碑·P1】同花顺金融数据手册上传项目空间：将iFinD技能本地references/下5本参数手册（operations.md操作索引/parameters-basic.md基础数据参数/parameters-common.md公共参数规范/parameters-market.md行情参数/parameters-services.md服务参数）归档到项目空间 `/同花顺金融数据手册/`，与ths-crawler项目数据采集共享官方参数参考
- [21:43] 【卡点突破·P1】iFinD技能鉴权配置诊断：定位技能调用需要THS_API_KEY（同花顺iFinD HTTP API V2 JWT Token），当前未配置导致技能不可用，需用户提供Token后解锁完整查询能力

---


## 2026-06-28（周日）

### 核心提炼

1. 积分消耗深度分析：梳理主对话/云电脑/后台任务/子session/技能调用五大消耗源，提出"主动换会话+切换模型+后台清理+自定义模型"四层降本策略
2. P0文档多版本失控发现与合并：定位P0任务vs前后端联调现状.md与P0现状更新.md并存问题，以主文档为权威完成状态合并（7→10项✅），消除冗余文件
3. 数据采集全链路闭环：06-22至06-26五日450条数据入库+90条趋势计算(552ms)+三张ECharts图表，光学光电子为唯一斜率+R²双高行业(R²=0.88，斜率24.6万/天)
4. 文档同步工作流规则建立：权威文档→子session更新→验证三步流程，同步生成代码审计差异报告

### 工作明细

- [19:13] 【思辨探索·P1】积分消耗深度分析：梳理五大消耗源（主对话长上下文/云电脑在线计费/后台任务系统/子session派发/技能调用），识别根因是对话上下文指数增长
- [19:14] 【思辨探索·P1】四层降本策略输出：主动换新会话（最立竿见影）→切换便宜模型（日常用Doubao-2.0-pro）→清理后台任务→接入自定义模型（长期方案，仅个人进阶版以上支持）
- [22:23] 【思辨探索·P1】自定义模型接入误解澄清：接入≠自动切换，需在Agent设置手动选择自定义模型，当前仍跑DeepSeek-v4-pro
- [22:35] 【卡点突破·P1】发现P0文档多版本失控：P0任务vs前后端联调现状.md 与 P0现状更新.md 并存，后者是前者的一次状态更新被另起文件导致双版本
- [22:49] 【思辨探索·P1】文档关系定位：P0现状更新.md是6月27日状态刷新（6项改动清单+遗留项），应原地更新而非新建，执行agent图省事导致失控
- [22:52] 【卡点突破·P1】文档合并完成：P0现状更新.md全部状态同步到主文档（日期行+总览表+P0-01~11逐项更新+接口联调表5→0+优先级移除已完成+阻塞项清理），P0现状更新.md标记为冗余
- [23:12] 【思辨探索·P1】数据采集方案从Python Playwright切回Java后端：Python脚本/spawn方案被叫停，改用Java后端直接采集（与ths-crawler项目统一技术栈）
- [23:20] 【里程碑·P0】数据采集全链路闭环：06-22至06-26五个交易日90行业×5日=450条数据入库+90条趋势计算(552ms)，斜率Top3：光学光电子+24.6万(R²=0.88)/元件+22.6万(R²=0.10)/军工装备+12.3万(R²=0.40)
- [23:22] 【里程碑·P1】三张ECharts图表生成：行业净额排名Top15柱状图+重点行业5日趋势折线图+趋势斜率Top15柱状图（R²标注可信度）
- [23:24] 【思辨探索·P1】图表分析结论：整体市场偏弱（77/90行业净流出），光学光电子唯一双高行业（净流入45.2万+斜率可信），半导体06-24爆量313万后连续回落
- [23:46] 【里程碑·P1】子session启动：更新3个文档+生成代码审计差异报告（作为后续工作流锚点）
- [23:50] 【里程碑·P1】文档同步工作流规则建立：权威文档→子session更新→验证三步骤，上传到项目空间，子session文档质量抽查通过

---

## 2026-06-27（周六）

### 核心提炼

1. 6项P0一次性闭环：调度器反重跑保护+统一响应+前端增强(趋势计算/补采/交易日历)+异常重试(指数退避2s/4s/6s)+链路埋点(V5 Flyway+traceId)，44单测全绿，commit d656ebd
2. 部署验证全链路通过：5个API全部验证+前端日历页200+crawl_log埋点traceId贯穿，commit f9e3421
3. dev-workflow-guide版本三合一：发现Git/项目空间三个版本不一致，以Git为基准更新+四层同步，消灭重复版本
4. 云电脑全面部署验证：restart-after-reboot.sh+startup.sh修复+前端build(14.98s/3638模块)+日历页https://ths.thsflow.xyz/calendar
5. npm install策略反思+heredoc方案：沙箱不做重复劳动，云电脑复用已有node_modules；砍掉base64，默认用heredoc

### 工作明细

- [09:05] 前端项目结构查看，确认Phase 1开发范围
- [09:13] 【里程碑·P0】6项P0一次性闭环：调度器反重跑保护放开+SectorCapitalFlowJob接入交易日历+统一响应+前端增强(趋势计算按钮+补采DatePicker+交易日历页面)+异常重试(指数退避2s/4s/6s+封禁不重试)+链路埋点(V5 Flyway+CrawlLogEntity对齐+traceId贯穿)，44单元测试全绿，commit d656ebd
- [09:17] 【里程碑·P0】部署验证全部通过：5个API全部验证+collect 90条17.7s(周六大盘无数据但框架正常)+crawl_log埋点traceId贯穿，commit f9e3421
- [09:31] 【思辨探索·P1】dev-workflow-guide版本对比：发现Git/项目空间三个版本不一致，Git版本是唯一靠谱基线
- [09:35] 【卡点突破·P1】关键发现：项目空间两个版本是中间草稿未同步Git，场景E hotfix与铁律冲突，操作顺序不一致
- [09:40] 【里程碑·P1】dev-workflow-guide更新方案确定：以Git为基准，更新+四层同步（GitHub→云电脑→项目空间→沙箱记忆），消灭重复版本
- [10:15] 【思辨探索·P1】MEMORY.md vs TOOLS.md本质区别分析：MEMORY管行为决策(我是谁/我在做什么/我该怎么做)，TOOLS管操作执行(用这个工具时哪些坑不能踩)
- [10:28] 【思辨探索·P1】npm install策略反思：沙箱从零下载node_modules是浪费，云电脑有现成缓存应复用，铁律①的意图是沙箱不做重复劳动
- [10:53] 验证沙箱git clone完整性：后端Java源码齐全，前端TSX齐全，缺node_modules和.m2
- [10:55] 【思辨探索·P1】base64传递方案反思：砍掉base64，默认用heredoc，小改动直接用sed
- [18:20] 【里程碑·P1】云电脑全面部署验证：restart-after-reboot.sh写入/root/+startup.sh mysqld目录+Java检查修复+前端build(14.98s/3638模块)+日历页200+API 200，发现tsc&&vite build链式调用会卡死改用npx vite build

---

## 2026-06-26（周五）

### 核心提炼

1. 采集器架构重整：全景梳理→发现OkHttpIndustryFetcher是孤儿类→重命名ThsIndustryFetcher→删除4废弃类，Pipeline A/B各留唯一实现，commit a2da266
2. isBlocked误判修复：HTML全文匹配→HTTP状态码检测(403/429=封禁/200=放行)，printStats NPE修复，采集90条通过
3. 铁律⑪-⑯确立：文档路径规范+职责分离+TDD优化(mvn test/mvn verify)+封禁检测(HTTP状态码优先)+Git同步(开发前先pull)，commit 976224f
4. stockIdx列偏移修复：少算"公司家数"列→cells.size()>=10?8:6，领涨股代码覆盖率0%→100%
5. logback+HTML留档+run-it.sh：日志落地+调试原始HTML可追溯+集成测试脚本(pkill残留+timeout 300兜底)

### 工作明细

- [12:35] 【卡点突破·P0】反封禁5风险点分析：isBlocked未调用/缺静默等待/漏检Nginx forbidden/翻页太快(500ms×30)/无反重跑保护，3个必须改业务代码+1个防御性+1个部署策略
- [12:42] 【思辨探索·P1】5风险点分类：isBlocked未调用+静默等待+翻页降速是业务代码问题（单元测试覆盖不了），反重跑是部署策略
- [12:53] 【卡点突破·P0】isBlocked激活方案确定：页面加载后调用+补nginx forbidden检测+3-6s随机等待+翻页1000-1500ms
- [15:22] 【思辨探索·P1】DataFetcher四套实现全景梳理：Pipeline A/B互不干扰，发现OkHttpIndustryFetcher缺少@Component从未被Spring调用
- [15:23] 【卡点突破·P0】关键发现：OkHttpIndustryFetcher是孤儿类（无@Component/无@ConditionalOnProperty），真正需要反封禁的是PlaywrightIndustryFetcher（408行无反封禁保护）
- [15:34] 【思辨探索·P1】DataFetcher实现全景图+两套Pipeline互不干扰分析，配置切换方式明确
- [16:14] 【卡点突破·P1】发现前端走Pipeline B但PlaywrightIndustryFetcher无反封禁保护，前端每次采集都在冒险
- [16:35] 【思辨探索·P1】采集器重命名方案：OkHttpIndustryFetcher→ThsIndustryFetcher（底层是Playwright不是OkHttp）
- [16:41] 【思辨探索·P1】确认OkHttpIndustryFetcher底层是Playwright（Browser/Page/PlaywrightConfig），类名起错了，重命名方案精确化
- [16:51] 【里程碑·P0】采集器架构重整完成：OkHttpIndustryFetcher→ThsIndustryFetcher(@Component+实现DataFetcher)，删除4废弃类(ThsIndustryPageFetcher/OkHttpSectorFlowFetcher/PlaywrightIndustryFetcher及其测试)，Pipeline A/B各留唯一实现，commit a2da266
- [17:16] 【里程碑·P1】铁律⑪确立：非代码文件统一放/ths-crawler/docs/，禁止放/docs/
- [17:18] 【里程碑·P1】文件纠正：P0任务vs前后端联调现状.md移到/ths-crawler/docs/
- [17:29] 【里程碑·P1】铁律⑫确立：/docs/为历史遗留禁止上传/更新/读取/删除，铁律全集上传到项目空间/ths-crawler/docs/作为唯一源
- [17:55] 【卡点突破·P0】isBlocked误判定位：独立Playwright脚本正常，Maven测试里isBlocked()返回true，需排查page.content()在Maven环境下的完整内容
- [18:06] 【思辨探索·P1】isBlocked简化方案：去掉HTML内容检测，只靠HTTP状态码（403/429=封禁，200=正常解析），数据落地优先
- [18:09] 【卡点突破·P0】isBlocked修复完成：删除HTML全文匹配方法，替换为response.status()==403/429检测，同步删除旧测试
- [18:15] 【里程碑·P0】采集验证通过：isBlocked修复后90条数据正常（第1页50条+第2页40条），误判问题彻底解决
- [18:19] 【思辨探索·P1】开发验证提速+定时采集提速方案：mvn test排除IT测试(6s)，mvn verify跑集成测试(40s)，Browser复用自然生效
- [18:24] 【思辨探索·P1】TDD优化：绿过的测试不碰，按改动自动选测试，surefire配置拆分
- [18:30] 【里程碑·P0】铁律⑮确立：mvn test只跑单元测试，mvn verify跑集成测试
- [18:31] 【里程碑·P1】surefire/failsafe配置拆分完成：44单元测试3s全绿，0集成测试
- [18:32] 【里程碑·P1】铁律⑬⑭⑯同步确立：架构变更后同步文档+需求清单与状态文档职责分离+封禁检测优先HTTP状态码，commit 976224f
- [18:42] 【思辨探索·P1】日志留存方案：logback文件输出+原始HTML留档（DEBUG级别），生产环境默认关闭
- [18:51] 【里程碑·P1】logback-spring.xml+HTML留档实现完成（commit b465b75），集成测试生成4个HTML文件验证通过
- [19:21] 【卡点突破·P1】stockIdx列偏移修复：少算"公司家数"列→cells.size()>=10?8:6，领涨股代码覆盖率0%→100%
- [19:38] 【思辨探索·P1】集成测试卡死分析：Playwright残留进程占端口→pkill+timeout方案
- [19:42] 【里程碑·P1】run-it.sh脚本创建：pkill残留+timeout 300兜底+debug-html开关，17秒跑完集成测试，commit cda4ec1

---

## 2026-06-25（周四）

### 核心提炼

1. P0-07趋势修复+P0-08共振修复完成（lookback窗口修正+R²阈值修复+NPE降级+parallelStream异常捕获），趋势计算25行业×4周期=100条
2. P0-10统一响应格式全链路完成（ApiResponse TDD 6/6 + 3 Controller改造 + 前端统一 + 云电脑部署验证通过）
3. 铁律体系从8条扩展至10条：⑨问题诊断优先静态分析/修复验证优先单元测试；⑩非任务范围问题不顺手修先报告
4. SSH连接优化里程碑：20-30s超时→稳定1s（keepalive+ControlMaster复用）
5. 采集方案从OkHttp→回归Playwright，15/15测试全绿但云电脑IP仍被封；新云电脑迁移方案制定

### 工作明细

- [06:22] 【思辨探索·P1】造测试数据推进不依赖采集器的任务：500条覆盖20交易日×25行业，P0-07/08/09/10可独立推进
- [06:26] 【卡点突破·P0】趋势计算0行插入→静态代码分析定位3个bug：lookback窗口用自然日非交易日、trend接口null fallback到LocalDate.now()、parallelStream吞异常
- [06:36] 【里程碑·P0】铁律⑨确立：问题诊断优先静态代码分析，不依赖运行时调试
- [06:43] 【里程碑·P0】趋势计算修复成功：25行业×4个有效周期(period=5/10/14/22)=100条inserted
- [06:56] 【卡点突破·P0】R²阈值问题定位：@Value的rSquaredThreshold被yml覆盖，skipR2=25全被跳过
- [07:08] 【思辨探索·P0】TDD复盘：跳过测试导致R²阈值改3轮+云电脑重启4-5次，单元测试不是运行时调试是静态分析的延伸
- [07:16] 【里程碑·P0】铁律⑨升级："问题诊断优先静态代码分析，修复验证优先单元测试"——流程：静态分析→写失败测试(红灯)→改代码(绿灯)→编译→push→部署→API验证
- [07:36] 【里程碑·P1】industries接口+history接口联调通过，前端TrendAnalysis.tsx改造完成（行业列表+ECharts折线图）
- [08:06] 【里程碑·P0】铁律⑩确立："非任务范围问题不顺手修，先报告再决定"——三条违规总结：顺手修FlowRanking(⑩)、云电脑调试循环(⑧)、跳过单元测试(⑨)
- [09:06] 【里程碑·P1】SSH连接优化：20-30s超时→稳定1s，配置ServerAliveInterval=30+ControlMaster auto+ControlPersist=600
- [09:10] 【思辨探索·P1】P0任务全面盘点：12个P0中7完成/2部分完成/3未开发，5个接口联调通，最大缺口是P0-10统一响应格式
- [09:25] 【里程碑·P1】P0-10统一响应格式开发启动：TDD流程，先写ApiResponse测试(红灯)
- [09:41] 【里程碑·P1】ApiResponse TDD 6/6全绿 + 3个Controller改造完成 + 前端ApiResponse<T>类型统一 + 7个API函数适配，沙箱编译通过，2 commits push GitHub
- [11:21] 【里程碑·P0】P0-10部署验证全部通过：bash(desktop_name="cloud")正确写法确认，git pull+mvn package+重启全通，ApiResponse格式`{"success":true,"data":[...],"count":25,"timestamp":...}`确认生效
- [12:34] 【里程碑·P1】OkHttp直连同花顺验证成功：JS_DATA正常返回20个行业资金数据，TDD红灯12个测试用例创建
- [12:42] 【思辨探索·P1】弃Playwright走OkHttp方案提出：5层防封禁策略（UA轮换+随机延迟+指数退避+封禁检测+单线程串行）
- [12:49] 【卡点突破·P1】发现HTML全量嵌入50行数据，以为不需要翻页——但这是非交易日，交易日90个行业2页翻页绕不过去
- [15:13] 【卡点突破·P0】反思OkHttp方案三个致命错误：试图绕翻页而非正面解决、纯HTTP拿不到hexin-v、分析耗时过多。参照用户本地Selenium成功方案：翻页用DOM指纹检测+正则提取+单位转换
- [15:36] 【里程碑·P1】Playwright采集器15/15单元测试全绿，参照Selenium方案实现翻页+数据提取，push GitHub
- [15:42] 【卡点突破·P0】云电脑Playwright采集超时——table tbody tr未找到，确认IP被封（只封data.10jqka.com.cn/funds/路径，非全站封禁）
- [16:39] 【思辨探索·P1】IP封禁精确确认：同花顺只封data.10jqka.com.cn/funds/资金流向路径，首页和行业板块页面正常
- [17:09] 【思辨探索·P1】新云电脑迁移方案制定：6大类迁移项（系统基础/配置文件/代码数据/重新配置/新IP风险/一键脚本），关键建议换云厂商避免IP段封禁

---

## 2026-06-24（周三）

### 核心提炼

1. Flyway数据库版本化管控+V3新字段升级全链路跑通，从此DDL变更走迁移脚本
2. P0-04交易日历模块完成（TDD 18测试+246交易日数据灌入+3 API验证+Job接入），调度基础设施就绪
3. P0-01/02/12采集器+接口抽象代码全部就绪（Playwright懒加载+反检测+条件注册），但云电脑IP被同花顺封禁成为基础设施卡点
4. A股行业资金流向日度数据体系建设需求清单+技术详细任务拆解书完成，开发主脉络6批次制定
5. P0复盘完成+铁律扩展至8条+"紧急先改再补"正式作废

### 工作明细

- [07:46] 【里程碑·P1】数据库变更管控指南+dev-workflow-guide修正：流程从"先项目空间后GitHub"改为"先push GitHub后write项目空间"，与铁律④一致
- [07:51] 【里程碑·P0】Flyway数据库版本化管控启动：pom.xml+application.yml+V1基线+V2增量脚本+7个文件加3字段
- [08:03] 【里程碑·P0】Flyway V1基线+V2增量迁移成功执行，industry_capital_flow 3新字段到位，API验证通过（commit: 5a2f524）
- [12:35] 【卡点突破·P1】现状盘点：采集端全线瘫痪（AKShare接口损坏），确定Playwright方案为必走之路
- [12:43] 【思辨探索·P1】日度数据4张业务表结构梳理，采集链路明确：Playwright→capital_flow→trend_stat→展示
- [13:03] 【里程碑·P0】A股行业资金流向日度数据体系建设需求清单完成：四大维度×结构化清单（64项），P0/P1/P2分期建议
- [13:06] 【里程碑·P1】技术详细任务拆解书完成：12个P0任务拆解到类名+方法+SQL+前端联动，总预估19h
- [14:46] 【思辨探索·P1】2.2多周期聚合设计：高频路径预计算+低频路径实时算，用存储换查询性能（金融数据系统标准范式）
- [15:26] 【思辨探索·P1】前端日历控件交互设计：非交易日灰置不可选（主路径）+自动回退最近交易日（兜底），依赖1.2.2交易日历表
- [16:10] 【思辨探索·P1】日志留存策略思辨：应用日志30天（量大+磁盘有限）vs ths_crawl_log 1年（量可控+长期分析价值）
- [16:12] 【思辨探索·P1】4.2.1批量计算优化：JdbcTemplate→MyBatis-Plus saveBatch，batchSize=500+executorType=BATCH
- [17:09] 【里程碑·P1】P0-01更新：删除HexinVGenerator类，Playwright浏览器自动执行JS绕过鉴权，新增类从3减到2
- [17:33] 【思辨探索·P1】完整TDD方案设计：6批次TDD节奏，Mock边界原则（Mapper用H2真跑、Fetcher Mock Page接口）
- [17:42] 【里程碑·P0】开发主脉络6批次制定：交易日历→采集器→调度+重试→修bug→前端增强→埋点收尾，总约16h
- [17:52] 【里程碑·P0】P0-04交易日历开发启动：Flyway V4建表+TradeCalendarEntity/Mapper/Service/Controller
- [18:43] 【卡点突破·P0】application.yml编码修复（utf8mb4→UTF-8），3个API初步验证通过
- [18:47] 【里程碑·P0】2026年交易日历数据灌入：365天246个交易日
- [18:53] 【里程碑·P0】P0-04交易日历模块全部验证通过：TDD 18测试全通+3 API验证+IndustryFlowJob接入替换周末判断（3次commit: 17ac94f/659cd45/a9e6cd1）
- [19:01] 【里程碑·P0】P0-01/02/12采集器+接口抽象三合一开发启动：PlaywrightConfig+PlaywrightIndustryFetcher+DataFetcher接口注入
- [19:06] 【卡点突破·P1】沙箱编译超时，按铁律⑦推到云电脑编译
- [19:13] 【里程碑·P1】18个TDD测试全通过（TradeCalendarService）
- [19:46] 【卡点突破·P0】云电脑IP 49.233.146.84被同花顺Nginx封禁——基础设施卡点而非代码问题，3种解封方案待选
- [21:34] 【卡点突破·P0】Playwright采集器代码全就绪并push：懒加载Browser（64s→8s）+反检测+多选择器兼容（commit: e19f2a9），IP封禁未解决
- [21:50] 【里程碑·P1】P0采集器开发复盘完成：45%时间做方向错误调试，铁律违反3次，核心改进"先curl验证可达性再写代码"
- [22:04] 【里程碑·P1】复盘报告转doc文档：P0采集器开发复盘与铁律演进报告_20260624.docx
- [22:39] 【里程碑·P1】env-check/cloud-setup增加Playwright chromium检查与安装段（commit: 037a803）
- [22:46] 【里程碑·P0】铁律体系扩展至8条完整梳理，覆盖沙箱/云电脑/GitHub/项目空间四环境协作
- [23:01] 【里程碑·P0】铁律⑧确立："云电脑禁止写/改任何文件内容，包括脚本，无例外"——"紧急先改再补"正式作废
- [23:10] 【思辨探索·P1】路径不一致问题已被v2工作流+铁律③彻底规避：git pull自带正确目录结构
- [23:47] 【思辨探索·P1】部署脚本shellzdh.sh+shellzdh-cloud.sh创建（commit: 8c0fd77）
- [23:55] 【思辨探索·P1】部署脚本价值反思：流程约束靠铁律不靠脚本，Agent逐条判断>脚本set -e粗暴终止
- [23:57] 【卡点突破·P1】删除部署脚本，结论：流程约束靠铁律（commit: 97f15b4）

---

## 2026-06-23（周二）

### 核心提炼

1. 铁律体系从3条扩展到5条，形成完整的防丢防错闭环
2. 云电脑编辑禁令从"禁止编辑代码"升级为"禁止编辑任何文件"，消除灰色地带
3. 协作链路文档V2三连更，逐步精确化铁律（commit: 4522d26→2b6bcf3→60543da→6eccbc0→ffa8ed0→4ffd846）
4. 建立"主动换会话"机制，防崩溃且省30-40%积分
5. worklog写入安全强制规范确立

### 工作明细

- [18:00] 【里程碑·P1】dev-workflow-guide V2完成并push GitHub，协作链路简化为GitHub分发中枢
- [18:01] 【里程碑·P1】v2工作流模式正式启用，写入MEMORY.md
- [18:14] 【卡点突破·P0】发现V3的10个文件未同步到GitHub，项目空间upload未覆盖成功
- [18:21] 【卡点突破·P0】**铁律违反**：云电脑用sed/vim直接编辑Java代码，正则转义翻车，编译失败。`git checkout -- .`回滚
- [18:22] 【思辨探索·P0】根因分析：不是沙箱崩溃，是"快"置于"对"之上，对文档/代码区别对待导致铁律降级
- [18:27] 【里程碑·P0】**铁律②**："沙箱写代码，云电脑只运行"+开工自检提示词，写入MEMORY/TOOLS
- [18:35] 【里程碑·P0】**铁律③**："沙箱崩溃后代码基线只从GitHub拉取"（commit: 2b6bcf3）
- [18:43] 【思辨探索·P1】铁律精确化：代码基线走GitHub，非代码文件不在GitHub可走项目空间（commit: 60543da）
- [18:56] 【卡点突破·P1】项目空间upload产生重复文件，CLI无delete无法删除。教训：write比upload可靠
- [19:02] 【思辨探索·P1】沙箱崩溃主因是上下文耗尽，预防有限，恢复机制才是关键
- [19:08] 【里程碑·P1】建立"主动换会话"机制：对话重了主动换，收尾后新会话3分钟接上
- [19:31] 【里程碑·P0】**铁律④**："编译通过后先push GitHub再上传项目空间"（commit: 6eccbc0）
- [19:38] 【里程碑·P0】**铁律⑤**："GitHub推送范围判断"+兜底规则"已push文件后续必须同步push"（commit: ffa8ed0）
- [19:47] 【思辨探索·P0】三次犯错共同模式：都是"顺手"心理绕过沙箱
- [23:35] 【里程碑·P0】**铁律①升级**：从"禁止编辑代码"→"禁止编辑任何文件"，云电脑只做消费不做生产（commit: 4ffd846）
- [00:02] 【里程碑·P1】worklog写入安全强制规范确立：沙箱为可信源、全量同步镜像、禁止增量片段直写
- [10:19] 【里程碑·P0】云电脑GitHub网络恢复，push仓库重组commit（85个文件）
- [10:24] 【卡点突破·P1】验证SSH over 443端口方案：TCP连通、SSH认证通过
- [10:33] 生成ed25519 SSH密钥，切换remote从HTTPS到SSH
- [10:39] deploy.sh + README.md git clone改为SSH方式
- [11:03] 【思辨探索·P1】梳理沙箱/云电脑/GitHub/项目空间四套环境定位和协作链条
- [11:14] 【里程碑·P1】配置5个服务开机自启：MySQL/Redis/Spring Boot/Nginx/Cloudflared
- [11:17] 构建Spring Boot fat jar（45M），创建ths-service.sh管理脚本
- [12:28] 确定域名方案：阿里云买域名 + Cloudflare Tunnel
- [13:55] 撰写Cloudflare固定隧道配置指南
- [14:14] 用户完成Cloudflare注册，thsflow.xyz域名Active
- [14:17] cloudflared tunnel login认证成功
- [14:18] 【里程碑·P0】固定隧道启动成功！https://ths.thsflow.xyz 可访问，API验证通过

---

## 2026-06-22（周一）

### 技术方案转型 + Gap分析

- [06:51] GitHub同步方案确认：HTTPS+PAT方式
- [07:06] GitHub同步协议写入MEMORY.md
- [07:11] 创建github-sync-guide.md
- [07:26] 【思辨探索·P1】项目全景回顾：最大Gap是"有系统没数据"
- [18:29] 【思辨探索·P1】讨论扣子计费模式，核心建议"一个会话只做一件事"
- [18:41] 回复末尾加积分预估档位写入MEMORY.md
- [19:11] 【卡点突破·P0】上传需求方案文档，识别5大Gap
- [19:28] 【里程碑·P0】决定放弃Python，改用Playwright浏览器自动化
- [19:47] 【卡点突破·P1】Playwright沙箱失败，同花顺AJAX返回401
- [23:04] 测试数据整理成SQL文件

---

## 2026-06-20（周六）

### 前端移动端适配

- [12:45] 【里程碑·P1】前端移动端响应式适配完成
- [14:31] 【思辨探索·P1】讨论扣子项目模式差异，决定不迁前端到编程项目

---

## 2026-06-18（周四）

### V2编译修复 + 前后端联调 + 铁律确立

- [07:34] 按设计文档创建V2全部16个文件
- [08:17] 前后端分离架构，新增前端项目（React+Vite+AntD+ECharts）
- [09:00] V2编译失败，误判Lombok问题
- [19:23] 【卡点突破·P0】关键发现：编译失败根因不是Lombok，是8个Java类文件缺失
- [19:37] 补齐31个文件，编译通过
- [19:48] 【里程碑·P0】铁律写入TOOLS.md：编译是唯一真相
- [22:50] 四套幂等启动脚本创建，前后端联调成功

---

## 2026-06-17（周三）

### 项目启动 + V1实现

- [00:50] 提出构建Java爬虫项目
- [01:12] 【里程碑·P0】确定核心需求：板块资金流入前三 + 成分股Top15
- [01:19] V1完整项目创建（24个文件），基于AKShare方案
- [02:25] 板块下钻功能
- [02:55] 多日区间回顾
- [16:31] 云环境启动验证通过
- [19:34] 云环境直连同花顺验证通过
