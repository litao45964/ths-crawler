package com.ths.crawler.unit.service;

import com.ths.crawler.mapper.TradeCalendarMapper;
import com.ths.crawler.model.entity.TradeCalendarEntity;
import com.ths.crawler.service.TradeCalendarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeCalendarServiceTest {

    @Mock
    private TradeCalendarMapper tradeCalendarMapper;

    private TradeCalendarService tradeCalendarService;

    @BeforeEach
    void setUp() {
        tradeCalendarService = new TradeCalendarService(tradeCalendarMapper);
    }

    @Nested
    @DisplayName("isTradeDay - 交易日判定")
    class IsTradeDayTest {

        @Test
        @DisplayName("普通工作日是交易日")
        void 普通工作日是交易日() {
            LocalDate workday = LocalDate.of(2026, 6, 24);
            when(tradeCalendarMapper.selectByTradeDate(workday))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(workday).isOpen(1).build());
            assertThat(tradeCalendarService.isTradeDay(workday)).isTrue();
        }

        @Test
        @DisplayName("周六不是交易日")
        void 周六不是交易日() {
            LocalDate saturday = LocalDate.of(2026, 6, 27);
            when(tradeCalendarMapper.selectByTradeDate(saturday))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(saturday).isOpen(0).build());
            assertThat(tradeCalendarService.isTradeDay(saturday)).isFalse();
        }

        @Test
        @DisplayName("周日不是交易日")
        void 周日不是交易日() {
            LocalDate sunday = LocalDate.of(2026, 6, 28);
            when(tradeCalendarMapper.selectByTradeDate(sunday))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(sunday).isOpen(0).build());
            assertThat(tradeCalendarService.isTradeDay(sunday)).isFalse();
        }

        @Test
        @DisplayName("国庆节不是交易日")
        void 国庆节不是交易日() {
            LocalDate nationalDay = LocalDate.of(2026, 10, 1);
            when(tradeCalendarMapper.selectByTradeDate(nationalDay))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(nationalDay).isOpen(0).build());
            assertThat(tradeCalendarService.isTradeDay(nationalDay)).isFalse();
        }

        @Test
        @DisplayName("调休补班是交易日（如周六补班）")
        void 调休补班是交易日() {
            LocalDate makeupWorkday = LocalDate.of(2026, 9, 27);
            when(tradeCalendarMapper.selectByTradeDate(makeupWorkday))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(makeupWorkday).isOpen(1).build());
            assertThat(tradeCalendarService.isTradeDay(makeupWorkday)).isTrue();
        }

        @Test
        @DisplayName("数据库无记录时退化为周末判断")
        void 数据库无记录时退化为周末判断() {
            LocalDate workday = LocalDate.of(2026, 6, 24);
            LocalDate saturday = LocalDate.of(2026, 6, 27);
            when(tradeCalendarMapper.selectByTradeDate(any())).thenReturn(null);
            assertThat(tradeCalendarService.isTradeDay(workday)).isTrue();
            assertThat(tradeCalendarService.isTradeDay(saturday)).isFalse();
        }
    }

    @Nested
    @DisplayName("getLatestTradeDay - 获取最近交易日")
    class GetLatestTradeDayTest {

        @Test
        @DisplayName("当日是交易日，返回当日")
        void 当日是交易日_返回当日() {
            LocalDate today = LocalDate.of(2026, 6, 24);
            when(tradeCalendarMapper.selectLatestTradeDayBeforeOrOn(today)).thenReturn(today);
            assertThat(tradeCalendarService.getLatestTradeDay(today)).isEqualTo(today);
        }

        @Test
        @DisplayName("当日是周末，返回上周五")
        void 当日是周末_返回上周五() {
            LocalDate saturday = LocalDate.of(2026, 6, 27);
            LocalDate friday = LocalDate.of(2026, 6, 26);
            when(tradeCalendarMapper.selectLatestTradeDayBeforeOrOn(saturday)).thenReturn(friday);
            assertThat(tradeCalendarService.getLatestTradeDay(saturday)).isEqualTo(friday);
        }

        @Test
        @DisplayName("当日是国庆节，返回9月30日")
        void 当日是国庆_返回9月30日() {
            LocalDate nationalDay = LocalDate.of(2026, 10, 1);
            LocalDate lastDayBeforeHoliday = LocalDate.of(2026, 9, 30);
            when(tradeCalendarMapper.selectLatestTradeDayBeforeOrOn(nationalDay)).thenReturn(lastDayBeforeHoliday);
            assertThat(tradeCalendarService.getLatestTradeDay(nationalDay)).isEqualTo(lastDayBeforeHoliday);
        }

        @Test
        @DisplayName("数据库无记录时退化为周末判断")
        void 数据库无记录时退化() {
            LocalDate saturday = LocalDate.of(2026, 6, 27);
            LocalDate friday = LocalDate.of(2026, 6, 26);
            when(tradeCalendarMapper.selectLatestTradeDayBeforeOrOn(saturday)).thenReturn(null);
            LocalDate result = tradeCalendarService.getLatestTradeDay(saturday);
            assertThat(result).isEqualTo(friday);
        }
    }

    @Nested
    @DisplayName("getPreviousTradeDay - 获取前一交易日")
    class GetPreviousTradeDayTest {

        @Test
        @DisplayName("周一的前一交易日是上周五")
        void 周一的前一交易日是上周五() {
            LocalDate monday = LocalDate.of(2026, 6, 22);
            LocalDate friday = LocalDate.of(2026, 6, 19);
            when(tradeCalendarMapper.selectPreviousTradeDay(monday)).thenReturn(friday);
            assertThat(tradeCalendarService.getPreviousTradeDay(monday)).isEqualTo(friday);
        }

        @Test
        @DisplayName("周三的前一交易日是周二")
        void 周三的前一交易日是周二() {
            LocalDate wednesday = LocalDate.of(2026, 6, 24);
            LocalDate tuesday = LocalDate.of(2026, 6, 23);
            when(tradeCalendarMapper.selectPreviousTradeDay(wednesday)).thenReturn(tuesday);
            assertThat(tradeCalendarService.getPreviousTradeDay(wednesday)).isEqualTo(tuesday);
        }

        @Test
        @DisplayName("国庆后首个交易日的前一交易日是9月30日")
        void 国庆后前一交易日() {
            LocalDate firstDayAfterHoliday = LocalDate.of(2026, 10, 8);
            LocalDate lastDayBeforeHoliday = LocalDate.of(2026, 9, 30);
            when(tradeCalendarMapper.selectPreviousTradeDay(firstDayAfterHoliday)).thenReturn(lastDayBeforeHoliday);
            assertThat(tradeCalendarService.getPreviousTradeDay(firstDayAfterHoliday)).isEqualTo(lastDayBeforeHoliday);
        }
    }

    @Nested
    @DisplayName("getTradeDays - 获取年度交易日列表")
    class GetTradeDaysTest {

        @Test
        @DisplayName("查询2026年交易日列表")
        void 查询年度交易日列表() {
            List<LocalDate> mockDays = List.of(
                    LocalDate.of(2026, 1, 2),
                    LocalDate.of(2026, 1, 5),
                    LocalDate.of(2026, 1, 6)
            );
            when(tradeCalendarMapper.selectTradeDaysByYear(2026)).thenReturn(mockDays);
            List<LocalDate> result = tradeCalendarService.getTradeDays(2026);
            assertThat(result).hasSize(3);
            assertThat(result.get(0)).isEqualTo(LocalDate.of(2026, 1, 2));
        }

        @Test
        @DisplayName("空年份返回空列表")
        void 空年份返回空列表() {
            when(tradeCalendarMapper.selectTradeDaysByYear(2099)).thenReturn(List.of());
            List<LocalDate> result = tradeCalendarService.getTradeDays(2099);
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("缓存机制验证")
    class CacheTest {

        @Test
        @DisplayName("同一日期多次查询isTradeDay，只查一次数据库")
        void 缓存命中_不查数据库() {
            LocalDate date = LocalDate.of(2026, 6, 24);
            when(tradeCalendarMapper.selectByTradeDate(date))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(date).isOpen(1).build());
            tradeCalendarService.isTradeDay(date);
            tradeCalendarService.isTradeDay(date);
            tradeCalendarService.isTradeDay(date);
            verify(tradeCalendarMapper, times(1)).selectByTradeDate(date);
        }

        @Test
        @DisplayName("不同日期查询各自命中数据库")
        void 不同日期各自查询() {
            LocalDate date1 = LocalDate.of(2026, 6, 24);
            LocalDate date2 = LocalDate.of(2026, 6, 25);
            when(tradeCalendarMapper.selectByTradeDate(date1))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(date1).isOpen(1).build());
            when(tradeCalendarMapper.selectByTradeDate(date2))
                    .thenReturn(TradeCalendarEntity.builder().tradeDate(date2).isOpen(1).build());
            tradeCalendarService.isTradeDay(date1);
            tradeCalendarService.isTradeDay(date2);
            verify(tradeCalendarMapper, times(1)).selectByTradeDate(date1);
            verify(tradeCalendarMapper, times(1)).selectByTradeDate(date2);
        }

        @Test
        @DisplayName("年度交易日列表缓存生效")
        void 年度交易日缓存() {
            when(tradeCalendarMapper.selectTradeDaysByYear(2026))
                    .thenReturn(List.of(LocalDate.of(2026, 1, 2)));
            tradeCalendarService.getTradeDays(2026);
            tradeCalendarService.getTradeDays(2026);
            verify(tradeCalendarMapper, times(1)).selectTradeDaysByYear(2026);
        }
    }
}
