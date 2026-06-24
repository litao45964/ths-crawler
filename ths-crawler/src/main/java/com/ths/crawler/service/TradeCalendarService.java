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

    private final Map<LocalDate, Boolean> tradeDayCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<LocalDate>> tradeDaysByYearCache = new ConcurrentHashMap<>();

    public boolean isTradeDay(LocalDate date) {
        Boolean cached = tradeDayCache.get(date);
        if (cached != null) {
            return cached;
        }

        TradeCalendarEntity entity = tradeCalendarMapper.selectByTradeDate(date);
        boolean result;
        if (entity != null) {
            result = entity.getIsOpen() == 1;
        } else {
            result = isWeekday(date);
            log.debug("交易日历无{}记录，退化为周末判断: {}", date, result);
        }

        tradeDayCache.put(date, result);
        return result;
    }

    public LocalDate getLatestTradeDay(LocalDate date) {
        LocalDate result = tradeCalendarMapper.selectLatestTradeDayBeforeOrOn(date);
        if (result != null) {
            return result;
        }
        LocalDate candidate = date;
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        log.debug("交易日历无数据，退化查找最近交易日: {} → {}", date, candidate);
        return candidate;
    }

    public LocalDate getPreviousTradeDay(LocalDate date) {
        LocalDate result = tradeCalendarMapper.selectPreviousTradeDay(date);
        if (result != null) {
            return result;
        }
        LocalDate candidate = date.minusDays(1);
        while (!isWeekday(candidate)) {
            candidate = candidate.minusDays(1);
        }
        return candidate;
    }

    public List<LocalDate> getTradeDays(int year) {
        List<LocalDate> cached = tradeDaysByYearCache.get(year);
        if (cached != null) {
            return cached;
        }
        List<LocalDate> result = tradeCalendarMapper.selectTradeDaysByYear(year);
        tradeDaysByYearCache.put(year, result);
        return result;
    }

    public void clearCache() {
        tradeDayCache.clear();
        tradeDaysByYearCache.clear();
        log.info("交易日历缓存已清除");
    }

    private boolean isWeekday(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
