package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 长短周期共振信号DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendResonanceDTO {

    /** 行业名称 */
    private String industryName;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 短周期天数 */
    private Integer shortPeriod;

    /** 长周期天数 */
    private Integer longPeriod;

    /** 短周期斜率 */
    private BigDecimal shortSlope;

    /** 短周期R² */
    private BigDecimal shortRSquared;

    /** 长周期斜率 */
    private BigDecimal longSlope;

    /** 长周期R² */
    private BigDecimal longRSquared;

    /** 共振信号类型 */
    private String signalType;

    /** 信号描述 */
    private String signalDesc;

    /** 短周期净额均值 */
    private BigDecimal shortAvgNet;

    /** 长周期净额均值 */
    private BigDecimal longAvgNet;
}
