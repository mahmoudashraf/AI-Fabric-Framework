package com.ai.infrastructure.chat.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for chat session (conversation) support.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.chat")
public class ChatSessionProperties {

    /**
     * Enables conversation enrichment and recording pipeline steps.
     */
    private boolean enabled = false;

    /**
     * Maximum number of recent turns to include as prompt context.
     */
    @Min(0)
    @Max(50)
    private int windowSize = 10;

    /**
     * Hard cap for rendered conversation history included in prompts (characters).
     */
    @Min(256)
    private int maxContextChars = 8_000;

    /**
     * When true, creates missing sessions on first use.
     */
    private boolean autoCreateSessions = true;

    /**
     * Sliding window is the only built-in strategy in this release.
     */
    private MemoryStrategy memoryStrategy = MemoryStrategy.SLIDING_WINDOW;

    public enum MemoryStrategy {
        SLIDING_WINDOW
    }
}

