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
    private String industryName;
    private LocalDate tradeDate;
    private int shortPeriod;
    private int longPeriod;
    private BigDecimal shortSlope;
    private BigDecimal shortRSquared;
    private BigDecimal longSlope;
    private BigDecimal longRSquared;
    private String signalType;
    private String signalDesc;
    private BigDecimal shortAvgNet;
    private BigDecimal longAvgNet;
}
