package com.ai.fabric.product.shopify.bridge.action.model;

import java.util.Map;

public record ShopifyBridgeActionExecuteRequest(
    String actionId,
    Map<String, Object> params,
    String idempotencyKey,
    Map<String, Object> trace
) {
}
