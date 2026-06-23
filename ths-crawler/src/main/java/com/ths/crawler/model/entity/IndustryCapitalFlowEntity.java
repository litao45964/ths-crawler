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
 * 行业资金日度明细实体
 * 对应表: industry_capital_flow
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("industry_capital_flow")
public class IndustryCapitalFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
