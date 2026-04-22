package com.ai.fabric.platform.backend.marketplace.model;

public record CreateMarketplacePublisherRequest(
    String slug,
    String displayName,
    String contactEmail
) {
}
