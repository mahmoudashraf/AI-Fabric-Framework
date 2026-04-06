package com.ai.fabric.platform.backend.secret.model;

import java.util.List;

public record DeploymentProviderSecretBindingCatalogSummary(
    String deploymentId,
    List<String> supportedPurposes,
    List<PlatformDeploymentOverrideSecretSummary> availableOverrideSecrets,
    List<DeploymentProviderSecretBindingSummary> bindings
) {
}
