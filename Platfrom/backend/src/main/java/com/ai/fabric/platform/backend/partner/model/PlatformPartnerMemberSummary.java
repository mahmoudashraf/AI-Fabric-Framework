package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PlatformPartnerMemberSummary(
    String id,
    String partnerAccountId,
    String partnerAccountName,
    String email,
    boolean emailVerified,
    String displayName,
    String avatarUrl,
    String role,
    String status,
    List<String> privileges,
    List<String> effectivePermissions,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {
}
