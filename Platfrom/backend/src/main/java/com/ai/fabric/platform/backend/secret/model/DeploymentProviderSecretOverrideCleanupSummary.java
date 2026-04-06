package com.ai.fabric.platform.backend.secret.model;

import java.util.List;

public record DeploymentProviderSecretOverrideCleanupSummary(
    List<String> removedBindingIds,
    List<String> deletedSecretNames,
    List<String> preservedSecretNames
) {
}
