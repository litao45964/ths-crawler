package com.ths.crawler.scheduler;

import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.model.dto.AkshareSectorFlowRawDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO.SectorFlowItem;
import com.ths.crawler.processor.SectorCapitalFlowProcessor;
import com.ths.crawler.storage.DualWriteService;
import com.ths.crawler.service.TradeCalendarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 板块资金流向定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SectorCapitalFlowJob {

    private final DataFetcher<List<AkshareSectorFlowRawDTO>> sectorFlowFetcher;
    private final SectorCapitalFlowProcessor processor;
    private final DualWriteService dualWriteService;
    private final TradeCalendarService tradeCalendarService;

    /**
     * 每个交易日 15:30 执行
     * 收盘后资金数据已稳定
     */
    @Scheduled(cron = "${ths.cron.sector-capital-flow:0 30 15 ? * MON-FRI}")
    public void fetchSectorCapitalFlow() {
        if (!isTradeDay()) {
            log.info("非交易日，跳过板块资金流向抓取");
            return;
        }

        log.info("=== 开始抓取板块资金流向 ===");

        // 1. 抓取行业板块
        FetchResult<List<AkshareSectorFlowRawDTO>> industryResult = sectorFlowFetcher.fetch(
                FetchContext.builder()
                        .extraParams(Map.of("type", "industry"))
                        .build()
        );

        // 2. 抓取概念板块
        FetchResult<List<AkshareSectorFlowRawDTO>> conceptResult = sectorFlowFetcher.fetch(
                FetchContext.builder()
                        .extraParams(Map.of("type", "concept"))
                        .build()
        );

        // 3. 处理 + 存储
        if (industryResult.isSuccess() || conceptResult.isSuccess()) {
            // 提取前三
            List<SectorFlowItem> industryTop3 = industryResult.isSuccess()
                    ? processor.processTopN(industryResult.getData(), "industry", 3)
                    : List.of();
            List<SectorFlowItem> conceptTop3 = conceptResult.isSuccess()
                    ? processor.processTopN(conceptResult.getData(), "concept", 3)
                    : List.of();

            SectorCapitalFlowDTO dto = SectorCapitalFlowDTO.builder()
                    .tradeDate(LocalDate.now().toString())
                    .fetchTime(java.time.LocalDateTime.now().toString())
                    .industryTop3(industryTop3)
                    .conceptTop3(conceptTop3)
                    .build();

            // 双写
            dualWriteService.writeSectorCapitalFlow(
                    dto,
                    industryResult.getRawJson(),
                    conceptResult.getRawJson()
            );

            log.info("板块资金流向抓取完成: 行业前3={}, 概念前3={}",
                    industryTop3.stream().map(SectorFlowItem::getBoardName).toList(),
                    conceptTop3.stream().map(SectorFlowItem::getBoardName).toList());

            // 记录日志
            dualWriteService.logCrawl(
                    "sector_capital_flow",
                    "SUCCESS",
                    industryTop3.size() + conceptTop3.size(),
                    industryResult.getCostMs() + conceptResult.getCostMs(),
                    null
            );
        } else {
            log.error("板块资金流向抓取失败: industry={}, concept={}",
                    industryResult.getErrorMsg(), conceptResult.getErrorMsg());
            dualWriteService.logCrawl(
                    "sector_capital_flow",
                    "FAIL",
                    0,
                    industryResult.getCostMs() + conceptResult.getCostMs(),
                    "industry: " + industryResult.getErrorMsg() + "; concept: " + conceptResult.getErrorMsg()
            );
        }

        log.info("=== 板块资金流向抓取结束 ===");
    }

    /**
     * 交易日判断（已接入 TradeCalendarService，支持节假日/调休）
     */
    private boolean isTradeDay() {
        return tradeCalendarService.isTradeDay(LocalDate.now());
    }
}
