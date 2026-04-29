package com.ai.fabric.platform.backend.shopify.model;

import java.util.List;

public record ShopifyCompanionPackageProfileChoiceSummary(
    String value,
    String label,
    String description,
    String source,
    String status,
    boolean recommended,
    List<String> packageKeys,
    List<String> tierKeys,
    List<String> runtimeProfileKeys,
    List<String> vectorProfileKeys,
    List<String> vectorStrategies,
    List<String> vectorProvisioningModes,
    List<String> vectorStoragePostures
) {
}
