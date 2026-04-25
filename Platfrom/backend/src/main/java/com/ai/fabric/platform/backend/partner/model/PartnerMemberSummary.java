package com.ai.fabric.platform.backend.partner.model;

public record PartnerMemberSummary(
    String id,
    String email,
    boolean emailVerified,
    String displayName,
    String avatarUrl,
    String role,
    String status
) {
}
