package com.ths.crawler.model;

import lombok.Data;
import java.math.BigDecimal;

/**
 * 线性回归趋势计算结果
 */
@Data
public class TrendResult {
    private int sampleCount;
    private double trendSlope;
    private double rSquared;
    private BigDecimal totalNetAmount;
    private BigDecimal avgNetAmount;
    private BigDecimal stdNetAmount;
    private BigDecimal minNetAmount;
    private BigDecimal maxNetAmount;
}
