package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PartnerVerificationRunSummary(
    String id,
    String storeAssignmentId,
    String shopDomain,
    String packId,
    String packName,
    String status,
    int totalSteps,
    int passedSteps,
    int failedSteps,
    int blockedSteps,
    String evidenceBundleId,
    Map<String, Object> summary,
    Instant startedAt,
    Instant completedAt,
    List<PartnerVerificationStepSummary> steps
) {
}
