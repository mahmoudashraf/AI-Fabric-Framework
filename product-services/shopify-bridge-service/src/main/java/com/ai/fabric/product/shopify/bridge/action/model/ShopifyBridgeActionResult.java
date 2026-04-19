package com.ai.fabric.product.shopify.bridge.action.model;

import java.util.List;
import java.util.Map;

public record ShopifyBridgeActionResult(
    boolean success,
    String message,
    Map<String, Object> data,
    List<Map<String, Object>> pinnedTargets,
    String errorCode
) {
    public static ShopifyBridgeActionResult ok(String message, Map<String, Object> data) {
        return new ShopifyBridgeActionResult(true, message, data, null, null);
    }

    public static ShopifyBridgeActionResult failure(String errorCode, String message) {
        return new ShopifyBridgeActionResult(false, message, null, null, errorCode);
    }
}
