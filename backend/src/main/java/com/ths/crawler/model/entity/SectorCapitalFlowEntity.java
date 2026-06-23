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
 * 板块资金流向实体（V1 AKShare方案用）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sector_capital_flow")
public class SectorCapitalFlowEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String boardName;

    private String boardType;

    private BigDecimal mainNetInflow;

    private BigDecimal mainInflow;

    private BigDecimal mainOutflow;

    private BigDecimal changePercent;

    private String leadStock;

    private BigDecimal leadChangePercent;

    private Integer flowRank;

    private String rawJson;

    private LocalDateTime createdAt;
}
