package com.ths.crawler.core;

/**
 * 数据抓取器接口 - 所有数据源的统一抽象
 * <p>
 * 实现类：
 * - AkshareSectorFlowFetcher (当前：通过Python AKShare获取)
 * - OkHttpSectorFlowFetcher   (后续：原生HTTP调用同花顺API)
 * - 更多数据源只需新增实现类，通过 @ConditionalOnProperty 切换
 * <p>
 * 设计原则：
 * 1. 接口只定义"抓什么"，不管"怎么抓"
 * 2. 不同实现可以并存，通过配置切换
 * 3. 每个实现独立维护自己的依赖和反爬策略
 *
 * @param <T> 返回的数据类型
 */
public interface DataFetcher<T> {

    /**
     * 数据源标识（如 sector_capital_flow, stock_quote）
     * 用于日志、Redis Key前缀、路由
     */
    String getSource();

    /**
     * 执行抓取
     *
     * @param context 抓取上下文，包含请求参数
     * @return 抓取结果
     */
    FetchResult<T> fetch(FetchContext context);

    /**
     * 是否支持降级（如主数据源失败时走备用源）
     * 默认不支持
     */
    default boolean supportFallback() {
        return false;
    }

    /**
     * 降级抓取 - 仅当 supportFallback() 返回 true 时调用
     */
    default FetchResult<T> fallback(FetchContext context) {
        return FetchResult.empty();
    }

    /**
     * 健康检查 - 验证数据源是否可用
     * 可用于启动时检查、定时探测
     */
    default boolean healthCheck() {
        try {
            FetchResult<?> result = fetch(FetchContext.builder().build());
            return result.isSuccess();
        } catch (Exception e) {
            return false;
        }
    }
}
