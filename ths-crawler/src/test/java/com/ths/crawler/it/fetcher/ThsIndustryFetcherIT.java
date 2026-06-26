package com.ths.crawler.it.fetcher;

import com.ths.crawler.fetcher.thsw.ThsIndustryFetcher;
import com.ths.crawler.model.entity.IndustryCapitalFlowEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

public class ThsIndustryFetcherIT {

    @Test
    public void testFetchRealData() {
        ThsIndustryFetcher fetcher = new ThsIndustryFetcher();
        List<IndustryCapitalFlowEntity> result = fetcher.doFetch();
        
        System.out.println("\n===== 采集结果 =====");
        System.out.println("总条数: " + result.size());
        
        if (!result.isEmpty()) {
            System.out.println("\n前5条:");
            for (int i = 0; i < Math.min(5, result.size()); i++) {
                var d = result.get(i);
                System.out.printf("  %d. %s (%s) 净额:%s万 领涨:%s (%s)%n",
                    i+1, d.getIndustryName(), d.getIndustryCode(),
                    d.getNetAmount(), d.getLeadingStock(), d.getLeadingStockCode());
            }
            System.out.println("\n后5条:");
            for (int i = Math.max(0, result.size()-5); i < result.size(); i++) {
                var d = result.get(i);
                System.out.printf("  %d. %s (%s) 净额:%s万 领涨:%s (%s)%n",
                    i+1, d.getIndustryName(), d.getIndustryCode(),
                    d.getNetAmount(), d.getLeadingStock(), d.getLeadingStockCode());
            }
        }
        
        assert result.size() >= 50 : "期望至少50条，实际" + result.size();
        System.out.println("\n✅ 采集验证通过！");
    }
}
