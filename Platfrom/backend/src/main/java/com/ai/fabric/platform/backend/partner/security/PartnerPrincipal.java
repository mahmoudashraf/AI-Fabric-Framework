package com.ai.fabric.platform.backend.partner.security;

import com.ai.fabric.platform.backend.security.PlatformRole;

public record PartnerPrincipal(
    String supabaseUserId,
    String email,
    boolean emailVerified,
    String displayName,
    String avatarUrl,
    String provider,
    String providerSubject,
    String partnerAccountId,
    String partnerMemberId,
    PlatformRole role,
    String memberStatus
) {
    public boolean provisioned() {
        return partnerAccountId != null && partnerMemberId != null;
    }
}
