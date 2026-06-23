package com.ths.crawler.controller;

import com.ths.crawler.model.IndustryCapitalFlow;
import com.ths.crawler.model.ResonanceResult;
import com.ths.crawler.model.TrendResult;
import com.ths.crawler.service.IndustryFlowService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/industry-flow")
public class IndustryFlowController {

    private final IndustryFlowService service;

    public IndustryFlowController(IndustryFlowService service) {
        this.service = service;
    }

    @GetMapping("/latest")
    public Map<String, Object> getLatest(
            @RequestParam(defaultValue = "10") int topN,
            @RequestParam(defaultValue = "net_amount") String orderBy) {
        List<IndustryCapitalFlow> list = service.getLatest(topN, orderBy);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", list);
        result.put("count", list.size());
        return result;
    }

    @GetMapping("/industries")
    public Map<String, Object> getIndustries() {
        List<String> list = service.getAllIndustries();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("data", list);
        result.put("count", list.size());
        return result;
    }

    @GetMapping("/history")
    public Map<String, Object> getHistory(
            @RequestParam String industry,
            @RequestParam(defaultValue = "60") int days) {
        List<IndustryCapitalFlow> list = service.getHistory(industry, days);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("industry", industry);
        result.put("days", days);
        result.put("data", list);
        result.put("count", list.size());
        return result;
    }

    @GetMapping("/trend")
    public Map<String, Object> getTrend(
            @RequestParam String industry,
            @RequestParam(defaultValue = "22") int period) {
        TrendResult trend = service.getTrend(industry, period);
        LocalDate tradeDate = service.getLatestTradeDate();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("industry", industry);
        result.put("period", period);
        result.put("tradeDate", tradeDate);
        result.put("data", trend);
        return result;
    }

    @GetMapping("/resonance")
    public Map<String, Object> getResonance(
            @RequestParam(defaultValue = "5") int shortPeriod,
            @RequestParam(defaultValue = "22") int longPeriod) {
        List<ResonanceResult> list = service.getResonance(shortPeriod, longPeriod);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("shortPeriod", shortPeriod);
        result.put("longPeriod", longPeriod);
        result.put("data", list);
        result.put("count", list.size());
        return result;
    }

    @PostMapping("/trend/calculate")
    public Map<String, Object> calculateTrend() {
        int count = service.calculateAndSaveTrend();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "趋势计算完成，共处理 " + count + " 条记录");
        result.put("count", count);
        return result;
    }

    @PostMapping("/collect")
    public Map<String, Object> collect() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "采集任务已触发（模拟）");
        return result;
    }
}
