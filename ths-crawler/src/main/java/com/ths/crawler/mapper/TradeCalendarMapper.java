package com.ths.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ths.crawler.model.entity.TradeCalendarEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

/**
 * A股交易日历 Mapper
 */
@Mapper
public interface TradeCalendarMapper extends BaseMapper<TradeCalendarEntity> {

    /**
     * 查询指定日期的交易日记录
     */
    @Select("SELECT id, trade_date, is_open, year FROM trade_calendar WHERE trade_date = #{date}")
    TradeCalendarEntity selectByTradeDate(@Param("date") LocalDate date);

    /**
     * 查询指定日期及之前的最近交易日
     */
    @Select("SELECT MAX(trade_date) FROM trade_calendar WHERE trade_date <= #{date} AND is_open = 1")
    LocalDate selectLatestTradeDayBeforeOrOn(@Param("date") LocalDate date);

    /**
     * 查询指定日期之前的最近一个交易日（不含当日）
     */
    @Select("SELECT MAX(trade_date) FROM trade_calendar WHERE trade_date < #{date} AND is_open = 1")
    LocalDate selectPreviousTradeDay(@Param("date") LocalDate date);

    /**
     * 查询指定年份所有交易日（返回实体列表，避免List<LocalDate>类型映射问题）
     */
    @Select("SELECT id, trade_date, is_open, year FROM trade_calendar WHERE year = #{year} AND is_open = 1 ORDER BY trade_date")
    List<TradeCalendarEntity> selectTradeDaysByYear(@Param("year") int year);
}
