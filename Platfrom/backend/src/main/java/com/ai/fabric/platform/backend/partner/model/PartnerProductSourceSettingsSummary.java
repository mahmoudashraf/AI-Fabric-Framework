package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerProductSourceSettingsSummary(
    boolean productsEnabled,
    boolean collectionsEnabled,
    boolean pagesEnabled,
    boolean policiesEnabled,
    boolean articlesEnabled,
    boolean metaobjectsEnabled,
    List<String> enabledCategories,
    Instant lastSourcePreflightAt,
    Instant lastSyncAt
) {
}
