package com.ths.crawler.fetcher.okhttp;

import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 当前状态：AJAX分页接口返回401，OkHttp+Jsoup直连方案目前不可用
 * 已被Playwright方案替代（ths.fetcher.impl=playwright）
 * <p>
 * 条件注册：仅当 ths.fetcher.impl=okhttp 时激活
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ths.fetcher.impl", havingValue = "okhttp", matchIfMissing = false)
public class ThsIndustryPageFetcher implements DataFetcher<List<IndustryCapitalFlowEntity>> {

    private static final String INDUSTRY_PAGE_URL = "http://data.10jqka.com.cn/funds/hyzjl/";
    private static final String INDUSTRY_AJAX_PAGE2_URL =
            "http://data.10jqka.com.cn/funds/hyzjl/field/zljlr/order/desc/ajax/2/free/1/";
    private static final Pattern JS_DATA_PATTERN =
            Pattern.compile("var\\s+JS_DATA\\s*=\\s*(\\[.+?\\])", Pattern.DOTALL);
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

    @Override
    public String getSource() {
        return "industry_capital_flow";
    }

    @Override
    public FetchResult<List<IndustryCapitalFlowEntity>> fetch(FetchContext context) {
        long start = System.currentTimeMillis();
        List<IndustryFlowData> rawData = fetchIndustryFlowPages();
        long costMs = System.currentTimeMillis() - start;

        if (rawData.isEmpty()) {
            return FetchResult.fail("OkHttp采集结果为空");
        }

        LocalDate tradeDate = LocalDate.now();
        List<IndustryCapitalFlowEntity> entities = rawData.stream()
                .map(d -> convertToEntity(d, tradeDate))
                .collect(java.util.stream.Collectors.toList());

        return FetchResult.ok(entities, null, costMs);
    }

    public List<IndustryFlowData> fetchIndustryFlowPages() {
        return fetchBoardFlowPages(INDUSTRY_PAGE_URL, INDUSTRY_AJAX_PAGE2_URL, "行业");
    }

    private List<IndustryFlowData> fetchBoardFlowPages(String mainUrl, String ajaxPage2Url, String boardLabel) {
        log.info("开始抓取{}资金流向: mainUrl={}", boardLabel, mainUrl);
        long start = System.currentTimeMillis();

        String page1Html = fetchWithRetry(mainUrl, boardLabel + "首页");
        if (page1Html == null || page1Html.isEmpty()) {
            log.error("{}首页抓取失败", boardLabel);
            return List.of();
        }

        Map<String, String> codeMap = parseJsData(page1Html);
        List<IndustryFlowData> page1Data = parseHtmlTable(page1Html, codeMap);
        List<IndustryFlowData> page2Data = List.of();
        String page2Html = fetchWithRetry(ajaxPage2Url, boardLabel + "第2页AJAX");
        if (page2Html != null && !page2Html.isEmpty()) {
            if (!isChameleonBlock(page2Html)) {
                page2Data = parseHtmlTable(page2Html, codeMap);
            }
        }

        List<IndustryFlowData> allData = new ArrayList<>(page1Data);
        allData.addAll(page2Data);

        long costMs = System.currentTimeMillis() - start;
        log.info("{}资金流向抓取完成: total={}, cost={}ms", boardLabel, allData.size(), costMs);
        return allData;
    }

    private String fetchWithRetry(String url, String label) {
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            try {
                if (attempt > 1) randomDelay();
                String html = fetchUrl(url);
                if (html != null && !html.isEmpty()) return html;
            } catch (Exception e) {
                log.warn("{}第{}次请求异常: {}", label, attempt, e.getMessage());
            }
            if (attempt < retryCount) randomDelay();
        }
        return null;
    }

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
            if (!response.isSuccessful()) return null;
            return response.body() != null ? response.body().string() : null;
        }
    }

    private Map<String, String> parseJsData(String html) {
        Map<String, String> codeMap = new ConcurrentHashMap<>();
        Matcher matcher = JS_DATA_PATTERN.matcher(html);
        if (!matcher.find()) return codeMap;
        String jsDataStr = matcher.group(1);
        Pattern itemPattern = Pattern.compile("name:\\s*\"([^\"]+)\".*?addr:\\s*\"(\\d+)\"");
        Matcher itemMatcher = itemPattern.matcher(jsDataStr);
        while (itemMatcher.find()) codeMap.put(itemMatcher.group(1), itemMatcher.group(2));
        return codeMap;
    }

    private List<IndustryFlowData> parseHtmlTable(String html, Map<String, String> codeMap) {
        List<IndustryFlowData> result = new ArrayList<>();
        Document doc = Jsoup.parse(html);
        Elements tables = doc.select("table");
        if (tables.isEmpty()) return result;
        Element dataTable = tables.last();
        Elements rows = dataTable.select("tbody tr");
        if (rows.isEmpty()) rows = dataTable.select("tr");

        for (Element row : rows) {
            Elements cells = row.select("td");
            if (cells.size() < 7) continue;
            try {
                IndustryFlowData data = new IndustryFlowData();
                data.rank = parseIntSafe(cells.get(0).text().trim());
                String industryName = cells.get(1).text().trim();
                if (industryName.isEmpty()) continue;
                data.industryName = industryName;
                Elements industryLinks = cells.get(1).select("a[href]");
                if (!industryLinks.isEmpty()) {
                    String href = industryLinks.first().attr("href");
                    if (href != null && !href.isEmpty()) {
                        data.industryLink = href.startsWith("http") ? href : "http://data.10jqka.com.cn" + href;
                    }
                }
                data.industryChangePct = parsePercentSafe(cells.get(3).text().trim());
                data.inflowAmount = yiToWan(parseBigDecimalSafe(cells.get(4).text().trim()));
                data.outflowAmount = yiToWan(parseBigDecimalSafe(cells.get(5).text().trim()));
                data.netAmount = yiToWan(parseBigDecimalSafe(cells.get(6).text().trim()));
                if (cells.size() > 8) {
                    data.leadingStock = cells.get(8).text().trim();
                    Elements stockLinks = cells.get(8).select("a[href]");
                    if (!stockLinks.isEmpty()) {
                        String stockHref = stockLinks.first().attr("href");
                        if (stockHref != null && !stockHref.isEmpty()) {
                            data.leadingStockLink = stockHref.startsWith("http") ? stockHref : "http://data.10jqka.com.cn" + stockHref;
                            Matcher m = Pattern.compile("(\\d{6})").matcher(stockHref);
                            if (m.find()) data.leadingStockCode = m.group(1);
                        }
                    }
                }
                if (cells.size() > 9) data.leadingStockPct = parsePercentSafe(cells.get(9).text().trim());
                data.industryCode = codeMap.getOrDefault(industryName, "");
                data.tradeDate = LocalDate.now();
                result.add(data);
            } catch (Exception e) {
                log.warn("解析表格行失败: {}", e.getMessage());
            }
        }
        return result;
    }

    private boolean isChameleonBlock(String html) {
        return html != null && html.length() < 500 && html.contains("<script") && html.contains("chameleon");
    }

    private IndustryCapitalFlowEntity convertToEntity(IndustryFlowData data, LocalDate tradeDate) {
        return IndustryCapitalFlowEntity.builder()
                .tradeDate(tradeDate)
                .industryCode(data.industryCode != null ? data.industryCode : "")
                .industryName(data.industryName)
                .industryLink(data.industryLink)
                .netAmount(data.netAmount)
                .inflowAmount(data.inflowAmount)
                .outflowAmount(data.outflowAmount)
                .industryChangePct(data.industryChangePct)
                .leadingStock(data.leadingStock)
                .leadingStockCode(data.leadingStockCode)
                .leadingStockLink(data.leadingStockLink)
                .leadingStockPct(data.leadingStockPct)
                .build();
    }

    private BigDecimal yiToWan(BigDecimal yi) {
        if (yi == null) return BigDecimal.ZERO;
        return yi.multiply(new BigDecimal("10000")).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercentSafe(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) return null;
        try {
            return new BigDecimal(str.replace("%", "").replace("+", "").trim()).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) { return null; }
    }

    private BigDecimal parseBigDecimalSafe(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) return BigDecimal.ZERO;
        try {
            return new BigDecimal(str.replace(",", "").replace("亿", "").trim());
        } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int parseIntSafe(String str) {
        if (str == null || str.isEmpty()) return 0;
        try { return Integer.parseInt(str.trim()); } catch (Exception e) { return 0; }
    }

    private void randomDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(requestDelayMin, requestDelayMax + 1));
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                    .connectTimeout(connectTimeout, TimeUnit.SECONDS)
                    .readTimeout(readTimeout, TimeUnit.SECONDS)
                    .writeTimeout(15, TimeUnit.SECONDS)
                    .followRedirects(true).retryOnConnectionFailure(true)
                    .build();
        }
        return httpClient;
    }

    public static class IndustryFlowData {
        public LocalDate tradeDate;
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
        public int rank;
    }
}
