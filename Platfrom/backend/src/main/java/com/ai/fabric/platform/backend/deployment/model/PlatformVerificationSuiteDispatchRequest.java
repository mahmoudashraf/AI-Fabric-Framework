package com.ai.fabric.platform.backend.deployment.model;

public record PlatformVerificationSuiteDispatchRequest(
    boolean allowControlPlaneRepair,
    ShopifyCompanionVerificationExpectationOverrides shopifyCompanionExpectations,
    ShopifyCompanionVerificationExpectationOverrides shopifyFirstProductReadinessExpectations
) {
}
