package com.ths.crawler.scheduler;

import com.ths.crawler.service.IndustryFlowService;
import com.ths.crawler.service.TrendStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.DayOfWeek;

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

    /**
     * 第一步：行业资金采集
     * 每个交易日 15:30 收盘后执行
     * cron: 0 30 15 ? * MON-FRI
     */
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

    /**
     * 第二步：趋势计算
     * 每个交易日 16:30 执行（采集后1小时，确保数据已入库）
     * cron: 0 30 16 ? * MON-FRI
     */
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

    /**
     * 第三步：异动信号（预留）
     * 每个交易日 17:00 执行
     * cron: 0 0 17 ? * MON-FRI
     * TODO: 实现异动信号检测逻辑
     */
    @Scheduled(cron = "${ths.cron.industry-anomaly-signal:0 0 17 ? * MON-FRI}")
    public void detectAnomalySignal() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过异动信号检测");
            return;
        }

        log.info(">>> 定时任务：异动信号检测（预留） <<<");
        // TODO: 实现异动信号检测逻辑
        // 1. 查询最新趋势数据
        // 2. 计算共振信号
        // 3. 推送告警（可选）
        log.info(">>> 异动信号检测完成（预留接口） <<<");
    }

    /**
     * 第四步：数据校验
     * 每个交易日 09:00 开盘前执行
     * cron: 0 0 9 ? * MON-FRI
     */
    @Scheduled(cron = "${ths.cron.industry-data-verify:0 0 9 ? * MON-FRI}")
    public void verifyData() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过数据校验");
            return;
        }

        log.info(">>> 定时任务：数据校验开始 <<<");

        try {
            // 检查昨天数据是否完整
            LocalDate yesterday = LocalDate.now().minusDays(1);
            // TODO: 查询昨日数据条数，验证是否采集成功
            // 如果数据缺失，触发告警或自动补采

            log.info(">>> 数据校验完成 <<<");
        } catch (Exception e) {
            log.error("数据校验异常", e);
        }
    }

    /**
     * 交易日判断（简化版，后续接入交易日历API）
     */
    private boolean isTradeDay() {
        LocalDate today = LocalDate.now();
        DayOfWeek dow = today.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        // TODO: 节假日判断，接入交易日历
    }
}
