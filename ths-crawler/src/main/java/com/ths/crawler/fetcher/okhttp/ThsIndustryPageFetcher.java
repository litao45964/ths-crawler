package com.ths.crawler.fetcher.okhttp;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 同花顺行业资金流向页面抓取器（OkHttp + Jsoup）
 * <p>
 * 抓取同花顺行业资金流向第1页和第2页数据（共约40个行业）
 * 主页面: http://data.10jqka.com.cn/funds/hyzjl/
 * 第2页AJAX: http://data.10jqka.com.cn/funds/hyzjl/field/zljlr/order/desc/ajax/2/free/1/
 * <p>
 * 数据解析：
 * 1. 从HTML中提取 var JS_DATA = [...] 获取板块代码映射（name → code 881xxx）
 * 2. 用Jsoup解析HTML表格获取详细字段
 * 3. 金额单位转换：页面显示"亿"，入库存储"万元"（×10000）
 */
@Slf4j
@Component
public class ThsIndustryPageFetcher {

    /** 行业资金流向主页 */
    private static final String INDUSTRY_PAGE_URL = "http://data.10jqka.com.cn/funds/hyzjl/";

    /** 行业资金流向第2页AJAX */
    private static final String INDUSTRY_AJAX_PAGE2_URL =
            "http://data.10jqka.com.cn/funds/hyzjl/field/zljlr/order/desc/ajax/2/free/1/";

    /** 概念资金流向主页 */
    private static final String CONCEPT_PAGE_URL = "http://data.10jqka.com.cn/funds/gnzjl/";

    /** 概念资金流向第2页AJAX */
    private static final String CONCEPT_AJAX_PAGE2_URL =
            "http://data.10jqka.com.cn/funds/gnzjl/field/zljlr/order/desc/ajax/2/free/1/";

    /** JS_DATA正则 */
    private static final Pattern JS_DATA_PATTERN =
            Pattern.compile("var\\s+JS_DATA\\s*=\\s*(\\[.+?\\])", Pattern.DOTALL);

    /** 浏览器UA */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    @Value("${ths.okhttp.connect-timeout:10}")
    private int connectTimeout;

    @Value("${ths.okhttp.read-timeout:30}")
    private int readTimeout;

    @Value("${ths.okhttp.retry-count:3}")
    private int retryCount;

    @Value("${ths.okhttp.request-delay-min:500}")
    private int requestDelayMin;

    @Value("${ths.okhttp.request-delay-max:1500}")
    private int requestDelayMax;

    private OkHttpClient httpClient;

    /**
     * 初始化OkHttpClient（懒加载）
     */
    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                    .readTimeout(readTimeout, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return httpClient;
    }

    /**
     * 抓取行业资金流向第1页和第2页数据
     *
     * @return 行业资金流向实体列表
     */
    public List<IndustryFlowData> fetchIndustryFlowPages() {
        return fetchBoardFlowPages(INDUSTRY_PAGE_URL, INDUSTRY_AJAX_PAGE2_URL, "行业");
    }

    /**
     * 抓取概念资金流向第1页和第2页数据
     *
     * @return 概念资金流向实体列表
     */
    public List<IndustryFlowData> fetchConceptFlowPages() {
        return fetchBoardFlowPages(CONCEPT_PAGE_URL, CONCEPT_AJAX_PAGE2_URL, "概念");
    }

    /**
     * 抓取板块资金流向（行业/概念通用）
     *
     * @param mainUrl      主页面URL
     * @param ajaxPage2Url 第2页AJAX URL
     * @param boardLabel   板块标签（用于日志）
     * @return 板块资金流向数据列表
     */
    private List<IndustryFlowData> fetchBoardFlowPages(String mainUrl, String ajaxPage2Url, String boardLabel) {
        log.info("开始抓取{}资金流向: mainUrl={}", boardLabel, mainUrl);
        long start = System.currentTimeMillis();

        // 1. 抓取首页
        String page1Html = fetchWithRetry(mainUrl, boardLabel + "首页");
        if (page1Html == null || page1Html.isEmpty()) {
            log.error("{}首页抓取失败", boardLabel);
            return List.of();
        }

        // 2. 从首页HTML提取JS_DATA（板块代码映射）
        Map<String, String> codeMap = parseJsData(page1Html);
        log.info("{}板块代码映射: count={}", boardLabel, codeMap.size());

        // 3. 解析首页表格数据
        List<IndustryFlowData> page1Data = parseHtmlTable(page1Html, codeMap);
        log.info("{}首页数据解析: count={}", boardLabel, page1Data.size());

        // 4. 抓取第2页AJAX
        List<IndustryFlowData> page2Data = List.of();
        String page2Html = fetchWithRetry(ajaxPage2Url, boardLabel + "第2页AJAX");
        if (page2Html != null && !page2Html.isEmpty()) {
            // 检查是否为chameleon验证页面
            if (isChameleonBlock(page2Html)) {
                log.warn("{}第2页AJAX被chameleon拦截，尝试从首页解析完整数据", boardLabel);
                // 尝试从首页HTML中获取更多数据（首页可能包含全部数据）
                page2Data = List.of();
            } else {
                page2Data = parseHtmlTable(page2Html, codeMap);
                log.info("{}第2页数据解析: count={}", boardLabel, page2Data.size());
            }
        } else {
            log.warn("{}第2页AJAX获取失败，仅使用首页数据", boardLabel);
        }

        // 5. 合并结果
        List<IndustryFlowData> allData = new ArrayList<>(page1Data);
        allData.addAll(page2Data);

        long costMs = System.currentTimeMillis() - start;
        log.info("{}资金流向抓取完成: page1={}, page2={}, total={}, cost={}ms",
                boardLabel, page1Data.size(), page2Data.size(), allData.size(), costMs);

        return allData;
    }

    /**
     * 带重试的HTTP GET请求
     *
     * @param url     请求URL
     * @param label   日志标签
     * @return HTML内容，失败返回null
     */
    private String fetchWithRetry(String url, String label) {
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                // 请求间隔随机延迟（非首次请求）
                if (attempt > 1) {
                    randomDelay();
                }

                String html = fetchUrl(url);
                if (html != null && !html.isEmpty()) {
                    return html;
                }
                log.warn("{}第{}次请求返回空, url={}", label, attempt, url);
            } catch (Exception e) {
                log.warn("{}第{}次请求异常: {}", label, attempt, e.getMessage());
            }

            if (attempt < retryCount) {
                randomDelay();
            }
        }
        log.error("{}重试{}次后仍失败, url={}", label, retryCount, url);
        return null;
    }

    /**
     * 单次HTTP GET请求
     */
    private String fetchUrl(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                .header("Referer", "http://data.10jqka.com.cn/funds/hyzjl/")
                .header("Connection", "keep-alive")
                .build();

        try (Response response = getHttpClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.warn("HTTP请求失败: code={}, url={}", response.code(), url);
                return null;
            }
            return response.body() != null ? response.body().string() : null;
        }
    }

    /**
     * 从HTML中解析 JS_DATA 获取板块代码映射
     * 格式: var JS_DATA = [{name:"半导体",amount:1.58,addr:"881121"},...]
     *
     * @param html HTML内容
     * @return 板块名称 → 板块代码映射
     */
    private Map<String, String> parseJsData(String html) {
        Map<String, String> codeMap = new ConcurrentHashMap<>();

        Matcher matcher = JS_DATA_PATTERN.matcher(html);
        if (!matcher.find()) {
            log.warn("未找到JS_DATA变量");
            return codeMap;
        }

        String jsDataStr = matcher.group(1);
        // 解析JSON数组中的name和addr字段
        // 格式: [{name:"半导体",amount:1.58,addr:"881121"}]
        Pattern itemPattern = Pattern.compile("name:\\s*\"([^\"]+)\".*?addr:\\s*\"(\\d+)\"");
        Matcher itemMatcher = itemPattern.matcher(jsDataStr);

        while (itemMatcher.find()) {
            String name = itemMatcher.group(1);
            String code = itemMatcher.group(2);
            codeMap.put(name, code);
        }

        return codeMap;
    }

    /**
     * 使用Jsoup解析HTML表格
     * <p>
     * 表格列：序号/行业/行业指数/涨跌幅/流入资金(亿)/流出资金(亿)/净额(亿)/公司家数/领涨股/领涨股涨跌幅/领涨股当前价
     *
     * @param html    HTML内容
     * @param codeMap 板块代码映射
     * @return 行业资金流向数据列表
     */
    private List<IndustryFlowData> parseHtmlTable(String html, Map<String, String> codeMap) {
        List<IndustryFlowData> result = new ArrayList<>();

        Document doc = Jsoup.parse(html);

        // 同花顺表格通常在 table 下
        Elements tables = doc.select("table");
        if (tables.isEmpty()) {
            log.warn("HTML中未找到表格");
            return result;
        }

        // 取最后一个table（通常数据表格在最后）
        Element dataTable = tables.last();
        Elements rows = dataTable.select("tbody tr");
        if (rows.isEmpty()) {
            // 有些页面用 tbody 不标准，直接 tr
            rows = dataTable.select("tr");
        }

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 7) {
                continue; // 跳过表头或不完整行
            }

            try {
                IndustryFlowData data = new IndustryFlowData();

                // 列0: 序号
                String rankStr = cells.get(0).text().trim();
                data.rank = parseIntSafe(rankStr);

                // 列1: 行业名称（可能包含链接）
                String industryName = cells.get(1).text().trim();
                if (industryName.isEmpty()) {
                    continue;
                }
                data.industryName = industryName;

                // 列2: 行业指数（跳过）
                // data.indexValue = cells.get(2).text().trim();

                // 列3: 涨跌幅（%）
                data.industryChangePct = parsePercentSafe(cells.get(3).text().trim());

                // 列4: 流入资金（亿）
                BigDecimal inflowYi = parseBigDecimalSafe(cells.get(4).text().trim());
                data.inflowAmount = yiToWan(inflowYi);

                // 列5: 流出资金（亿）
                BigDecimal outflowYi = parseBigDecimalSafe(cells.get(5).text().trim());
                data.outflowAmount = yiToWan(outflowYi);

                // 列6: 净额（亿）
                BigDecimal netYi = parseBigDecimalSafe(cells.get(6).text().trim());
                data.netAmount = yiToWan(netYi);

                // 列7: 公司家数（跳过）

                // 列8: 领涨股
                if (cells.size() > 8) {
                    data.leadingStock = cells.get(8).text().trim();
                }

                // 列9: 领涨股涨跌幅（%）
                if (cells.size() > 9) {
                    data.leadingStockPct = parsePercentSafe(cells.get(9).text().trim());
                }

                // 从codeMap获取板块代码
                data.industryCode = codeMap.getOrDefault(industryName, "");

                // 设置交易日期（当天）
                data.tradeDate = LocalDate.now();

                result.add(data);

            } catch (Exception e) {
                log.warn("解析表格行失败: row={}, error={}", row.text(), e.getMessage());
            }
        }

        return result;
    }

    /**
     * 检测是否为chameleon验证拦截页面
     */
    private boolean isChameleonBlock(String html) {
        if (html == null || html.length() < 500) {
            // AJAX返回的验证页面通常很短
            return html != null && (html.contains("<script") && html.contains("chameleon"));
        }
        return false;
    }

    /**
     * 亿 → 万元 转换
     */
    private BigDecimal yiToWan(BigDecimal yi) {
        if (yi == null) {
            return BigDecimal.ZERO;
        }
        return yi.multiply(new BigDecimal("10000")).setScale(4, RoundingMode.HALF_UP);
    }

    /**
     * 安全解析百分比字符串（如 "+2.35%" → 2.35）
     */
    private BigDecimal parsePercentSafe(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) {
            return null;
        }
        try {
            String cleaned = str.replace("%", "").replace("+", "").trim();
            return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 安全解析BigDecimal
     */
    private BigDecimal parseBigDecimalSafe(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) {
            return BigDecimal.ZERO;
        }
        try {
            String cleaned = str.replace(",", "").replace("亿", "").trim();
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 安全解析整数
     */
    private int parseIntSafe(String str) {
        if (str == null || str.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 随机延迟
     */
    private void randomDelay() {
        try {
            int delay = ThreadLocalRandom.current().nextInt(requestDelayMin, requestDelayMax + 1);
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 行业资金流向数据内部传输对象
     */
    public static class IndustryFlowData {
        public LocalDate tradeDate;
        public String industryCode;
        public String industryName;
        public BigDecimal netAmount;
        public BigDecimal inflowAmount;
        public BigDecimal outflowAmount;
        public BigDecimal industryChangePct;
        public String leadingStock;
        public BigDecimal leadingStockPct;
        public int rank;
    }
}
