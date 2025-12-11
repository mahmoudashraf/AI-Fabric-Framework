package com.ai.infrastructure.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for AISearchableEntity storage strategy selection.
 */
@ConfigurationProperties(prefix = "ai-infrastructure.storage")
public class AIStorageProperties {

    private Strategy strategy = Strategy.SINGLE_TABLE;
    private String customStrategyClass;

    public Strategy getStrategy() {
        return strategy;
    }

    public void setStrategy(Strategy strategy) {
        this.strategy = strategy != null ? strategy : Strategy.SINGLE_TABLE;
    }

    public String getCustomStrategyClass() {
        return customStrategyClass;
    }

    public void setCustomStrategyClass(String customStrategyClass) {
        this.customStrategyClass = customStrategyClass;
    }

    public enum Strategy {
        SINGLE_TABLE,
        PER_TYPE_TABLE,
        CUSTOM;
    }
}
