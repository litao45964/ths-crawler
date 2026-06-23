package com.ths.crawler.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 爬取日志实体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("crawl_log")
public class CrawlLogEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String source;

    private String status;

    private Integer recordCount;

    private Long costMs;

    private String errorMsg;

    private LocalDateTime createdAt;
}
