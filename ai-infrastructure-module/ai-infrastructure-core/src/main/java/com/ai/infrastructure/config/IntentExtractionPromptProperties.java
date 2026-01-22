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
     * Controls which intent extraction schema and guidance is used.
     */
    private PromptMode promptMode = PromptMode.FULL_CONTRACT;

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

