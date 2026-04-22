package com.ai.fabric.platform.backend.marketplace.model;

public record UpdateMarketplacePublisherVerificationRequest(
    String verificationStatus,
    String status
) {
}
