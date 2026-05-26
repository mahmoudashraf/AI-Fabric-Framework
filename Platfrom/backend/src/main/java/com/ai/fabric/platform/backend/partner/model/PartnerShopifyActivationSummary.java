package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerShopifyActivationSummary(
    String status,
    String storefrontUrl,
    String themeEditorUrl,
    String appEmbedHandle,
    String message,
    List<PartnerShopifyStorefrontPlacementSummary> placements,
    List<PartnerShopifyMerchantActionSummary> merchantActions
) {
}
