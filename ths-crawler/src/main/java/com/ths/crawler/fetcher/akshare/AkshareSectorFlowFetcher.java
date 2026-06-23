package com.ths.crawler.fetcher.akshare;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.model.dto.AkshareSectorFlowRawDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * AKShare板块资金流向抓取器
 * <p>
 * 实现方式：Java调用Python脚本，Python通过AKShare库获取数据
 * <p>
 * 配置切换：ths.fetcher.sector-flow=akshare 时激活此实现
 * 后续切换OkHttp实现时，改为 ths.fetcher.sector-flow=okhttp
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ths.fetcher.sector-flow", havingValue = "akshare", matchIfMissing = true)
public class AkshareSectorFlowFetcher implements DataFetcher<List<AkshareSectorFlowRawDTO>> {

    @Value("${ths.akshare.python-path:python3}")
    private String pythonPath;

    @Value("${ths.akshare.script-path:scripts/fetch_sector_flow.py}")
    private String scriptPath;

    @Value("${ths.akshare.timeout:60}")
    private int timeoutSeconds;

    @Override
    public String getSource() {
        return "sector_capital_flow";
    }

    @Override
    public FetchResult<List<AkshareSectorFlowRawDTO>> fetch(FetchContext context) {
        long start = System.currentTimeMillis();
        String boardType = context.getExtraParams().getOrDefault("type", "industry");

        try {
            log.info("开始抓取板块资金流向: type={}", boardType);

            // 调用Python脚本
            String jsonOutput = executePythonScript(boardType);

            // 解析返回的JSON
            List<AkshareSectorFlowRawDTO> dataList = parseResult(jsonOutput, boardType);

            long costMs = System.currentTimeMillis() - start;
            log.info("板块资金流向抓取完成: type={}, count={}, cost={}ms", boardType, dataList.size(), costMs);

            return FetchResult.ok(dataList, jsonOutput, costMs);

        } catch (Exception e) {
            long costMs = System.currentTimeMillis() - start;
            log.error("板块资金流向抓取失败: type={}", boardType, e);
            return FetchResult.<List<AkshareSectorFlowRawDTO>>builder()
                    .success(false)
                    .costMs(costMs)
                    .errorMsg(e.getMessage())
                    .build();
        }
    }

    /**
     * 执行Python脚本，返回stdout的JSON字符串
     */
    private String executePythonScript(String boardType) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                pythonPath, scriptPath, "--type", boardType
        );
        // 不合并stderr到stdout，避免tqdm等进度条输出污染JSON
        pb.redirectErrorStream(false);
        // 抑制tqdm进度条
        pb.environment().put("TQDM_DISABLE", "1");

        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
        }

        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Python脚本执行超时: " + timeoutSeconds + "s");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new RuntimeException("Python脚本执行失败(exitCode=" + exitCode + "): " + output);
        }

        return output.toString();
    }

    /**
     * 解析Python脚本返回的JSON
     * 期望格式：
     * {
     *   "board_type": "industry",
     *   "trade_date": "2026-06-17",
     *   "data": [
     *     {
     *       "rank": 1,
     *       "board_name": "半导体",
     *       "change_percent": 2.35,
     *       "main_net_inflow": 1580000000.0,
     *       ...
     *     }
     *   ]
     * }
     */
    private List<AkshareSectorFlowRawDTO> parseResult(String jsonOutput, String boardType) {
        JSONObject root = JSON.parseObject(jsonOutput);
        JSONArray dataArray = root.getJSONArray("data");

        if (dataArray == null || dataArray.isEmpty()) {
            log.warn("板块资金流向数据为空: type={}", boardType);
            return List.of();
        }

        List<AkshareSectorFlowRawDTO> result = new ArrayList<>();
        for (int i = 0; i < dataArray.size(); i++) {
            JSONObject item = dataArray.getJSONObject(i);
            AkshareSectorFlowRawDTO dto = AkshareSectorFlowRawDTO.builder()
                    .rank(item.getInteger("rank"))
                    .boardName(item.getString("board_name"))
                    .changePercent(toBigDecimal(item.get("change_percent")))
                    .mainNetInflow(toBigDecimal(item.get("main_net_inflow")))
                    .mainNetInflowRatio(toBigDecimal(item.get("main_net_inflow_ratio")))
                    .superLargeNetInflow(toBigDecimal(item.get("super_large_net_inflow")))
                    .superLargeNetInflowRatio(toBigDecimal(item.get("super_large_net_inflow_ratio")))
                    .largeNetInflow(toBigDecimal(item.get("large_net_inflow")))
                    .largeNetInflowRatio(toBigDecimal(item.get("large_net_inflow_ratio")))
                    .mediumNetInflow(toBigDecimal(item.get("medium_net_inflow")))
                    .mediumNetInflowRatio(toBigDecimal(item.get("medium_net_inflow_ratio")))
                    .smallNetInflow(toBigDecimal(item.get("small_net_inflow")))
                    .smallNetInflowRatio(toBigDecimal(item.get("small_net_inflow_ratio")))
                    .leadStock(item.getString("lead_stock"))
                    .leadChangePercent(toBigDecimal(item.get("lead_change_percent")))
                    .upCount(item.getInteger("up_count"))
                    .downCount(item.getInteger("down_count"))
                    .build();
            result.add(dto);
        }

        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof Number) return BigDecimal.valueOf(((Number) value).doubleValue());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}
