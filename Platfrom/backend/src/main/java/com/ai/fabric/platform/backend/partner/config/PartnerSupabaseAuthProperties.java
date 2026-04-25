package com.ai.fabric.platform.backend.partner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "platform.auth.supabase")
public record PartnerSupabaseAuthProperties(
    boolean enabled,
    String issuer,
    String jwksUri,
    String audience,
    String projectRef,
    boolean requireEmailVerified,
    List<String> allowedProviderIds,
    Duration merchantApprovalTtl,
    String partnerAppUrl
) {
    public PartnerSupabaseAuthProperties {
        if (merchantApprovalTtl == null) {
            merchantApprovalTtl = Duration.ofDays(7);
        }
        if (allowedProviderIds == null || allowedProviderIds.isEmpty()) {
            allowedProviderIds = List.of("google", "apple", "linkedin_oidc", "email");
        }
        if (partnerAppUrl == null || partnerAppUrl.isBlank()) {
            partnerAppUrl = "http://localhost:5174";
        }
    }
}
