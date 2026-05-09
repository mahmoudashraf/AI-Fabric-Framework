package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerLaunchReadinessSummary(
    String storeId,
    String shopDomain,
    String status,
    boolean stagingReady,
    boolean evidenceReady,
    boolean productionPromotionAllowed,
    boolean productionPromotionReady,
    boolean goLiveEligible,
    List<String> blockers,
    List<String> nextActions,
    String latestVerificationRunId,
    String latestVerificationStatus,
    String latestEvidenceBundleId,
    String latestEvidenceStatus,
    String merchantAction,
    Instant checkedAt
) {
}
