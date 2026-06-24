package com.ths.crawler.fetcher.playwright;

import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
import com.ths.crawler.config.PlaywrightConfig;
import com.ths.crawler.config.PlaywrightEnabledCondition;
import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Playwright浏览器自动化采集器 — 行业资金流向全量数据
 * <p>
 * 方案选型理由：
 * hexin-v逆向门槛极高且同花顺不定期更新加密逻辑，维护成本大；
 * 浏览器自动化天然绕过所有前端鉴权（hexin-v由浏览器JS自动生成），跟着页面走就行，
 * 页面改版只需改选择器。预期30分钟写脚本，单次可稳定爬50-100页。
 * <p>
 * 核心链路：
 * 1. 导航+等渲染：page.navigate(url) -> domcontentloaded -> 等待table出现
 * 2. 分页点击+等待：点击分页按钮后等待DOM重渲染
 * 3. 表格DOM提取：querySelectorAll逐行提取文本+链接
 * 4. 资源控制：Browser全局单例，每次采集新建Page，采集完关闭
 * <p>
 * 条件注册：仅当 ths.fetcher.impl=playwright 时激活
 */
@Slf4j
@Component
@Conditional(PlaywrightEnabledCondition.class)
@RequiredArgsConstructor
public class PlaywrightIndustryFetcher implements DataFetcher<List<IndustryCapitalFlowEntity>> {

    private final PlaywrightConfig playwrightConfig;

    @Value("${ths.playwright.url:http://data.10jqka.com.cn/funds/hyzjl/}")
    private String targetUrl;

    @Value("${ths.playwright.max-pages:4}")
    private int maxPages;

    @Value("${ths.playwright.page-wait-ms:2000}")
    private int pageWaitMs;

    @Value("${ths.playwright.timeout:30000}")
    private int timeout;

    @Value("${ths.playwright.max-retry:3}")
    private int maxRetry;

    @Value("${ths.playwright.retry-delay-base:1000}")
    private int retryDelayBase;

    /** 股票代码正则（6位数字） */
    private static final Pattern STOCK_CODE_PATTERN = Pattern.compile("(\\d{6})");

    /** 数据源标识 */
    private static final String SOURCE = "industry_capital_flow";

    /** 多种表格选择器（按优先级尝试） */
    private static final List<String> TABLE_SELECTORS = List.of(
            "table.table-orbit tbody tr",     // 同花顺新版本
            "table tbody tr",                  // 标准HTML表格
            "table.board-table tbody tr",      // board类表格
            "div.table-body table tbody tr",   // 嵌套结构
            "table tr"                         // 降级：无tbody
    );

    @Override
    public String getSource() {
        return SOURCE;
    }

    /**
     * 执行全量采集（1~4页，约90个行业）
     */
    @Override
    public FetchResult<List<IndustryCapitalFlowEntity>> fetch(FetchContext context) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[traceId={}] Playwright采集开始, url={}, maxPages={}", traceId, targetUrl, maxPages);

        Page page = null;
        try {
            // 1. 创建新Page
            page = playwrightConfig.getBrowser().newPage();
            page.setDefaultTimeout(timeout);

            // 反webdriver检测
            page.addInitScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");
            log.info("[traceId={}] 反检测脚本注入完成", traceId);

            // 2. 导航（用domcontentloaded而非load，更快）
            log.info("[traceId={}] 导航到: {}", traceId, targetUrl);
            page.navigate(targetUrl, new Page.NavigateOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED));
            log.info("[traceId={}] DOM加载完成，等待表格渲染...", traceId);

            // 3. 等待表格出现（尝试多种选择器）
            boolean tableFound = waitForTable(page, traceId);
            if (!tableFound) {
                // 降级：多等5秒再试
                log.warn("[traceId={}] 表格未即时出现，额外等待5秒...", traceId);
                page.waitForTimeout(5000);
                tableFound = waitForTable(page, traceId);
            }

            if (!tableFound) {
                // 调试：输出页面信息
                String title = page.title();
                String url = page.url();
                log.error("[traceId={}] 仍未找到表格! title={}, url={}", traceId, title, url);
                // 调试：输出页面部分内容
                try {
                    String bodyText = page.querySelector("body").innerText();
                    log.error("[traceId={}] 页面body前500字符: {}", traceId,
                            bodyText != null && bodyText.length() > 500 ? bodyText.substring(0, 500) : bodyText);
                } catch (Exception e2) {
                    log.error("[traceId={}] 获取body失败: {}", traceId, e2.getMessage());
                }
                // 检查是否有iframe
                List<ElementHandle> iframes = page.querySelectorAll("iframe");
                log.error("[traceId={}] iframe数量: {}", traceId, iframes.size());
                return FetchResult.fail("页面未加载到表格数据, title=" + title);
            }

            log.info("[traceId={}] 表格渲染完成", traceId);

            // 4. 循环翻页+提取
            List<IndustryCapitalFlowEntity> allData = fetchAllPages(page, traceId);

            long costMs = System.currentTimeMillis() - start;
            if (allData.isEmpty()) {
                log.warn("[traceId={}] Playwright采集结果为空, cost={}ms", traceId, costMs);
                return FetchResult.fail("采集结果为空");
            }

            log.info("[traceId={}] Playwright采集完成: count={}, cost={}ms", traceId, allData.size(), costMs);
            return FetchResult.ok(allData, null, costMs);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("[traceId={}] Playwright采集异常, cost={}ms", traceId, costMs, e);
            return FetchResult.fail("采集异常: " + e.getMessage());
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (Exception e) {
                    log.warn("[traceId={}] Page关闭异常: {}", traceId, e.getMessage());
                }
            }
        }
    }

    /**
     * 等待表格出现（尝试多种选择器）
     */
    private boolean waitForTable(Page page, String traceId) {
        for (String selector : TABLE_SELECTORS) {
            try {
                page.waitForSelector(selector,
                        new Page.WaitForSelectorOptions().setTimeout(5000));
                log.info("[traceId={}] 表格选择器命中: {}", traceId, selector);
                return true;
            } catch (Exception e) {
                log.debug("[traceId={}] 选择器未命中: {}", traceId, selector);
            }
        }
        return false;
    }

    /**
     * 循环翻页提取全量数据
     */
    private List<IndustryCapitalFlowEntity> fetchAllPages(Page page, String traceId) {
        List<IndustryCapitalFlowEntity> allData = new ArrayList<>();

        for (int pageNum = 1; pageNum <= maxPages; pageNum++) {
            List<IndustryCapitalFlowEntity> pageData = fetchPageWithRetry(page, pageNum, traceId);
            allData.addAll(pageData);

            if (pageData.isEmpty() && pageNum > 1) {
                log.info("[traceId={}] 第{}页数据为空，停止翻页", traceId, pageNum);
                break;
            }

            if (pageNum < maxPages) {
                boolean hasNext = clickNextPage(page, pageNum, traceId);
                if (!hasNext) {
                    log.info("[traceId={}] 第{}页无下一页按钮，采集结束", traceId, pageNum);
                    break;
                }
            }
        }

        return allData;
    }

    /**
     * 单页提取（带重试）
     */
    private List<IndustryCapitalFlowEntity> fetchPageWithRetry(Page page, int pageNum, String traceId) {
        for (int attempt = 1; attempt <= maxRetry; attempt++) {
            try {
                page.waitForTimeout(pageWaitMs);

                List<IndustryCapitalFlowEntity> data = extractTableData(page);
                if (!data.isEmpty()) {
                    log.info("[traceId={}] 第{}页提取成功: count={}, attempt={}",
                            traceId, pageNum, data.size(), attempt);
                    return data;
                }
                log.warn("[traceId={}] 第{}页提取为空, attempt={}", traceId, pageNum, attempt);
            } catch (Exception e) {
                log.warn("[traceId={}] 第{}页第{}次提取失败: {}",
                        traceId, pageNum, attempt, e.getMessage());
            }

            if (attempt < maxRetry) {
                int delay = (int) Math.pow(2, attempt) * retryDelayBase;
                log.info("[traceId={}] 第{}页重试等待: {}ms", traceId, pageNum, delay);
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.error("[traceId={}] 第{}页重试{}次后仍失败", traceId, pageNum, maxRetry);
        return Collections.emptyList();
    }

    /**
     * 点击下一页按钮
     */
    private boolean clickNextPage(Page page, int currentPage, String traceId) {
        try {
            // 同花顺分页按钮选择器（多种尝试）
            ElementHandle nextBtn = page.querySelector("a.nextpage");
            if (nextBtn == null) {
                nextBtn = page.querySelector("li.next a");
            }
            if (nextBtn == null) {
                nextBtn = page.querySelector("a[title='下一页']");
            }
            if (nextBtn == null) {
                // 找页码链接
                String nextSelector = "a:has-text('" + (currentPage + 1) + "')";
                nextBtn = page.querySelector(nextSelector);
            }

            if (nextBtn == null) {
                log.info("[traceId={}] 未找到第{}页按钮", traceId, currentPage + 1);
                return false;
            }

            nextBtn.click();
            log.info("[traceId={}] 点击第{}页按钮成功", traceId, currentPage + 1);
            return true;

        } catch (Exception e) {
            log.warn("[traceId={}] 点击第{}页按钮失败: {}", traceId, currentPage + 1, e.getMessage());
            return false;
        }
    }

    /**
     * 从当前页面提取表格数据
     * <p>
     * 同花顺行业资金流向表格列（约10列）：
     * 序号 / 行业 / 行业指数 / 涨跌幅 / 流入资金(亿) / 流出资金(亿) / 净额(亿) / 公司家数 / 领涨股 / 领涨股涨跌幅
     */
    private List<IndustryCapitalFlowEntity> extractTableData(Page page) {
        List<IndustryCapitalFlowEntity> result = new ArrayList<>();
        LocalDate tradeDate = LocalDate.now();

        // 尝试多种选择器找到数据行
        List<ElementHandle> rows = Collections.emptyList();
        for (String selector : TABLE_SELECTORS) {
            rows = page.querySelectorAll(selector);
            if (!rows.isEmpty()) {
                log.debug("用选择器[{}]提取到{}行", selector, rows.size());
                break;
            }
        }

        if (rows.isEmpty()) {
            // 最后降级：直接找所有table的tr
            rows = page.querySelectorAll("table tr");
            log.debug("降级选择器提取到{}行", rows.size());
        }

        for (ElementHandle row : rows) {
            List<ElementHandle> cells = row.querySelectorAll("td");
            if (cells.size() < 7) {
                continue;
            }

            try {
                IndustryCapitalFlowEntity entity = new IndustryCapitalFlowEntity();
                entity.setTradeDate(tradeDate);

                // 列1: 行业名称
                String industryName = cells.get(1).innerText().trim();
                if (industryName.isEmpty()) {
                    continue;
                }
                entity.setIndustryName(industryName);

                // 列1: 行业详情页链接
                List<ElementHandle> industryLinks = cells.get(1).querySelectorAll("a[href]");
                if (!industryLinks.isEmpty()) {
                    String href = industryLinks.get(0).getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        entity.setIndustryLink(href.startsWith("http") ? href : "http://data.10jqka.com.cn" + href);
                    }
                }

                // 列3: 涨跌幅
                entity.setIndustryChangePct(parsePercent(cells.get(3).innerText().trim()));

                // 列4: 流入资金(亿)
                BigDecimal inflowYi = parseBigDecimal(cells.get(4).innerText().trim());
                entity.setInflowAmount(yiToWan(inflowYi));

                // 列5: 流出资金(亿)
                BigDecimal outflowYi = parseBigDecimal(cells.get(5).innerText().trim());
                entity.setOutflowAmount(yiToWan(outflowYi));

                // 列6: 净额(亿)
                BigDecimal netYi = parseBigDecimal(cells.get(6).innerText().trim());
                entity.setNetAmount(yiToWan(netYi));

                // 列8+: 领涨股
                if (cells.size() > 8) {
                    entity.setLeadingStock(cells.get(8).innerText().trim());

                    List<ElementHandle> stockLinks = cells.get(8).querySelectorAll("a[href]");
                    if (!stockLinks.isEmpty()) {
                        String stockHref = stockLinks.get(0).getAttribute("href");
                        if (stockHref != null && !stockHref.isEmpty()) {
                            entity.setLeadingStockLink(
                                    stockHref.startsWith("http") ? stockHref : "http://data.10jqka.com.cn" + stockHref);
                            Matcher m = STOCK_CODE_PATTERN.matcher(stockHref);
                            if (m.find()) {
                                entity.setLeadingStockCode(m.group(1));
                            }
                        }
                    }
                }

                // 列9+: 领涨股涨跌幅
                if (cells.size() > 9) {
                    entity.setLeadingStockPct(parsePercent(cells.get(9).innerText().trim()));
                }

                entity.setIndustryCode("");
                result.add(entity);

            } catch (Exception e) {
                log.warn("解析表格行失败: {}", e.getMessage());
            }
        }

        return result;
    }

    // ===================== 工具方法 =====================

    private BigDecimal yiToWan(BigDecimal yi) {
        if (yi == null) return BigDecimal.ZERO;
        return yi.multiply(new BigDecimal("10000")).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal parsePercent(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) return null;
        try {
            String cleaned = str.replace("%", "").replace("+", "").trim();
            return new BigDecimal(cleaned).setScale(4, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String str) {
        if (str == null || str.isEmpty() || str.equals("--")) return BigDecimal.ZERO;
        try {
            String cleaned = str.replace(",", "").replace("亿", "").trim();
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
