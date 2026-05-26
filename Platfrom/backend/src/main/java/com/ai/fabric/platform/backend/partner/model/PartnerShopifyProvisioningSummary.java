package com.ai.fabric.platform.backend.partner.model;

import java.util.List;

public record PartnerShopifyProvisioningSummary(
    String status,
    String phase,
    String nextAction,
    String summaryMessage,
    String effectivePackageKey,
    String effectiveTierKey,
    PartnerShopifyProvisioningJobSummary latestJob,
    List<PartnerShopifyProvisioningJobSummary> recentJobs
) {
}
