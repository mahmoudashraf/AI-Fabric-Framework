package com.ai.fabric.platform.backend.partner.model;

public record PartnerShopifyMerchantActionSummary(
    String actionKey,
    String label,
    String owner,
    String status,
    String url,
    String reason
) {
}
