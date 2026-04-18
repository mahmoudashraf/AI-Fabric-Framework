package com.ai.fabric.platform.backend.productservice.model;

import java.util.List;

public record PlatformManagedProductServiceOverviewSummary(
    String serviceRef,
    String status,
    String summaryMessage,
    String appName,
    String productFamily,
    String serviceKind,
    String environmentScope,
    String platformBaseUrl,
    String publicBaseUrl,
    boolean adminApiKeyConfigured,
    String serverStartedAt,
    PlatformManagedProductServiceInstallOverview installs,
    PlatformManagedProductServiceStoreOverview stores,
    PlatformManagedProductServiceBillingSummary billing,
    PlatformManagedProductServiceUsageSummary usage,
    List<String> capabilities,
    List<String> notYetImplemented
) {
}
