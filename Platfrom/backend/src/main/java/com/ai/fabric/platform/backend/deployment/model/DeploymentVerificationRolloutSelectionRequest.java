package com.ai.fabric.platform.backend.deployment.model;

import java.util.List;

public record DeploymentVerificationRolloutSelectionRequest(
    List<String> rolloutKeys
) {
}
