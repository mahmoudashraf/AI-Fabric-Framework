package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentPocDatasetSummary(
    String configSource,
    String profileId,
    String profileLabel,
    String profileDescription,
    String upstreamBaseUrl,
    List<String> entityTypes
) {
}
