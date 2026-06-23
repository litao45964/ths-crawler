package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线性回归结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionResult {
    private double slope;
    private double intercept;
    private double rSquared;
    private int sampleCount;
}
