package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record PartnerEvidenceBundleSummary(
    String id,
    String storeAssignmentId,
    String shopDomain,
    String verificationRunId,
    String bundleName,
    String bundleKind,
    String status,
    String generatedBy,
    Map<String, Object> summary,
    List<String> attachments,
    Instant generatedAt,
    Instant expiresAt
) {
}
