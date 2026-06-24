package com.ths.crawler.config;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Conditional;

import jakarta.annotation.PreDestroy;

/**
 * Playwright浏览器生命周期管理
 * <p>
 * 核心设计：
 * 1. Playwright实例全局单例（重量级资源，一个JVM只创建一次）
 * 2. Browser懒加载——首次采集时才创建（避免启动卡住）
 * 3. 每次采集创建新Page，采集完关闭Page
 * 4. 应用关闭时@PreDestroy优雅销毁
 * <p>
 * 条件注册：仅当 ths.fetcher.impl=playwright 时激活
 */
@Slf4j
@Configuration
@Conditional(PlaywrightEnabledCondition.class)
public class PlaywrightConfig {

    @Value("${ths.playwright.headless:true}")
    private boolean headless;

    @Value("${ths.playwright.timeout:30000}")
    private int timeout;

    private volatile Playwright playwright;
    private volatile Browser browser;
    private final Object lock = new Object();

    /**
     * 懒加载获取Browser实例
     * 首次调用时创建Playwright和Browser，后续复用
     */
    public Browser getBrowser() {
        if (browser == null) {
            synchronized (lock) {
                if (browser == null) {
                    log.info("[Playwright] 懒加载初始化: headless={}", headless);
                    this.playwright = Playwright.create();
                    BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                            .setHeadless(headless)
                            .setTimeout(timeout);
                    options.setArgs(java.util.List.of(
                            "--disable-gpu",
                            "--no-sandbox",
                            "--disable-dev-shm-usage",
                            "--disable-extensions",
                            "--disable-background-networking",
                            "--disable-blink-features=AutomationControlled"
                    ));
                    this.browser = playwright.chromium().launch(options);
                    log.info("[Playwright] Browser实例创建完成");
                }
            }
        }
        return browser;
    }

    @PreDestroy
    public void close() {
        log.info("[Playwright] 优雅关闭...");
        try {
            if (browser != null) {
                browser.close();
            }
        } catch (Exception e) {
            log.warn("[Playwright] Browser关闭异常: {}", e.getMessage());
        }
        try {
            if (playwright != null) {
                playwright.close();
            }
        } catch (Exception e) {
            log.warn("[Playwright] Playwright关闭异常: {}", e.getMessage());
        }
        log.info("[Playwright] 关闭完成");
    }
}
