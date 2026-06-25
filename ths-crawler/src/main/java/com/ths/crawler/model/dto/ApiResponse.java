package com.ths.crawler.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 统一API响应格式
 * <p>
 * 所有Controller返回此类型，前端统一按此结构解析。
 * 替代原有的 String(JSON) 和 Map<String,Object> 返回方式。
 *
 * @param <T> 数据类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private T data;
    private Integer count;
    private long timestamp;
    private String message;

    // ==================== 构造器 ====================

    private ApiResponse() {
        this.timestamp = System.currentTimeMillis();
    }

    // ==================== 静态工厂方法 ====================

    /** 成功响应（无数据） */
    public static <T> ApiResponse<T> ok() {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        return r;
    }

    /** 成功响应（带数据） */
    public static <T> ApiResponse<T> ok(T data) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        return r;
    }

    /** 成功响应（带数据和计数） */
    public static <T> ApiResponse<T> ok(T data, int count) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = true;
        r.data = data;
        r.count = count;
        return r;
    }

    /** 失败响应 */
    public static <T> ApiResponse<T> fail(String message) {
        ApiResponse<T> r = new ApiResponse<>();
        r.success = false;
        r.message = message;
        return r;
    }

    // ==================== Getter/Setter ====================

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}