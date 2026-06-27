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
@TableName("crawl_log")
public class CrawlLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 链路追踪ID */
    private String traceId;

    /** 任务类型 */
    private String taskType;

    /** 状态：running / success / failed */
    private String status;

    /** 采集阶段：fetch / save / total */
    private String phase;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 抓取行数 */
    private Integer rowsFetched;

    /** 保存行数 */
    private Integer rowsSaved;

    /** 阶段详情（JSON） */
    private String detail;

    /** 重试次数 */
    private Integer retryCount;

    /** 错误信息 */
    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
