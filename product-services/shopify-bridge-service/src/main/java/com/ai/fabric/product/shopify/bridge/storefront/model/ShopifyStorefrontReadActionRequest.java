package com.ai.fabric.product.shopify.bridge.storefront.model;

import java.util.Map;

public record ShopifyStorefrontReadActionRequest(
    String actionId,
    Map<String, Object> params,
    String surfaceId
) {
}
