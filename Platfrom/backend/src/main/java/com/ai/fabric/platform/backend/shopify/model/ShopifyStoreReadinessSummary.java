package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyStoreReadinessSummary(
    String overallStatus,
    boolean goLiveEligible,
    boolean storefrontReady,
    List<String> goLiveBlockingReasons,
    List<String> storefrontBlockingReasons,
    List<String> nextActions
) {
}
