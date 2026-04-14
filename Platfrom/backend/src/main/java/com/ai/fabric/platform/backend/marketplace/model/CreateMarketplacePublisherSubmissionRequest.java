package com.ai.fabric.platform.backend.marketplace.model;

import com.fasterxml.jackson.databind.JsonNode;

public record CreateMarketplacePublisherSubmissionRequest(
    String pluginSlug,
    String releaseChannel,
    JsonNode manifest
) {
}
