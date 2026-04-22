package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplacePluginInstallFieldSummary(
    String id,
    String label,
    String type,
    boolean required,
    String description,
    List<String> options
) {
}
