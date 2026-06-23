package com.ths.crawler.core;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 抓取上下文 - 每次请求的参数容器
 */
@Data
@Builder
public class FetchContext {

    /** 股票代码（可选，个股查询时使用） */
    private String stockCode;

    /** 批量股票代码 */
    private java.util.List<String> stockCodes;

    /** 交易日期 */
    private String tradeDate;

    /** 扩展参数（不同Fetcher按需使用） */
    @Builder.Default
    private Map<String, String> extraParams = Map.of();
}
