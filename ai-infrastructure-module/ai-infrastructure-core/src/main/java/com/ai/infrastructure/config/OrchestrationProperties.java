package com.ai.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for orchestration behavior.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ai.orchestration")
public class OrchestrationProperties {

    /**
     * How INFORMATION intents are handled.
     */
    private InformationMode informationMode = InformationMode.LLM_DRIVEN;

    /**
     * How INFORMATION intents are handled.
     */
    public enum InformationMode {
        /**
         * Current behavior: intent extraction controls requiresRetrieval/requiresGeneration and vectorSpace routing.
         */
        LLM_DRIVEN,

        /**
         * Deterministic behavior: INFORMATION always performs retrieval (RAG) and generation.
         * Missing vectorSpace triggers fan-out across all known vector spaces (when available).
         */
        DETERMINISTIC_RAG_GENERATE
    }
}
