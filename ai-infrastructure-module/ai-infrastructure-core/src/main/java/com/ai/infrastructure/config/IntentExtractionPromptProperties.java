package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for intent extraction prompt shaping.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.intent-extraction")
public class IntentExtractionPromptProperties {

    /**
     * Explicit override for which intent extraction schema and guidance is used.
     *
     * <p>When unset (null), the effective value is derived from the resolved orchestration policy.</p>
     */
    private PromptMode promptMode;

    public enum PromptMode {
        /**
         * Full contract: model decides requiresRetrieval/requiresGeneration/vectorSpace/next steps.
         */
        FULL_CONTRACT,

        /**
         * Minimal contract: model focuses on ACTION vs INFORMATION and optimizedQuery.
         * Intended for deterministic INFORMATION orchestration modes.
         */
        MINIMAL_FOR_RAG
    }
}
