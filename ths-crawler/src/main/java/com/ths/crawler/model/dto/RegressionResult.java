package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 线性回归结果
 * 使用最小二乘法拟合 y = slope * x + intercept
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegressionResult {

    /** 斜率 */
    private double slope;

    /** 截距 */
    private double intercept;

    /** 拟合优度R²（0~1） */
    private double rSquared;

    /** 样本数 */
    private int sampleCount;

    /**
     * 判断回归是否有效（R² >= 阈值）
     *
     * @param threshold R²阈值
     * @return 是否有效
     */
    public boolean isValid(double threshold) {
        return rSquared >= threshold;
    }
}
