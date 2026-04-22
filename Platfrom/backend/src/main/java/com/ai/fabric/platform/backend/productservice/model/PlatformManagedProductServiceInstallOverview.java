package com.ai.fabric.platform.backend.productservice.model;

public record PlatformManagedProductServiceInstallOverview(
    int totalCount,
    int installedCount,
    int uninstalledCount,
    int credentialReadyCount,
    String lastAuthenticatedAt,
    String lastUninstalledAt
) {
}
