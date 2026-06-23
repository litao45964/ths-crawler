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

/**
 * 行业资金趋势统计实体（对应 industry_trend_stat 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("industry_trend_stat")
public class IndustryTrendStatEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String industryName;

    /** 统计周期（天数） */
    private Integer statPeriod;

    /** 有效样本数 */
    private Integer sampleCount;

    /** 趋势斜率 */
    private BigDecimal trendSlope;

    /** 截距 */
    private BigDecimal intercept;

    /** R² 拟合度 */
    private BigDecimal rSquared;

    /** 累计净额(万元) */
    private BigDecimal totalNetAmount;

    /** 均值净额(万元) */
    private BigDecimal avgNetAmount;

    /** 标准差(万元) */
    private BigDecimal stdNetAmount;

    /** 最小净额(万元) */
    private BigDecimal minNetAmount;

    /** 最大净额(万元) */
    private BigDecimal maxNetAmount;
}
