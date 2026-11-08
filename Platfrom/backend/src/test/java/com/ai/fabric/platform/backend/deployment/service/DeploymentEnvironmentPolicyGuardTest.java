package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPolicySummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

class DeploymentEnvironmentPolicyGuardTest {
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"production", "prod", "unknown", "staging-prod"})
    void developerPolicyRejectsEveryNonStagingEnvironment(String environment) {
        var deployment = new DeploymentEntity(); deployment.restrictToStaging();
        assertThatThrownBy(() -> DeploymentEnvironmentPolicyGuard.requireEnvironmentAllowed(deployment, environment))
            .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                assertThat(e.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                assertThat(e.getReason()).contains("DEPLOYMENT_STAGING_ONLY");
            });
    }

    @Test void actualTargetEnvironmentIsAuthoritativeNotProfileIdOrDeploymentLabel() {
        var deployment = new DeploymentEntity(); deployment.restrictToStaging(); deployment.setEnvironmentName("staging");
        var target = new DeploymentTargetProfileEntity(); target.setId("staging-profile"); target.setEnvironmentName("production");
        assertThatThrownBy(() -> DeploymentEnvironmentPolicyGuard.requireAllowed(deployment, target)).hasMessageContaining("STAGING_ONLY");
        target.setId("production-looking-id"); target.setEnvironmentName("staging");
        deployment.setEnvironmentName("production");
        assertThatCode(() -> DeploymentEnvironmentPolicyGuard.requireAllowed(deployment, target)).doesNotThrowAnyException();
    }

    @Test void ordinaryDeploymentsAndUpgradesStayUnrestricted() {
        var deployment = new DeploymentEntity();
        assertThatCode(() -> DeploymentEnvironmentPolicyGuard.requireEnvironmentAllowed(deployment, "production")).doesNotThrowAnyException();
        assertThat(DeploymentPolicySummary.from(deployment.getEnvironmentPolicy()).tags()).isEmpty();
    }
}
