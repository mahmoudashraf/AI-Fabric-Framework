package com.ai.fabric.product.shopify.bridge.customeraccount.model;

import java.util.List;

public record ShopifyCustomerAccountTokenBrokerRequest(
    String shopperSessionId,
    List<String> requiredScopes
) {
}
