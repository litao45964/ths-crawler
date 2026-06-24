package com.ths.crawler.service;

import com.ths.crawler.mapper.TradeCalendarMapper;
import com.ths.crawler.model.entity.TradeCalendarEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 交易日历服务
 * <p>
 * 核心职责：
 * 1. 判定某日是否交易日
 * 2. 查询最近交易日（含当日或往前）
 * 3. 查询前一交易日
 * 4. 提供年度交易日列表（前端日历控件用）
 * <p>
 * 缓存策略：按日期缓存isTradeDay结果，按年缓存交易日列表，避免重复查库
 * 兜底策略：数据库无记录时退化为周末判断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TradeCalendarService {

    private final TradeCalendarMapper tradeCalendarMapper;

    /** 日期→是否交易日缓存 */
    private final Map<LocalDate, Boolean> tradeDayCache = new ConcurrentHashMap<>();

    /** 年份→交易日列表缓存 */
    private final Map<Integer, List<LocalDate>> tradeDaysByYearCache = new ConcurrentHashMap<>();

    /**
     * 判定指定日期是否为交易日
     * <p>
     * 优先查数据库，数据库无记录时退化为周末判断（周一至周五=是，周六周日=否）
     *
     * @param date 待判定日期
     * @return true=交易日
     */
    public boolean isTradeDay(LocalDate date) {
        // 缓存命中
        Boolean cached = tradeDayCache.get(date);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        TradeCalendarEntity entity = tradeCalendarMapper.selectByTradeDate(date);
        boolean result;
        if (entity != null) {
            result = entity.getIsOpen() == 1;
        } else {
            // 兜底：退化为周末判断
            result = isWeekday(date);
            log.debug("交易日历无{}记录，退化为周末判断: {}", date, result);
        }

        tradeDayCache.put(date, result);
        return result;
    }

    /**
     * 获取指定日期及之前的最近交易日（含当日）
     *
     * @param date 参照日期
     * @return 最近交易日
     */
    public LocalDate getLatestTradeDay(LocalDate date) {
        LocalDate result = tradeCalendarMapper.selectLatestTradeDayBeforeOrOn(date);
        if (result != null) {
            return result;
        }

        // 兜底：往前找最近的非周末日
        LocalDate candidate = date;
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        log.debug("交易日历无数据，退化查找最近交易日: {} → {}", date, candidate);
        return candidate;
    }

    /**
     * 获取指定日期的前一个交易日（不含当日）
     *
     * @param date 参照日期
     * @return 前一交易日
     */
    public LocalDate getPreviousTradeDay(LocalDate date) {
        LocalDate result = tradeCalendarMapper.selectPreviousTradeDay(date);
        if (result != null) {
            return result;
        }

        // 兜底：往前找最近的非周末日
        LocalDate candidate = date.minusDays(1);
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    /**
     * 获取指定年份所有交易日列表
     *
     * @param year 年份
     * @return 交易日列表（升序）
     */
    public List<LocalDate> getTradeDays(int year) {
        List<LocalDate> cached = tradeDaysByYearCache.get(year);
        if (cached != null) {
            return cached;
        }

        List<LocalDate> result = tradeCalendarMapper.selectTradeDaysByYear(year)
                .stream()
                .map(TradeCalendarEntity::getTradeDate)
                .toList();
        tradeDaysByYearCache.put(year, result);
        return result;
    }

    /**
     * 清除缓存（用于交易日历更新后刷新）
     */
    public void clearCache() {
        tradeDayCache.clear();
        tradeDaysByYearCache.clear();
        log.info("交易日历缓存已清除");
    }

    // ===================== 私有方法 =====================

    /**
     * 简单工作日判断（不含节假日）
     */
    private boolean isWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
