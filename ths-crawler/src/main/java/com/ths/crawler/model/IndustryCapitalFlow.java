package com.ths.crawler.model;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * V2行业资金流水日度明细
 * 对应表: industry_capital_flow (schema_v2)
 */
@Data
public class IndustryCapitalFlow {
    private Long id;
    private LocalDate tradeDate;
    private String industryCode;
    private String industryName;
    private BigDecimal netAmount;
    private BigDecimal inflowAmount;
    private BigDecimal outflowAmount;
    private BigDecimal industryChangePct;
    private String leadingStock;
    private BigDecimal leadingStockPct;
}
