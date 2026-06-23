package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 板块资金流向DTO - 清洗后的最终数据结构
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SectorCapitalFlowDTO {

    /** 交易日期 yyyy-MM-dd */
    private String tradeDate;

    /** 抓取时间 yyyy-MM-dd HH:mm:ss */
    private String fetchTime;

    /** 行业板块资金净流入前三 */
    private List<SectorFlowItem> industryTop3;

    /** 概念板块资金净流入前三 */
    private List<SectorFlowItem> conceptTop3;

    /**
     * 单个板块的资金流向数据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectorFlowItem {

        /** 排名 */
        private Integer rank;

        /** 板块名称 */
        private String boardName;

        /** 板块类型：industry=行业, concept=概念 */
        private String boardType;

        /** 涨跌幅% */
        private BigDecimal changePercent;

        /** 主力净流入（元） */
        private BigDecimal mainNetInflow;

        /** 主力流入（元） */
        private BigDecimal mainInflow;

        /** 主力流出（元） */
        private BigDecimal mainOutflow;

        /** 领涨股名称 */
        private String leadStock;

        /** 领涨股涨跌幅% */
        private BigDecimal leadChangePercent;

        /** 板块内上涨家数 */
        private Integer upCount;

        /** 板块内下跌家数 */
        private Integer downCount;
    }
}
