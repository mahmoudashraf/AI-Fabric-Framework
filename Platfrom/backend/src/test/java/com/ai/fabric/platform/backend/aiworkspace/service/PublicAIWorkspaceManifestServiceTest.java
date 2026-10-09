package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceReadinessSummary;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicAIWorkspaceManifestServiceTest {

    private final AIWorkspaceInstallationRepository repository = mock(AIWorkspaceInstallationRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final PlatformCustomerConsumerService consumerService = mock(PlatformCustomerConsumerService.class);
    private final AIWorkspaceAssignmentService assignments = mock(AIWorkspaceAssignmentService.class);
    private final AIWorkspaceReadinessService readiness = mock(AIWorkspaceReadinessService.class);
    private final AIWorkspaceAssetCatalogService assets = mock(AIWorkspaceAssetCatalogService.class);
    private final AIWorkspaceExperiencePackRegistry packs = mock(AIWorkspaceExperiencePackRegistry.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final PlatformAIWorkspaceProperties properties = new PlatformAIWorkspaceProperties(
        Path.of("."), false, 120, Duration.ofSeconds(60), null, "https://shopify-bridge.loomai.pro");

    @Test
    void returnsOnlySafeAssignedRuntimeProjectionForAllowedOrigin() throws Exception {
        AIWorkspaceInstallationEntity installation = installation();
        PlatformConsumerEntity consumer = consumer();
        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = release();
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setSecurityConfigJson("{\"privateSecret\":\"must-not-leak\"}");
        AIWorkspaceAssignmentService.Assignment assignment = new AIWorkspaceAssignmentService.Assignment(
            consumer, deployment, release, version, objectMapper.readTree(version.getSecurityConfigJson()),
            "https://dep-example.loomai.pro", "sha256:" + "a".repeat(64));
        AIWorkspaceAssetDescriptor workspace = asset("workspace", "max-mode-widget", "/api/public/ai-workspace/assets/workspace/1.1.0/widget.js");
        AIWorkspaceAssetDescriptor pack = asset("experience-pack", "dealership", "/api/public/ai-workspace/assets/dealership/1.1.0/pack.js");

        when(repository.findByInstallationId(installation.getInstallationId())).thenReturn(Optional.of(installation));
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(consumerService.resolvePublicConsumer(consumer.getConsumerId()))
            .thenReturn(new PlatformCustomerConsumerService.ResolvedPublicConsumer(consumer, deployment, release));
        when(readiness.evaluate(installation)).thenReturn(new AIWorkspaceReadinessSummary(
            true, installation.getInstallationId(), "public-runtime-anonymous", consumer.getConsumerId(),
            deployment.getId(), release.getId(), assignment.runtimeBaseUrl(), assignment.assignmentRevision(), java.util.List.of()));
        when(assignments.resolveCurrent(consumer)).thenReturn(assignment);
        when(assets.workspace()).thenReturn(workspace);
        when(packs.resolve("dealership", "1.1.0"))
            .thenReturn(new AIWorkspaceExperiencePackRegistry.ExperiencePack(
                "dealership", "1.1.0", "Dealership", "loomai-dealership-experience-config-v1", true, pack));

        var result = service().resolve(installation.getInstallationId(), "https://dealer.example");
        String json = objectMapper.writeValueAsString(result.manifest());
        assertThat(result.origin()).isEqualTo("https://dealer.example");
        assertThat(result.manifest().connection().path("runtimeBaseUrl").asText())
            .isEqualTo("https://dep-example.loomai.pro");
        assertThat(result.manifest().connection().path("anonymousBootstrap").path("url").asText())
            .isEqualTo("/api/public/chat/session");
        assertThat(json)
            .doesNotContain("must-not-leak")
            .doesNotContain("allowedOrigins")
            .doesNotContain("customer-1")
            .doesNotContain("tenant-1");
    }

    @Test
    void hidesInstallationForMissingOrWrongOrigin() {
        AIWorkspaceInstallationEntity installation = installation();
        when(repository.findByInstallationId(installation.getInstallationId())).thenReturn(Optional.of(installation));

        assertThatThrownBy(() -> service().resolve(installation.getInstallationId(), null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
        assertThatThrownBy(() -> service().resolve(installation.getInstallationId(), "https://attacker.example"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("404");
    }

    @Test
    void projectsAuthenticatedBrokerWithoutHostSpecificWidgetConfiguration() throws Exception {
        AIWorkspaceInstallationEntity installation = installation();
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED);
        installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.AUTHENTICATED);
        stubReadyInstallation(installation);
        PlatformAIWorkspaceProperties authenticatedProperties = new PlatformAIWorkspaceProperties(
            Path.of("."), false, 120, Duration.ofSeconds(60),
            "https://identity.example/workspace/token", "https://shopify-bridge.loomai.pro");

        var result = service(authenticatedProperties).resolve(
            installation.getInstallationId(), "https://dealer.example");
        JsonNode connection = result.manifest().connection();

        assertThat(connection.path("mode").asText()).isEqualTo("public-runtime-authenticated");
        assertThat(connection.path("handler").asText()).isEqualTo("brokered-public-runtime");
        assertThat(connection.path("runtimeBaseUrl").asText()).isEqualTo("https://dep-example.loomai.pro");
        assertThat(connection.path("credentialBroker").path("url").asText())
            .isEqualTo("https://identity.example/workspace/token");
        assertThat(connection.path("credentialBroker").path("credentials").asText()).isEqualTo("include");
        assertThat(connection.has("anonymousBootstrap")).isFalse();
        assertThat(connection.has("adapter")).isFalse();
    }

    @Test
    void projectsPrivateAdapterWithoutRuntimeOrPrivateAssignmentMaterial() throws Exception {
        AIWorkspaceInstallationEntity installation = installation();
        installation.setConnectionMode(AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME);
        installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.SHOPIFY);
        installation.setConnectionConfigurationJson("{\"shopDomain\":\"northfield.myshopify.com\"}");
        stubReadyInstallation(installation);

        var result = service().resolve(installation.getInstallationId(), "https://dealer.example");
        JsonNode connection = result.manifest().connection();
        String json = objectMapper.writeValueAsString(result.manifest());

        assertThat(connection.path("mode").asText()).isEqualTo("backend-mediated-private-runtime");
        assertThat(connection.path("handler").asText()).isEqualTo("private-backend-adapter");
        assertThat(connection.path("adapter").path("bootstrapUrl").asText())
            .isEqualTo("https://shopify-bridge.loomai.pro/api/storefront/shops/"
                + "northfield.myshopify.com/workspace/bootstrap");
        assertThat(connection.path("adapter").path("credentials").asText()).isEqualTo("include");
        assertThat(connection.has("runtimeBaseUrl")).isFalse();
        assertThat(connection.has("routes")).isFalse();
        assertThat(json)
            .doesNotContain("https://dep-example.loomai.pro")
            .doesNotContain("/api/chat/me/")
            .doesNotContain("must-not-leak");
    }

    private PublicAIWorkspaceManifestService service() {
        return service(properties);
    }

    private PublicAIWorkspaceManifestService service(PlatformAIWorkspaceProperties serviceProperties) {
        return new PublicAIWorkspaceManifestService(
            repository, consumers, consumerService, assignments, readiness, assets, packs,
            new AIWorkspaceConnectionProfileRegistry(serviceProperties),
            new PlatformDeliveryProperties("https://api.loomai.pro", true, Duration.ofMinutes(10)),
            serviceProperties, objectMapper
        );
    }

    private void stubReadyInstallation(AIWorkspaceInstallationEntity installation) throws Exception {
        PlatformConsumerEntity consumer = consumer();
        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity release = release();
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setSecurityConfigJson("{\"privateSecret\":\"must-not-leak\"}");
        AIWorkspaceAssignmentService.Assignment assignment = new AIWorkspaceAssignmentService.Assignment(
            consumer, deployment, release, version, objectMapper.readTree(version.getSecurityConfigJson()),
            "https://dep-example.loomai.pro", "sha256:" + "a".repeat(64));
        AIWorkspaceAssetDescriptor workspace = asset(
            "workspace", "max-mode-widget", "/api/public/ai-workspace/assets/workspace/1.1.0/widget.js");
        AIWorkspaceAssetDescriptor pack = asset(
            "experience-pack", "dealership", "/api/public/ai-workspace/assets/dealership/1.1.0/pack.js");

        when(repository.findByInstallationId(installation.getInstallationId())).thenReturn(Optional.of(installation));
        when(consumers.findById(consumer.getId())).thenReturn(Optional.of(consumer));
        when(consumerService.resolvePublicConsumer(consumer.getConsumerId()))
            .thenReturn(new PlatformCustomerConsumerService.ResolvedPublicConsumer(consumer, deployment, release));
        when(readiness.evaluate(installation)).thenReturn(new AIWorkspaceReadinessSummary(
            true, installation.getInstallationId(), installation.getConnectionMode().manifestValue(),
            consumer.getConsumerId(), deployment.getId(), release.getId(), assignment.runtimeBaseUrl(),
            assignment.assignmentRevision(), java.util.List.of()));
        when(assignments.resolveCurrent(consumer)).thenReturn(assignment);
        when(assets.workspace()).thenReturn(workspace);
        when(packs.resolve("dealership", "1.1.0"))
            .thenReturn(new AIWorkspaceExperiencePackRegistry.ExperiencePack(
                "dealership", "1.1.0", "Dealership", "loomai-dealership-experience-config-v1", true, pack));
    }

    private AIWorkspaceInstallationEntity installation() {
        AIWorkspaceInstallationEntity installation = new AIWorkspaceInstallationEntity();
        installation.setId("awi-1");
        installation.setInstallationId("awi_pub_0123456789abcdef0123456789abcdef");
        installation.setCustomerId("customer-1");
        installation.setConsumerEntityId("consumer-entity-1");
        installation.setDisplayName("Northfield");
        installation.setStatus(AIWorkspaceInstallationStatus.ACTIVE);
        installation.setExperiencePackCode("dealership");
        installation.setExperiencePackVersion("1.1.0");
        installation.setConnectionMode(AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS);
        installation.setConnectionProfileCode(AIWorkspaceConnectionProfileRegistry.ANONYMOUS);
        installation.setConnectionProfileVersion("1.0.0");
        installation.setConnectionConfigurationJson("{}");
        installation.setAllowedOriginsJson("[\"https://dealer.example\"]");
        installation.setConfigurationJson("{\"dealer\":{\"id\":\"dealer-1\",\"assistantLabel\":\"Northfield AI\"},\"page\":{\"kind\":\"auto\",\"rootSelector\":\"main\",\"contextLabel\":\"Current page\"}}");
        installation.setCreatedAt(Instant.parse("2026-10-09T00:00:00Z"));
        installation.setUpdatedAt(Instant.parse("2026-10-09T00:00:00Z"));
        return installation;
    }

    private PlatformConsumerEntity consumer() {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("consumer-entity-1");
        consumer.setCustomerId("customer-1");
        consumer.setConsumerId("northfield-web");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId("dep-1");
        consumer.setBoundReleaseId("rel-1");
        return consumer;
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setStatus("ACTIVE");
        return deployment;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setDeploymentId("dep-1");
        release.setDeploymentVersionId("ver-1");
        release.setStatus("APPLIED_VERIFIED");
        release.setVerificationStatus("PASSED");
        return release;
    }

    private AIWorkspaceAssetDescriptor asset(String role, String code, String path) {
        return new AIWorkspaceAssetDescriptor(
            role, code, "1.0.0", path, code + ".js", "b".repeat(64),
            "sha384-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", 10
        );
    }
}
