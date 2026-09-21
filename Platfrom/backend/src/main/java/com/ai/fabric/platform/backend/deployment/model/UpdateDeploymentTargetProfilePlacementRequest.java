package com.ai.fabric.platform.backend.deployment.model;

import jakarta.validation.constraints.NotBlank;

public record UpdateDeploymentTargetProfilePlacementRequest(
    @NotBlank String serverUuid,
    @NotBlank String destinationUuid,
    String expectedCurrentServerUuid,
    String expectedCurrentDestinationUuid
) {
}
