package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentEnvironmentPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Enforces resource eligibility at infrastructure and target-assignment boundaries, not model decisions. */
public final class DeploymentEnvironmentPolicyGuard {
    private DeploymentEnvironmentPolicyGuard() { }

    public static void requireAllowed(DeploymentEntity deployment, DeploymentTargetProfileEntity target) {
        requireEnvironmentAllowed(deployment, target == null ? null : target.getEnvironmentName());
    }

    public static boolean allows(DeploymentEntity deployment, DeploymentTargetProfileEntity target) {
        return allowsEnvironment(deployment, target == null ? null : target.getEnvironmentName());
    }

    public static void requireEnvironmentAllowed(DeploymentEntity deployment, String environment) {
        if (!allowsEnvironment(deployment, environment)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "DEPLOYMENT_STAGING_ONLY: This developer Smart Brain can only use a staging target.");
        }
    }

    private static boolean allowsEnvironment(DeploymentEntity deployment, String environment) {
        return deployment.getEnvironmentPolicy() != DeploymentEnvironmentPolicy.STAGING_ONLY
            || "staging".equalsIgnoreCase(environment);
    }
}
