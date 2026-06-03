package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderCredentialEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderCredentialRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoolifyTargetProfileResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void preflightResolvesSecretAndChecksCoolifyHealthWithoutExposingToken() throws Exception {
        DeploymentProviderCredentialRepository credentialRepository = mock(DeploymentProviderCredentialRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        CoolifyApiClient coolifyApiClient = mock(CoolifyApiClient.class);
        DeploymentProviderCredentialEntity credential = credential();
        DeploymentTargetProfileEntity profile = profile();

        when(credentialRepository.findById("dpc-coolify-staging")).thenReturn(Optional.of(credential));
        when(platformSecretService.resolveSecret("COOLIFY_STAGING_API_TOKEN")).thenReturn("mock-token");
        when(coolifyApiClient.health(org.mockito.ArgumentMatchers.any())).thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));
        when(coolifyApiClient.version(org.mockito.ArgumentMatchers.any())).thenReturn("4.0.0");

        CoolifyTargetProfileResolver resolver = new CoolifyTargetProfileResolver(
            credentialRepository,
            platformSecretService,
            coolifyApiClient,
            objectMapper
        );

        DeploymentProviderPreflightSummary summary = resolver.preflight(profile);

        assertThat(summary.status()).isEqualTo("PASSED");
        assertThat(summary.version()).isEqualTo("4.0.0");
        assertThat(summary.message()).doesNotContain("mock-token");
        assertThat(summary.checks()).contains("credential_resolved", "health_endpoint_ok", "version_endpoint_ok");
    }

    @Test
    void customerGroupedProfileCanResolveConfigWithoutDefaultEnvironmentUuid() {
        CoolifyTargetProfileResolver resolver = new CoolifyTargetProfileResolver(
            mock(DeploymentProviderCredentialRepository.class),
            mock(PlatformSecretService.class),
            mock(CoolifyApiClient.class),
            objectMapper
        );
        DeploymentTargetProfileEntity profile = profile();
        profile.setProviderConfigJson("""
            {"baseUrl":"http://coolify.example","projectUuid":"project","environmentName":"staging","serverUuid":"server","destinationUuid":"destination","apiVersionPinned":"4.0.0"}
            """);
        profile.setResourceDefaultsJson("""
            {"customerProjectGroupingEnabled":true,"customerProjectEnvironmentName":"staging"}
            """);

        CoolifyTargetProfileConfig config = resolver.readConfig(profile);

        assertThat(config.environmentName()).isEqualTo("staging");
        assertThat(config.environmentUuid()).isNull();
    }

    @Test
    void nonGroupedProfileStillRequiresEnvironmentUuid() {
        CoolifyTargetProfileResolver resolver = new CoolifyTargetProfileResolver(
            mock(DeploymentProviderCredentialRepository.class),
            mock(PlatformSecretService.class),
            mock(CoolifyApiClient.class),
            objectMapper
        );
        DeploymentTargetProfileEntity profile = profile();
        profile.setProviderConfigJson("""
            {"baseUrl":"http://coolify.example","projectUuid":"project","environmentName":"staging","serverUuid":"server","destinationUuid":"destination","apiVersionPinned":"4.0.0"}
            """);

        assertThatThrownBy(() -> resolver.readConfig(profile))
            .hasMessageContaining("environmentUuid");
    }

    private DeploymentProviderCredentialEntity credential() {
        DeploymentProviderCredentialEntity credential = new DeploymentProviderCredentialEntity();
        credential.setId("dpc-coolify-staging");
        credential.setName("Coolify staging");
        credential.setProviderType(DeploymentProviderType.COOLIFY);
        credential.setSecretRef("COOLIFY_STAGING_API_TOKEN");
        credential.setStatus("READY");
        credential.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        credential.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return credential;
    }

    private DeploymentTargetProfileEntity profile() {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId("dtp-coolify-staging");
        profile.setName("Coolify staging");
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName("staging");
        profile.setRegion("nbg1");
        profile.setActive(true);
        profile.setSourceStrategy("IMAGE_SOURCE");
        profile.setCredentialRefId("dpc-coolify-staging");
        profile.setProviderConfigJson("""
            {"baseUrl":"http://coolify.example","projectUuid":"project","environmentName":"staging","environmentUuid":"env","serverUuid":"server","destinationUuid":"destination","apiVersionPinned":"4.0.0"}
            """);
        profile.setNetworkPolicyJson("{}");
        profile.setResourceDefaultsJson("{}");
        profile.setCreatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        profile.setUpdatedAt(Instant.parse("2026-05-01T00:00:00Z"));
        return profile;
    }
}
