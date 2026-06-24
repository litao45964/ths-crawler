package com.ths.crawler.unit.service;

import com.ths.crawler.mapper.IndustryCapitalFlowMapper;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import com.ths.crawler.service.IndustryFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * IndustryFlowService 单元测试
 * <p>
 * TDD红灯阶段：先定义行为契约，再写实现让测试通过。
 * 测试范围：getIndustryNames()、getIndustryHistory()
 */
@ExtendWith(MockitoExtension.class)
class IndustryFlowServiceTest {

    @Mock
    private com.ths.crawler.core.DataFetcher<List<IndustryCapitalFlowEntity>> industryFetcher;

    @Mock
    private IndustryCapitalFlowMapper flowMapper;

    private IndustryFlowService flowService;

    @BeforeEach
    void setUp() {
        flowService = new IndustryFlowService(industryFetcher, flowMapper);
    }

    // ===================== getIndustryNames =====================

    @Nested
    @DisplayName("getIndustryNames - 获取行业名称列表")
    class GetIndustryNamesTest {

        @Test
        @DisplayName("返回去重的行业名称列表，按名称排序")
        void 返回去重排序的行业名称列表() {
            List<String> names = List.of("半导体", "银行", "化学制药", "零售");
            when(flowMapper.selectDistinctIndustryNames()).thenReturn(names);

            List<String> result = flowService.getIndustryNames();

            assertThat(result).hasSize(4);
            assertThat(result).containsExactly("半导体", "银行", "化学制药", "零售");
        }

        @Test
        @DisplayName("无数据时返回空列表")
        void 无数据返回空列表() {
            when(flowMapper.selectDistinctIndustryNames()).thenReturn(List.of());

            List<String> result = flowService.getIndustryNames();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("数据库返回null时降级为空列表")
        void 数据库返回null降级为空() {
            when(flowMapper.selectDistinctIndustryNames()).thenReturn(null);

            List<String> result = flowService.getIndustryNames();

            assertThat(result).isEmpty();
        }
    }

    // ===================== getIndustryHistory =====================

    @Nested
    @DisplayName("getIndustryHistory - 获取单行业历史净额序列")
    class GetIndustryHistoryTest {

        @Test
        @DisplayName("返回指定行业最近N天的历史数据，按日期升序")
        void 返回指定行业历史数据() {
            List<IndustryCapitalFlowEntity> entities = List.of(
                    buildEntity("半导体", LocalDate.of(2026, 6, 20), new BigDecimal("50000")),
                    buildEntity("半导体", LocalDate.of(2026, 6, 23), new BigDecimal("60000")),
                    buildEntity("半导体", LocalDate.of(2026, 6, 24), new BigDecimal("70000"))
            );

            when(flowMapper.selectByIndustryAndDateRange(eq("半导体"), any(), any())).thenReturn(entities);

            var result = flowService.getIndustryHistory("半导体", 60);

            assertThat(result).hasSize(3);
            assertThat(result.get(0).getIndustryName()).isEqualTo("半导体");
            assertThat(result.get(0).getNetAmount()).isEqualByComparingTo(new BigDecimal("50000"));
        }

        @Test
        @DisplayName("行业无数据时返回空列表")
        void 行业无数据返回空() {
            when(flowMapper.selectByIndustryAndDateRange(anyString(), any(), any()))
                    .thenReturn(List.of());

            var result = flowService.getIndustryHistory("不存在的行业", 30);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("days参数=0时，startDate=endDate，最多返回1天数据")
        void days为0时最多1天数据() {
            when(flowMapper.selectByIndustryAndDateRange(eq("银行"), any(), any()))
                    .thenReturn(List.of(buildEntity("银行", LocalDate.now(), new BigDecimal("10000"))));

            var result = flowService.getIndustryHistory("银行", 0);

            assertThat(result).hasSize(1);
        }
    }

    // ===================== 辅助方法 =====================

    private IndustryCapitalFlowEntity buildEntity(String industryName, LocalDate tradeDate, BigDecimal netAmount) {
        return IndustryCapitalFlowEntity.builder()
                .tradeDate(tradeDate)
                .industryCode("881001")
                .industryName(industryName)
                .netAmount(netAmount)
                .inflowAmount(netAmount.multiply(new BigDecimal("2")))
                .outflowAmount(netAmount)
                .industryChangePct(new BigDecimal("1.5"))
                .leadingStock("测试股")
                .leadingStockPct(new BigDecimal("3.2"))
                .build();
    }
}
