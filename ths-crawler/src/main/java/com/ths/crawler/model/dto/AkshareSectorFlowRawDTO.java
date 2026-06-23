package com.ths.crawler.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * AKShare原始返回的单条板块资金数据
 * 字段名与AKShare返回的DataFrame列名对应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AkshareSectorFlowRawDTO {

    /** 排名 */
    private Integer rank;

    /** 板块名称 */
    private String boardName;

    /** 涨跌幅% */
    private BigDecimal changePercent;

    /** 主力净流入-净额（元） */
    private BigDecimal mainNetInflow;

    /** 主力净流入-净占比% */
    private BigDecimal mainNetInflowRatio;

    /** 超大单净流入-净额 */
    private BigDecimal superLargeNetInflow;

    /** 超大单净流入-净占比 */
    private BigDecimal superLargeNetInflowRatio;

    /** 大单净流入-净额 */
    private BigDecimal largeNetInflow;

    /** 大单净流入-净占比 */
    private BigDecimal largeNetInflowRatio;

    /** 中单净流入-净额 */
    private BigDecimal mediumNetInflow;

    /** 中单净流入-净占比 */
    private BigDecimal mediumNetInflowRatio;

    /** 小单净流入-净额 */
    private BigDecimal smallNetInflow;

    /** 小单净流入-净占比 */
    private BigDecimal smallNetInflowRatio;

    /** 领涨股 */
    private String leadStock;

    /** 领涨股涨跌幅% */
    private BigDecimal leadChangePercent;

    /** 上涨家数 */
    private Integer upCount;

    /** 下跌家数 */
    private Integer downCount;
}
