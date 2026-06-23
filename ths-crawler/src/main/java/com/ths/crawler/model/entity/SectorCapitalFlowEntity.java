package com.ths.crawler.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 板块资金流向 - MySQL实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ths_sector_capital_flow")
public class SectorCapitalFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 板块名称 */
    private String boardName;

    /** 板块类型：industry=行业, concept=概念 */
    private String boardType;

    /** 涨跌幅% */
    private BigDecimal changePercent;

    /** 主力净流入(元) */
    private BigDecimal mainNetInflow;

    /** 主力流入(元) */
    private BigDecimal mainInflow;

    /** 主力流出(元) */
    private BigDecimal mainOutflow;

    /** 领涨股 */
    private String leadStock;

    /** 领涨股涨幅% */
    private BigDecimal leadChangePercent;

    /** 当日排名 */
    private Integer flowRank;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 原始JSON */
    private String rawJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
