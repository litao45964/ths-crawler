package com.ths.crawler.unit.model;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ths.crawler.model.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ApiResponse 单元测试
 * <p>
 * TDD红灯阶段：验证统一响应格式的序列化行为。
 */
@DisplayName("ApiResponse 统一响应格式")
class ApiResponseTest {

    @Nested
    @DisplayName("ok() 方法")
    class OkTest {

        @Test
        @DisplayName("ok() 无参 → success=true, data=null, count=null")
        void 无参ok() {
            ApiResponse<String> r = ApiResponse.ok();
            String json = JSON.toJSONString(r);
            JSONObject obj = JSON.parseObject(json);

            assertThat(obj.getBoolean("success")).isTrue();
            assertThat(obj.containsKey("data")).isFalse(); // @JsonInclude NON_NULL
            assertThat(obj.containsKey("count")).isFalse();
            assertThat(obj.containsKey("timestamp")).isTrue();
        }

        @Test
        @DisplayName("ok(data) 带数据 → success=true, data存在")
        void 带数据ok() {
            ApiResponse<String> r = ApiResponse.ok("hello");
            String json = JSON.toJSONString(r);
            JSONObject obj = JSON.parseObject(json);

            assertThat(obj.getBoolean("success")).isTrue();
            assertThat(obj.getString("data")).isEqualTo("hello");
            assertThat(obj.containsKey("count")).isFalse();
        }

        @Test
        @DisplayName("ok(data, count) 带计数 → count正确")
        void 带计数ok() {
            List<String> list = List.of("a", "b", "c");
            ApiResponse<List<String>> r = ApiResponse.ok(list, 3);
            String json = JSON.toJSONString(r);
            JSONObject obj = JSON.parseObject(json);

            assertThat(obj.getBoolean("success")).isTrue();
            assertThat(obj.getIntValue("count")).isEqualTo(3);
            assertThat(obj.getJSONArray("data")).hasSize(3);
        }
    }

    @Nested
    @DisplayName("fail() 方法")
    class FailTest {

        @Test
        @DisplayName("fail(message) → success=false, message存在, data=null")
        void fail() {
            ApiResponse<String> r = ApiResponse.fail("行业不存在");
            String json = JSON.toJSONString(r);
            JSONObject obj = JSON.parseObject(json);

            assertThat(obj.getBoolean("success")).isFalse();
            assertThat(obj.getString("message")).isEqualTo("行业不存在");
            assertThat(obj.containsKey("data")).isFalse();
        }
    }

    @Nested
    @DisplayName("边界场景")
    class EdgeCaseTest {

        @Test
        @DisplayName("ok(null) → success=true, data不序列化")
        void okNull() {
            ApiResponse<String> r = ApiResponse.ok(null);
            String json = JSON.toJSONString(r);
            JSONObject obj = JSON.parseObject(json);

            assertThat(obj.getBoolean("success")).isTrue();
            assertThat(obj.containsKey("data")).isFalse();
        }

        @Test
        @DisplayName("timestamp为毫秒级时间戳")
        void timestamp格式() {
            ApiResponse<String> r = ApiResponse.ok();
            long ts = r.getTimestamp();
            // 2026年毫秒时间戳范围
            assertThat(ts).isGreaterThan(1_700_000_000_000L);
            assertThat(ts).isLessThan(2_000_000_000_000L);
        }
    }
}