package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerMemberSummary(
    String id,
    String email,
    boolean emailVerified,
    String displayName,
    String avatarUrl,
    String role,
    String status,
    List<String> privileges,
    List<String> effectivePermissions
) {
}
