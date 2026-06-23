package com.ths.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 行业资金日度明细 Mapper
 */
@Mapper
public interface IndustryCapitalFlowMapper extends BaseMapper<IndustryCapitalFlowEntity> {

    /**
     * 批量插入或更新（ON DUPLICATE KEY UPDATE）
     *
     * @param list 行业资金流向列表
     * @return 受影响行数
     */
    int batchInsertOrUpdate(@Param("list") List<IndustryCapitalFlowEntity> list);

    /**
     * 查询指定日期的所有行业数据
     *
     * @param tradeDate 交易日期
     * @return 行业资金流向列表
     */
    List<IndustryCapitalFlowEntity> selectByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    /**
     * 查询指定行业在日期范围内的数据
     *
     * @param industryName 行业名称
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @return 行业资金流向列表
     */
    List<IndustryCapitalFlowEntity> selectByIndustryAndDateRange(
            @Param("industryName") String industryName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 查询最新交易日期
     *
     * @return 最新交易日期
     */
    LocalDate selectLatestTradeDate();
}
