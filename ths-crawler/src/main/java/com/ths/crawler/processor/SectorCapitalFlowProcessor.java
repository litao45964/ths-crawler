package com.ths.crawler.processor;

import com.ths.crawler.core.DataProcessor;
import com.ths.crawler.model.dto.AkshareSectorFlowRawDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO;
import com.ths.crawler.model.dto.SectorCapitalFlowDTO.SectorFlowItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 板块资金流向处理器
 * 将原始数据清洗为标准DTO，提取Top N
 */
@Slf4j
@Component
public class SectorCapitalFlowProcessor implements DataProcessor<List<AkshareSectorFlowRawDTO>, SectorCapitalFlowDTO> {

    @Override
    public String getSource() {
        return "sector_capital_flow";
    }

    /**
     * 从原始列表中提取净流入Top N
     * 原始数据已按净流入降序排列，直接截取
     */
    @Override
    public SectorCapitalFlowDTO process(List<AkshareSectorFlowRawDTO> rawData) {
        // 此方法由Job层分开调用，每次处理一种板块类型
        // 使用 industryTop3 / conceptTop3 的重载方法更合适
        throw new UnsupportedOperationException("请使用带参数的process方法");
    }

    /**
     * 提取净流入Top N
     *
     * @param rawData   原始数据（已排序）
     * @param boardType 板块类型 industry / concept
     * @param topN      取前N名
     */
    public List<SectorFlowItem> processTopN(List<AkshareSectorFlowRawDTO> rawData, String boardType, int topN) {
        // 二次排序确保正确（以防AKShare返回顺序变化）
        List<AkshareSectorFlowRawDTO> sorted = rawData.stream()
                .sorted((a, b) -> b.getMainNetInflow().compareTo(a.getMainNetInflow()))
                .limit(topN)
                .toList();

        return sorted.stream()
                .map(raw -> convert(raw, boardType))
                .collect(Collectors.toList());
    }

    private SectorFlowItem convert(AkshareSectorFlowRawDTO raw, String boardType) {
        return SectorFlowItem.builder()
                .rank(raw.getRank())
                .boardName(raw.getBoardName())
                .boardType(boardType)
                .changePercent(raw.getChangePercent())
                .mainNetInflow(raw.getMainNetInflow())
                // AKShare仅返回净流入，无法拆分总流入/流出；后续OkHttp方案可补充
                .mainInflow(null)
                .mainOutflow(null)
                .leadStock(raw.getLeadStock())
                .leadChangePercent(raw.getLeadChangePercent())
                .upCount(raw.getUpCount())
                .downCount(raw.getDownCount())
                .build();
    }
}
