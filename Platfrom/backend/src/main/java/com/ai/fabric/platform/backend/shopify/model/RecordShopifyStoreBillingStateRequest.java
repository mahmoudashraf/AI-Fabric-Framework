package com.ai.fabric.platform.backend.shopify.model;

import jakarta.validation.constraints.Size;

public record RecordShopifyStoreBillingStateRequest(
    @Size(max = 64) String tierKey,
    @Size(max = 64) String status,
    @Size(max = 255) String subscriptionId,
    @Size(max = 255) String subscriptionName,
    @Size(max = 500) String reason
) {
}
