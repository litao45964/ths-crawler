package com.ths.crawler.service;

import com.ths.crawler.mapper.IndustryCapitalFlowMapper;
import com.ths.crawler.mapper.IndustryTrendStatMapper;
import com.ths.crawler.model.dto.RegressionResult;
import com.ths.crawler.model.dto.TrendResonanceDTO;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import com.ths.crawler.model.entity.IndustryTrendStatEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 行业资金趋势计算服务
 * <p>
 * 功能：
 * 1. 基于历史净额数据计算线性回归趋势
 * 2. 预设6个统计周期：5, 10, 14, 22, 30, 60
 * 3. 有效样本不足周期80%则跳过
 * 4. 使用 parallelStream 并行计算
 * 5. 长短周期共振信号判断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrendStatService {

    private final IndustryCapitalFlowMapper flowMapper;
    private final IndustryTrendStatMapper trendMapper;

    /** 预设统计周期 */
    private static final int[] STAT_PERIODS = {5, 10, 14, 22, 30, 60};

    /** 有效样本最低比例（周期 × 0.8） */
    private static final double MIN_SAMPLE_RATIO = 0.8;

    /** R²过滤阈值（模拟数据R²普遍偏低，真实数据可调高到0.3-0.4） */
    private static final double R_SQUARED_THRESHOLD = 0.1;

    @Value("${ths.trend.min-sample-ratio:0.8}")
    private double minSampleRatio;

    @Value("${ths.trend.r-squared-threshold:0.1}")
    private double rSquaredThreshold;

    /**
     * 全行业全周期趋势计算
     * 使用 parallelStream 并行计算所有行业的所有周期
     *
     * @return 计算结果统计
     */
    public TrendCalcResult calculateDailyTrendStat() {
        long start = System.currentTimeMillis();
        log.info("=== 开始全行业趋势计算 ===");

        // 1. 获取最新交易日
        LocalDate tradeDate = flowMapper.selectLatestTradeDate();
        if (tradeDate == null) {
            log.error("无可用交易日数据");
            return TrendCalcResult.fail("无可用交易日数据");
        }

        // 2. 查询所有行业名称（从最新日度数据中获取）
        List<IndustryCapitalFlowEntity> latestData = flowMapper.selectByTradeDate(tradeDate);
        List<String> industryNames = latestData.stream()
                .map(IndustryCapitalFlowEntity::getIndustryName)
                .distinct()
                .collect(Collectors.toList());

        log.info("待计算行业数: {}, 交易日: {}", industryNames.size(), tradeDate);

        // 3. 并行计算每个行业的所有周期
        List<IndustryTrendStatEntity> allStats = new ArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        industryNames.parallelStream().forEach(industryName -> {
            try {
                List<IndustryTrendStatEntity> industryStats = new ArrayList<>();
                for (int period : STAT_PERIODS) {
                    IndustryTrendStatEntity stat = calculateStat(industryName, tradeDate, period);
                    if (stat != null) {
                        industryStats.add(stat);
                    }
                }
                if (!industryStats.isEmpty()) {
                    synchronized (allStats) {
                        allStats.addAll(industryStats);
                    }
                }
            } catch (Exception e) {
                errorCount.incrementAndGet();
                log.error("行业趋势计算异常: industry={}", industryName, e);
            }
        });

        if (errorCount.get() > 0) {
            log.warn("趋势计算中有{}个行业出现异常", errorCount.get());
        }

        // 4. 批量入库
        int inserted = 0;
        if (!allStats.isEmpty()) {
            // 分批插入，每批100条
            int batchSize = 100;
            for (int i = 0; i < allStats.size(); i += batchSize) {
                int end = Math.min(i + batchSize, allStats.size());
                List<IndustryTrendStatEntity> batch = allStats.subList(i, end);
                inserted += trendMapper.batchInsertOrUpdate(batch);
            }
        }

        long costMs = System.currentTimeMillis() - start;
        log.info("=== 趋势计算完成: industries={}, stats={}, inserted={}, cost={}ms ===",
                industryNames.size(), allStats.size(), inserted, costMs);

        return TrendCalcResult.ok(industryNames.size(), allStats.size(), inserted, tradeDate.toString(), costMs);
    }

    /**
     * 单行业单周期趋势计算
     *
     * @param industryName 行业名称
     * @param tradeDate    交易日期
     * @param period       统计周期（天数）
     * @return 趋势统计实体，样本不足返回null
     */
    public IndustryTrendStatEntity calculateStat(String industryName, LocalDate tradeDate, int period) {
        // 1. 查询历史数据
        // 注意：period是交易日数，minusDays用的是自然日，周末/节假日会导致实际交易日不足
        // 乘以2作为安全系数，确保回溯足够多的自然日来覆盖period个交易日
        LocalDate startDate = tradeDate.minusDays(Math.max(period * 2, period + 10));
        List<IndustryCapitalFlowEntity> historyData = flowMapper.selectByIndustryAndDateRange(
                industryName, startDate, tradeDate);

        // 2. 检查有效样本量
        int sampleCount = historyData.size();
        int minRequired = (int) Math.ceil(period * minSampleRatio);
        if (sampleCount < minRequired) {
            log.debug("样本不足跳过: industry={}, period={}, sample={}, required={}",
                    industryName, period, sampleCount, minRequired);
            return null;
        }

        // 3. 提取净额序列
        double[] y = historyData.stream()
                .mapToDouble(e -> e.getNetAmount() != null ? e.getNetAmount().doubleValue() : 0.0)
                .toArray();
        double[] x = new double[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            x[i] = i;
        }

        // 4. 线性回归
        RegressionResult regression = linearRegression(x, y);

        // 5. 统计量计算
        BigDecimal totalNet = BigDecimal.ZERO;
        BigDecimal minNet = null;
        BigDecimal maxNet = null;
        double sum = 0.0;
        double sumSq = 0.0;

        for (IndustryCapitalFlowEntity e : historyData) {
            BigDecimal net = e.getNetAmount() != null ? e.getNetAmount() : BigDecimal.ZERO;
            totalNet = totalNet.add(net);
            if (minNet == null || net.compareTo(minNet) < 0) minNet = net;
            if (maxNet == null || net.compareTo(maxNet) > 0) maxNet = net;
            sum += net.doubleValue();
            sumSq += net.doubleValue() * net.doubleValue();
        }

        double avg = sum / sampleCount;
        double variance = (sumSq / sampleCount) - (avg * avg);
        double std = Math.sqrt(Math.max(0, variance));

        // 6. 构建实体
        return IndustryTrendStatEntity.builder()
                .tradeDate(tradeDate)
                .industryName(industryName)
                .statPeriod(period)
                .sampleCount(sampleCount)
                .trendSlope(BigDecimal.valueOf(regression.getSlope()).setScale(10, RoundingMode.HALF_UP))
                .intercept(BigDecimal.valueOf(regression.getIntercept()).setScale(10, RoundingMode.HALF_UP))
                .rSquared(BigDecimal.valueOf(regression.getRSquared()).setScale(6, RoundingMode.HALF_UP))
                .totalNetAmount(totalNet.setScale(4, RoundingMode.HALF_UP))
                .avgNetAmount(BigDecimal.valueOf(avg).setScale(4, RoundingMode.HALF_UP))
                .stdNetAmount(BigDecimal.valueOf(std).setScale(4, RoundingMode.HALF_UP))
                .minNetAmount(minNet != null ? minNet.setScale(4, RoundingMode.HALF_UP) : null)
                .maxNetAmount(maxNet != null ? maxNet.setScale(4, RoundingMode.HALF_UP) : null)
                .build();
    }

    /**
     * 最小二乘法线性回归
     * y = slope * x + intercept
     *
     * @param x 自变量序列
     * @param y 因变量序列
     * @return 回归结果（斜率、截距、R²）
     */
    public RegressionResult linearRegression(double[] x, double[] y) {
        int n = x.length;
        if (n < 2) {
            return RegressionResult.builder()
                    .slope(0).intercept(0).rSquared(0).sampleCount(n)
                    .build();
        }

        double sumX = 0.0, sumY = 0.0, sumXY = 0.0, sumXX = 0.0, sumYY = 0.0;

        for (int i = 0; i < n; i++) {
            sumX += x[i];
            sumY += y[i];
            sumXY += x[i] * y[i];
            sumXX += x[i] * x[i];
            sumYY += y[i] * y[i];
        }

        double meanX = sumX / n;
        double meanY = sumY / n;

        double ssXX = sumXX - n * meanX * meanX;
        double ssYY = sumYY - n * meanY * meanY;
        double ssXY = sumXY - n * meanX * meanY;

        // 避免除零
        double slope = (ssXX != 0) ? ssXY / ssXX : 0.0;
        double intercept = meanY - slope * meanX;

        // R² = (ssXY)² / (ssXX * ssYY)
        double rSquared = (ssXX != 0 && ssYY != 0) ? (ssXY * ssXY) / (ssXX * ssYY) : 0.0;

        return RegressionResult.builder()
                .slope(slope)
                .intercept(intercept)
                .rSquared(rSquared)
                .sampleCount(n)
                .build();
    }

    /**
     * 长短周期共振信号
     *
     * @param shortPeriod 短周期
     * @param longPeriod  长周期
     * @return 共振信号列表
     */
    public List<TrendResonanceDTO> calculateResonance(int shortPeriod, int longPeriod) {
        LocalDate tradeDate = flowMapper.selectLatestTradeDate();
        if (tradeDate == null) {
            log.warn("无可用交易日数据");
            return List.of();
        }

        // 查询所有行业名称
        List<String> industryNames = trendMapper.selectDistinctIndustries();
        if (industryNames.isEmpty()) {
            // 从flow表获取
            List<IndustryCapitalFlowEntity> latestData = flowMapper.selectByTradeDate(tradeDate);
            industryNames = latestData.stream()
                    .map(IndustryCapitalFlowEntity::getIndustryName)
                    .distinct()
                    .collect(Collectors.toList());
        }

        List<TrendResonanceDTO> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger skipNullCount = new AtomicInteger(0);
        AtomicInteger skipR2Count = new AtomicInteger(0);
        AtomicInteger errorCount2 = new AtomicInteger(0);

        industryNames.parallelStream().forEach(industryName -> {
            try {
                IndustryTrendStatEntity shortStat = trendMapper.selectByIndustryDatePeriod(
                        industryName, tradeDate, shortPeriod);
                IndustryTrendStatEntity longStat = trendMapper.selectByIndustryDatePeriod(
                        industryName, tradeDate, longPeriod);

                if (shortStat == null || longStat == null) {
                    skipNullCount.incrementAndGet();
                    return;
                }

                // R²过滤
                double shortRSq = shortStat.getRSquared() != null ? shortStat.getRSquared().doubleValue() : 0;
                double longRSq = longStat.getRSquared() != null ? longStat.getRSquared().doubleValue() : 0;

                if (shortRSq < rSquaredThreshold || longRSq < rSquaredThreshold) {
                    skipR2Count.incrementAndGet();
                    return;
                }

                double shortSlope = shortStat.getTrendSlope() != null ? shortStat.getTrendSlope().doubleValue() : 0;
                double longSlope = longStat.getTrendSlope() != null ? longStat.getTrendSlope().doubleValue() : 0;

                // 共振信号判断
                String signalType;
                String signalDesc;

                if (longSlope > 0 && shortSlope > 0) {
                    signalType = "BULLISH_RESONANCE";
                    signalDesc = "长短共振向上（强势做多信号）";
                } else if (longSlope > 0 && shortSlope <= 0) {
                    signalType = "LONG_BULLISH_SHORT_BEARISH";
                    signalDesc = "长多短空（趋势回踩低吸）";
                } else if (longSlope <= 0 && shortSlope > 0) {
                    signalType = "LONG_BEARISH_SHORT_BULLISH";
                    signalDesc = "长空短多（下跌反弹谨慎）";
                } else {
                    signalType = "BEARISH_RESONANCE";
                    signalDesc = "长短共振向下（规避板块）";
                }

                TrendResonanceDTO dto = TrendResonanceDTO.builder()
                        .industryName(industryName)
                        .tradeDate(tradeDate)
                        .shortPeriod(shortPeriod)
                        .longPeriod(longPeriod)
                        .shortSlope(BigDecimal.valueOf(shortSlope).setScale(10, RoundingMode.HALF_UP))
                        .shortRSquared(BigDecimal.valueOf(shortRSq).setScale(6, RoundingMode.HALF_UP))
                        .longSlope(BigDecimal.valueOf(longSlope).setScale(10, RoundingMode.HALF_UP))
                        .longRSquared(BigDecimal.valueOf(longRSq).setScale(6, RoundingMode.HALF_UP))
                        .signalType(signalType)
                        .signalDesc(signalDesc)
                        .shortAvgNet(shortStat.getAvgNetAmount())
                        .longAvgNet(longStat.getAvgNetAmount())
                        .build();

                results.add(dto);

            } catch (Exception e) {
                errorCount2.incrementAndGet();
                log.error("共振信号计算异常: industry={}", industryName, e);
            }
        });

        log.info("共振信号计算详情: total={}, skipNull={}, skipR2={}, error={}, pass={}",
                industryNames.size(), skipNullCount.get(), skipR2Count.get(),
                errorCount2.get(), results.size());

        // 按信号类型排序：做多信号优先
        results.sort((a, b) -> {
            int orderA = getSignalOrder(a.getSignalType());
            int orderB = getSignalOrder(b.getSignalType());
            return Integer.compare(orderA, orderB);
        });

        log.info("共振信号计算完成: shortPeriod={}, longPeriod={}, count={}",
                shortPeriod, longPeriod, results.size());

        return results;
    }

    /**
     * 信号排序权重
     */
    private int getSignalOrder(String signalType) {
        return switch (signalType) {
            case "BULLISH_RESONANCE" -> 0;
            case "LONG_BULLISH_SHORT_BEARISH" -> 1;
            case "LONG_BEARISH_SHORT_BULLISH" -> 2;
            case "BEARISH_RESONANCE" -> 3;
            default -> 4;
        };
    }

    // ===================== 趋势计算结果 =====================

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrendCalcResult {
        private boolean success;
        private int industryCount;
        private int statCount;
        private int insertedRows;
        private String tradeDate;
        private long costMs;
        private String errorMsg;

        public static TrendCalcResult ok(int industryCount, int statCount, int insertedRows, String tradeDate, long costMs) {
            return TrendCalcResult.builder()
                    .success(true)
                    .industryCount(industryCount)
                    .statCount(statCount)
                    .insertedRows(insertedRows)
                    .tradeDate(tradeDate)
                    .costMs(costMs)
                    .build();
        }

        public static TrendCalcResult fail(String errorMsg) {
            return TrendCalcResult.builder()
                    .success(false)
                    .errorMsg(errorMsg)
                    .build();
        }
    }
}
