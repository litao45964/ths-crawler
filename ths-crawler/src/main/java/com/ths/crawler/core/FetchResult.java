package com.ths.crawler.core;

import lombok.Builder;
import lombok.Data;

/**
 * 抓取结果 - 统一的返回容器
 */
@Data
@Builder
public class FetchResult<T> {

    /** 是否成功 */
    private boolean success;

    /** 结构化数据 */
    private T data;

    /** 原始JSON（留存，用于调试和回溯） */
    private String rawJson;

    /** 耗时毫秒 */
    private long costMs;

    /** 错误信息 */
    private String errorMsg;

    /** 数据源标识 */
    private String source;

    public static <T> FetchResult<T> empty() {
        return FetchResult.<T>builder()
                .success(false)
                .errorMsg("empty result")
                .build();
    }

    public static <T> FetchResult<T> fail(String errorMsg) {
        return FetchResult.<T>builder()
                .success(false)
                .errorMsg(errorMsg)
                .build();
    }

    public static <T> FetchResult<T> ok(T data, String rawJson, long costMs) {
        return FetchResult.<T>builder()
                .success(true)
                .data(data)
                .rawJson(rawJson)
                .costMs(costMs)
                .build();
    }
}
