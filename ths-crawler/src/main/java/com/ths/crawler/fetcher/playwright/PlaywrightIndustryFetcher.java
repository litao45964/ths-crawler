package com.ths.crawler.fetcher.playwright;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Page;
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
 * 1. 导航+等渲染：page.navigate(url) -> waitForSelector -> 浏览器自动执行所有JS
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

    private final Browser browser;

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
            // 1. 创建新Page（隔离cookie/缓存）
            page = browser.newPage();
            page.setDefaultTimeout(timeout);

            // 2. 导航到目标页面
            log.info("[traceId={}] 导航到: {}", traceId, targetUrl);
            page.navigate(targetUrl);
            page.waitForSelector("table tbody tr",
                    new Page.WaitForSelectorOptions().setTimeout(timeout));
            log.info("[traceId={}] 首页渲染完成", traceId);

            // 3. 循环翻页+提取
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
            // 4. 关闭Page释放资源
            if (page != null) {
                try {
                    page.close();
                    log.debug("[traceId={}] Page已关闭", traceId);
                } catch (Exception e) {
                    log.warn("[traceId={}] Page关闭异常: {}", traceId, e.getMessage());
                }
            }
        }
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

            // 翻到下一页（非最后一页时）
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
                // 等待DOM渲染
                page.waitForTimeout(pageWaitMs);
                waitForDataReady(page);

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

            // 重试前指数退避
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
            // 同花顺分页按钮选择器：常见的"下一页"模式
            ElementHandle nextBtn = page.querySelector("a.nextpage");
            if (nextBtn == null) {
                // 方案2：找页码链接（当前页+1）
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
     * 等待表格数据渲染就绪
     */
    private boolean waitForDataReady(Page page) {
        try {
            page.waitForSelector("table tbody tr",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            return true;
        } catch (Exception e) {
            log.debug("等待表格数据超时: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 从当前页面提取表格数据
     * <p>
     * 同花顺行业资金流向表格列：
     * 序号 / 行业 / 行业指数 / 涨跌幅 / 流入资金(亿) / 流出资金(亿) / 净额(亿) / 公司家数 / 领涨股 / 领涨股涨跌幅
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

                // 列1: 行业详情页链接（从<a>标签提取href）
                List<ElementHandle> industryLinks = cells.get(1).querySelectorAll("a[href]");
                if (!industryLinks.isEmpty()) {
                    String href = industryLinks.get(0).getAttribute("href");
                    if (href != null && !href.isEmpty()) {
                        entity.setIndustryLink(href.startsWith("http") ? href : "http://data.10jqka.com.cn" + href);
                    }
                }

                // 列3: 涨跌幅（%）
                entity.setIndustryChangePct(parsePercent(cells.get(3).innerText().trim()));

                // 列4: 流入资金（亿）
                BigDecimal inflowYi = parseBigDecimal(cells.get(4).innerText().trim());
                entity.setInflowAmount(yiToWan(inflowYi));

                // 列5: 流出资金（亿）
                BigDecimal outflowYi = parseBigDecimal(cells.get(5).innerText().trim());
                entity.setOutflowAmount(yiToWan(outflowYi));

                // 列6: 净额（亿）
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
