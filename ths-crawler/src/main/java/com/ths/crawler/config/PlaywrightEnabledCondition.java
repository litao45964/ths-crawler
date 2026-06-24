package com.ths.crawler.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Playwright激活条件：ths.fetcher.impl=playwright
 */
public class PlaywrightEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String impl = context.getEnvironment().getProperty("ths.fetcher.impl", "");
        return "playwright".equalsIgnoreCase(impl);
    }
}
