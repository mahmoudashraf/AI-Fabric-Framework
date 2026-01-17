package com.ai.infrastructure.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for relationship_query post-action generation (summarize/explain/recommend)
 * based on action results.
 *
 * <p>Feature is disabled by default and must be explicitly enabled.</p>
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.infrastructure.relationship.post-action-generation")
public class RelationshipQueryPostActionGenerationProperties {

    /**
     * Enables chained relationship_query -> LLM generation (summary) execution.
     */
    private boolean enabled = false;

    /**
     * Maximum number of items (documents/rows) included in the facts payload.
     */
    @Min(0)
    private int maxItems = 10;

    /**
     * Maximum characters allowed for the facts payload JSON embedded in the LLM prompt.
     */
    @Min(1_000)
    private int maxChars = 12_000;

    /**
     * Temperature for summarization generation.
     */
    @NotNull
    @Min(0)
    @Max(1)
    private Double temperature = 0.2d;

    /**
     * Optional max tokens override for summarization generation. Null defers to provider defaults.
     */
    @Min(1)
    private Integer maxTokens;

    public void setTemperature(Double temperature) {
        if (temperature == null) {
            this.temperature = 0.2d;
            return;
        }
        double value = temperature;
        if (value < 0) {
            value = 0;
        } else if (value > 1) {
            value = 1;
        }
        this.temperature = value;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = Math.max(0, maxItems);
    }

    public void setMaxChars(int maxChars) {
        this.maxChars = Math.max(1_000, maxChars);
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens != null ? Math.max(1, maxTokens) : null;
    }
}

