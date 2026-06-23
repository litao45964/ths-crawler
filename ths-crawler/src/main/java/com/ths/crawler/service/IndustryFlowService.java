package com.ths.crawler.service;

import com.ths.crawler.fetcher.okhttp.ThsIndustryPageFetcher;
import com.ths.crawler.fetcher.okhttp.ThsIndustryPageFetcher.IndustryFlowData;
import com.ths.crawler.mapper.IndustryCapitalFlowMapper;
import com.ths.crawler.model.dto.IndustryFlowDTO;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 行业资金流向业务编排服务
 * <p>
 * 负责协调抓取、转换、存储的完整流程
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryFlowService {

    private final ThsIndustryPageFetcher pageFetcher;
    private final IndustryCapitalFlowMapper flowMapper;

    @Value("${ths.fetcher.sector-flow:akshare}")
    private String fetcherType;

    /**
     * 日度采集入口
     * 抓取同花顺行业资金流向第1页和第2页数据并入库
     *
     * @return 采集结果
     */
    public CollectResult collectDailyData() {
        long start = System.currentTimeMillis();
        log.info("=== 开始日度行业资金流向采集 ===");

        try {
            // 1. 抓取行业数据（第1页 + 第2页）
            List<IndustryFlowData> rawData = pageFetcher.fetchIndustryFlowPages();
            if (rawData.isEmpty()) {
                log.error("行业资金流向数据为空");
                return CollectResult.fail("抓取数据为空");
            }

            // 2. 转换为实体
            LocalDate tradeDate = LocalDate.now();
            List<IndustryCapitalFlowEntity> entities = rawData.stream()
                    .map(d -> convertToEntity(d, tradeDate))
                    .collect(Collectors.toList());

            // 3. 批量入库
            int inserted = flowMapper.batchInsertOrUpdate(entities);

            long costMs = System.currentTimeMillis() - start;
            log.info("=== 日度行业资金流向采集完成: count={}, inserted={}, cost={}ms ===",
                    entities.size(), inserted, costMs);

            return CollectResult.ok(entities.size(), inserted, tradeDate.toString(), costMs);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("日度行业资金流向采集失败", e);
            return CollectResult.fail("采集异常: " + e.getMessage());
        }
    }

    /**
     * 手动补采指定日期数据
     * 注意：同花顺页面只提供当天数据，补采功能需要确认历史数据可用性
     *
     * @param date 指定日期（yyyy-MM-dd）
     * @return 采集结果
     */
    public CollectResult manualCollect(String date) {
        log.info("手动补采行业资金流向: date={}", date);
        // 同花顺页面仅提供当日数据，手动补采实际走当天采集流程
        // 如需历史数据，需通过其他API或缓存
        return collectDailyData();
    }

    /**
     * 查询最新日度数据
     *
     * @param topN    返回条数（null或0表示全部）
     * @param orderBy 排序字段（net_amount / inflow_amount / outflow_amount / industry_change_pct）
     * @return 行业资金流向DTO列表
     */
    public List<IndustryFlowDTO> getLatestFlow(Integer topN, String orderBy) {
        // 1. 查询最新交易日
        LocalDate latestDate = flowMapper.selectLatestTradeDate();
        if (latestDate == null) {
            log.warn("无可用数据");
            return List.of();
        }

        // 2. 查询当天所有行业数据
        List<IndustryCapitalFlowEntity> entities = flowMapper.selectByTradeDate(latestDate);

        // 3. 排序
        Comparator<IndustryCapitalFlowEntity> comparator = getComparator(orderBy);
        entities.sort(comparator);

        // 4. 截取topN
        if (topN != null && topN > 0 && entities.size() > topN) {
            entities = entities.subList(0, topN);
        }

        // 5. 转换为DTO
        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定行业的历史数据
     *
     * @param industryName 行业名称
     * @param days         查询天数
     * @return 行业资金流向DTO列表
     */
    public List<IndustryFlowDTO> getIndustryHistory(String industryName, int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days);

        List<IndustryCapitalFlowEntity> entities = flowMapper.selectByIndustryAndDateRange(
                industryName, startDate, endDate);

        return entities.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // ===================== 私有方法 =====================

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
        // 默认降序
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

    // ===================== 采集结果 =====================

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
}
