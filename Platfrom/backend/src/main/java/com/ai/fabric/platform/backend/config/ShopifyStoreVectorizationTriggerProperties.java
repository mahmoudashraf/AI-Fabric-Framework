package com.ai.fabric.platform.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.shopify.vectorization")
public record ShopifyStoreVectorizationTriggerProperties(
    boolean enabled,
    Duration initialDelay,
    Duration fixedDelay,
    Duration leaseTtl,
    Duration retryBackoff,
    Duration completedRetention,
    Duration deadLetterRetention,
    int maxAttempts,
    int schedulerBatchSize,
    int recentEventsLimit
) {

    public ShopifyStoreVectorizationTriggerProperties {
        initialDelay = normalize(initialDelay, Duration.ofSeconds(20));
        fixedDelay = normalize(fixedDelay, Duration.ofSeconds(10));
        leaseTtl = normalize(leaseTtl, Duration.ofSeconds(45));
        retryBackoff = normalize(retryBackoff, Duration.ofSeconds(30));
        completedRetention = normalize(completedRetention, Duration.ofDays(7));
        deadLetterRetention = normalize(deadLetterRetention, Duration.ofDays(30));
        maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
        schedulerBatchSize = schedulerBatchSize <= 0 ? 25 : schedulerBatchSize;
        recentEventsLimit = recentEventsLimit <= 0 ? 20 : Math.min(recentEventsLimit, 100);
    }

    private static Duration normalize(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
