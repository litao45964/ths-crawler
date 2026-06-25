package com.ths.crawler.fetcher.okhttp;

import com.microsoft.playwright.*;
import com.ths.crawler.config.PlaywrightConfig;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 行业资金流向采集器 — 参照 Selenium 成功方案，用 Playwright 浏览器自动化
 * <p>
 * 核心思路（来自本地 Selenium 验证通过的方案）：
 * 1. 浏览器引擎绕过 hexin-v（浏览器自动处理前端鉴权）
 * 2. 翻页：点击分页按钮 + DOM 指纹检测（getFirstRowFingerprint + waitForDomChange）
 * 3. 数据提取：正则从 &lt;a href&gt; 提取行业代码和领涨股代码
 * 4. 单位转换：亿×10000→万
 * 5. 反检测配置：disable-blink-features、excludeSwitches
 * <p>
 * HTML 解析（parseHtmlTable）用 Jsoup 处理测试中的 HTML 片段
 * 实际采集（fetch）用 Playwright 浏览器自动化
 */
@Slf4j
public class OkHttpIndustryFetcher {

    // ============ 配置（通过反射注入） ============
    private int retryCount = 3;
    private int requestDelayMin = 500;
    private int requestDelayMax = 1500;
    private int maxPages = 2;

    // ============ 正则（参照 Selenium 方案） ============
    /** 行业代码正则：从 URL 中提取 /code/881121/ */
    static final Pattern INDUSTRY_CODE_PAT = Pattern.compile("/code/(\\d+)/");
    /** 领涨股代码正则：从 URL 中提取 /688981/ */
    static final Pattern STOCK_CODE_PAT = Pattern.compile("/(\\d{6})/");

    // ============ UA 池 ============
    private static final List<String> USER_AGENTS = List.of(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/121.0"
    );

    private final Random random = new Random();

    // ============ Playwright（懒加载） ============
    private Browser browser;
    private PlaywrightConfig playwrightConfig;

    // ============ 构造器 ============
    public OkHttpIndustryFetcher() {
        // 无参构造（测试用，配置通过反射注入）
    }

    public OkHttpIndustryFetcher(PlaywrightConfig playwrightConfig) {
        this.playwrightConfig = playwrightConfig;
    }

    // ==================== 测试引用的方法 ====================

    public String getSource() {
        return "industry_capital_flow";
    }

    /**
     * 解析 HTML 表格（Jsoup，测试用）
     * <p>
     * 测试HTML格式（8列）：排名 / 行业名(含链接) / 涨跌幅 / 流入 / 流出 / 净额 / 领涨股(含链接) / 领涨涨幅
     * 实际页面格式（10列）：序号 / 行业 / 行业指数 / 涨跌幅 / 流入 / 流出 / 净额 / 公司家数 / 领涨股 / 领涨涨幅
     */
    List<IndustryFlowData> parseHtmlTable(String html) {
        List<IndustryFlowData> result = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        // 先找 tbody tr，降级到 table tr
        Elements rows = doc.select("table tbody tr");
        if (rows.isEmpty()) {
            rows = doc.select("table tr");
        }

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 7) continue;

            try {
                IndustryFlowData d = new IndustryFlowData();
                d.rank = parseInt(cells.get(0).text());

                // ── 行业名称 + 链接 + 代码（Jsoup版）──
                org.jsoup.nodes.Element industryCell = cells.get(1);
                Elements indLinks = industryCell.select("a");
                if (!indLinks.isEmpty()) {
                    org.jsoup.nodes.Element a = indLinks.get(0);
                    d.industryName = a.text().trim();
                    d.industryLink = a.attr("href");
                    d.industryCode = extractFromUrl(d.industryLink, INDUSTRY_CODE_PAT);
                } else {
                    d.industryName = industryCell.text().trim();
                }
                if (d.industryName.isEmpty()) continue;

                // ── 数值列（兼容8列和10列格式）──
                int colOffset = cells.size() >= 10 ? 1 : 0; // 10列时多一列"行业指数"
                d.industryChangePct = parsePct(cells.get(2 + colOffset).text());
                d.inflowAmount = parseYi(cells.get(3 + colOffset).text());
                d.outflowAmount = parseYi(cells.get(4 + colOffset).text());
                d.netAmount = parseYi(cells.get(5 + colOffset).text());

                // ── 领涨股 + 链接 + 代码 + 涨幅（Jsoup版）──
                int stockIdx = 6 + colOffset;
                if (cells.size() > stockIdx) {
                    org.jsoup.nodes.Element stockCell = cells.get(stockIdx);
                    Elements stLinks = stockCell.select("a");
                    if (!stLinks.isEmpty()) {
                        org.jsoup.nodes.Element a = stLinks.get(0);
                        d.leadingStock = a.text().trim();
                        d.leadingStockLink = a.attr("href");
                        d.leadingStockCode = extractFromUrl(d.leadingStockLink, STOCK_CODE_PAT);
                    } else {
                        d.leadingStock = stockCell.text().trim();
                    }
                }
                if (cells.size() > stockIdx + 1) {
                    d.leadingStockPct = parsePct(cells.get(stockIdx + 1).text());
                }

                result.add(d);
            } catch (Exception ignored) {
                // skip malformed row
            }
        }
        return result;
    }

    /**
     * 解析 JS_DATA 获取行业名→代码映射
     */
    Map<String, String> parseJsData(String html) {
        Map<String, String> codeMap = new LinkedHashMap<>();
        int start = html.indexOf("var JS_DATA");
        if (start < 0) return codeMap;

        int bracketStart = html.indexOf("[", start);
        int bracketEnd = html.indexOf("]", bracketStart);
        if (bracketStart < 0 || bracketEnd < 0) return codeMap;

        String jsonStr = html.substring(bracketStart, bracketEnd + 1);
        try {
            com.alibaba.fastjson2.JSONArray arr = com.alibaba.fastjson2.JSON.parseArray(jsonStr);
            for (int i = 0; i < arr.size(); i++) {
                Object item = arr.get(i);
                if (!(item instanceof com.alibaba.fastjson2.JSONObject)) continue;
                com.alibaba.fastjson2.JSONObject obj = (com.alibaba.fastjson2.JSONObject) item;
                String name = obj.getString("name");
                String addr = obj.getString("addr");
                if (name != null && addr != null) {
                    Matcher m = INDUSTRY_CODE_PAT.matcher(addr);
                    if (m.find()) {
                        codeMap.put(name, m.group(1));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析JS_DATA失败: {}", e.getMessage());
        }
        return codeMap;
    }

    IndustryCapitalFlowEntity convertToEntity(IndustryFlowData data, LocalDate tradeDate) {
        return IndustryCapitalFlowEntity.builder()
                .tradeDate(tradeDate)
                .industryCode(data.industryCode != null ? data.industryCode : "")
                .industryName(data.industryName)
                .industryLink(data.industryLink != null ? data.industryLink : "")
                .netAmount(data.netAmount != null ? data.netAmount : BigDecimal.ZERO)
                .inflowAmount(data.inflowAmount != null ? data.inflowAmount : BigDecimal.ZERO)
                .outflowAmount(data.outflowAmount != null ? data.outflowAmount : BigDecimal.ZERO)
                .industryChangePct(data.industryChangePct)
                .leadingStock(data.leadingStock != null ? data.leadingStock : "")
                .leadingStockCode(data.leadingStockCode != null ? data.leadingStockCode : "")
                .leadingStockLink(data.leadingStockLink != null ? data.leadingStockLink : "")
                .leadingStockPct(data.leadingStockPct)
                .build();
    }

    String getRandomUserAgent() {
        return USER_AGENTS.get(random.nextInt(USER_AGENTS.size()));
    }

    long calculateDelay() {
        return requestDelayMin + random.nextInt(requestDelayMax - requestDelayMin + 1);
    }

    boolean isBlocked(String html) {
        if (html == null) return true;
        String lower = html.toLowerCase();
        return lower.contains("403 forbidden")
                || lower.contains("429 too many requests")
                || lower.contains("访问过于频繁")
                || lower.contains("ip已被封禁")
                || lower.contains("chameleon");
    }

    // ==================== 核心采集（Playwright，参照 Selenium 方案） ====================

    private static final String PAGE_URL = "http://data.10jqka.com.cn/funds/hyzjl/";
    private static final String SEP = "=".repeat(120);

    /**
     * 执行全量采集（1~2页，约90个行业）
     * 参照 Selenium 方案的翻页策略：点击按钮 + DOM 指纹检测
     */
    public List<IndustryCapitalFlowEntity> fetch() {
        long start = System.currentTimeMillis();
        log.info("\n" + SEP);
        log.info("  10jqka 行业资金流向 — Playwright 全量抓取");
        log.info("  提取：行业代码 | 行业链接 | 领涨股代码 | 领涨股链接");
        log.info(SEP);

        Browser browser = null;
        Page page = null;
        try {
            // 1. 获取或创建 Browser
            if (playwrightConfig != null) {
                browser = playwrightConfig.getBrowser();
            }
            if (browser == null) {
                // 降级：独立创建 Playwright（参照 Selenium 反检测配置）
                Playwright playwright = Playwright.create();
                BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(List.of(
                                "--no-sandbox", "--disable-dev-shm-usage",
                                "--disable-gpu", "--window-size=1920,1080",
                                "--disable-blink-features=AutomationControlled",
                                "--disable-features=IsolateOrigins,site-per-process",
                                "--disable-setuid-sandbox"
                        ));
                browser = playwright.chromium().launch(launchOptions);
            }

            // 2. 创建 Page
            Browser.NewPageOptions pageOptions = new Browser.NewPageOptions()
                    .setUserAgent(getRandomUserAgent());
            page = browser.newPage(pageOptions);
            page.setDefaultTimeout(30000);

            // 反 webdriver 检测（参照 Selenium 方案）
            page.addInitScript("""
                Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});
                Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh', 'en']});
                """);

            // 3. 导航到页面
            log.info("🌐 导航到: {}", PAGE_URL);
            page.navigate(PAGE_URL, new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            log.info("   DOM 加载完成，等待表格渲染...");

            // 等待表格出现（多种选择器容错，参照 Selenium 方案）
            boolean tableFound = false;
            for (String selector : List.of(
                    "table tbody tr", "table.m-table tbody tr",
                    ".J-ajax-table tbody tr", "table tr")) {
                try {
                    page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(10000));
                    log.info("   选择器命中: {}", selector);
                    tableFound = true;
                    break;
                } catch (Exception ignored) {}
            }
            if (!tableFound) {
                log.warn("⚠️ 表格选择器未命中，等待额外 8 秒...");
                page.waitForTimeout(8000);
            }
            page.waitForTimeout(2000);
            log.info("✅ 页面加载完成");

            // 4. 解析第1页
            List<IndustryCapitalFlowEntity> page1 = extractTableData(page);
            log.info("📋 第1页: {} 条", page1.size());
            printPageData(page1, 1);

            // 5. 翻页 — 参照 Selenium 方案的 DOM 指纹检测
            List<IndustryCapitalFlowEntity> page2 = clickPage2(page);
            log.info("📋 第2页: {} 条", page2.size());
            printPageData(page2, 2);

            // 6. 合并
            List<IndustryCapitalFlowEntity> all = new ArrayList<>(page1);
            all.addAll(page2);

            long cost = System.currentTimeMillis() - start;
            printStats(all, cost);

            return all;

        } catch (Exception e) {
            log.error("❌ 采集异常: {}", e.getMessage(), e);
            return Collections.emptyList();
        } finally {
            if (page != null) {
                try { page.close(); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * 翻页到第2页 — 参照 Selenium 方案
     * <p>
     * 策略：
     * 1. 记录第1页第一行指纹
     * 2. 点击第2页按钮
     * 3. 轮询等待 DOM 指纹变化（最多30次×500ms=15秒）
     */
    private List<IndustryCapitalFlowEntity> clickPage2(Page page) {
        try {
            // 记录第1页指纹
            String oldFp = getFirstRowFingerprint(page);
            log.info("🔍 第1页指纹: {}", oldFp.length() > 50 ? oldFp.substring(0, 50) + "..." : oldFp);

            // 找第2页按钮（多种选择器）
            ElementHandle page2Btn = null;
            for (String selector : List.of(
                    ".J-ajax-page a", ".pagination a", "a[page]",
                    "a.page-link", ".m-page a")) {
                for (ElementHandle a : page.querySelectorAll(selector)) {
                    if ("2".equals(a.innerText().trim())) {
                        page2Btn = a;
                        break;
                    }
                }
                if (page2Btn != null) break;
            }

            if (page2Btn == null) {
                log.warn("⚠️ 未找到第2页按钮，尝试 JS 方式");
                return clickPage2ByJs(page, oldFp);
            }

            log.info("🔗 点击第2页按钮");
            page2Btn.click();

            // 轮询等待 DOM 真正更新
            boolean changed = waitForDomChange(page, oldFp);
            log.info("   DOM 变化: {}", changed ? "✅ 检测到新数据" : "⚠️ 可能未更新");

            if (changed) {
                page.waitForTimeout(1000);
            }

            return extractTableData(page);

        } catch (Exception e) {
            log.warn("⚠️ 翻页异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<IndustryCapitalFlowEntity> clickPage2ByJs(Page page, String oldFp) {
        try {
            String result = (String) page.evaluate(
                "var links = document.querySelectorAll('.J-ajax-page a, a[page]');" +
                "for (var i=0; i<links.length; i++) {" +
                "  if (links[i].innerText.trim()==='2') { links[i].click(); return 'clicked'; }" +
                "} return 'notfound';");
            log.info("   JS 点击: {}", result);
            if ("clicked".equals(result)) {
                waitForDomChange(page, oldFp);
                page.waitForTimeout(1000);
                return extractTableData(page);
            }
        } catch (Exception e) {
            log.warn("   JS 方式失败: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    /**
     * 等待 DOM 变化（参照 Selenium 方案）
     */
    private boolean waitForDomChange(Page page, String oldFp) {
        for (int i = 0; i < 30; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            String newFp = getFirstRowFingerprint(page);
            if (!newFp.equals(oldFp) && !newFp.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取第一行指纹（参照 Selenium 方案）
     */
    private String getFirstRowFingerprint(Page page) {
        try {
            List<ElementHandle> rows = page.querySelectorAll("table tbody tr");
            if (rows.isEmpty()) return "";
            return rows.get(0).innerText();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 从当前页面提取表格数据
     * <p>
     * 实际页面列（10列）：序号 / 行业 / 行业指数 / 涨跌幅 / 流入 / 流出 / 净额 / 家数 / 领涨股 / 领涨涨幅
     * 参照 Selenium 方案的正则提取方式
     */
    private List<IndustryCapitalFlowEntity> extractTableData(Page page) {
        List<IndustryCapitalFlowEntity> result = new ArrayList<>();
        LocalDate tradeDate = LocalDate.now();

        List<ElementHandle> rows = page.querySelectorAll("table tbody tr");
        if (rows.isEmpty()) {
            rows = page.querySelectorAll("table tr");
        }

        for (ElementHandle row : rows) {
            List<ElementHandle> cells = row.querySelectorAll("td");
            if (cells.size() < 7) continue;

            try {
                IndustryCapitalFlowEntity entity = new IndustryCapitalFlowEntity();
                entity.setTradeDate(tradeDate);

                // ── 行业名称 + 行业链接 + 行业代码（参照 Selenium 正则提取）──
                List<ElementHandle> industryLinks = cells.get(1).querySelectorAll("a");
                if (!industryLinks.isEmpty()) {
                    String href = industryLinks.get(0).getAttribute("href");
                    entity.setIndustryName(industryLinks.get(0).innerText().trim());
                    entity.setIndustryLink(href != null ? href : "");
                    entity.setIndustryCode(extractFromUrl(href, INDUSTRY_CODE_PAT));
                } else {
                    entity.setIndustryName(cells.get(1).innerText().trim());
                }

                if (entity.getIndustryName().isEmpty()) continue;

                // ── 数值列（兼容8列和10列）──
                int colOffset = cells.size() >= 10 ? 1 : 0;
                entity.setIndustryChangePct(parsePct(cells.get(2 + colOffset).innerText().trim()));
                entity.setInflowAmount(parseYi(cells.get(3 + colOffset).innerText().trim()));
                entity.setOutflowAmount(parseYi(cells.get(4 + colOffset).innerText().trim()));
                entity.setNetAmount(parseYi(cells.get(5 + colOffset).innerText().trim()));

                // ── 领涨股 + 领涨股链接 + 领涨股代码（参照 Selenium 正则提取）──
                int stockIdx = 6 + colOffset;
                if (cells.size() > stockIdx) {
                    List<ElementHandle> stockLinks = cells.get(stockIdx).querySelectorAll("a");
                    if (!stockLinks.isEmpty()) {
                        String stockHref = stockLinks.get(0).getAttribute("href");
                        entity.setLeadingStock(stockLinks.get(0).innerText().trim());
                        entity.setLeadingStockLink(stockHref != null ? stockHref : "");
                        entity.setLeadingStockCode(extractFromUrl(stockHref, STOCK_CODE_PAT));
                    } else {
                        entity.setLeadingStock(cells.get(stockIdx).innerText().trim());
                    }
                }
                if (cells.size() > stockIdx + 1) {
                    entity.setLeadingStockPct(parsePct(cells.get(stockIdx + 1).innerText().trim()));
                }

                result.add(entity);

            } catch (Exception e) {
                log.debug("解析行失败: {}", e.getMessage());
            }
        }
        return result;
    }

    /**
     * 从 URL 中提取代码（参照 Selenium 方案）
     */
    private String extractFromUrl(String url, Pattern pattern) {
        if (url == null || url.isEmpty()) return "";
        Matcher m = pattern.matcher(url);
        return m.find() ? m.group(1) : "";
    }

    // ==================== 输出 ====================

    private void printPageData(List<IndustryCapitalFlowEntity> list, int pageNum) {
        log.info("\n{}", "-".repeat(130));
        log.info("  📄 第{}页 行业资金流向明细", pageNum);
        log.info("{}", "-".repeat(130));
        log.info("  {:<4} {:<14} {:<8} {:>14} {:>14} {:>14} {:>7}  {:<10} {:<8} {:>7}",
                "序号", "行业名", "代码", "净额(万)", "流入(万)", "流出(万)", "涨跌%", "领涨股", "代码", "领涨%");
        log.info("  {}", "-".repeat(125));

        for (int i = 0; i < list.size(); i++) {
            IndustryCapitalFlowEntity d = list.get(i);
            log.info("  {:<4} {:<14} {:<8} {:>14} {:>14} {:>14} {:>7}  {:<10} {:<8} {:>7}",
                    i + 1,
                    truncate(d.getIndustryName(), 14),
                    d.getIndustryCode(),
                    fmt(d.getNetAmount()),
                    fmt(d.getInflowAmount()),
                    fmt(d.getOutflowAmount()),
                    pct(d.getIndustryChangePct()),
                    truncate(d.getLeadingStock(), 10),
                    d.getLeadingStockCode(),
                    pct(d.getLeadingStockPct()));
        }
        log.info("  {}", "-".repeat(130));
    }

    private void printStats(List<IndustryCapitalFlowEntity> list, long costMs) {
        int withCode = (int) list.stream().filter(d -> !d.getIndustryCode().isEmpty()).count();
        int withLink = (int) list.stream().filter(d -> !d.getIndustryLink().isEmpty()).count();
        int withSCode = (int) list.stream().filter(d -> !d.getLeadingStockCode().isEmpty()).count();
        int withSLink = (int) list.stream().filter(d -> !d.getLeadingStockLink().isEmpty()).count();
        int total = list.size();

        log.info("\n" + SEP);
        log.info("  📊 数据完整性统计");
        log.info(SEP);
        log.info("  总条数:     {}", total);
        log.info("  行业代码:   {} / {}  ({}%)", withCode, total, total > 0 ? String.format("%.0f", 100.0 * withCode / total) : "0");
        log.info("  行业链接:   {} / {}  ({}%)", withLink, total, total > 0 ? String.format("%.0f", 100.0 * withLink / total) : "0");
        log.info("  领涨股代码: {} / {}  ({}%)", withSCode, total, total > 0 ? String.format("%.0f", 100.0 * withSCode / total) : "0");
        log.info("  领涨股链接: {} / {}  ({}%)", withSLink, total, total > 0 ? String.format("%.0f", 100.0 * withSLink / total) : "0");
        log.info("  总耗时:     {} ms", costMs);

        if (total > 0) {
            IndustryCapitalFlowEntity sample = list.get(0);
            log.info("  抽样（第1条）:");
            log.info("    行业: {} ({})", sample.getIndustryName(), sample.getIndustryCode());
            log.info("    行业链接: {}", sample.getIndustryLink());
            log.info("    领涨股: {} ({})", sample.getLeadingStock(), sample.getLeadingStockCode());
            log.info("    领涨股链接: {}", sample.getLeadingStockLink());
        }
        log.info(SEP + "\n");
    }

    // ==================== 工具方法 ====================

    private BigDecimal parseYi(String s) {
        if (s == null || s.isEmpty() || s.equals("--")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s.replace(",", "").replace("亿", "").trim())
                    .multiply(new BigDecimal("10000")).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parsePct(String s) {
        if (s == null || s.isEmpty() || s.equals("--")) return null;
        try {
            return new BigDecimal(s.replace("%", "").replace("+", "").trim());
        } catch (Exception e) {
            return null;
        }
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) return "-";
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static String fmt(BigDecimal v) {
        if (v == null) return "-";
        return String.format("%,.0f", v);
    }

    private static String pct(BigDecimal v) {
        if (v == null) return "-";
        return String.format("%+.2f%%", v);
    }

    // ==================== 内部类：IndustryFlowData ====================

    public static class IndustryFlowData {
        public int rank;
        public String industryCode;
        public String industryName;
        public String industryLink;
        public BigDecimal netAmount;
        public BigDecimal inflowAmount;
        public BigDecimal outflowAmount;
        public BigDecimal industryChangePct;
        public String leadingStock;
        public String leadingStockCode;
        public String leadingStockLink;
        public BigDecimal leadingStockPct;
        public LocalDate tradeDate;
    }
}