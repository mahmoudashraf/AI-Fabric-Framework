package com.ai.fabric.platform.backend.partner.model;

public record PartnerShopifyStorefrontPlacementSummary(
    String surfaceId,
    String label,
    String blockHandle,
    String template,
    String target,
    String requiredTierKey,
    boolean enabled,
    boolean available,
    String guidance
) {
}
