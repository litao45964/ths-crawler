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
 * 2. Browser全局单例（Chromium进程，常驻复用）
 * 3. 每次采集创建新Page，采集完关闭Page（轻量级，隔离cookie/缓存）
 * 4. 应用关闭时@PreDestroy优雅销毁Browser和Playwright
 * <p>
 * 条件注册：仅当 ths.fetcher.impl=playwright 时激活
 */
@Slf4j
@Configuration
@Conditional(PlaywrightEnabledCondition.class)
public class PlaywrightConfig {

    @Value("${ths.playwright.headless:false}")
    private boolean headless;

    @Value("${ths.playwright.timeout:30000}")
    private int timeout;

    private Playwright playwright;

    @Bean
    public Playwright playwrightInstance() {
        log.info("[Playwright] 初始化Playwright实例, headless={}", headless);
        this.playwright = Playwright.create();
        return this.playwright;
    }

    @Bean(destroyMethod = "close")
    public Browser playwrightBrowser(Playwright pw) {
        log.info("[Playwright] 创建Browser实例 (Chromium), headless={}", headless);
        BrowserType.LaunchOptions options = new BrowserType.LaunchOptions()
                .setHeadless(headless)
                .setTimeout(timeout);

        // 云电脑环境可能没有GPU，禁用GPU加速
        options.setArgs(java.util.List.of(
                "--disable-gpu",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-extensions",
                "--disable-background-networking"
        ));

        return pw.chromium().launch(options);
    }

    @PreDestroy
    public void close() {
        log.info("[Playwright] 优雅关闭...");
        if (playwright != null) {
            try {
                playwright.close();
                log.info("[Playwright] 关闭完成");
            } catch (Exception e) {
                log.warn("[Playwright] 关闭异常: {}", e.getMessage());
            }
        }
    }
}
