package com.ths.crawler.it.fetcher;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;
import java.util.List;

public class DebugPageIT {
    public static void main(String[] args) {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                    "--no-sandbox", "--disable-dev-shm-usage",
                    "--disable-gpu", "--window-size=1920,1080",
                    "--disable-blink-features=AutomationControlled",
                    "--disable-features=IsolateOrigins,site-per-process",
                    "--disable-setuid-sandbox"
                )));
            Page page = browser.newPage(new Browser.NewPageOptions()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"));
            page.setDefaultTimeout(30000);
            
            // Same initScript as ThsIndustryFetcher
            page.addInitScript("""
                Object.defineProperty(navigator, 'webdriver', {get: () => undefined});
                Object.defineProperty(navigator, 'plugins', {get: () => [1, 2, 3, 4, 5]});
                Object.defineProperty(navigator, 'languages', {get: () => ['zh-CN', 'zh', 'en']});
                """);
            
            page.navigate("http://data.10jqka.com.cn/funds/hyzjl/",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForTimeout(3000);
            
            String content = page.content();
            String lower = content.toLowerCase();
            
            System.out.println("=== DEBUG ===");
            System.out.println("Title: " + page.title());
            System.out.println("Contains 'forbidden': " + lower.contains("forbidden"));
            System.out.println("Contains 'nginx': " + lower.contains("nginx"));
            System.out.println("Contains '403 forbidden': " + lower.contains("403 forbidden"));
            System.out.println("Contains 'nginx forbidden': " + lower.contains("nginx forbidden"));
            
            // Check isBlocked result
            boolean blocked = lower.contains("403 forbidden")
                    || lower.contains("nginx forbidden")
                    || lower.contains("forbidden");
            System.out.println("isBlocked result: " + blocked);
            
            // Find "forbidden" context
            if (lower.contains("forbidden")) {
                int idx = lower.indexOf("forbidden");
                System.out.println("Context: " + lower.substring(Math.max(0, idx-80), Math.min(lower.length(), idx+80)));
            }
            
            var rows = page.querySelectorAll("table tbody tr");
            System.out.println("Rows found: " + rows.size());
            
            browser.close();
        }
    }
}
