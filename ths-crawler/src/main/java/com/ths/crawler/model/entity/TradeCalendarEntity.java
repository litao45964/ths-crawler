package com.ths.crawler.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * A股交易日历表实体
 * <p>
 * 数据来源：公开A股交易日历
 * 用途：判定交易日、查询最近交易日、前端日历控件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("trade_calendar")
public class TradeCalendarEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 是否交易日: 1=是 0=否 */
    private Integer isOpen;

    /** 年份（冗余字段，加速按年查询） */
    private Integer year;
}
