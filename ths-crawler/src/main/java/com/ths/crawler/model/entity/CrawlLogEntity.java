package com.ths.crawler.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 抓取任务日志 — P0-11 增强：traceId/phase/detail/retryCount
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ths_crawl_log")
public class CrawlLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 数据源标识 */
    private String source;

    /** 状态：SUCCESS / FAIL / PARTIAL */
    private String status;

    /** 采集阶段：fetch / save / total */
    private String phase;

    /** 抓取记录数 */
    private Integer recordCount;

    /** 耗时毫秒 */
    private Long costMs;

    /** 阶段详情（JSON） */
    private String detail;

    /** 重试次数 */
    private Integer retryCount;

    /** 错误信息 */
    private String errorMsg;

    /** 交易日期 */
    private LocalDate tradeDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
