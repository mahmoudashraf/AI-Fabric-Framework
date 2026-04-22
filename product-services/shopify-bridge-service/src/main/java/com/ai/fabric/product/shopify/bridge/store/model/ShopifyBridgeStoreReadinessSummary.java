package com.ai.fabric.product.shopify.bridge.store.model;

import java.util.List;

public record ShopifyBridgeStoreReadinessSummary(
    String overallStatus,
    boolean goLiveEligible,
    boolean storefrontReady,
    List<String> goLiveBlockingReasons,
    List<String> storefrontBlockingReasons,
    List<String> nextActions
) {
}
