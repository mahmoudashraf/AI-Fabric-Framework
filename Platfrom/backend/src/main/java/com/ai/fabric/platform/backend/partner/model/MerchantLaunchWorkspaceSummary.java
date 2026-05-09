package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record MerchantLaunchWorkspaceSummary(
    MerchantPartnerAccessRequestSummary accessRequest,
    PartnerStoreSummary store,
    PartnerLaunchReadinessSummary launchReadiness,
    List<PartnerEvidenceBundleSummary> evidenceBundles,
    List<PartnerSupportEscalationSummary> supportEscalations,
    List<String> availableActions,
    List<String> limitations,
    Instant checkedAt
) {
}
