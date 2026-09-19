package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentSpecialistBundleSummary(
    String bundleId,
    String contractVersion,
    String contentHash,
    List<String> behaviorTypes,
    List<String> specialistRefs,
    List<String> chainRefs,
    List<String> resourceLocations
) {
}
