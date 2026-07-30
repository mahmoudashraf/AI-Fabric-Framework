package com.ai.fabric.platform.backend.partner.model;

import java.time.Instant;
import java.util.List;

public record PartnerShopifyOperationsSummary(
    String storeId,
    String shopDomain,
    String merchantName,
    String installStatus,
    String widgetStatus,
    String knowledgeSyncStatus,
    String readinessStatus,
    PartnerShopifyBillingSummary billing,
    PartnerShopifyActivationSummary activation,
    PartnerShopifyUsageSummary usage,
    PartnerShopifyProvisioningSummary provisioning,
    PartnerShopifySupportReadinessSummary supportReadiness,
    PartnerShopifyWebhookSummary webhooks,
    List<PartnerShopifyActionAuditSummary> recentActions,
    PartnerShopifyVectorizationOperationsSummary vectorization,
    List<String> capabilities,
    Instant checkedAt
) {
}
