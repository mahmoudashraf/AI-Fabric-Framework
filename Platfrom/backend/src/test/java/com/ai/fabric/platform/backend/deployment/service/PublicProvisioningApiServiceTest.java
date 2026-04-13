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
            true,
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
        var access = response.access();
        var integration = response.integration();

        assertThat(integration).isNotNull();
        assertThat(integration.preferredIntegrationMode()).isEqualTo("PUBLIC_RUNTIME_BROWSER_TOKEN");
        assertThat(integration.runtime().chatBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(integration.runtime().crudBaseUrl()).isNull();
        assertThat(integration.runtime().chatQueryUrl()).isEqualTo("https://runtime.example/api/chat/me/query");
        assertThat(integration.runtime().suggestionsUrl()).isEqualTo("https://runtime.example/api/chat/me/suggestions");
        assertThat(integration.runtime().conversationsUrl()).isEqualTo("https://runtime.example/api/chat/me/conversations");
        assertThat(integration.runtime().conversationItemUrlTemplate()).isEqualTo("https://runtime.example/api/chat/me/conversations/{conversationId}");
        assertThat(integration.runtime().operationalBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(integration.runtime().authContextUrl()).isEqualTo("https://runtime.example/api/chat/me/auth-context");
        assertThat(integration.runtime().authOverviewUrl()).isEqualTo("https://runtime.example/api/admin/auth/overview");
        assertThat(integration.posture().verifiedAuthContextRequired()).isTrue();
        assertThat(integration.trustedBackend().authorizationHeader()).isNull();
        assertThat(integration.trustedBackend().acceptedIssuerPolicyConfigured()).isFalse();
        assertThat(integration.trustedBackend().acceptedAudiencePolicyConfigured()).isFalse();
        assertThat(integration.trustedBackend().platformDefaultIssuerPolicy()).isFalse();
        assertThat(integration.trustedBackend().externalIntegrationReady()).isFalse();
        assertThat(integration.publicRuntime().bootstrapUrl()).isEqualTo("https://runtime.example/api/public/chat/session");
        assertThat(integration.publicRuntime().authorizationHeader()).isEqualTo("Authorization");
        assertThat(integration.publicRuntime().tokenScheme()).isEqualTo("Bearer");
        assertThat(integration.publicRuntime().tokenIssuerHint()).isEqualTo("shopify-app");
        assertThat(integration.publicRuntime().defaultAudience()).isEqualTo("storefront-chat");
        assertThat(integration.posture().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(integration.posture().hostBackedRuntimeRequired()).isFalse();
        assertThat(integration.paths().connectorInternalOnly()).isTrue();
        assertThat(integration.trustedBackend().callerAuthConfigured()).isFalse();
        assertThat(integration.publicRuntime().tokenValidationConfigured()).isTrue();
        assertThat(integration.publicRuntime().anonymousBootstrapSupported()).isTrue();
        assertThat(integration.publicRuntime().acceptedIssuerPolicyConfigured()).isTrue();
        assertThat(integration.publicRuntime().acceptedAudiencePolicyConfigured()).isTrue();
        assertThat(integration.paths().browserDirectRuntimeAccessSupported()).isTrue();
        assertThat(integration.paths().browserDirectChatBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(integration.paths().browserDirectCrudBaseUrl()).isNull();
        assertThat(integration.paths().backendMediatedRuntimeBaseUrl()).isNull();
        assertThat(integration.guidance()).contains("anonymous bootstrap is enabled");
        assertThat(access.posture().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(access.runtime().chatQueryUrl()).isEqualTo("https://runtime.example/api/chat/me/query");
        assertThat(access.runtime().suggestionsUrl()).isEqualTo("https://runtime.example/api/chat/me/suggestions");
        assertThat(access.runtime().conversationsUrl()).isEqualTo("https://runtime.example/api/chat/me/conversations");
        assertThat(access.runtime().conversationItemUrlTemplate()).isEqualTo("https://runtime.example/api/chat/me/conversations/{conversationId}");
        assertThat(access.runtime().operationalBaseUrl()).isEqualTo("https://runtime.example");
        assertThat(access.runtime().authContextUrl()).isEqualTo("https://runtime.example/api/chat/me/auth-context");
        assertThat(access.runtime().authOverviewUrl()).isEqualTo("https://runtime.example/api/admin/auth/overview");
        assertThat(access.posture().verifiedAuthContextRequired()).isTrue();
        assertThat(access.posture().hostBackedRuntimeRequired()).isFalse();
        assertThat(access.trustedBackend().callerAuthConfigured()).isFalse();
        assertThat(access.trustedBackend().authorizationHeader()).isNull();
        assertThat(access.trustedBackend().acceptedIssuerPolicyConfigured()).isFalse();
        assertThat(access.trustedBackend().acceptedAudiencePolicyConfigured()).isFalse();
        assertThat(access.trustedBackend().platformDefaultIssuerPolicy()).isFalse();
        assertThat(access.trustedBackend().externalIntegrationReady()).isFalse();
        assertThat(access.publicRuntime().tokenValidationConfigured()).isTrue();
        assertThat(access.publicRuntime().anonymousBootstrapSupported()).isTrue();
        assertThat(access.publicRuntime().bootstrapUrl()).isEqualTo("https://runtime.example/api/public/chat/session");
        assertThat(access.publicRuntime().authorizationHeader()).isEqualTo("Authorization");
        assertThat(access.publicRuntime().tokenScheme()).isEqualTo("Bearer");
        assertThat(access.publicRuntime().acceptedIssuerPolicyConfigured()).isTrue();
        assertThat(access.publicRuntime().acceptedAudiencePolicyConfigured()).isTrue();
        assertThat(access.publicRuntime().tokenIssuerHint()).isEqualTo("shopify-app");
        assertThat(access.publicRuntime().defaultAudience()).isEqualTo("storefront-chat");
        assertThat(access.guidance()).contains("anonymous bootstrap is enabled");
        assertThat(access.guidance()).contains("Customer-facing business CRUD routes");
        assertThat(access.guidance()).contains("does not expose the internal connector URL");
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
            true,
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
        var access = response.access();
        var integration = response.integration();

        assertThat(integration.preferredIntegrationMode()).isEqualTo("PUBLIC_RUNTIME_BROWSER_TOKEN");
        assertThat(integration.posture().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(integration.posture().hostBackedRuntimeRequired()).isFalse();
        assertThat(integration.runtime().authContextUrl()).isEqualTo("https://runtime-both.example/api/chat/me/auth-context");
        assertThat(integration.runtime().authOverviewUrl()).isEqualTo("https://runtime-both.example/api/admin/auth/overview");
        assertThat(integration.posture().verifiedAuthContextRequired()).isTrue();
        assertThat(integration.publicRuntime().tokenValidationConfigured()).isTrue();
        assertThat(integration.trustedBackend().callerAuthConfigured()).isTrue();
        assertThat(integration.paths().browserDirectRuntimeAccessSupported()).isTrue();
        assertThat(integration.paths().backendMediatedRuntimeBaseUrl()).isNull();
        assertThat(access.posture().runtimeAuthMode()).isEqualTo("PUBLIC_RUNTIME_SIGNED_TOKEN");
        assertThat(access.runtime().authContextUrl()).isEqualTo("https://runtime-both.example/api/chat/me/auth-context");
        assertThat(access.runtime().authOverviewUrl()).isEqualTo("https://runtime-both.example/api/admin/auth/overview");
        assertThat(access.posture().verifiedAuthContextRequired()).isTrue();
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
            true,
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
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
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
        var access = response.access();
        var integration = response.integration();

        assertThat(integration).isNotNull();
        assertThat(integration.preferredIntegrationMode()).isEqualTo("BACKEND_MEDIATED_PRIVATE_RUNTIME");
        assertThat(integration.runtime().chatBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(integration.runtime().crudBaseUrl()).isNull();
        assertThat(integration.runtime().chatQueryUrl()).isEqualTo("https://runtime-private.example/api/chat/me/query");
        assertThat(integration.runtime().suggestionsUrl()).isEqualTo("https://runtime-private.example/api/chat/me/suggestions");
        assertThat(integration.runtime().conversationsUrl()).isEqualTo("https://runtime-private.example/api/chat/me/conversations");
        assertThat(integration.runtime().conversationItemUrlTemplate()).isEqualTo("https://runtime-private.example/api/chat/me/conversations/{conversationId}");
        assertThat(integration.runtime().operationalBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(integration.runtime().authContextUrl()).isEqualTo("https://runtime-private.example/api/chat/me/auth-context");
        assertThat(integration.runtime().authOverviewUrl()).isEqualTo("https://runtime-private.example/api/admin/auth/overview");
        assertThat(integration.posture().verifiedAuthContextRequired()).isTrue();
        assertThat(integration.trustedBackend().authorizationHeader()).isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(integration.trustedBackend().assertionValidationConfigured()).isTrue();
        assertThat(integration.trustedBackend().assertionAuthorizationHeader()).isEqualTo("X-AIFABRIC-RUNTIME-AUTHORIZATION");
        assertThat(integration.trustedBackend().assertionTokenScheme()).isEqualTo("Bearer");
        assertThat(integration.trustedBackend().acceptedIssuerPolicyConfigured()).isTrue();
        assertThat(integration.trustedBackend().acceptedAudiencePolicyConfigured()).isTrue();
        assertThat(integration.trustedBackend().platformDefaultIssuerPolicy()).isTrue();
        assertThat(integration.trustedBackend().externalIntegrationReady()).isFalse();
        assertThat(integration.publicRuntime().bootstrapUrl()).isNull();
        assertThat(integration.publicRuntime().authorizationHeader()).isNull();
        assertThat(integration.publicRuntime().tokenScheme()).isNull();
        assertThat(integration.publicRuntime().tokenIssuerHint()).isNull();
        assertThat(integration.publicRuntime().defaultAudience()).isNull();
        assertThat(integration.posture().runtimeAuthMode()).isEqualTo("PRIVATE_RUNTIME_SIGNED_ASSERTION");
        assertThat(integration.posture().hostBackedRuntimeRequired()).isTrue();
        assertThat(integration.paths().connectorInternalOnly()).isTrue();
        assertThat(integration.trustedBackend().callerAuthConfigured()).isTrue();
        assertThat(integration.publicRuntime().tokenValidationConfigured()).isFalse();
        assertThat(integration.publicRuntime().anonymousBootstrapSupported()).isFalse();
        assertThat(integration.publicRuntime().acceptedIssuerPolicyConfigured()).isFalse();
        assertThat(integration.publicRuntime().acceptedAudiencePolicyConfigured()).isFalse();
        assertThat(integration.paths().browserDirectRuntimeAccessSupported()).isFalse();
        assertThat(integration.paths().browserDirectChatBaseUrl()).isNull();
        assertThat(integration.paths().browserDirectCrudBaseUrl()).isNull();
        assertThat(integration.paths().backendMediatedRuntimeBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(integration.guidance()).contains("signed private-runtime integration");
        assertThat(access.posture().runtimeAuthMode()).isEqualTo("PRIVATE_RUNTIME_SIGNED_ASSERTION");
        assertThat(access.runtime().chatQueryUrl()).isEqualTo("https://runtime-private.example/api/chat/me/query");
        assertThat(access.runtime().suggestionsUrl()).isEqualTo("https://runtime-private.example/api/chat/me/suggestions");
        assertThat(access.runtime().conversationsUrl()).isEqualTo("https://runtime-private.example/api/chat/me/conversations");
        assertThat(access.runtime().conversationItemUrlTemplate()).isEqualTo("https://runtime-private.example/api/chat/me/conversations/{conversationId}");
        assertThat(access.runtime().operationalBaseUrl()).isEqualTo("https://runtime-private.example");
        assertThat(access.runtime().authContextUrl()).isEqualTo("https://runtime-private.example/api/chat/me/auth-context");
        assertThat(access.runtime().authOverviewUrl()).isEqualTo("https://runtime-private.example/api/admin/auth/overview");
        assertThat(access.posture().verifiedAuthContextRequired()).isTrue();
        assertThat(access.posture().hostBackedRuntimeRequired()).isTrue();
        assertThat(access.trustedBackend().callerAuthConfigured()).isTrue();
        assertThat(access.trustedBackend().authorizationHeader()).isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(access.trustedBackend().assertionValidationConfigured()).isTrue();
        assertThat(access.trustedBackend().assertionAuthorizationHeader()).isEqualTo("X-AIFABRIC-RUNTIME-AUTHORIZATION");
        assertThat(access.trustedBackend().assertionTokenScheme()).isEqualTo("Bearer");
        assertThat(access.trustedBackend().acceptedIssuerPolicyConfigured()).isTrue();
        assertThat(access.trustedBackend().acceptedAudiencePolicyConfigured()).isTrue();
        assertThat(access.trustedBackend().platformDefaultIssuerPolicy()).isTrue();
        assertThat(access.trustedBackend().externalIntegrationReady()).isFalse();
        assertThat(access.publicRuntime().tokenValidationConfigured()).isFalse();
        assertThat(access.publicRuntime().anonymousBootstrapSupported()).isFalse();
        assertThat(access.guidance()).contains("Customer-facing business CRUD routes");
        assertThat(access.guidance()).contains("does not expose the internal connector URL");
        assertThat(access.guidance()).contains("platform-managed defaults");
    }

    @Test
    void credentialsExposeExternalPrivateRuntimeReadinessWhenCallerPolicyIsCustomized() {
        PublicApiDeploymentRepository repository = mock(PublicApiDeploymentRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

        PublicApiDeploymentEntity binding = new PublicApiDeploymentEntity();
        binding.setId("pub-custom");
        binding.setClientId("shopify-dev");
        binding.setExternalDeploymentKey("shop-custom");
        binding.setDeploymentId("dep-custom");
        binding.setCreatedAt(Instant.parse("2026-04-06T12:00:00Z"));
        binding.setUpdatedAt(Instant.parse("2026-04-06T12:00:00Z"));

        when(repository.findByClientIdAndDeploymentId("shopify-dev", "dep-custom")).thenReturn(Optional.of(binding));
        when(deploymentService.getDeploymentOverview("dep-custom")).thenReturn(new DeploymentOverviewSummary(
            "dep-custom",
            "Custom Private Deployment",
            "dev",
            "dev-openai-lucene",
            null,
            null,
            null,
            "ACTIVE",
            "v1",
            "HEALTHY",
            "ok",
            "https://runtime-custom.example",
            true,
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
        latestVersion.setId("ver-custom");
        latestVersion.setDeploymentId("dep-custom");
        latestVersion.setSecurityConfigJson("""
            {
              "authzMode": "REMOTE_HTTP",
              "adminApiKeyEnabled": true,
              "connectorApiKeyEnabled": true,
              "privateRuntimeAcceptedIssuers": "merchant-storefront",
              "privateRuntimeAcceptedAudiences": "dep-custom"
            }
            """);
        when(deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc("dep-custom"))
            .thenReturn(List.of(latestVersion));
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn(true);
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
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

        PublicDeploymentCredentialsResponse response = service.getDeploymentCredentials("dep-custom");
        var access = response.access();
        var integration = response.integration();

        assertThat(integration.preferredIntegrationMode()).isEqualTo("BACKEND_MEDIATED_PRIVATE_RUNTIME");
        assertThat(integration.trustedBackend().acceptedIssuerPolicyConfigured()).isTrue();
        assertThat(integration.trustedBackend().acceptedAudiencePolicyConfigured()).isTrue();
        assertThat(integration.trustedBackend().platformDefaultIssuerPolicy()).isFalse();
        assertThat(integration.trustedBackend().externalIntegrationReady()).isTrue();
        assertThat(integration.guidance()).contains("explicitly configured for external host-backed integration");
        assertThat(access.trustedBackend().acceptedIssuerPolicyConfigured()).isTrue();
        assertThat(access.trustedBackend().acceptedAudiencePolicyConfigured()).isTrue();
        assertThat(access.trustedBackend().platformDefaultIssuerPolicy()).isFalse();
        assertThat(access.trustedBackend().externalIntegrationReady()).isTrue();
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
            true,
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
        when(platformSecretService.isSecretPresent("AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY")).thenReturn(true);
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
            .isEqualTo("PRIVATE_RUNTIME_SIGNED_ASSERTION");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredOperationalBaseUrl())
            .isEqualTo("https://runtime-internal.example");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredConnectorOverviewUrl())
            .isEqualTo("https://runtime-internal.example/api/admin/connector/overview");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredConnectorHealthUrl())
            .isEqualTo("https://runtime-internal.example/api/admin/connector/health");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredConnectorActionsOverviewUrl())
            .isEqualTo("https://runtime-internal.example/api/admin/connector/actions/overview");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredAuthContextUrl())
            .isEqualTo("https://runtime-internal.example/api/chat/me/auth-context");
        assertThat(service.getInternalIntegrationSummary("dep-789").preferredAuthOverviewUrl())
            .isEqualTo("https://runtime-internal.example/api/admin/auth/overview");
        assertThat(service.getInternalIntegrationSummary("dep-789").verifiedAuthContextRequired())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").trustedBackendAuthorizationHeader())
            .isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(service.getInternalIntegrationSummary("dep-789").privateRuntimeAssertionValidationConfigured())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").privateRuntimeAuthorizationHeader())
            .isEqualTo("X-AIFABRIC-RUNTIME-AUTHORIZATION");
        assertThat(service.getInternalIntegrationSummary("dep-789").privateRuntimeTokenScheme())
            .isEqualTo("Bearer");
        assertThat(service.getInternalIntegrationSummary("dep-789").trustedBackendAcceptedIssuerPolicyConfigured())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").trustedBackendAcceptedAudiencePolicyConfigured())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").trustedBackendPlatformDefaultIssuerPolicy())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").externalTrustedBackendIntegrationReady())
            .isFalse();
        assertThat(service.getInternalIntegrationSummary("dep-789").connectorInternalOnly())
            .isTrue();
        assertThat(service.getInternalIntegrationSummary("dep-789").browserDirectRuntimeAccessSupported())
            .isFalse();
        assertThat(service.getInternalIntegrationSummary("dep-789").backendMediatedRuntimeBaseUrl())
            .isEqualTo("https://runtime-internal.example");
    }
}
