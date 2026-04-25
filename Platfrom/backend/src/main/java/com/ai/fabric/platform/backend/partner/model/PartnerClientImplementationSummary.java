package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerClientImplementationSummary(
    String id,
    String clientName,
    String contactEmail,
    String shopDomain,
    String vertical,
    String requestedTier,
    List<String> requestedSurfaces,
    List<String> knownIntegrations,
    String status,
    String approvalUrl,
    Instant approvalExpiresAt,
    Instant createdAt,
    Instant updatedAt
) {
}
