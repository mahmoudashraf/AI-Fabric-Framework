package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerCatalogEntrySummary(
    String surfaceId,
    String name,
    String tier,
    String type,
    String shopperProblem,
    String storefrontPlacement,
    List<String> requiredSourceData,
    List<String> merchantSetup,
    List<String> verificationSteps,
    String healthyResult,
    List<String> failureSigns,
    List<String> limitations,
    String launchSafeClaim,
    List<String> escalationEvidence
) {
}
