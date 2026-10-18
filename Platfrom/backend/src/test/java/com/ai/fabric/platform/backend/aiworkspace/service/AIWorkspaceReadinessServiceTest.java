package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDeliveryMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInteractionPolicy;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceDomainRepository;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AIWorkspaceReadinessServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final AIWorkspaceAssignmentService assignments = mock(AIWorkspaceAssignmentService.class);
    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final AIWorkspaceExperiencePackRegistry packs = mock(AIWorkspaceExperiencePackRegistry.class);
    private final AIWorkspaceDomainService domains = mock(AIWorkspaceDomainService.class);
    private final PlatformSecretService secrets = mock(PlatformSecretService.class);
    private final PlatformAIWorkspaceProperties properties = new PlatformAIWorkspaceProperties(
        Path.of("."), false, 120, Duration.ofSeconds(60), null, null,
        "apps.loomai.test", "https://workspace.loomai.test");

    @Test
    void requiresAssignedRuntimeIdentityConfigurationToMatchInstallationProvider() throws Exception {
        AIWorkspaceInstallationEntity installation = installation();
        PlatformConsumerEntity consumer = consumer();
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(assets.health()).thenReturn(new AIWorkspaceAssetCatalogService.CatalogHealth(true, "READY", "1.1.0"));
        AIWorkspaceAssetDescriptor packAsset = new AIWorkspaceAssetDescriptor(
            "experience-pack", "generic-workspace", "1.0.0", "/pack.js", "pack.js",
            "a".repeat(64), "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 10);
        when(packs.resolve("generic-workspace", "1.0.0"))
            .thenReturn(new AIWorkspaceExperiencePackRegistry.ExperiencePack(
                "generic-workspace", "1.0.0", "Generic", "loomai-generic-workspace-experience-config-v1",
                true, packAsset));
        when(domains.summaries(installation.getId())).thenReturn(List.of(new AIWorkspaceDomainSummary(
            "northfield.apps.loomai.test", "https://northfield.apps.loomai.test", "PLATFORM_DEFAULT",
            "VERIFIED", "", "", null, null, null, Instant.now())));
        when(domains.deliveryAvailable(installation.getId())).thenReturn(true);

        when(secrets.isSecretPresent(any())).thenReturn(true);

        var matching = assignment(security("https://example.supabase.co/auth/v1"));
        when(assignments.resolveTarget(consumer, "dep-1", "rel-1")).thenReturn(matching);
        var ready = service().evaluate(installation);
        assertThat(ready.ready()).isTrue();
        assertThat(ready.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("EXTERNAL_IDENTITY_BINDING");
            assertThat(check.status()).isEqualTo("PASSED");
        });

        when(domains.deliveryAvailable(installation.getId())).thenReturn(false);
        var deliveryFailure = service().evaluate(installation);
        assertThat(deliveryFailure.ready()).isFalse();
        assertThat(deliveryFailure.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("APP_DELIVERY");
            assertThat(check.status()).isEqualTo("BLOCKED");
        });
        assertThat(deliveryFailure.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("ASSIGNMENT");
            assertThat(check.status()).isEqualTo("PASSED");
        });
        when(domains.deliveryAvailable(installation.getId())).thenReturn(true);

        matching.version().setBehaviorConfigJson("""
            {"executionExtensions":["CONVERSATION_HANDOVER"],"conversationHandover":{"enabled":true}}
            """);
        var incompleteScopes = service().evaluate(installation);
        assertThat(incompleteScopes.ready()).isFalse();
        assertThat(incompleteScopes.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("EXTERNAL_IDENTITY_SCOPES");
            assertThat(check.status()).isEqualTo("BLOCKED");
        });
        ((com.fasterxml.jackson.databind.node.ObjectNode) matching.securityConfig()).put(
            "externalIdentityGrantedScopes", "chat:query,chat:suggestions,chat:conversations,chat:messages,chat:handover:request,chat:handover:control,chat:handover:read");
        assertThat(service().evaluate(installation).ready()).isTrue();

        when(assignments.resolveTarget(consumer, "dep-1", "rel-1"))
            .thenReturn(assignment(security("https://other.supabase.co/auth/v1")));
        var mismatched = service().evaluate(installation);
        assertThat(mismatched.ready()).isFalse();
        assertThat(mismatched.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("EXTERNAL_IDENTITY_BINDING");
            assertThat(check.status()).isEqualTo("BLOCKED");
        });
    }

    private AIWorkspaceReadinessService service() {
        return new AIWorkspaceReadinessService(
            consumers,
            assignments,
            assets,
            packs,
            new AIWorkspaceConnectionProfileRegistry(properties),
            new AIWorkspaceAuthenticationProfileRegistry(objectMapper),
            domains,
            secrets,
            properties,
            objectMapper
        );
    }

    private AIWorkspaceInstallationEntity installation() {
        AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
        installation.setId("workspace-1");
        installation.setInstallationId("awi_pub_0123456789abcdef0123456789abcdef");
        installation.setCustomerId("customer-1");
        installation.setConsumerEntityId("consumer-1");
        installation.setExperiencePackCode("generic-workspace");
        installation.setExperiencePackVersion("1.0.0");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.ANONYMOUS);
        installation.setConnectionProfileVersion("1.0.0");
        installation.setConnectionConfigurationJson("{}");
        installation.setAllowedOriginsJson("[\"https://northfield.apps.loomai.test\"]");
        installation.setDeliveryMode(AIWorkspaceDeliveryMode.STANDALONE);
        installation.setAppSlug("northfield");
        installation.setInteractionPolicy(AIWorkspaceInteractionPolicy.PUBLIC_WITH_PROTECTED_CAPABILITIES);
        installation.setAuthProfileCode("supabase");
        installation.setAuthProfileVersion("1.0.0");
        installation.setAuthConfigurationJson("""
            {"projectUrl":"https://example.supabase.co","publishableKey":"sb_publishable_fixture","methods":["email-password"]}
            """);
        return installation;
    }

    private PlatformConsumerEntity consumer() {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-1");
        consumer.setCustomerId("customer-1");
        consumer.setConsumerId("northfield-web");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-1");
        consumer.setBoundReleaseId("rel-1");
        return consumer;
    }

    private AIWorkspaceAssignmentService.Assignment assignment(com.fasterxml.jackson.databind.JsonNode security) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        return new AIWorkspaceAssignmentService.Assignment(
            consumer(), deployment, release, version, security,
            "https://runtime.customer.example", "sha256:" + "b".repeat(64));
    }

    private com.fasterxml.jackson.databind.JsonNode security(String issuer) throws Exception {
        String projectUrl = issuer.substring(0, issuer.length() - "/auth/v1".length());
        return objectMapper.readTree("""
            {
              "publicRuntimeBootstrapEnabled":true,
              "publicRuntimeTokenIssuer":"runtime-public-bootstrap",
              "publicRuntimeAcceptedIssuers":"runtime-public-bootstrap",
              "publicRuntimeAcceptedAudiences":"dep-1",
              "publicRuntimeDefaultAudience":"dep-1",
              "corsAllowedOrigins":"https://northfield.apps.loomai.test",
              "externalIdentityBrokerEnabled":true,
              "externalIdentityProvider":"SUPABASE",
              "externalIdentityIssuer":"%s",
              "externalIdentityJwkSetUri":"%s/auth/v1/.well-known/jwks.json",
              "externalIdentityAudience":"authenticated"
            }
            """.formatted(issuer, projectUrl));
    }
}
