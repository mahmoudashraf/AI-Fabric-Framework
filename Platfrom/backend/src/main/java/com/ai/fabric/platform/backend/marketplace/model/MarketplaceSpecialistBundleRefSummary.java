package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplaceSpecialistBundleRefSummary(
    String bundleId,
    String contractVersion,
    String contentHash,
    List<String> specialistRefs,
    List<String> chainRefs
) {
}
