package com.ths.crawler.storage;

import com.alibaba.fastjson2.JSON;
import com.ths.crawler.mapper.CrawlLogMapper;
import com.ths.crawler.mapper.SectorCapitalFlowMapper;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO.SectorFlowItem;
import com.ths.crawler.model.entity.CrawlLogEntity;
import com.ths.crawler.model.entity.SectorCapitalFlowEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * 双写存储服务：Redis + MySQL
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DualWriteService {

    private final StringRedisTemplate redisTemplate;
    private final SectorCapitalFlowMapper sectorFlowMapper;
    private final CrawlLogMapper crawlLogMapper;

    @Value("${ths.redis.raw-ttl:7}")
    private int rawTtlDays;

    @Value("${ths.redis.clean-ttl:30}")
    private int cleanTtlDays;

    @Value("${ths.storage.save-raw-json:true}")
    private boolean saveRawJson;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 写入板块资金流向数据（Redis + MySQL双写）
     */
    @Async("storageExecutor")
    public void writeSectorCapitalFlow(SectorCapitalFlowDTO dto,
                                       String industryRawJson,
                                       String conceptRawJson) {
        String today = LocalDate.now().format(DATE_FMT);
        String cleanJson = JSON.toJSONString(dto);

        // ========== 1. 写Redis ==========

        // 最新数据（常驻缓存，主动更新）
        if (dto.getIndustryTop3() != null && !dto.getIndustryTop3().isEmpty()) {
            redisTemplate.opsForValue().set(
                    "ths:latest:sector_capital_flow:industry_top3",
                    JSON.toJSONString(dto.getIndustryTop3()));
        }
        if (dto.getConceptTop3() != null && !dto.getConceptTop3().isEmpty()) {
            redisTemplate.opsForValue().set(
                    "ths:latest:sector_capital_flow:concept_top3",
                    JSON.toJSONString(dto.getConceptTop3()));
        }

        // 每日清洗后数据（30天过期）
        redisTemplate.opsForValue().set(
                "ths:clean:sector_capital_flow:" + today,
                cleanJson, cleanTtlDays, TimeUnit.DAYS);

        // 每日原始数据（7天过期）
        if (industryRawJson != null) {
            redisTemplate.opsForValue().set(
                    "ths:raw:sector_capital_flow:industry:" + today,
                    industryRawJson, rawTtlDays, TimeUnit.DAYS);
        }
        if (conceptRawJson != null) {
            redisTemplate.opsForValue().set(
                    "ths:raw:sector_capital_flow:concept:" + today,
                    conceptRawJson, rawTtlDays, TimeUnit.DAYS);
        }

        log.info("Redis写入完成: today={}", today);

        // ========== 2. 写MySQL ==========

        // 行业Top3
        if (dto.getIndustryTop3() != null) {
            for (SectorFlowItem item : dto.getIndustryTop3()) {
                sectorFlowMapper.insert(toEntity(item, industryRawJson));
            }
        }

        // 概念Top3
        if (dto.getConceptTop3() != null) {
            for (SectorFlowItem item : dto.getConceptTop3()) {
                sectorFlowMapper.insert(toEntity(item, conceptRawJson));
            }
        }

        log.info("MySQL写入完成: industry={}, concept={}",
                dto.getIndustryTop3() != null ? dto.getIndustryTop3().size() : 0,
                dto.getConceptTop3() != null ? dto.getConceptTop3().size() : 0);
    }

    /**
     * 记录抓取日志
     */
    public void logCrawl(String source, String status, int recordCount, long costMs, String errorMsg) {
        CrawlLogEntity entity = CrawlLogEntity.builder()
                .source(source)
                .status(status)
                .recordCount(recordCount)
                .costMs(costMs)
                .errorMsg(errorMsg)
                .phase("total")
                .retryCount(0)
                .tradeDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();
        crawlLogMapper.insert(entity);
    }

    /**
     * 记录抓取日志（增强版，支持 traceId/phase/retryCount）
     */
    public void logCrawlWithTrace(String traceId, String source, String status, String phase,
                                   int recordCount, int rowsSaved, long costMs,
                                   int retryCount, String detail, String errorMsg) {
        CrawlLogEntity entity = CrawlLogEntity.builder()
                .traceId(traceId)
                .source(source)
                .status(status)
                .phase(phase)
                .recordCount(recordCount)
                .costMs(costMs)
                .detail(detail)
                .retryCount(retryCount)
                .errorMsg(errorMsg)
                .tradeDate(LocalDate.now())
                .createdAt(LocalDateTime.now())
                .build();
        crawlLogMapper.insert(entity);
    }

    private SectorCapitalFlowEntity toEntity(SectorFlowItem item, String rawJson) {
        return SectorCapitalFlowEntity.builder()
                .boardName(item.getBoardName())
                .boardType(item.getBoardType())
                .changePercent(item.getChangePercent())
                .mainNetInflow(item.getMainNetInflow())
                .mainInflow(item.getMainInflow())
                .mainOutflow(item.getMainOutflow())
                .leadStock(item.getLeadStock())
                .leadChangePercent(item.getLeadChangePercent())
                .flowRank(item.getRank())
                .tradeDate(LocalDate.now())
                .rawJson(saveRawJson ? rawJson : null)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
