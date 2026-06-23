package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AKShare返回的单条板块资金流向原始数据
 * 每行对应一个板块的资金流向记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AkshareSectorFlowRawDTO {

    private Integer rank;
    private String boardName;
    private BigDecimal mainNetInflow;
    private BigDecimal mainInflow;
    private BigDecimal mainOutflow;
    private BigDecimal changePercent;
    private String leadStock;
    private BigDecimal leadChangePercent;
    private int upCount;
    private int downCount;
}
