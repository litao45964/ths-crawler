package com.ths.crawler.unit.fetcher;

import com.ths.crawler.fetcher.thsw.ThsIndustryFetcher;
import com.ths.crawler.fetcher.thsw.ThsIndustryFetcher.IndustryFlowData;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ThsIndustryFetcher 单元测试 — TDD红灯
 * <p>
 * 测试策略：用真实HTML片段验证解析逻辑，不依赖外部网络
 * 集成测试在云电脑上跑（铁律⑦）
 */
@DisplayName("ThsIndustryFetcher")
class ThsIndustryFetcherTest {

    private ThsIndustryFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new ThsIndustryFetcher();
        // 注入测试配置（反射设置私有字段）
        setField(fetcher, "retryCount", 2);
        setField(fetcher, "requestDelayMin", 10);
        setField(fetcher, "requestDelayMax", 20);
        setField(fetcher, "maxPages", 2);
    }

    @Nested
    @DisplayName("getSource")
    class GetSourceTests {
        @Test
        @DisplayName("返回industry_capital_flow")
        void returnsSource() {
            assertThat(fetcher.getSource()).isEqualTo("industry_capital_flow");
        }
    }

    @Nested
    @DisplayName("HTML解析 — parseHtmlTable")
    class ParseHtmlTableTests {
        @Test
        @DisplayName("解析正常HTML表格 → 正确提取行业数据")
        void parseNormalTable() {
            String html = """
                <html><body>
                <table class="m-table J-ajax-table">
                <tbody>
                <tr><td>1</td><td><a href="http://q.10jqka.com.cn/stock/thshy/881121/">半导体</a></td>
                <td class="c-rise">0.67%</td><td>785.30亿</td><td>576.16亿</td>
                <td class="c-rise">209.14亿</td><td><a href="http://stockpage.10jqka.com.cn/688981/">中芯国际</a></td>
                <td class="c-rise">0.32%</td></tr>
                <tr><td>2</td><td><a href="http://q.10jqka.com.cn/stock/thshy/881166/">军工装备</a></td>
                <td class="c-rise">1.43%</td><td>396.25亿</td><td>257.40亿</td>
                <td class="c-rise">138.85亿</td><td><a href="http://stockpage.10jqka.com.cn/600760/">中航沈飞</a></td>
                <td class="c-rise">1.66%</td></tr>
                </tbody></table></body></html>""";

            List<IndustryFlowData> result = invokeParseHtmlTable(html);

            assertThat(result).hasSize(2);
            // 第1行：半导体
            IndustryFlowData row1 = result.get(0);
            assertThat(row1.rank).isEqualTo(1);
            assertThat(row1.industryName).isEqualTo("半导体");
            assertThat(row1.industryLink).contains("881121");
            assertThat(row1.industryChangePct).isEqualByComparingTo(new BigDecimal("0.67"));
            assertThat(row1.inflowAmount).isEqualByComparingTo(new BigDecimal("7853000.0000")); // 785.30亿 → 万
            assertThat(row1.outflowAmount).isEqualByComparingTo(new BigDecimal("5761600.0000"));
            assertThat(row1.netAmount).isEqualByComparingTo(new BigDecimal("2091400.0000"));
            assertThat(row1.leadingStock).isEqualTo("中芯国际");
            assertThat(row1.leadingStockCode).isEqualTo("688981");
            assertThat(row1.leadingStockPct).isEqualByComparingTo(new BigDecimal("0.32"));

            // 第2行：军工装备
            IndustryFlowData row2 = result.get(1);
            assertThat(row2.rank).isEqualTo(2);
            assertThat(row2.industryName).isEqualTo("军工装备");
            assertThat(row2.netAmount).isEqualByComparingTo(new BigDecimal("1388500.0000"));
        }

        @Test
        @DisplayName("解析负值净额 → 正确保留符号")
        void parseNegativeNetAmount() {
            String html = """
                <table class="m-table"><tbody>
                <tr><td>1</td><td>电力</td><td class="c-fall">-1.23%</td>
                <td>100.00亿</td><td>133.12亿</td><td class="c-fall">-33.12亿</td>
                <td><a href="001234/">长江电力</a></td><td class="c-fall">-0.56%</td></tr>
                </tbody></table>""";

            List<IndustryFlowData> result = invokeParseHtmlTable(html);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).netAmount).isEqualByComparingTo(new BigDecimal("-331200.0000"));
            assertThat(result.get(0).industryChangePct).isEqualByComparingTo(new BigDecimal("-1.23"));
        }

        @Test
        @DisplayName("解析数值为 '--' → 返回null/0")
        void parseDashValues() {
            String html = """
                <table class="m-table"><tbody>
                <tr><td>1</td><td>测试行业</td><td>--</td><td>--</td><td>--</td><td>--</td><td><a href="000001/">测试股</a></td><td>--</td></tr>
                </tbody></table>""";

            List<IndustryFlowData> result = invokeParseHtmlTable(html);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).industryChangePct).isNull();
            assertThat(result.get(0).netAmount).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.get(0).leadingStockPct).isNull();
        }

        @Test
        @DisplayName("解析空表格 → 返回空列表")
        void parseEmptyTable() {
            String html = "<html><body><table><tbody></tbody></table></body></html>";
            List<IndustryFlowData> result = invokeParseHtmlTable(html);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("解析无tbody的表格 → 降级到tr")
        void parseTableWithoutTbody() {
            String html = """
                <table>
                <tr><td>1</td><td>白酒</td><td>2.50%</td><td>50.00亿</td><td>30.00亿</td><td>20.00亿</td><td><a href="600519/">贵州茅台</a></td><td>1.50%</td></tr>
                </table>""";

            List<IndustryFlowData> result = invokeParseHtmlTable(html);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).industryName).isEqualTo("白酒");
        }
    }

    @Nested
    @DisplayName("JS_DATA解析 — parseJsData")
    class ParseJsDataTests {
        @Test
        @DisplayName("解析标准JS_DATA → 建立行业名→Code映射")
        void parseJsData() {
            String jsData = "var JS_DATA = [{\"name\":\"半导体\",\"amount\":26.54,\"addr\":\"http://q.10jqka.com.cn/thshy/detail/code/881121/\"}]";

            var codeMap = invokeParseJsData(jsData);
            assertThat(codeMap).containsEntry("半导体", "881121");
        }

        @Test
        @DisplayName("解析空JS_DATA → 返回空Map")
        void parseEmptyJsData() {
            var codeMap = invokeParseJsData("var JS_DATA = []");
            assertThat(codeMap).isEmpty();
        }

        @Test
        @DisplayName("无JS_DATA → 返回空Map")
        void parseNoJsData() {
            var codeMap = invokeParseJsData("<html>no data</html>");
            assertThat(codeMap).isEmpty();
        }
    }

    @Nested
    @DisplayName("convertToEntity")
    class ConvertToEntityTests {
        @Test
        @DisplayName("转换IndustryFlowData → IndustryCapitalFlowEntity")
        void convertToEntity() {
            IndustryFlowData data = new IndustryFlowData();
            data.tradeDate = LocalDate.of(2026, 6, 25);
            data.industryCode = "881121";
            data.industryName = "半导体";
            data.industryLink = "http://q.10jqka.com.cn/stock/thshy/881121/";
            data.netAmount = new BigDecimal("2091400.0000");
            data.inflowAmount = new BigDecimal("7853000.0000");
            data.outflowAmount = new BigDecimal("5761600.0000");
            data.industryChangePct = new BigDecimal("0.67");
            data.leadingStock = "中芯国际";
            data.leadingStockCode = "688981";
            data.leadingStockLink = "http://stockpage.10jqka.com.cn/688981/";
            data.leadingStockPct = new BigDecimal("0.32");
            data.rank = 1;

            IndustryCapitalFlowEntity entity = invokeConvertToEntity(data, data.tradeDate);

            assertThat(entity.getTradeDate()).isEqualTo(LocalDate.of(2026, 6, 25));
            assertThat(entity.getIndustryCode()).isEqualTo("881121");
            assertThat(entity.getIndustryName()).isEqualTo("半导体");
            assertThat(entity.getNetAmount()).isEqualByComparingTo(new BigDecimal("2091400.0000"));
            assertThat(entity.getLeadingStockCode()).isEqualTo("688981");
        }

        @Test
        @DisplayName("industryCode为null → 设为空字符串")
        void convertNullCode() {
            IndustryFlowData data = new IndustryFlowData();
            data.industryName = "测试";
            data.industryCode = null;
            data.netAmount = BigDecimal.ZERO;

            IndustryCapitalFlowEntity entity = invokeConvertToEntity(data, LocalDate.now());
            assertThat(entity.getIndustryCode()).isEmpty();
        }
    }

    @Nested
    @DisplayName("UserAgent轮换")
    class UserAgentRotationTests {
        @Test
        @DisplayName("连续调用返回不同UA")
        void rotatesUserAgents() throws Exception {
            String ua1 = invokeGetRandomUserAgent();
            // 连续取10次，至少有一次不同
            boolean foundDifferent = false;
            for (int i = 0; i < 10; i++) {
                String ua = invokeGetRandomUserAgent();
                if (!ua.equals(ua1)) {
                    foundDifferent = true;
                    break;
                }
            }
            assertThat(foundDifferent).isTrue();
        }

        @Test
        @DisplayName("所有UA都包含Mozilla")
        void allContainMozilla() throws Exception {
            for (int i = 0; i < 20; i++) {
                String ua = invokeGetRandomUserAgent();
                assertThat(ua).contains("Mozilla");
            }
        }
    }

    @Nested
    @DisplayName("反封禁策略验证")
    class AntiBlockTests {
        @Test
        @DisplayName("delayBetweenRequests在配置范围内")
        void delayInRange() {
            long delay = invokeCalculateDelay();
            assertThat(delay).isBetween(10L, 20L); // 测试配置范围
        }

    }

    // ===================== 反射辅助方法 =====================

    private List<IndustryFlowData> invokeParseHtmlTable(String html) {
        try {
            var method = ThsIndustryFetcher.class.getDeclaredMethod("parseHtmlTable", String.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<IndustryFlowData> result = (List<IndustryFlowData>) method.invoke(fetcher, html);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private java.util.Map<String, String> invokeParseJsData(String html) {
        try {
            var method = ThsIndustryFetcher.class.getDeclaredMethod("parseJsData", String.class);
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            var result = (java.util.Map<String, String>) method.invoke(fetcher, html);
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private IndustryCapitalFlowEntity invokeConvertToEntity(IndustryFlowData data, LocalDate tradeDate) {
        try {
            var method = ThsIndustryFetcher.class.getDeclaredMethod("convertToEntity", IndustryFlowData.class, LocalDate.class);
            method.setAccessible(true);
            return (IndustryCapitalFlowEntity) method.invoke(fetcher, data, tradeDate);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String invokeGetRandomUserAgent() {
        try {
            var method = ThsIndustryFetcher.class.getDeclaredMethod("getRandomUserAgent");
            method.setAccessible(true);
            return (String) method.invoke(fetcher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private long invokeCalculateDelay() {
        try {
            var method = ThsIndustryFetcher.class.getDeclaredMethod("calculateDelay");
            method.setAccessible(true);
            return (long) method.invoke(fetcher);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}