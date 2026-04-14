package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record MarketplacePublisherSubmissionSummary(
    String pluginVersionId,
    String publisherId,
    String pluginId,
    String pluginSlug,
    String pluginDisplayName,
    String pluginType,
    String version,
    String releaseChannel,
    String status,
    String bundleSha256,
    String reviewNotes,
    JsonNode manifest,
    Instant submittedAt,
    String submittedByActorId,
    Instant reviewedAt,
    String reviewedByActorId
) {
}
