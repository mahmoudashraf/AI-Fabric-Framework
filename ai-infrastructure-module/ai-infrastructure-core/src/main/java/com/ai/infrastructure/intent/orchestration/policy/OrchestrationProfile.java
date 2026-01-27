package com.ai.infrastructure.intent.orchestration.policy;

import com.ai.infrastructure.config.IntentExtractionPromptProperties;
import com.ai.infrastructure.config.OrchestrationProperties;

/**
 * Optimization profiles providing coherent defaults for orchestration behavior.
 *
 * <p>Profiles are resolved server-side and can be overridden by explicitly configured flags and/or mode overrides.</p>
 */
public enum OrchestrationProfile {
    DEFAULT(
        OrchestrationProperties.InformationMode.LLM_DRIVEN,
        IntentExtractionPromptProperties.PromptMode.FULL_CONTRACT
    ),
    DEMO_CATALOG(
        OrchestrationProperties.InformationMode.DETERMINISTIC_RAG_GENERATE,
        IntentExtractionPromptProperties.PromptMode.MINIMAL_FOR_RAG
    ),
    PRODUCTION_CHAT(
        OrchestrationProperties.InformationMode.LLM_DRIVEN,
        IntentExtractionPromptProperties.PromptMode.FULL_CONTRACT
    );

    private final OrchestrationProperties.InformationMode defaultInformationMode;
    private final IntentExtractionPromptProperties.PromptMode defaultPromptMode;

    OrchestrationProfile(
        OrchestrationProperties.InformationMode defaultInformationMode,
        IntentExtractionPromptProperties.PromptMode defaultPromptMode
    ) {
        this.defaultInformationMode = defaultInformationMode;
        this.defaultPromptMode = defaultPromptMode;
    }

    public OrchestrationProperties.InformationMode defaultInformationMode() {
        return defaultInformationMode;
    }

    public IntentExtractionPromptProperties.PromptMode defaultPromptMode() {
        return defaultPromptMode;
    }
}

