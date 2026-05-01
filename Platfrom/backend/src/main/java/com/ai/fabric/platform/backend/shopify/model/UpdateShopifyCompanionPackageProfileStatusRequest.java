package com.ai.fabric.platform.backend.shopify.model;

public record UpdateShopifyCompanionPackageProfileStatusRequest(
    String status,
    String reason
) {
}
