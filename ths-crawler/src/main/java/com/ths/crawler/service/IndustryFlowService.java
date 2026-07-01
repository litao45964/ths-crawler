package com.ths.crawler.service;

import cn.hutool.core.text.csv.CsvData;
import cn.hutool.core.text.csv.CsvReader;
import cn.hutool.core.text.csv.CsvRow;
import cn.hutool.core.text.csv.CsvUtil;
import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.mapper.IndustryCapitalFlowMapper;
import com.ths.crawler.storage.DualWriteService;
import com.ths.crawler.model.dto.IndustryFlowDTO;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 行业资金流向业务编排服务
 * <p>
 * P0-02/P0-12改造：依赖DataFetcher接口而非具体实现，通过@ConditionalOnProperty切换
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryFlowService {

    private final DataFetcher<List<IndustryCapitalFlowEntity>> industryFetcher;

    private final IndustryCapitalFlowMapper flowMapper;

    private final DualWriteService dualWriteService;

    @Value("${ths.fetcher.sector-flow:akshare}")
    private String fetcherType;

    /**
     * 日度采集入口
     * <p>
     * 事务边界说明（P0-03）：
     * - 本编排方法无@Transactional（避免Playwright长IO持有DB连接）
     * - 数据入库由flowMapper.batchInsertOrUpdate完成（独立短事务）
     * - 采集数据是"原始事实"，入库即保留，不因后续计算失败而回滚
     */
    public CollectResult collectDailyData() {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[traceId={}] === 开始日度行业资金流向采集 ===", traceId);

        // 记录开始
        try {
            dualWriteService.logCrawlWithTrace(traceId, "industry_flow", "running", "total",
                    0, 0, 0, 0, null, null);
        } catch (Exception ignored) {
            // 日志记录失败不影响主流程
        }

        try {
            // 1. 通过接口抓取行业数据
            long fetchStart = System.currentTimeMillis();
            FetchResult<List<IndustryCapitalFlowEntity>> fetchResult = industryFetcher.fetch(
                    FetchContext.builder()
                            .tradeDate(LocalDate.now().toString())
                            .build()
            );
            long fetchCost = System.currentTimeMillis() - fetchStart;
            log.info("[traceId={}] 抓取完成: success={}, count={}, cost={}ms",
                    traceId, fetchResult.isSuccess(),
                    fetchResult.getData() != null ? fetchResult.getData().size() : 0,
                    fetchCost);

            if (!fetchResult.isSuccess() || fetchResult.getData() == null || fetchResult.getData().isEmpty()) {
                log.error("[traceId={}] 行业资金流向数据为空: {}", traceId, fetchResult.getErrorMsg());
                try {
                dualWriteService.logCrawlWithTrace(traceId, "industry_flow", "fail", "fetch",
                        0, 0, fetchCost, 0, null,
                        "抓取数据为空: " + fetchResult.getErrorMsg());
            } catch (Exception ignored) {}
            return CollectResult.fail("抓取数据为空: " + fetchResult.getErrorMsg());
            }

            List<IndustryCapitalFlowEntity> entities = fetchResult.getData();

            // 2. 批量入库
            long saveStart = System.currentTimeMillis();
            int inserted = flowMapper.batchInsertOrUpdate(entities);
            long saveCost = System.currentTimeMillis() - saveStart;
            log.info("[traceId={}] 入库完成: inserted={}, cost={}ms", traceId, inserted, saveCost);

            long costMs = System.currentTimeMillis() - start;
            log.info("[traceId={}] === 日度行业资金流向采集完成: count={}, inserted={}, cost={}ms ===",
                    traceId, entities.size(), inserted, costMs);

            // 记录成功
            String detail = String.format("{\"fetchCost\":%d,\"saveCost\":%d}", fetchCost, saveCost);
            try {
                dualWriteService.logCrawlWithTrace(traceId, "industry_flow", "success", "total",
                        entities.size(), inserted, costMs, 0, detail, null);
            } catch (Exception ignored) {}

            return CollectResult.ok(entities.size(), inserted, LocalDate.now().toString(), costMs);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("[traceId={}] 日度行业资金流向采集失败, cost={}ms", traceId, costMs, e);
            try {
                dualWriteService.logCrawlWithTrace(traceId, "industry_flow", "fail", "total",
                        0, 0, costMs, 0, null, "采集异常: " + e.getMessage());
            } catch (Exception ignored) {}
            return CollectResult.fail("采集异常: " + e.getMessage());
        }
    }

    public CollectResult manualCollect(String date) {
        log.info("手动补采行业资金流向: date={}", date);
        return collectDailyData();
    }

    /**
     * 从CSV文件导入行业资金流向数据
     * <p>
     * CSV格式（无标题行，15列逗号分隔）：
     * 序号,交易日期,行业代码,行业名称,行业URL,净额,流入额,流出额,涨跌幅,
     * 领涨股,领涨股代码,领涨股URL,领涨股涨幅,抓取开始时间,抓取结束时间
     *
     * @param filePath CSV文件绝对路径
     * @return 导入结果
     */
    public CollectResult importFromCsv(String filePath) {
        long start = System.currentTimeMillis();
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[traceId={}] === 开始CSV导入行业资金流向: file={} ===", traceId, filePath);

        File csvFile = new File(filePath);
        if (!csvFile.exists() || !csvFile.isFile()) {
            log.error("[traceId={}] CSV文件不存在: {}", traceId, filePath);
            return CollectResult.fail("CSV文件不存在: " + filePath);
        }

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        List<IndustryCapitalFlowEntity> allEntities = new ArrayList<>();
        LocalDate minDate = null;
        LocalDate maxDate = null;

        try (CsvReader reader = CsvUtil.getReader()) {
            CsvData csvData = reader.read(csvFile, StandardCharsets.UTF_8);
            List<CsvRow> rows = csvData.getRows();

            for (CsvRow row : rows) {
                if (row.size() < 15) {
                    log.warn("[traceId={}] 跳过列数不足的行: {}", traceId, row);
                    continue;
                }

                try {
                    String rawDate = row.get(1).trim();
                    LocalDate tradeDate = LocalDate.parse(rawDate, dateFormatter);

                    IndustryCapitalFlowEntity entity = IndustryCapitalFlowEntity.builder()
                            .tradeDate(tradeDate)
                            .industryCode(row.get(2).trim())
                            .industryName(row.get(3).trim())
                            .industryLink(row.get(4).trim())
                            .netAmount(new BigDecimal(row.get(5).trim()))
                            .inflowAmount(new BigDecimal(row.get(6).trim()))
                            .outflowAmount(new BigDecimal(row.get(7).trim()))
                            .industryChangePct(new BigDecimal(row.get(8).trim()))
                            .leadingStock(row.get(9).trim())
                            .leadingStockCode(row.get(10).trim())
                            .leadingStockLink(row.get(11).trim())
                            .leadingStockPct(new BigDecimal(row.get(12).trim()))
                            .createdAt(LocalDateTime.parse(row.get(13).trim(), dateTimeFormatter))
                            .updatedAt(LocalDateTime.parse(row.get(14).trim(), dateTimeFormatter))
                            .build();

                    allEntities.add(entity);

                    if (minDate == null || tradeDate.isBefore(minDate)) {
                        minDate = tradeDate;
                    }
                    if (maxDate == null || tradeDate.isAfter(maxDate)) {
                        maxDate = tradeDate;
                    }
                } catch (Exception e) {
                    log.warn("[traceId={}] 解析行数据失败: row={}, error={}", traceId, row, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[traceId={}] CSV读取失败", traceId, e);
            return CollectResult.fail("CSV读取失败: " + e.getMessage());
        }

        if (allEntities.isEmpty()) {
            log.warn("[traceId={}] CSV文件中无有效数据", traceId);
            return CollectResult.fail("CSV文件中无有效数据");
        }

        // 分批入库（每批100条）
        int totalRows = allEntities.size();
        int inserted = 0;
        int batchSize = 100;

        for (int i = 0; i < allEntities.size(); i += batchSize) {
            int end = Math.min(i + batchSize, allEntities.size());
            List<IndustryCapitalFlowEntity> batch = allEntities.subList(i, end);
            inserted += flowMapper.batchInsertOrUpdate(batch);
        }

        long costMs = System.currentTimeMillis() - start;
        String dateRange = (minDate != null && maxDate != null)
                ? minDate + "~" + maxDate
                : "unknown";

        log.info("[traceId={}] CSV导入完成: file={}, totalRows={}, inserted={}, dateRange={}, cost={}ms",
                traceId, filePath, totalRows, inserted, dateRange, costMs);

        return CollectResult.ok(totalRows, inserted, dateRange, costMs);
    }

    public List<IndustryFlowDTO> getLatestFlow(Integer topN, String orderBy) {
        LocalDate latestDate = flowMapper.selectLatestTradeDate();
        if (latestDate == null) {
            log.warn("无可用数据");
            return List.of();
        }

        List<IndustryCapitalFlowEntity> entities = flowMapper.selectByTradeDate(latestDate);

        Comparator<IndustryCapitalFlowEntity> comparator = getComparator(orderBy);
        entities.sort(comparator);

        if (topN != null && topN > 0 && entities.size() > topN) {
            entities = entities.subList(0, topN);
        }

        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public List<IndustryFlowDTO> getIndustryHistory(String industryName, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<IndustryCapitalFlowEntity> entities = flowMapper.selectByIndustryAndDateRange(
                industryName, startDate, endDate);

        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    private IndustryFlowDTO convertToDTO(IndustryCapitalFlowEntity entity) {
        return IndustryFlowDTO.builder()
                .tradeDate(entity.getTradeDate())
                .industryCode(entity.getIndustryCode())
                .industryName(entity.getIndustryName())
                .industryLink(entity.getIndustryLink())
                .netAmount(entity.getNetAmount())
                .inflowAmount(entity.getInflowAmount())
                .outflowAmount(entity.getOutflowAmount())
                .industryChangePct(entity.getIndustryChangePct())
                .leadingStock(entity.getLeadingStock())
                .leadingStockCode(entity.getLeadingStockCode())
                .leadingStockLink(entity.getLeadingStockLink())
                .leadingStockPct(entity.getLeadingStockPct())
                .rank(null)
                .build();
    }

    private Comparator<IndustryCapitalFlowEntity> getComparator(String orderBy) {
        if (orderBy == null || orderBy.isEmpty()) {
            orderBy = "net_amount";
        }
        // 支持 _asc 后缀，表示升序（去掉后缀后不复用 .reversed()）
        boolean asc = false;
        if (orderBy.endsWith("_asc")) {
            asc = true;
            orderBy = orderBy.replace("_asc", "");
        }
        Comparator<IndustryCapitalFlowEntity> cmp = switch (orderBy) {
            case "inflow_amount" -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getInflowAmount,
                    Comparator.nullsLast(BigDecimal::compareTo));
            case "outflow_amount" -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getOutflowAmount,
                    Comparator.nullsLast(BigDecimal::compareTo));
            case "industry_change_pct" -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getIndustryChangePct,
                    Comparator.nullsLast(BigDecimal::compareTo));
            default -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getNetAmount,
                    Comparator.nullsLast(BigDecimal::compareTo));
        };
        return asc ? cmp : cmp.reversed();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CollectResult {
        private boolean success;
        private int totalRows;
        private int insertedRows;
        private String tradeDate;
        private long costMs;
        private String errorMsg;

        public static CollectResult ok(int totalRows, int insertedRows, String tradeDate, long costMs) {
            return CollectResult.builder()
                    .success(true)
                    .totalRows(totalRows)
                    .insertedRows(insertedRows)
                    .tradeDate(tradeDate)
                    .costMs(costMs)
                    .build();
        }

        public static CollectResult fail(String errorMsg) {
            return CollectResult.builder()
                    .success(false)
                    .errorMsg(errorMsg)
                    .build();
        }
    }

    /**
     * 获取所有去重的行业名称列表
     *
     * @return 行业名称列表
     */
    public List<String> getIndustryNames() {
        List<String> names = flowMapper.selectDistinctIndustryNames();
        return names != null ? names : List.of();
    }
}
