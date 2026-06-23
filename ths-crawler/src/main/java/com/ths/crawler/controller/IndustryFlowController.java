package com.ths.crawler.controller;

import com.alibaba.fastjson2.JSON;
import com.ths.crawler.model.dto.IndustryFlowDTO;
import com.ths.crawler.model.dto.TrendResonanceDTO;
import com.ths.crawler.model.entity.IndustryTrendStatEntity;
import com.ths.crawler.mapper.IndustryTrendStatMapper;
import com.ths.crawler.service.IndustryFlowService;
import com.ths.crawler.service.TrendStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 行业资金流向 V2 API
 * <p>
 * 接口列表：
 * POST /api/industry-flow/collect         - 手动触发日度采集
 * GET  /api/industry-flow/latest          - 查询最新日度排行
 * GET  /api/industry-flow/trend           - 查询单行业趋势
 * GET  /api/industry-flow/resonance       - 长短周期共振信号
 */
@Slf4j
@RestController
@RequestMapping("/api/industry-flow")
@RequiredArgsConstructor
public class IndustryFlowController {

    private final IndustryFlowService flowService;
    private final TrendStatService trendService;
    private final IndustryTrendStatMapper trendMapper;

    /**
     * 手动触发日度采集
     * POST /api/industry-flow/collect
     */
    @PostMapping("/collect")
    public String collect() {
        log.info("手动触发行业资金流向采集");
        IndustryFlowService.CollectResult result = flowService.collectDailyData();
        return JSON.toJSONString(result);
    }

    /**
     * 手动补采指定日期数据
     * POST /api/industry-flow/collect?date=2026-06-17
     */
    @PostMapping("/collect/{date}")
    public String manualCollect(@PathVariable String date) {
        log.info("手动补采行业资金流向: date={}", date);
        IndustryFlowService.CollectResult result = flowService.manualCollect(date);
        return JSON.toJSONString(result);
    }

    /**
     * 查询最新日度排行
     * GET /api/industry-flow/latest?topN=10&orderBy=net_amount
     *
     * @param topN    返回条数（默认10）
     * @param orderBy 排序字段（net_amount / inflow_amount / outflow_amount / industry_change_pct）
     */
    @GetMapping("/latest")
    public String getLatest(
            @RequestParam(defaultValue = "10") Integer topN,
            @RequestParam(defaultValue = "net_amount") String orderBy) {
        log.info("查询最新行业资金流向: topN={}, orderBy={}", topN, orderBy);
        List<IndustryFlowDTO> list = flowService.getLatestFlow(topN, orderBy);
        return JSON.toJSONString(Map.of(
                "success", true,
                "data", list,
                "count", list.size()
        ));
    }

    /**
     * 查询单行业趋势
     * GET /api/industry-flow/trend?industry=半导体&period=22
     *
     * @param industry 行业名称
     * @param period   统计周期（5, 10, 14, 22, 30, 60）
     */
    @GetMapping("/trend")
    public String getTrend(
            @RequestParam String industry,
            @RequestParam(defaultValue = "22") Integer period) {
        log.info("查询行业趋势: industry={}, period={}", industry, period);
        LocalDate latestDate = trendMapper.selectByIndustryAndDateRange(industry,
                LocalDate.now().minusDays(1), LocalDate.now())
                .stream().findFirst()
                .map(IndustryTrendStatEntity::getTradeDate)
                .orElse(null);
        if (latestDate == null) {
            // 尝试从flow表获取最新日期
            latestDate = LocalDate.now();
        }
        IndustryTrendStatEntity stat = trendMapper.selectByIndustryDatePeriod(industry, latestDate, period);
        return JSON.toJSONString(Map.of(
                "success", true,
                "industry", industry,
                "period", period,
                "tradeDate", latestDate.toString(),
                "data", stat
        ));
    }

    /**
     * 长短周期共振信号
     * GET /api/industry-flow/resonance?shortPeriod=5&longPeriod=22
     *
     * @param shortPeriod 短周期（默认5）
     * @param longPeriod  长周期（默认22）
     */
    @GetMapping("/resonance")
    public String getResonance(
            @RequestParam(defaultValue = "5") Integer shortPeriod,
            @RequestParam(defaultValue = "22") Integer longPeriod) {
        log.info("查询共振信号: shortPeriod={}, longPeriod={}", shortPeriod, longPeriod);
        List<TrendResonanceDTO> list = trendService.calculateResonance(shortPeriod, longPeriod);
        return JSON.toJSONString(Map.of(
                "success", true,
                "shortPeriod", shortPeriod,
                "longPeriod", longPeriod,
                "data", list,
                "count", list.size()
        ));
    }

    /**
     * 触发趋势计算（手动）
     * POST /api/industry-flow/trend/calculate
     */
    @PostMapping("/trend/calculate")
    public String calculateTrend() {
        log.info("手动触发趋势计算");
        TrendStatService.TrendCalcResult result = trendService.calculateDailyTrendStat();
        return JSON.toJSONString(result);
    }
}
