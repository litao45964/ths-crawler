package com.ths.crawler.model;

import lombok.Data;

/**
 * 长短周期共振信号结果
 */
@Data
public class ResonanceResult {
    private String industryName;
    private double shortSlope;
    private double longSlope;
    private double shortAvgNet;
    private double longAvgNet;
    private String signalType;
    private String signalDesc;
}
