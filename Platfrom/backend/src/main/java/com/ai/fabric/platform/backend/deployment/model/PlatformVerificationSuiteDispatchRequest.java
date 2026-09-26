package com.ai.fabric.platform.backend.deployment.model;

public record PlatformVerificationSuiteDispatchRequest(
    boolean allowControlPlaneRepair,
    ShopifyCompanionVerificationExpectationOverrides shopifyCompanionExpectations,
    ShopifyCompanionVerificationExpectationOverrides shopifyFirstProductReadinessExpectations,
    DeploymentBehaviorVerificationExpectationOverrides deploymentBehaviorExpectations,
    DocumentKnowledgeVerificationExpectationOverrides documentKnowledgeExpectations
) {
    public PlatformVerificationSuiteDispatchRequest(
        boolean allowControlPlaneRepair,
        ShopifyCompanionVerificationExpectationOverrides shopifyCompanionExpectations,
        ShopifyCompanionVerificationExpectationOverrides shopifyFirstProductReadinessExpectations,
        DeploymentBehaviorVerificationExpectationOverrides deploymentBehaviorExpectations
    ) {
        this(
            allowControlPlaneRepair,
            shopifyCompanionExpectations,
            shopifyFirstProductReadinessExpectations,
            deploymentBehaviorExpectations,
            null
        );
    }
}
