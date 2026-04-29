package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;
import java.util.Map;

public record ShopifyReadinessAuditAnswerResultSummary(
    String queryId,
    String surface,
    String tierProfile,
    String expectedBehavior,
    boolean passed,
    int httpStatus,
    String failureCategory,
    int answerLength,
    String answerPreview,
    Map<String, Boolean> checks,
    List<String> forbiddenHits,
    List<String> missingRequiredConcepts,
    List<String> unsafeActionExecutionHits
) {
}
