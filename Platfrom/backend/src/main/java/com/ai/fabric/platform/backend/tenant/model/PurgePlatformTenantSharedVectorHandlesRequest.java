package com.ai.fabric.platform.backend.tenant.model;

import java.util.List;

public record PurgePlatformTenantSharedVectorHandlesRequest(
    List<String> handleIds,
    boolean purgeAllDetached,
    boolean providerDeleteConfirmed,
    String confirmationText,
    String reason
) {
}
