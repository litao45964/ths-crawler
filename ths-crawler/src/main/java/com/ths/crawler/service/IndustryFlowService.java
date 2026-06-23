package com.ths.crawler.service;

import com.ths.crawler.model.IndustryCapitalFlow;
import com.ths.crawler.model.ResonanceResult;
import com.ths.crawler.model.TrendResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class IndustryFlowService {

    private final JdbcTemplate jdbc;

    public IndustryFlowService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<IndustryCapitalFlow> rowMapper = (rs, rowNum) -> {
        IndustryCapitalFlow f = new IndustryCapitalFlow();
        f.setId(rs.getLong("id"));
        f.setTradeDate(rs.getDate("trade_date").toLocalDate());
        f.setIndustryCode(rs.getString("industry_code"));
        f.setIndustryName(rs.getString("industry_name"));
        f.setNetAmount(rs.getBigDecimal("net_amount"));
        f.setInflowAmount(rs.getBigDecimal("inflow_amount"));
        f.setOutflowAmount(rs.getBigDecimal("outflow_amount"));
        f.setIndustryChangePct(rs.getBigDecimal("industry_change_pct"));
        f.setLeadingStock(rs.getString("leading_stock"));
        f.setLeadingStockPct(rs.getBigDecimal("leading_stock_pct"));
        return f;
    };

    // 1. 最新交易日行业排行
    public List<IndustryCapitalFlow> getLatest(int topN, String orderBy) {
        // 只允许合法的排序列
        if (!"net_amount".equals(orderBy) && !"inflow_amount".equals(orderBy)
                && !"industry_change_pct".equals(orderBy) && !"outflow_amount".equals(orderBy)) {
            orderBy = "net_amount";
        }
        String sql = "SELECT * FROM industry_capital_flow WHERE trade_date = "
                + "(SELECT MAX(trade_date) FROM industry_capital_flow) ORDER BY "
                + orderBy + " DESC LIMIT ?";
        return jdbc.query(sql, rowMapper, topN);
    }

    // 2. 所有行业名称
    public List<String> getAllIndustries() {
        String sql = "SELECT DISTINCT industry_name FROM industry_capital_flow ORDER BY industry_name";
        return jdbc.queryForList(sql, String.class);
    }

    // 3. 单行业历史净额
    public List<IndustryCapitalFlow> getHistory(String industry, int days) {
        String sql = "SELECT * FROM industry_capital_flow WHERE industry_name = ? ORDER BY trade_date DESC LIMIT ?";
        return jdbc.query(sql, rowMapper, industry, days);
    }

    // 4. 单行业趋势统计（线性回归）
    public TrendResult getTrend(String industry, int period) {
        String sql = "SELECT net_amount FROM industry_capital_flow WHERE industry_name = ? ORDER BY trade_date DESC LIMIT ?";
        List<BigDecimal> amounts = jdbc.queryForList(sql, BigDecimal.class, industry, period);
        return computeTrend(amounts);
    }

    // 5. 共振信号
    public List<ResonanceResult> getResonance(int shortPeriod, int longPeriod) {
        List<String> industries = getAllIndustries();
        List<ResonanceResult> results = new ArrayList<>();
        for (String industry : industries) {
            TrendResult shortTrend = getTrend(industry, shortPeriod);
            TrendResult longTrend = getTrend(industry, longPeriod);
            if (shortTrend.getSampleCount() < 2 || longTrend.getSampleCount() < 2) continue;

            ResonanceResult r = new ResonanceResult();
            r.setIndustryName(industry);
            r.setShortSlope(shortTrend.getTrendSlope());
            r.setLongSlope(longTrend.getTrendSlope());
            r.setShortAvgNet(shortTrend.getAvgNetAmount() != null ? shortTrend.getAvgNetAmount().doubleValue() : 0.0);
            r.setLongAvgNet(longTrend.getAvgNetAmount() != null ? longTrend.getAvgNetAmount().doubleValue() : 0.0);

            boolean shortBull = shortTrend.getTrendSlope() > 0;
            boolean longBull = longTrend.getTrendSlope() > 0;

            if (shortBull && longBull) {
                r.setSignalType("BULLISH_RESONANCE");
                r.setSignalDesc("长短周期同向上行，多头共振");
            } else if (!shortBull && longBull) {
                r.setSignalType("LONG_BULLISH_SHORT_BEARISH");
                r.setSignalDesc("长周期上行但短期回调，注意节奏");
            } else if (shortBull) {
                r.setSignalType("LONG_BEARISH_SHORT_BULLISH");
                r.setSignalDesc("长周期下行但短期反弹，观察反转");
            } else {
                r.setSignalType("BEARISH_RESONANCE");
                r.setSignalDesc("长短周期同向下行，空头共振");
            }
            results.add(r);
        }
        // 按长周期斜率降序排列
        results.sort((a, b) -> Double.compare(b.getLongSlope(), a.getLongSlope()));
        return results;
    }

    // 6. 趋势计算入库
    public int calculateAndSaveTrend() {
        List<String> industries = getAllIndustries();
        int[] periods = {5, 10, 22, 60};
        int total = 0;
        LocalDate tradeDate = getLatestTradeDate();

        for (String industry : industries) {
            for (int period : periods) {
                String sql = "SELECT net_amount FROM industry_capital_flow WHERE industry_name = ? ORDER BY trade_date DESC LIMIT ?";
                List<BigDecimal> amounts = jdbc.queryForList(sql, BigDecimal.class, industry, period);
                if (amounts.size() < 2) continue;

                TrendResult trend = computeTrend(amounts);
                String insertSql = "INSERT INTO industry_trend_stat "
                        + "(trade_date, industry_name, stat_period, sample_count, trend_slope, intercept, r_squared, "
                        + "total_net_amount, avg_net_amount, std_net_amount, min_net_amount, max_net_amount) "
                        + "VALUES (?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE sample_count=VALUES(sample_count), trend_slope=VALUES(trend_slope), "
                        + "r_squared=VALUES(r_squared), total_net_amount=VALUES(total_net_amount), "
                        + "avg_net_amount=VALUES(avg_net_amount), std_net_amount=VALUES(std_net_amount), "
                        + "min_net_amount=VALUES(min_net_amount), max_net_amount=VALUES(max_net_amount)";

                jdbc.update(insertSql,
                        tradeDate, industry, period, trend.getSampleCount(),
                        trend.getTrendSlope(), trend.getRSquared(),
                        trend.getTotalNetAmount(), trend.getAvgNetAmount(),
                        trend.getStdNetAmount(), trend.getMinNetAmount(), trend.getMaxNetAmount());
                total++;
            }
        }
        return total;
    }

    // ====== 工具方法 ======

    public LocalDate getLatestTradeDate() {
        String sql = "SELECT MAX(trade_date) FROM industry_capital_flow";
        return jdbc.queryForObject(sql, LocalDate.class);
    }

    /**
     * 最小二乘法线性回归 + 统计
     * amounts 按时间倒序，回归时按时间正序：x = 0,1,2,...,n-1
     */
    private TrendResult computeTrend(List<BigDecimal> amounts) {
        TrendResult result = new TrendResult();
        int n = amounts.size();
        result.setSampleCount(n);
        if (n == 0) return result;

        // 基本统计
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal min = amounts.get(0);
        BigDecimal max = amounts.get(0);
        for (BigDecimal v : amounts) {
            sum = sum.add(v);
            if (v.compareTo(min) < 0) min = v;
            if (v.compareTo(max) > 0) max = v;
        }
        BigDecimal avg = sum.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        result.setTotalNetAmount(sum.setScale(4, RoundingMode.HALF_UP));
        result.setAvgNetAmount(avg);
        result.setMinNetAmount(min);
        result.setMaxNetAmount(max);

        // 标准差
        BigDecimal variance = BigDecimal.ZERO;
        for (BigDecimal v : amounts) {
            BigDecimal diff = v.subtract(avg);
            variance = variance.add(diff.multiply(diff));
        }
        variance = variance.divide(BigDecimal.valueOf(n), 4, RoundingMode.HALF_UP);
        double std = Math.sqrt(variance.doubleValue());
        result.setStdNetAmount(BigDecimal.valueOf(std).setScale(4, RoundingMode.HALF_UP));

        if (n < 2) {
            result.setTrendSlope(0.0);
            result.setRSquared(0.0);
            return result;
        }

        // 线性回归：x=0..n-1（正序，即 amounts 倒序反转）
        // y[i] = amounts[n-1-i] (最早的数据对应 x=0)
        double sumX = 0, sumY = 0, sumXY = 0, sumX2 = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = amounts.get(n - 1 - i).doubleValue();
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        double xMean = sumX / n;
        double yMean = sumY / n;
        double slope = (sumXY - n * xMean * yMean) / (sumX2 - n * xMean * xMean);
        result.setTrendSlope(slope);

        // R²
        double ssTot = 0, ssRes = 0;
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = amounts.get(n - 1 - i).doubleValue();
            double yPred = yMean + slope * (x - xMean);
            ssTot += (y - yMean) * (y - yMean);
            ssRes += (y - yPred) * (y - yPred);
        }
        double rSquared = ssTot > 0 ? 1.0 - ssRes / ssTot : 0.0;
        result.setRSquared(rSquared);
        return result;

    }

    // ====== V2 定时任务接口 ======

    /**
     * 采集日度数据（V2预留，当前由scripts/fetch_sector_flow.py + API触发）
     */
    public CollectResult collectDailyData() {
        // TODO: 实现OkHttp+Jsoup直连同花顺页面采集
        return CollectResult.fail("V2采集功能待实现");
    }

    /**
     * 采集结果
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CollectResult {
        private boolean success;
        private int recordCount;
        private String tradeDate;
        private String errorMsg;

        public static CollectResult ok(int recordCount, String tradeDate) {
            return CollectResult.builder().success(true).recordCount(recordCount).tradeDate(tradeDate).build();
        }
        public static CollectResult fail(String errorMsg) {
            return CollectResult.builder().success(false).errorMsg(errorMsg).build();
        }
    }
}
