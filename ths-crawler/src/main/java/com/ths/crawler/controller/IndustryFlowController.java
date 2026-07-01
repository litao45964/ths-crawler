package com.ths.crawler.controller;

import com.ths.crawler.model.dto.ApiResponse;
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
    public ApiResponse<IndustryFlowService.CollectResult> collect() {
        log.info("手动触发行业资金流向采集");
        IndustryFlowService.CollectResult result = flowService.collectDailyData();
        return ApiResponse.ok(result);
    }

    /**
     * 手动补采指定日期数据
     * POST /api/industry-flow/collect/{date}
     */
    @PostMapping("/collect/{date}")
    public ApiResponse<IndustryFlowService.CollectResult> manualCollect(@PathVariable String date) {
        log.info("手动补采行业资金流向: date={}", date);
        IndustryFlowService.CollectResult result = flowService.manualCollect(date);
        return ApiResponse.ok(result);
    }

    /**
     * 从CSV文件导入行业资金流向数据
     * POST /api/industry-flow/import-csv?filePath=/path/to/file.csv
     *
     * @param filePath CSV文件绝对路径
     */
    @PostMapping("/import-csv")
    public ApiResponse<IndustryFlowService.CollectResult> importCsv(
            @RequestParam String filePath) {
        log.info("CSV导入行业资金流向: filePath={}", filePath);
        IndustryFlowService.CollectResult result = flowService.importFromCsv(filePath);
        if (result.isSuccess()) {
            return ApiResponse.ok(result);
        }
        return ApiResponse.fail(result.getErrorMsg());
    }

    /**
     * 查询最新日度排行
     * GET /api/industry-flow/latest?topN=10&orderBy=net_amount
     *
     * @param topN    返回条数（默认10）
     * @param orderBy 排序字段（net_amount / inflow_amount / outflow_amount / industry_change_pct）
     */
    @GetMapping("/latest")
    public ApiResponse<List<IndustryFlowDTO>> getLatest(
            @RequestParam(defaultValue = "10") Integer topN,
            @RequestParam(defaultValue = "net_amount") String orderBy) {
        log.info("查询最新行业资金流向: topN={}, orderBy={}", topN, orderBy);
        List<IndustryFlowDTO> list = flowService.getLatestFlow(topN, orderBy);
        return ApiResponse.ok(list, list.size());
    }

    /**
     * 查询单行业趋势
     * GET /api/industry-flow/trend?industry=半导体&period=22
     *
     * @param industry 行业名称
     * @param period   统计周期（5, 10, 14, 22, 30, 60）
     */
    @GetMapping("/trend")
    public ApiResponse<IndustryTrendStatEntity> getTrend(
            @RequestParam String industry,
            @RequestParam(defaultValue = "22") Integer period) {
        log.info("查询行业趋势: industry={}, period={}", industry, period);

        // 先从trend_stat表查找最新数据
        LocalDate latestDate = null;
        List<IndustryTrendStatEntity> recentStats = trendMapper.selectByIndustryAndDateRange(industry,
                LocalDate.now().minusDays(60), LocalDate.now());
        if (!recentStats.isEmpty()) {
            latestDate = recentStats.get(recentStats.size() - 1).getTradeDate();
        }

        // trend_stat无数据时，从flow表获取最新交易日
        if (latestDate == null) {
            latestDate = flowService.getLatestFlow(1, "net_amount")
                    .stream().findFirst()
                    .map(IndustryFlowDTO::getTradeDate)
                    .orElse(null);
        }

        if (latestDate == null) {
            return ApiResponse.fail("无可用数据");
        }

        IndustryTrendStatEntity stat = trendMapper.selectByIndustryDatePeriod(industry, latestDate, period);
        if (stat == null) {
            return ApiResponse.fail("该行业无趋势统计数据，请先执行趋势计算 POST /api/industry-flow/trend/calculate");
        }

        return ApiResponse.ok(stat);
    }

    /**
     * 长短周期共振信号
     * GET /api/industry-flow/resonance?shortPeriod=5&longPeriod=22
     *
     * @param shortPeriod 短周期（默认5）
     * @param longPeriod  长周期（默认22）
     */
    @GetMapping("/resonance")
    public ApiResponse<List<TrendResonanceDTO>> getResonance(
            @RequestParam(defaultValue = "5") Integer shortPeriod,
            @RequestParam(defaultValue = "22") Integer longPeriod) {
        log.info("查询共振信号: shortPeriod={}, longPeriod={}", shortPeriod, longPeriod);
        List<TrendResonanceDTO> list = trendService.calculateResonance(shortPeriod, longPeriod);
        return ApiResponse.ok(list, list.size());
    }

    /**
     * 查询行业名称列表
     * GET /api/industry-flow/industries
     */
    @GetMapping("/industries")
    public ApiResponse<List<String>> getIndustries() {
        log.info("查询行业名称列表");
        List<String> names = flowService.getIndustryNames();
        return ApiResponse.ok(names, names.size());
    }

    /**
     * 查询单行业历史净额序列
     * GET /api/industry-flow/history?industry=半导体&days=60
     *
     * @param industry 行业名称
     * @param days     回看天数（默认60）
     */
    @GetMapping("/history")
    public ApiResponse<List<IndustryFlowDTO>> getHistory(
            @RequestParam String industry,
            @RequestParam(defaultValue = "60") Integer days) {
        log.info("查询行业历史: industry={}, days={}", industry, days);
        List<IndustryFlowDTO> list = flowService.getIndustryHistory(industry, days);
        return ApiResponse.ok(list, list.size());
    }

    /**
     * 触发趋势计算（手动）
     * POST /api/industry-flow/trend/calculate
     */
    @PostMapping("/trend/calculate")
    public ApiResponse<TrendStatService.TrendCalcResult> calculateTrend() {
        log.info("手动触发趋势计算");
        TrendStatService.TrendCalcResult result = trendService.calculateDailyTrendStat();
        return ApiResponse.ok(result);
    }
}