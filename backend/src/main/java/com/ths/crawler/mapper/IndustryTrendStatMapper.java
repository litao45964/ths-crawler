package com.ths.crawler.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ths.crawler.model.entity.IndustryTrendStatEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * 行业资金趋势统计 Mapper
 */
@Mapper
public interface IndustryTrendStatMapper extends BaseMapper<IndustryTrendStatEntity> {

    /**
     * 批量插入或更新（ON DUPLICATE KEY UPDATE）
     *
     * @param list 趋势统计列表
     * @return 受影响行数
     */
    int batchInsertOrUpdate(@Param("list") List<IndustryTrendStatEntity> list);

    /**
     * 查询指定行业在日期范围内的趋势数据
     *
     * @param industryName 行业名称
     * @param startDate    开始日期
     * @param endDate      结束日期
     * @return 趋势统计列表
     */
    List<IndustryTrendStatEntity> selectByIndustryAndDateRange(
            @Param("industryName") String industryName,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 查询指定行业、日期、周期的趋势数据
     *
     * @param industryName 行业名称
     * @param tradeDate    交易日期
     * @param statPeriod   统计周期
     * @return 趋势统计实体
     */
    IndustryTrendStatEntity selectByIndustryDatePeriod(
            @Param("industryName") String industryName,
            @Param("tradeDate") LocalDate tradeDate,
            @Param("statPeriod") Integer statPeriod
    );

    /**
     * 查询指定日期和周期的所有行业趋势
     *
     * @param tradeDate  交易日期
     * @param statPeriod 统计周期
     * @return 趋势统计列表
     */
    List<IndustryTrendStatEntity> selectByDateAndPeriod(
            @Param("tradeDate") LocalDate tradeDate,
            @Param("statPeriod") Integer statPeriod
    );

    /**
     * 查询所有不重复的行业名称
     *
     * @return 行业名称列表
     */
    List<String> selectDistinctIndustries();

    /**
     * 查询指定日期所有行业的趋势数据
     *
     * @param tradeDate 交易日期
     * @return 趋势统计列表
     */
    List<IndustryTrendStatEntity> selectByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
