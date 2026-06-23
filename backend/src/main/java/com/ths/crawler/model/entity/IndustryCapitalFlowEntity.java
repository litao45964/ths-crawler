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
 * 行业资金日度明细实体（对应 industry_capital_flow 表）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("industry_capital_flow")
public class IndustryCapitalFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String industryName;

    private String industryCode;

    /** 净额(万元) */
    private BigDecimal netAmount;

    /** 流入(万元) */
    private BigDecimal inflowAmount;

    /** 流出(万元) */
    private BigDecimal outflowAmount;

    /** 行业涨跌幅% */
    private BigDecimal industryChangePct;

    /** 领涨股 */
    private String leadingStock;

    /** 领涨股涨幅% */
    private BigDecimal leadingStockPct;
}
