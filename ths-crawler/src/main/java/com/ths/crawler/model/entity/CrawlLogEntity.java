package com.ths.crawler.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 抓取任务日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("ths_crawl_log")
public class CrawlLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 数据源标识 */
    private String source;

    /** 状态：SUCCESS / FAIL / PARTIAL */
    private String status;

    /** 抓取记录数 */
    private Integer recordCount;

    /** 耗时毫秒 */
    private Long costMs;

    /** 错误信息 */
    private String errorMsg;

    /** 交易日期 */
    private LocalDate tradeDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
