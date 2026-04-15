package com.ai.fabric.platform.backend.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "platform.marketplace.dataset-sync")
public class MarketplaceDatasetSyncProperties {

    private boolean enabled = true;
    private long initialDelayMs = 300_000L;
    private long fixedDelayMs = 900_000L;
    private Duration minimumInterval = Duration.ofHours(1);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getInitialDelayMs() {
        return initialDelayMs;
    }

    public void setInitialDelayMs(long initialDelayMs) {
        this.initialDelayMs = initialDelayMs;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public Duration getMinimumInterval() {
        return minimumInterval;
    }

    public void setMinimumInterval(Duration minimumInterval) {
        this.minimumInterval = minimumInterval;
    }
}
