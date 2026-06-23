package com.ths.crawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 同花顺数据爬虫启动类
 */
@EnableScheduling
@SpringBootApplication
public class ThsCrawlerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ThsCrawlerApplication.class, args);
    }
}
