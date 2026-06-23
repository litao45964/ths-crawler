package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 行业资金流向DTO（用于API返回）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndustryFlowDTO {

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 行业代码（881xxx） */
    private String industryCode;

    /** 行业名称 */
    private String industryName;

    /** 行业详情页URL */
    private String industryLink;

    /** 净额（万元） */
    private BigDecimal netAmount;

    /** 流入额（万元） */
    private BigDecimal inflowAmount;

    /** 流出额（万元） */
    private BigDecimal outflowAmount;

    /** 行业涨跌幅（%） */
    private BigDecimal industryChangePct;

    /** 领涨股 */
    private String leadingStock;

    /** 领涨股代码 */
    private String leadingStockCode;

    /** 领涨股详情页URL */
    private String leadingStockLink;

    /** 领涨股涨幅（%） */
    private BigDecimal leadingStockPct;

    /** 排名 */
    private Integer rank;
}
