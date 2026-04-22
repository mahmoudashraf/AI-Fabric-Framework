package com.ai.fabric.platform.backend.marketplace.model;

import java.util.List;

public record MarketplacePublisherDetailSummary(
    MarketplacePublisherSummary publisher,
    List<MarketplacePublisherSubmissionSummary> submissions
) {
}
