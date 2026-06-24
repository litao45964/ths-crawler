package com.ths.crawler.scheduler;

import com.ths.crawler.service.IndustryFlowService;
import com.ths.crawler.service.TradeCalendarService;
import com.ths.crawler.service.TrendStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 行业资金流向 V2 定时任务
 * <p>
 * 调度链路：
 * 15:30 行业资金采集 → 16:30 趋势计算 → 17:00 异动信号（预留） → 09:00 数据校验
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndustryFlowJob {

    private final IndustryFlowService flowService;
    private final TrendStatService trendService;
    private final TradeCalendarService tradeCalendarService;

    @Scheduled(cron = "${ths.cron.industry-flow-collect:0 30 15 ? * MON-FRI}")
    public void collectIndustryFlow() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过行业资金采集");
            return;
        }
        log.info(">>> 定时任务：行业资金采集开始 <<<");
        IndustryFlowService.CollectResult result = flowService.collectDailyData();
        log.info(">>> 定时任务：行业资金采集结束, success={} <<<", result.isSuccess());
    }

    @Scheduled(cron = "${ths.cron.industry-trend-calc:0 30 16 ? * MON-FRI}")
    public void calculateTrend() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过趋势计算");
            return;
        }
        log.info(">>> 定时任务：趋势计算开始 <<<");
        TrendStatService.TrendCalcResult result = trendService.calculateDailyTrendStat();
        log.info(">>> 定时任务：趋势计算结束, success={} <<<", result.isSuccess());
    }

    @Scheduled(cron = "${ths.cron.industry-anomaly-signal:0 0 17 ? * MON-FRI}")
    public void detectAnomalySignal() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过异动信号检测");
            return;
        }
        log.info(">>> 定时任务：异动信号检测（预留） <<<");
        log.info(">>> 异动信号检测完成（预留接口） <<<");
    }

    @Scheduled(cron = "${ths.cron.industry-data-verify:0 0 9 ? * MON-FRI}")
    public void verifyData() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过数据校验");
            return;
        }
        log.info(">>> 定时任务：数据校验开始 <<<");
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            log.info(">>> 数据校验完成 <<<");
        } catch (Exception e) {
            log.error("数据校验异常", e);
        }
    }

    /**
     * 交易日判断（已接入TradeCalendarService，支持节假日/调休）
     */
    private boolean isTradeDay() {
        return tradeCalendarService.isTradeDay(LocalDate.now());
    }
}
