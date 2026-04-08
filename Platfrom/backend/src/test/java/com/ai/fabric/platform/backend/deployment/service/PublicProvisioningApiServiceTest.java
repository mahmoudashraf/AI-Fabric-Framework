package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entity.PublicApiDeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
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
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
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
        DeploymentVersionEntity latestVersion = new DeploymentVersionEntity();
        latestVersion.setId("ver-123");
        latestVersion.setDeploymentId("dep-123");
        latestVersion.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true,
              "publicRuntimeBootstrapEnabled": true,
              "publicRuntimeTokenIssuer": "shopify-app",
              "publicRuntimeAcceptedIssuers": "shopify-app,runtime-public-bootstrap",
              "publicRuntimeAcceptedAudiences": "storefront-chat,account-chat",
              "publicRuntimeDefaultAudience": "storefront-chat"
            }
            """);
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-123"))
            .thenReturn(List.of(latestVersion));
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(false);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

        PublicProvisioningApiService service = new PublicProvisioningApiService(
            repository,
            deploymentService,
            deploymentVersionRepository,
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

        assertThat(response.connectorBaseUrl()).isNull();
        assertThat(response.integration()).isNotNull();
        assertThat(response.integration().preferredIntegrationMode()).isEqualTo("PUBLIC_RUNTIME_BROWSER_TOKEN");
        assertThat(response.integration().preferredChatBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(response.integration().preferredCrudBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(response.integration().preferredOperationalBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(response.integration().preferredAuthContextUrl()).isEqualTo("https://runtime.example/api/chat/me/auth-context");
        assertThat(response.integration().verifiedAuthContextRequired()).isTrue();
        assertThat(response.integration().trustedBackendAuthorizationHeader()).isNull();
        assertThat(response.integration().publicRuntimeBootstrapUrl()).isEqualTo("https://runtime.example/api/public/chat/session");
        assertThat(response.integration().publicRuntimeAuthorizationHeader()).isEqualTo("Authorization");
        assertThat(response.integration().publicRuntimeTokenScheme()).isEqualTo("Bearer");
        assertThat(response.integration().publicRuntimeTokenIssuerHint()).isEqualTo("shopify-app");
        assertThat(response.integration().publicRuntimeDefaultAudience()).isEqualTo("storefront-chat");
        assertThat(response.integration().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(response.integration().hostBackedRuntimeRequired()).isFalse();
        assertThat(response.integration().connectorInternalOnly()).isTrue();
        assertThat(response.integration().trustedBackendCallerAuthConfigured()).isFalse();
        assertThat(response.integration().publicRuntimeTokenValidationConfigured()).isTrue();
        assertThat(response.integration().anonymousBootstrapSupported()).isTrue();
        assertThat(response.integration().publicRuntimeAcceptedIssuerPolicyConfigured()).isTrue();
        assertThat(response.integration().publicRuntimeAcceptedAudiencePolicyConfigured()).isTrue();
        assertThat(response.integration().browserDirectRuntimeAccessSupported()).isTrue();
        assertThat(response.integration().browserDirectChatBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(response.integration().browserDirectCrudBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(response.integration().backendMediatedRuntimeBaseUrl()).isNull();
        assertThat(response.integration().guidance()).contains("anonymous bootstrap is enabled");
        assertThat(response.access().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(response.access().preferredOperationalBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(response.access().preferredAuthContextUrl()).isEqualTo("https://runtime.example/api/chat/me/auth-context");
        assertThat(response.access().verifiedAuthContextRequired()).isTrue();
        assertThat(response.access().hostBackedRuntimeRequired()).isFalse();
        assertThat(response.access().trustedBackendCallerAuthConfigured()).isFalse();
        assertThat(response.access().trustedBackendAuthorizationHeader()).isNull();
        assertThat(response.access().publicRuntimeTokenValidationConfigured()).isTrue();
        assertThat(response.access().anonymousBootstrapSupported()).isTrue();
        assertThat(response.access().publicRuntimeBootstrapUrl()).isEqualTo("https://runtime.example/api/public/chat/session");
        assertThat(response.access().publicRuntimeAuthorizationHeader()).isEqualTo("Authorization");
        assertThat(response.access().publicRuntimeTokenScheme()).isEqualTo("Bearer");
        assertThat(response.access().publicRuntimeAcceptedIssuerPolicyConfigured()).isTrue();
        assertThat(response.access().publicRuntimeAcceptedAudiencePolicyConfigured()).isTrue();
        assertThat(response.access().publicRuntimeTokenIssuerHint()).isEqualTo("shopify-app");
        assertThat(response.access().publicRuntimeDefaultAudience()).isEqualTo("storefront-chat");
        assertThat(response.access().guidance()).contains("anonymous bootstrap is enabled");
        assertThat(response.access().guidance()).contains("does not expose the internal connector URL");
    }

    @Test
    void credentialsPreferExplicitPublicRuntimeModeWhenBothPublicAndTrustedBackendSecretsExist() {
        PublicApiDeploymentRepository repository = mock(PublicApiDeploymentRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        PublicApiDeploymentEntity binding = new PublicApiDeploymentEntity();
        binding.setId("pub-both");
        binding.setClientId("shopify-dev");
        binding.setExternalDeploymentKey("shop-both");
        binding.setDeploymentId("dep-both");
        binding.setCreatedAt(Instant.parse("2026-04-06T12:00:00Z"));
        binding.setUpdatedAt(Instant.parse("2026-04-06T12:00:00Z"));

        when(repository.findByClientIdAndDeploymentId("shopify-dev", "dep-both")).thenReturn(Optional.of(binding));
        when(deploymentService.getDeploymentOverview("dep-both")).thenReturn(new DeploymentOverviewSummary(
            "dep-both",
            "Public Shop Deployment",
            "dev",
            "dev-openai-lucene",
            null,
            null,
            null,
            "ACTIVE",
            "v1",
            "HEALTHY",
            "ok",
            "https://runtime-both.example",
            "https://connector-both.example",
            false,
            false,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T12:00:00Z")
        ));
        DeploymentVersionEntity latestVersion = new DeploymentVersionEntity();
        latestVersion.setId("ver-both");
        latestVersion.setDeploymentId("dep-both");
        latestVersion.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true,
              "publicRuntimeAcceptedAudiences": "storefront-chat"
            }
            """);
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-both"))
            .thenReturn(List.of(latestVersion));
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(true);

        PublicProvisioningApiService service = new PublicProvisioningApiService(
            repository,
            deploymentService,
            deploymentVersionRepository,
            mock(PlatformAuditService.class),
            platformSecretService,
            new ObjectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("shopify-dev", PlatformRole.PUBLIC_API_CLIENT, "Shopify Dev", "PUBLIC_API_KEY"),
            null,
            List.of()
        ));

        PublicDeploymentCredentialsResponse response = service.getDeploymentCredentials("dep-both");

        assertThat(response.integration().preferredIntegrationMode()).isEqualTo("PUBLIC_RUNTIME_BROWSER_TOKEN");
        assertThat(response.integration().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(response.integration().hostBackedRuntimeRequired()).isFalse();
        assertThat(response.integration().preferredAuthContextUrl()).isEqualTo("https://runtime-both.example/api/chat/me/auth-context");
        assertThat(response.integration().verifiedAuthContextRequired()).isTrue();
        assertThat(response.integration().publicRuntimeTokenValidationConfigured()).isTrue();
        assertThat(response.integration().trustedBackendCallerAuthConfigured()).isTrue();
        assertThat(response.integration().browserDirectRuntimeAccessSupported()).isTrue();
        assertThat(response.integration().backendMediatedRuntimeBaseUrl()).isNull();
        assertThat(response.access().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(response.access().preferredAuthContextUrl()).isEqualTo("https://runtime-both.example/api/chat/me/auth-context");
        assertThat(response.access().verifiedAuthContextRequired()).isTrue();
    }

    @Test
    void credentialsAdvertiseBackendMediatedModeWhenTrustedBackendRuntimeAuthIsConfigured() {
        PublicApiDeploymentRepository repository = mock(PublicApiDeploymentRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        PublicApiDeploymentEntity binding = new PublicApiDeploymentEntity();
        binding.setId("pub-2");
        binding.setClientId("shopify-dev");
        binding.setExternalDeploymentKey("shop-456");
        binding.setDeploymentId("dep-456");
        binding.setCreatedAt(Instant.parse("2026-04-06T12:00:00Z"));
        binding.setUpdatedAt(Instant.parse("2026-04-06T12:00:00Z"));

        when(repository.findByClientIdAndDeploymentId("shopify-dev", "dep-456")).thenReturn(Optional.of(binding));
        when(deploymentService.getDeploymentOverview("dep-456")).thenReturn(new DeploymentOverviewSummary(
            "dep-456",
            "Private Shop Deployment",
            "dev",
            "dev-openai-lucene",
            null,
            null,
            null,
            "ACTIVE",
            "v1",
            "HEALTHY",
            "ok",
            "https://runtime-private.example",
            "https://connector-private.example",
            false,
            false,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T12:00:00Z")
        ));
        DeploymentVersionEntity latestVersion = new DeploymentVersionEntity();
        latestVersion.setId("ver-456");
        latestVersion.setDeploymentId("dep-456");
        latestVersion.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true
            }
            """);
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-456"))
            .thenReturn(List.of(latestVersion));
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(false);

        PublicProvisioningApiService service = new PublicProvisioningApiService(
            repository,
            deploymentService,
            deploymentVersionRepository,
            mock(PlatformAuditService.class),
            platformSecretService,
            new ObjectMapper()
        );

        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("shopify-dev", PlatformRole.PUBLIC_API_CLIENT, "Shopify Dev", "PUBLIC_API_KEY"),
            null,
            List.of()
        ));

        PublicDeploymentCredentialsResponse response = service.getDeploymentCredentials("dep-456");

        assertThat(response.connectorBaseUrl()).isNull();
        assertThat(response.integration()).isNotNull();
        assertThat(response.integration().preferredIntegrationMode()).isEqualTo("BACKEND_MEDIATED_PRIVATE_RUNTIME");
        assertThat(response.integration().preferredChatBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(response.integration().preferredCrudBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(response.integration().preferredOperationalBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(response.integration().preferredAuthContextUrl()).isEqualTo("https://runtime-private.example/api/chat/me/auth-context");
        assertThat(response.integration().verifiedAuthContextRequired()).isTrue();
        assertThat(response.integration().trustedBackendAuthorizationHeader()).isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(response.integration().publicRuntimeBootstrapUrl()).isNull();
        assertThat(response.integration().publicRuntimeAuthorizationHeader()).isNull();
        assertThat(response.integration().publicRuntimeTokenScheme()).isNull();
        assertThat(response.integration().publicRuntimeTokenIssuerHint()).isNull();
        assertThat(response.integration().publicRuntimeDefaultAudience()).isNull();
        assertThat(response.integration().runtimeAuthMode()).isEqualTo("PRIVATE_RUNTIME_TRUSTED_BACKEND");
        assertThat(response.integration().hostBackedRuntimeRequired()).isTrue();
        assertThat(response.integration().connectorInternalOnly()).isTrue();
        assertThat(response.integration().trustedBackendCallerAuthConfigured()).isTrue();
        assertThat(response.integration().publicRuntimeTokenValidationConfigured()).isFalse();
        assertThat(response.integration().anonymousBootstrapSupported()).isFalse();
        assertThat(response.integration().publicRuntimeAcceptedIssuerPolicyConfigured()).isFalse();
        assertThat(response.integration().publicRuntimeAcceptedAudiencePolicyConfigured()).isFalse();
        assertThat(response.integration().browserDirectRuntimeAccessSupported()).isFalse();
        assertThat(response.integration().browserDirectChatBaseUrl()).isNull();
        assertThat(response.integration().browserDirectCrudBaseUrl()).isNull();
        assertThat(response.integration().backendMediatedRuntimeBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(response.integration().guidance()).contains("trusted-backend/private-runtime integration");
        assertThat(response.access().runtimeAuthMode()).isEqualTo("PRIVATE_RUNTIME_TRUSTED_BACKEND");
        assertThat(response.access().preferredOperationalBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(response.access().preferredAuthContextUrl()).isEqualTo("https://runtime-private.example/api/chat/me/auth-context");
        assertThat(response.access().verifiedAuthContextRequired()).isTrue();
        assertThat(response.access().hostBackedRuntimeRequired()).isTrue();
        assertThat(response.access().trustedBackendCallerAuthConfigured()).isTrue();
        assertThat(response.access().trustedBackendAuthorizationHeader()).isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(response.access().publicRuntimeTokenValidationConfigured()).isFalse();
        assertThat(response.access().anonymousBootstrapSupported()).isFalse();
        assertThat(response.access().guidance()).contains("does not expose the internal connector URL");
    }

    @Test
    void internalIntegrationSummaryDoesNotRequirePublicClientBinding() {
        PublicApiDeploymentRepository repository = mock(PublicApiDeploymentRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        when(deploymentService.getDeploymentOverview("dep-789")).thenReturn(new DeploymentOverviewSummary(
            "dep-789",
            "Internal Deployment",
            "dev",
            "dev-openai-lucene",
            null,
            null,
            null,
            "ACTIVE",
            "v1",
            "HEALTHY",
            "ok",
            "https://runtime-internal.example",
            "https://connector-internal.example",
            false,
            false,
            null,
            null,
            null,
            null,
            Instant.parse("2026-04-06T12:00:00Z"),
            Instant.parse("2026-04-06T12:00:00Z")
        ));
        DeploymentVersionEntity latestVersion = new DeploymentVersionEntity();
        latestVersion.setId("ver-789");
        latestVersion.setDeploymentId("dep-789");
        latestVersion.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true
            }
            """);
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-789"))
            .thenReturn(List.of(latestVersion));
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY")).thenReturn(false);

        PublicProvisioningApiService service = new PublicProvisioningApiService(
            repository,
            deploymentService,
            deploymentVersionRepository,
            mock(PlatformAuditService.class),
            platformSecretService,
            new ObjectMapper()
        );

        assertThat(service.getInternalIntegrationSummary("dep-789").preferredIntegrationMode())
            .isEqualTo("BACKEND_MEDIATED_PRIVATE_RUNTIME");
        assertThat(service.getInternalIntegrationSummary("dep-789").runtimeAuthMode())
            .isEqualTo("PRIVATE_RUNTIME_TRUSTED_BACKEND");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredOperationalBaseUrl())
            .isEqualTo("https://runtime-internal.example");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredAuthContextUrl())
            .isEqualTo("https://runtime-internal.example/api/chat/me/auth-context");
        assertThat(service.getInternalIntegrationSummary("dep-789").verifiedAuthContextRequired())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").trustedBackendAuthorizationHeader())
            .isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(service.getInternalIntegrationSummary("dep-789").connectorInternalOnly())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").browserDirectRuntimeAccessSupported())
            .isFalse();
        assertThat(service.getInternalIntegrationSummary("dep-789").backendMediatedRuntimeBaseUrl())
            .isEqualTo("https://runtime-internal.example");
    }
}
