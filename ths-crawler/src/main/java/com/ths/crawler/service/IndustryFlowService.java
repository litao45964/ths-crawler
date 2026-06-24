package com.ths.crawler.service;

import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.mapper.IndustryCapitalFlowMapper;
import com.ths.crawler.model.dto.IndustryFlowDTO;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
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

            return CollectResult.ok(entities.size(), inserted, LocalDate.now().toString(), costMs);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("[traceId={}] 日度行业资金流向采集失败, cost={}ms", traceId, costMs, e);
            return CollectResult.fail("采集异常: " + e.getMessage());
        }
    }

    public CollectResult manualCollect(String date) {
        log.info("手动补采行业资金流向: date={}", date);
        return collectDailyData();
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
        return switch (orderBy) {
            case "inflow_amount" -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getInflowAmount,
                    Comparator.nullsLast(BigDecimal::compareTo)).reversed();
            case "outflow_amount" -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getOutflowAmount,
                    Comparator.nullsLast(BigDecimal::compareTo)).reversed();
            case "industry_change_pct" -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getIndustryChangePct,
                    Comparator.nullsLast(BigDecimal::compareTo)).reversed();
            default -> Comparator.comparing(
                    IndustryCapitalFlowEntity::getNetAmount,
                    Comparator.nullsLast(BigDecimal::compareTo)).reversed();
        };
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
