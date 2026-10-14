package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentReleaseEndpointServiceTest {
    private final DeploymentReleaseEndpointService service = new DeploymentReleaseEndpointService(new ObjectMapper());

    @Test
    void equivalentProviderFieldsResolveOnce() {
        assertThat(service.runtimeBaseUrl(release("{\"runtimeBaseUrl\":\"https://runtime.example/\",\"fqdn\":\"https://runtime.example\"}")))
            .isEqualTo("https://runtime.example");
    }

    @Test
    void conflictingEvidenceIsNotResolvedByPrecedence() {
        assertThatThrownBy(() -> service.runtimeBaseUrl(release("{\"runtimeBaseUrl\":\"https://one.example\",\"fqdn\":\"https://other.example\"}")))
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("ambiguous");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{", "{\"fqdn\":\"runtime.example\"}",
        "{\"fqdn\":\"https://one.example,https://two.example\"}", "{\"fqdn\":\"file:///tmp/x\"}",
        "{\"fqdn\":\"https://user:password@runtime.example\"}", "{\"fqdn\":\"https://runtime.example?secret=x\"}"})
    void absentInvalidOrAmbiguousUrlFailsClosed(String details) {
        assertThatThrownBy(() -> service.runtimeBaseUrl(release(details))).isInstanceOf(ResponseStatusException.class);
    }

    private DeploymentReleaseEntity release(String details) {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setProvisioningDetailsJson(details);
        return release;
    }
}
