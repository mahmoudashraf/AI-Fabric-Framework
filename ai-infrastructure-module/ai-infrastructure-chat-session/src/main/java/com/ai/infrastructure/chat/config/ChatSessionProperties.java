package com.ai.infrastructure.chat.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "ai.chat")
public class ChatSessionProperties {

    private boolean enabled = true;

    @NotNull
    private MemoryStrategyType memoryStrategy = MemoryStrategyType.SLIDING_WINDOW;

    @Min(1)
    private int defaultWindowSize = 5;

    @Min(1)
    private int ttlMinutes = 60;

    @Min(1)
    private int hotCacheSize = 1000;

    private boolean enableAutoCleanup = true;

    private String cleanupSchedule = "0 0 * * * *";

    public enum MemoryStrategyType {
        SLIDING_WINDOW,
        SUMMARY
    }

    public MemoryStrategyType getMemoryStrategy(@DefaultValue("SLIDING_WINDOW") MemoryStrategyType defaultValue) {
        return memoryStrategy != null ? memoryStrategy : defaultValue;
    }
}
