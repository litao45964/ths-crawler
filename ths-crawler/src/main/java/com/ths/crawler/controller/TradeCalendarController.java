package com.ths.crawler.controller;

import com.ths.crawler.service.TradeCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 交易日历API
 * <p>
 * 供前端日历控件查询交易日列表，支持灰置非交易日
 */
@Slf4j
@RestController
@RequestMapping("/api/trade-calendar")
@RequiredArgsConstructor
public class TradeCalendarController {

    private final TradeCalendarService tradeCalendarService;

    @GetMapping
    public Map<String, Object> getTradeDays(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().getYear()}") int year) {
        List<LocalDate> tradeDays = tradeCalendarService.getTradeDays(year);
        List<String> dateStrings = tradeDays.stream()
                .map(LocalDate::toString)
                .toList();
        return Map.of(
                "success", true,
                "data", dateStrings,
                "count", dateStrings.size()
        );
    }

    @GetMapping("/check")
    public Map<String, Object> checkTradeDay(@RequestParam String date) {
        LocalDate localDate = LocalDate.parse(date);
        boolean isTradeDay = tradeCalendarService.isTradeDay(localDate);
        return Map.of(
                "success", true,
                "data", Map.of("date", date, "isTradeDay", isTradeDay)
        );
    }

    @GetMapping("/latest")
    public Map<String, Object> getLatestTradeDay(
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }
        LocalDate latestTradeDay = tradeCalendarService.getLatestTradeDay(date);
        return Map.of(
                "success", true,
                "data", Map.of("date", latestTradeDay.toString())
        );
    }
}
