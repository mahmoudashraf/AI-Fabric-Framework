package com.ai.fabric.platform.backend.shopify.model;

import jakarta.validation.constraints.Size;

public record UpdateShopifyStoreCustomerAccountConfigRequest(
    @Size(max = 255) String storefrontDomain
) {
}
