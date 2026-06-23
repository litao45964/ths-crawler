package com.ths.crawler.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 行业资金趋势统计实体
 * 对应表: industry_trend_stat
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("industry_trend_stat")
public class IndustryTrendStatEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 统计日期 */
    private LocalDate tradeDate;

    /** 行业名称 */
    private String industryName;

    /** 统计周期（天数） */
    private Integer statPeriod;

    /** 实际有效样本量 */
    private Integer sampleCount;

    /** 线性回归斜率 */
    private BigDecimal trendSlope;

    /** 线性回归截距 */
    private BigDecimal intercept;

    /** 拟合优度R² */
    private BigDecimal rSquared;

    /** 周期内净额总和 */
    private BigDecimal totalNetAmount;

    /** 周期内净额均值 */
    private BigDecimal avgNetAmount;

    /** 周期内净额标准差 */
    private BigDecimal stdNetAmount;

    /** 周期内最小净额 */
    private BigDecimal minNetAmount;

    /** 周期内最大净额 */
    private BigDecimal maxNetAmount;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
