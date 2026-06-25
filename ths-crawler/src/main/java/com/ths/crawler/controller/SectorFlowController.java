package com.ths.crawler.controller;

import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.model.dto.ApiResponse;
import com.ths.crawler.model.dto.AkshareSectorFlowRawDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO.SectorFlowItem;
import com.ths.crawler.processor.SectorCapitalFlowProcessor;
import com.ths.crawler.storage.DualWriteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 板块资金流向 API
 * 提供手动触发抓取 + 查询最新数据的接口
 */
@Slf4j
@RestController
@RequestMapping("/api/sector-flow")
@RequiredArgsConstructor
public class SectorFlowController {

    private final DataFetcher<List<AkshareSectorFlowRawDTO>> sectorFlowFetcher;
    private final SectorCapitalFlowProcessor processor;
    private final DualWriteService dualWriteService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 手动触发一次抓取
     * GET /api/sector-flow/fetch?type=industry&topN=3
     */
    @GetMapping("/fetch")
    public ApiResponse<Map<String, Object>> fetch(@RequestParam(defaultValue = "industry") String type,
                                                  @RequestParam(defaultValue = "3") int topN) {
        log.info("手动触发抓取: type={}, topN={}", type, topN);

        FetchResult<List<AkshareSectorFlowRawDTO>> result = sectorFlowFetcher.fetch(
                FetchContext.builder()
                        .extraParams(Map.of("type", type))
                        .build()
        );

        if (result.isSuccess()) {
            List<SectorFlowItem> topNList = processor.processTopN(result.getData(), type, topN);
            return ApiResponse.ok(Map.of(
                    "type", type,
                    "topN", topNList,
                    "totalCount", result.getData().size(),
                    "costMs", result.getCostMs()
            ));
        } else {
            return ApiResponse.fail(result.getErrorMsg());
        }
    }

    /**
     * 查询最新缓存的行业前三
     * GET /api/sector-flow/industry-top3
     */
    @GetMapping("/industry-top3")
    public ApiResponse<String> getIndustryTop3() {
        String cached = redisTemplate.opsForValue().get("ths:latest:sector_capital_flow:industry_top3");
        if (cached != null) {
            return ApiResponse.ok(cached);
        }
        return ApiResponse.fail("暂无数据，请先执行抓取");
    }

    /**
     * 查询最新缓存的概念前三
     * GET /api/sector-flow/concept-top3
     */
    @GetMapping("/concept-top3")
    public ApiResponse<String> getConceptTop3() {
        String cached = redisTemplate.opsForValue().get("ths:latest:sector_capital_flow:concept_top3");
        if (cached != null) {
            return ApiResponse.ok(cached);
        }
        return ApiResponse.fail("暂无数据，请先执行抓取");
    }

    /**
     * 手动触发完整的日度抓取（行业+概念）并存储
     * POST /api/sector-flow/daily
     */
    @PostMapping("/daily")
    public ApiResponse<Map<String, Object>> dailyFetch() {
        log.info("手动触发日度抓取");

        FetchResult<List<AkshareSectorFlowRawDTO>> industryResult = sectorFlowFetcher.fetch(
                FetchContext.builder().extraParams(Map.of("type", "industry")).build()
        );
        FetchResult<List<AkshareSectorFlowRawDTO>> conceptResult = sectorFlowFetcher.fetch(
                FetchContext.builder().extraParams(Map.of("type", "concept")).build()
        );

        List<SectorFlowItem> industryTop3 = industryResult.isSuccess()
                ? processor.processTopN(industryResult.getData(), "industry", 3) : List.of();
        List<SectorFlowItem> conceptTop3 = conceptResult.isSuccess()
                ? processor.processTopN(conceptResult.getData(), "concept", 3) : List.of();

        SectorCapitalFlowDTO dto = SectorCapitalFlowDTO.builder()
                .tradeDate(java.time.LocalDate.now().toString())
                .fetchTime(java.time.LocalDateTime.now().toString())
                .industryTop3(industryTop3)
                .conceptTop3(conceptTop3)
                .build();

        dualWriteService.writeSectorCapitalFlow(dto, industryResult.getRawJson(), conceptResult.getRawJson());

        return ApiResponse.ok(Map.of(
                "industryTop3", industryTop3,
                "conceptTop3", conceptTop3
        ));
    }
}