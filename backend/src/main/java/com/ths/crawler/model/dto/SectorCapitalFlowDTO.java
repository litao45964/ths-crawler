package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 板块资金流向标准DTO（V1 AKShare方案）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorCapitalFlowDTO {

    private String sectorType;
    private String tradeDate;
    private String fetchTime;
    private List<SectorFlowItem> industryTop3;
    private List<SectorFlowItem> conceptTop3;

    /**
     * 板块单项数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorFlowItem {
        private Integer rank;
        private String boardName;
        private String boardType;
        private BigDecimal mainNetInflow;
        private BigDecimal mainInflow;
        private BigDecimal mainOutflow;
        private BigDecimal changePercent;
        private String leadStock;
        private BigDecimal leadChangePercent;
        private int upCount;
        private int downCount;
        // V2兼容字段
        private String sectorName;
        private BigDecimal netAmount;
        private BigDecimal inflowAmount;
        private BigDecimal outflowAmount;
        private BigDecimal changePct;
        private String leadingStock;
        private BigDecimal leadingStockPct;
    }
}
