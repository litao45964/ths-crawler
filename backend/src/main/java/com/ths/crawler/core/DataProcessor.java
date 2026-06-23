package com.ths.crawler.core;

/**
 * 数据处理器接口 - 清洗/转换/校验
 *
 * @param <IN>  输入类型（Fetcher的输出）
 * @param <OUT> 输出类型（存储的输入）
 */
public interface DataProcessor<IN, OUT> {

    /**
     * 处理器标识（通常与对应Fetcher的source一致）
     */
    String getSource();

    /**
     * 核心清洗转换逻辑
     */
    OUT process(IN rawData);

    /**
     * 数据校验（在process之前调用）
     *
     * @return 校验结果，null表示通过
     */
    default String validate(IN rawData) {
        if (rawData == null) {
            return "raw data is null";
        }
        return null; // 通过
    }
}
