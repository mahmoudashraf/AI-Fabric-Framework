package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record MerchantPartnerAccessRequestSummary(
    String requestId,
    String implementationRequestId,
    String partnerAccountId,
    String partnerName,
    String clientName,
    String contactEmail,
    String storeConnectionId,
    String shopDomain,
    String requestedTier,
    List<String> requestedSurfaces,
    List<String> knownIntegrations,
    String notes,
    String requestedScope,
    String status,
    Instant createdAt,
    Instant expiresAt,
    Instant approvedAt,
    Instant updatedAt
) {
}
