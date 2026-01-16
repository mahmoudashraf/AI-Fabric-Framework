package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;

/**
 * Strategy for extracting intents from a query.
 */
public interface IntentExtractionStrategy {

    ExtractionAttempt attemptExtract(String query, OrchestrationContext context);

    String getStrategyName();
}

