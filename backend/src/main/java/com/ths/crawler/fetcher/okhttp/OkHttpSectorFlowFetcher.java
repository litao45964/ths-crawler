package com.ths.crawler.fetcher.okhttp;

import com.ths.crawler.core.DataFetcher;
import com.ths.crawler.core.FetchContext;
import com.ths.crawler.core.FetchResult;
import com.ths.crawler.model.dto.AkshareSectorFlowRawDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OkHttp板块资金流向抓取器（占位实现）
 * <p>
 * 后续实现步骤：
 * 1. 真机抓包确认手机端API的URL、Header、参数
 * 2. 解决hexin-v生成（GraalJS执行ths.js 或 Java重写算法）
 * 3. 实现fetch方法，用OkHttp直接调用同花顺API
 * 4. 配置切换：ths.fetcher.sector-flow=okhttp
 * <p>
 * 预期优势：
 * - 无Python依赖，纯Java
 * - 手机端API可能直接返回JSON，不需要解析HTML
 * - 可能不需要hexin-v，只需APP UA + Cookie
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ths.fetcher.sector-flow", havingValue = "okhttp")
public class OkHttpSectorFlowFetcher implements DataFetcher<List<AkshareSectorFlowRawDTO>> {

    // TODO: 注入 OkHttp客户端、hexin-v生成器等

    @Override
    public String getSource() {
        return "sector_capital_flow";
    }

    @Override
    public FetchResult<List<AkshareSectorFlowRawDTO>> fetch(FetchContext context) {
        // TODO: 实现原生HTTP抓取
        // 1. 构建请求URL（从抓包获取）
        // 2. 构建请求头（UA / hexin-v / Cookie）
        // 3. OkHttp发起请求
        // 4. 解析JSON响应
        log.warn("OkHttp实现尚未完成，请使用akshare方案：ths.fetcher.sector-flow=akshare");
        return FetchResult.fail("OkHttp实现尚未完成，请先使用akshare方案");
    }
}
