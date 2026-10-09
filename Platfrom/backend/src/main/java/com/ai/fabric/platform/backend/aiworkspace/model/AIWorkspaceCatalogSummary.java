package com.ai.fabric.platform.backend.aiworkspace.model;

import java.util.List;

public record AIWorkspaceCatalogSummary(
    boolean assetsReady,
    String assetStatus,
    List<ExperiencePackSummary> experiencePacks,
    List<ConnectionProfileSummary> connectionProfiles
) {
    public record ExperiencePackSummary(
        String code,
        String version,
        String name,
        String configurationSchemaVersion,
        boolean enabled
    ) {
    }

    public record ConnectionProfileSummary(
        String code,
        String version,
        String name,
        String mode,
        String handler,
        String configurationSchemaVersion,
        boolean enabled,
        String availabilityMessage
    ) {
    }
}
