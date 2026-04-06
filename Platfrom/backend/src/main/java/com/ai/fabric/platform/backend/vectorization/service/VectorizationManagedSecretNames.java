package com.ai.fabric.platform.backend.vectorization.service;

import java.util.Locale;

public final class VectorizationManagedSecretNames {

    private VectorizationManagedSecretNames() {
    }

    public static String registrationTokenSecretName(String deploymentId) {
        return "MANAGED_VECTORIZATION_RUNNER_TOKEN_DEP_" + normalizedDeploymentKey(deploymentId);
    }

    private static String normalizedDeploymentKey(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return "UNKNOWN";
        }
        return deploymentId.trim()
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]+", "_")
            .replaceAll("_+", "_")
            .replaceAll("(^_|_$)", "");
    }
}
