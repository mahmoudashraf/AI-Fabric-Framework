package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicProvisioningApiServiceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void credentialsAdvertiseSignedPublicRuntimeModeWhenPublicTokenValidationIsConfigured() {
        PublicApiDeploymentRepository repository = mock(PublicApiDeploymentRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        PublicApiDeploymentEntity binding = new PublicApiDeploymentEntity();
        binding.setId("pub-1");
        binding.setClientId("shopify-dev");
        binding.setExternalDeploymentKey("shop-123");
        binding.setDeploymentId("dep-123");
        binding.setCreatedAt(Instant.parse("2026-04-06T12:00:00Z"));
        binding.setUpdatedAt(Instant.parse("2026-04-06T12:00:00Z"));

        when(repository.findByClientIdAndDeploymentId("shopify-dev", "dep-123")).thenReturn(Optional.of(binding));
        when(deploymentService.getDeploymentOverview("dep-123")).thenReturn(new DeploymentOverviewSummary(
            "dep-123",
            "Shop Deployment",
            "dev",
            "dev-openai-lucene",
            null,
            null,
            null,
            "ACTIVE",
            "v1",
            "HEALTHY",
            "ok",
            "https://runtime.example",
            "https://connector.example",
            false,
            false,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T12:00:00Z")
        ));
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(false);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

        PublicProvisioningApiService service = new PublicProvisioningApiService(
            repository,
            deploymentService,
            mock(PlatformAuditService.class),
            platformSecretService,
            new ObjectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("shopify-dev", PlatformRole.PUBLIC_API_CLIENT, "Shopify Dev", "PUBLIC_API_KEY"),
            null,
            List.of()
        ));

        PublicDeploymentCredentialsResponse response = service.getDeploymentCredentials("dep-123");

        assertThat(response.access().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(response.access().hostBackedRuntimeRequired()).isFalse();
        assertThat(response.access().publicRuntimeTokenValidationConfigured()).isTrue();
        assertThat(response.access().anonymousBootstrapSupported()).isFalse();
        assertThat(response.access().publicRuntimeBootstrapUrl()).isNull();
        assertThat(response.access().publicRuntimeAuthorizationHeader()).isEqualTo("Authorization");
        assertThat(response.access().publicRuntimeTokenScheme()).isEqualTo("Bearer");
        assertThat(response.access().guidance()).contains("signed public bearer tokens");
    }
}
