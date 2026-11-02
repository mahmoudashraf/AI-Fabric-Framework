package com.ai.fabric.platform.backend.externalintegration;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentBootstrapService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerReviewedCompositionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDocumentStorageBindingService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentVersionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerDeploymentStorageSelectionService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerPermissionMatrix;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerPlacementEvidenceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.model.AiStateStorageMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.*;
import com.ai.fabric.platform.backend.desk.DeskServiceClient;
import com.ai.fabric.platform.backend.desk.DeskTargetConfigurationResolver;
import com.ai.fabric.platform.backend.marketplace.model.CreateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplaceTemplateBootstrapRequest;
import com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceDraftCompilerService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceTemplateBootstrapService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** Real customer commands, factory, compiler and production SQL; no hosted/provider readiness is implied. */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class ExternalDealershipBootstrapPostgresTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        properties.add("spring.flyway.locations", () -> "classpath:db/migration");
        properties.add("spring.flyway.target", () -> "latest");
        for (String intent : List.of("CUSTOMER_STAGING", "CUSTOMER_PRODUCTION")) {
            String prefix = "platform.customer-self-service.trusted-servers." + intent + ".";
            boolean staging = "CUSTOMER_STAGING".equals(intent);
            properties.add(prefix + "uuid", () -> staging ? "external-test-stage" : "external-test-prod");
            properties.add(prefix + "address", () -> staging ? "192.0.2.10" : "192.0.2.20");
            properties.add(prefix + "reported-address", () -> staging ? "192.0.2.10" : "192.0.2.20");
        }
    }

    @Autowired MarketplaceTemplateBootstrapService templates;
    @Autowired DeploymentMarketplaceDraftCompilerService compiler;
    @Autowired ExternalIntegrationInstallInitializer initializer;
    @Autowired ExternalIntegrationCompositionService composition;
    @Autowired DeploymentMarketplacePluginInstallRepository installs;
    @Autowired com.ai.fabric.platform.backend.marketplace.service.DeploymentMarketplaceInstallService installService;
    @Autowired MarketplacePluginVersionRepository versions;
    @Autowired DeploymentRepository deployments;
    @Autowired DeploymentService deploymentService;
    @Autowired CustomerOfferRepository offers;
    @Autowired CustomerOfferDeploymentRepository associations;
    @Autowired CustomerOfferCodec codec;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired CustomerDeploymentBootstrapService customerBootstrap;
    @Autowired CustomerOfferEntitlementService entitlements;
    @Autowired CustomerReviewedCompositionService reviewed;
    @Autowired PlatformCustomerRepository customers;
    @Autowired PlatformUserRepository users;
    @Autowired DeploymentTargetProfileRepository targets;
    @Autowired DeploymentSourceArtifactRepository artifacts;
    @Autowired CustomerPlacementEvidenceRepository placements;
    @Autowired CustomerDocumentStorageBindingService documentStorage;
    @Autowired CustomerDeploymentVersionService customerVersions;
    @Autowired CustomerDeploymentStorageSelectionService storageSelection;
    @Autowired jakarta.validation.Validator validator;
    @Autowired ExternalIntegrationService externalBindings;
    @Autowired ExternalIntegrationResolver externalResolver;
    @Autowired com.ai.fabric.platform.backend.secret.service.PlatformSecretService secrets;
    @MockBean CoolifyApiClient coolify;
    @MockBean RailwayGraphqlClient railway;
    @MockBean PineconeControlPlaneClient pinecone;
    @MockBean QdrantCloudControlPlaneClient qdrant;
    @MockBean ZillizCloudControlPlaneClient zilliz;
    @MockBean DeskServiceClient desk;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void clientReadOnlyRevisionStartsUnboundAndBlocksPublication() {
        authenticate("synthetic-client-operator", PlatformRole.PLATFORM_ADMIN, "SESSION");
        long handlesBefore = count("deployment_provider_resource_handles");
        long graphsBefore = count("deployment_managed_dependency_graphs");
        long bindingsBefore = count("deployment_external_integration_bindings");
        long releasesBefore = count("platform_deployment_releases");
        var created = templates.bootstrapConfiguration("mkp-template-dealership-external-systems-v1",
            new CreateMarketplaceTemplateBootstrapRequest("1.2.0", "Read-only client configuration fixture",
                "staging", null, null, null, null));
        var deployment = deployments.findById(created.id()).orElseThrow();
        // CustomerDeploymentBootstrapService applies this explicit setting after template configuration.
        transactions.executeWithoutResult(status -> storageSelection.initialize(deployment, "CUSTOMER_POSTGRES"));
        var initial = installs.findByDeploymentIdOrderByUpdatedAtDesc(created.id());
        assertThat(initial).extracting(value -> value.getPluginId()).containsExactlyInAnyOrder(
            "mkp-template-dealership-external-systems-v1", "mkp-data-dealership-external-stock-v1",
            "mkp-action-dealership-external-discovery-v1", "mkp-data-dealership-knowledge-v1");
        var stock = installs.findByDeploymentIdAndPluginId(created.id(), "mkp-data-dealership-external-stock-v1").orElseThrow();
        var documents = installs.findByDeploymentIdAndPluginId(created.id(), "mkp-data-dealership-knowledge-v1").orElseThrow();
        assertThat(stock.getStatus()).isEqualTo("DISABLED");
        assertThat(stock.getConfigJson()).isEqualTo("{}");
        assertThat(stock.getSecretRefsJson()).isEqualTo("{}");
        assertThat(documents.getStatus()).isEqualTo("DISABLED");
        var draft = deploymentService.getActiveDraftForDeployment(created.id());
        assertThat(draft.providerConfig().path("aiStateStorageMode").asText()).isEqualTo("CUSTOMER_POSTGRES");
        assertThat(draft.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(draft.behaviorConfig().path("executionExtensions")).isEmpty();
        assertThat(draft.behaviorConfig().has("conversationHandover")).isFalse();
        assertThat(draft.behaviorConfig().at("/marketplaceRequirements/unresolvedRequiredPluginRefs"))
            .extracting(value -> value.asText()).contains(
                "mkp-data-dealership-external-stock-v1@1.0.0",
                "mkp-action-dealership-external-discovery-v1@1.1.0",
                "mkp-data-dealership-knowledge-v1@1.0.0");
        assertThat(deploymentService.validateDraft(draft.id()).publishReady()).isFalse();
        assertThatThrownBy(() -> deploymentService.publishDraft(draft.id()))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(jdbc.queryForObject("select count(*) from platform_vectorization_plans where deployment_id = ?", Long.class, created.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from platform_desk_bindings where deployment_id = ?", Long.class, created.id())).isZero();

        assertThat(count("deployment_provider_resource_handles")).isEqualTo(handlesBefore);
        assertThat(count("deployment_managed_dependency_graphs")).isEqualTo(graphsBefore);
        assertThat(count("deployment_external_integration_bindings")).isEqualTo(bindingsBefore);
        assertThat(count("platform_deployment_releases")).isEqualTo(releasesBefore);
        assertThat(deploymentService.listVersions(created.id())).isEmpty();
        verifyNoInteractions(coolify, railway, pinecone, qdrant, zilliz, desk);
    }

    @Test
    void clientReadOnlyCustomerBootstrapRequiresExplicitExternalSqlWithoutAReceiverSlot() {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String customerId = "cus-" + suffix, userId = "usr-" + suffix, email = suffix + "@customer.invalid";
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId);
        customer.setName("Read-only client fixture"); customer.setStatus("ACTIVE");
        customer.setCreatedAt(now); customer.setUpdatedAt(now); customers.saveAndFlush(customer);
        var user = new PlatformUserEntity(); user.setId(userId); user.setEmail(email); user.setDisplayName("Fixture owner");
        user.setPasswordHash("not-a-real-password"); user.setRole("CUSTOMER_ADMIN"); user.setStatus("ACTIVE");
        user.setCustomerId(customerId); user.setCreatedAt(now); user.setUpdatedAt(now); users.saveAndFlush(user);
        var stage = target("stage-" + suffix, "external-test-stage", "staging");
        var production = target("prod-" + suffix, "external-test-prod", "production");
        var permissions = List.of(new TargetPermission(stage.getId(), TargetIntent.CUSTOMER_STAGING, reviewed.profileFingerprint(stage)),
            new TargetPermission(production.getId(), TargetIntent.CUSTOMER_PRODUCTION, reviewed.profileFingerprint(production)));
        placements.saveAndFlush(new CustomerPlacementEvidenceEntity("cpe-" + suffix, stage.getId(), TargetIntent.CUSTOMER_STAGING,
            permissions.getFirst().profileFingerprint(), "external-test-stage", "192.0.2.10", "destination", now.minusSeconds(1), now.plusSeconds(300)));
        var template = versions.findById("mkv-template-dealership-external-systems-v1-2").orElseThrow();
        var pins = List.of("mkv-data-dealership-external-stock-v1-0", "mkv-action-dealership-external-discovery-v1-1",
            "mkv-data-dealership-knowledge-v1-0").stream().map(id -> versions.findById(id).orElseThrow())
            .map(version -> new PluginPin(version.getPluginId(), version.getId(), codec.hash(codec.tree(version.getManifestJson())))).toList();
        var stock = pins.getFirst();
        var profile = new Profile(PROFILE_SCHEMA, "external-stock", "vehicle-stock", stock.pluginId(), stock.versionId(),
            stock.manifestSha256(), Set.of(stage.getId(), production.getId()), Set.of("providerBaseUrl", "providerHost", "advertiserId"),
            Set.of("providerKey", "providerSecret", "notificationSecret"), Set.of("providerBaseUrl"), Set.of("advertiserId"), Set.of("provider.example.com"));
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setId("art-" + suffix); artifact.setServiceName("runtime");
        artifact.setArtifactType("DOCKER_IMAGE"); artifact.setArtifactRole("RUNTIME");
        artifact.setImageRepository("registry.example.com/synthetic-runtime"); artifact.setImageDigest("sha256:" + "a".repeat(64));
        artifact.setCapabilityManifestJson("{}"); artifact.setCapabilityManifestHash(codec.hash(codec.tree("{}")));
        artifact.setCreatedAt(now); artifact.setPromotedAt(now); artifacts.saveAndFlush(artifact);
        var topology = new ReviewedTopology(1, 1250, 1792, 0, 0, 2, 0, 1);
        var definition = new Definition("1", "Synthetic read-only client setup", "dev-openai-milvus",
            new PluginPin(template.getPluginId(), template.getId(), codec.hash(codec.tree(template.getManifestJson()))), pins,
            List.of(new ArtifactPin(artifact.getId(), reviewed.artifactFingerprint(artifact))), permissions,
            java.util.Arrays.stream(Operation.values()).filter(CustomerPermissionMatrix::supported)
                .collect(java.util.stream.Collectors.toSet()),
            List.of(new SettingRule(Setting.DEPLOYMENT_NAME, ValueType.TEXT, 100, Set.of()),
                new SettingRule(Setting.AI_STATE_STORAGE_MODE, ValueType.CHOICE, 32, Set.of("CUSTOMER_POSTGRES"))),
            List.of(new BindingSlot("vehicle-stock", BindingKind.EXTERNAL_SERVICE, Set.of(profile.id()), stock.pluginId(), null),
                new BindingSlot("ai-state-database", BindingKind.CUSTOMER_AI_STATE, Set.of("CUSTOMER_POSTGRES")),
                new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("synthetic-read-only-s3"),
                    "mkp-data-dealership-knowledge-v1", "documentStorageBindingRef")),
            Set.of(DocumentAudience.PUBLIC), EnumSet.allOf(Prerequisite.class), new ReviewedLimits("synthetic-not-capacity-evidence", 1,
                1, 1250, 1792, 1, 0, 2, 0, 1, 24, 24, 24), topology,
            "synthetic-policy", "synthetic-operating", "synthetic-identity", "synthetic-storage", List.of(),
            Map.of(AiStateStorageMode.CUSTOMER_POSTGRES, topology), List.of(profile));
        assertThat(validator.validate(definition)).isEmpty();
        String offerId = "offer-" + suffix;
        authenticate("synthetic-staff", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        entitlements.publish(new PublishOfferRequest(offerId, definition, "synthetic-local-review"));
        var invitation = entitlements.invite(offerId, new InviteRequest(customerId, userId, "synthetic-invitation",
            now.plusSeconds(3600), 0, "invite", "Local test only"));
        authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        var accepted = entitlements.accept(invitation.id(), new EntitlementCommand(1, "accept", "Local test only"));
        long handlesBefore = count("deployment_provider_resource_handles"), graphsBefore = count("deployment_managed_dependency_graphs");
        long bindingsBefore = count("deployment_external_integration_bindings"), releasesBefore = count("platform_deployment_releases");
        assertThatThrownBy(() -> customerBootstrap.bootstrap(offerId,
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request(
                stage.getId(), "Read-only client fixture", accepted.revision(), "missing-choice", null)))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessage("INVALID_CONTRACT");
        assertThatThrownBy(() -> customerBootstrap.bootstrap(offerId,
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request(
                stage.getId(), "Read-only client fixture", accepted.revision(), "wrong-choice", "MANAGED_POSTGRES")))
            .isInstanceOf(CustomerSelfServiceException.class).hasMessage("SETTING_FORBIDDEN");
        var result = customerBootstrap.bootstrap(offerId,
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request(
                stage.getId(), "Read-only client fixture", accepted.revision(), "bootstrap", "CUSTOMER_POSTGRES"));
        var draft = deploymentService.getActiveDraftForDeployment(result.deploymentId());
        assertThat(result.aiStateStorageMode()).isEqualTo("CUSTOMER_POSTGRES");
        assertThat(draft.providerConfig().path("aiStateStorageMode").asText()).isEqualTo("CUSTOMER_POSTGRES");
        assertThat(draft.marketplaceDatasetConfig().toString()).contains("MANAGED_EXTERNAL_INTEGRATION_", "dealership-external-stock-source")
            .doesNotContain("MANAGED_SIMULATOR", "MANAGED_RECEIVER");
        assertThat(draft.actionsConfig().path("actions")).hasSize(3).allSatisfy(action ->
            assertThat(action.path("accessMode").asText()).isEqualTo("READ"));
        assertThat(draft.behaviorConfig().path("executionExtensions")).isEmpty();
        assertReadOnlyCompilationAndOptionalDesk(result.deploymentId());
        assertThat(count("deployment_provider_resource_handles")).isEqualTo(handlesBefore);
        assertThat(count("deployment_managed_dependency_graphs")).isEqualTo(graphsBefore);
        assertThat(count("deployment_external_integration_bindings")).isEqualTo(bindingsBefore);
        assertThat(count("platform_deployment_releases")).isEqualTo(releasesBefore);
        assertThat(jdbc.queryForObject("select count(*) from platform_vectorization_plans where deployment_id = ?", Long.class, result.deploymentId())).isZero();
        verifyNoInteractions(coolify, railway, pinecone, qdrant, zilliz, desk);
    }

    private void assertReadOnlyCompilationAndOptionalDesk(String deploymentId) {
        var deployment = deployments.findById(deploymentId).orElseThrow();
        var documents = installs.findByDeploymentIdAndPluginId(deploymentId, "mkp-data-dealership-knowledge-v1").orElseThrow();
        // A symbolic document slot proves compilation, not actual storage access or public-content approval.
        installService.updateInstallForTrustedCallerWithoutDraftSync(deployment, documents.getId(),
            new UpdateDeploymentMarketplaceInstallRequest(null, "ENABLED",
                codec.tree("{\"documentStorageBindingRef\":\"slot:document-storage\",\"publicContentApprovalConfirmed\":true}"), codec.tree("{}")));
        var compiled = compiler.syncDeploymentDraftForTrustedCaller(deploymentId);
        assertThat(compiled.actionsConfig().path("actions")).extracting(value -> value.path("name").asText())
            .containsExactlyInAnyOrder("dealership_search_inventory", "dealership_get_vehicle", "dealership_compare_vehicles");
        assertThat(compiled.actionsConfig().path("actions")).allSatisfy(action -> {
            assertThat(action.path("accessMode").asText()).isEqualTo("READ");
            assertThat(action.at("/route/source-projection/require-successful-sync").asBoolean()).isTrue();
            assertThat(action.at("/route/source-projection/max-staleness-seconds").asInt()).isEqualTo(1800);
        });
        assertThat(compiled.marketplaceDatasetConfig().path("datasets")).anySatisfy(dataset -> {
            assertThat(dataset.path("entityType").asText()).isEqualTo("document");
            assertThat(dataset.at("/sourceConnector/connectorType").asText()).isEqualTo("S3_COMPATIBLE_OBJECT_STORAGE");
            assertThat(dataset.at("/sourceConnector/bindingRef").asText()).isEqualTo("slot:document-storage");
            assertThat(dataset.at("/sourceConnector/deleteSourceOnRemoval").asBoolean()).isFalse();
        });
        assertThat(compiled.routingConfig().toString()).doesNotContain("receiverActionUrl", "X-DEALERSHIP-INTERNAL-KEY",
            "dealership_request_callback", "dealership_request_test_drive");
        var provider = ((ObjectNode) compiled.providerConfig()).deepCopy();
        provider.put("openaiModel", "customer-selected-model");
        var prompts = ((ObjectNode) compiled.promptConfig()).deepCopy();
        prompts.put("ragMaxContextChars", 3000);
        deploymentService.updateDraftForTrustedCaller(compiled.id(), new UpdateDeploymentDraftRequest(
            null, null, null, provider, null, prompts, null, null, null, null));
        var edited = compiler.syncDeploymentDraftForTrustedCaller(deploymentId);
        assertThat(edited.providerConfig().path("openaiModel").asText()).isEqualTo("customer-selected-model");
        assertThat(edited.promptConfig().path("ragMaxContextChars").asInt()).isEqualTo(3000);
        installService.createInstallForTrustedCallerWithoutDraftSync(deployment,
            new CreateDeploymentMarketplaceInstallRequest(
                "mkp-action-conversation-handover", "1.1.0", codec.tree("{}"), codec.tree("{}")));
        installService.createInstallForTrustedCallerWithoutDraftSync(deployment,
            new CreateDeploymentMarketplaceInstallRequest(
                "mkp-integration-loomai-desk", "1.0.0",
                codec.tree("{\"bindingId\":\"${MANAGED_DESK_BINDING_ID}\",\"providerBaseUrl\":\"${MANAGED_DESK_PROVIDER_BASE_URL}\",\"operatorConsoleUrl\":\"${MANAGED_DESK_OPERATOR_CONSOLE_URL}\"}"),
                codec.tree("{\"providerApiKeySecretRef\":\"MANAGED_DESK_PROVIDER_API_KEY\",\"runtimeCallbackSigningSecretRef\":\"MANAGED_DESK_RUNTIME_CALLBACK_SIGNING_SECRET\",\"providerWebhookSigningSecretRef\":\"MANAGED_DESK_PROVIDER_WEBHOOK_SIGNING_SECRET\"}")));
        var withDesk = compiler.syncDeploymentDraftForTrustedCaller(deploymentId, Set.of("CONVERSATION_HANDOVER"));
        assertThat(DeskTargetConfigurationResolver.isTargetIndependent(withDesk.behaviorConfig())).isTrue();
        assertThat(withDesk.behaviorConfig().path("executionExtensions"))
            .extracting(value -> value.asText()).containsExactly("CONVERSATION_HANDOVER");
        assertThat(withDesk.behaviorConfig().at("/conversationHandover/actionId").asText())
            .isEqualTo("request_conversation_handover");
        assertThat(withDesk.behaviorConfig().at("/conversationHandover/actionBinding/implementation").asText())
            .isEqualTo("SERVER_OWNED_RUNTIME_ACTION");
        assertThat(withDesk.actionsConfig().path("actions")).hasSize(3).allSatisfy(action ->
            assertThat(action.path("accessMode").asText()).isEqualTo("READ"));
        assertThat(withDesk.actionsConfig().toString())
            .doesNotContain("dealership_request_callback", "dealership_request_test_drive");
        assertThat(deploymentService.listVersions(deploymentId)).isEmpty();
    }

    @ParameterizedTest @EnumSource(AiStateStorageMode.class)
    void acceptedCustomerCreatesOnlyAiConfigurationWithTheExplicitStorageChoice(AiStateStorageMode mode) {
        String suffix = UUID.randomUUID().toString();
        Instant now = Instant.now();
        String customerId = "cus-" + suffix, userId = "usr-" + suffix, email = suffix + "@customer.invalid";
        var customer = new PlatformCustomerEntity(); customer.setId(customerId); customer.setSlug(customerId);
        customer.setName("Synthetic external integration customer"); customer.setStatus("ACTIVE");
        customer.setCreatedAt(now); customer.setUpdatedAt(now); customers.saveAndFlush(customer);
        var user = new PlatformUserEntity(); user.setId(userId); user.setEmail(email); user.setDisplayName("Synthetic owner");
        user.setPasswordHash("not-a-real-password"); user.setRole("CUSTOMER_ADMIN"); user.setStatus("ACTIVE");
        user.setCustomerId(customerId); user.setCreatedAt(now); user.setUpdatedAt(now); users.saveAndFlush(user);
        var stage = target("stage-" + suffix, "external-test-stage", "staging");
        var production = target("prod-" + suffix, "external-test-prod", "production");
        for (var permission : List.of(new TargetPermission(stage.getId(), TargetIntent.CUSTOMER_STAGING, reviewed.profileFingerprint(stage)),
            new TargetPermission(production.getId(), TargetIntent.CUSTOMER_PRODUCTION, reviewed.profileFingerprint(production)))) {
            boolean staging = permission.intent() == TargetIntent.CUSTOMER_STAGING;
            placements.saveAndFlush(new CustomerPlacementEvidenceEntity("cpe-" + UUID.randomUUID(), permission.targetProfileId(),
                permission.intent(), permission.profileFingerprint(), staging ? "external-test-stage" : "external-test-prod",
                staging ? "192.0.2.10" : "192.0.2.20", "destination", now.minusSeconds(1), now.plusSeconds(300)));
        }
        var template = versions.findById("mkv-template-dealership-external-systems-managed-inbox-v1-0").orElseThrow();
        var pins = List.of("mkv-data-dealership-external-stock-v1-0", "mkv-action-dealership-external-discovery-v1-0",
            "mkv-action-dealership-external-engagement-v1-0", "mkv-data-dealership-knowledge-v1-0",
            "mkv-action-conversation-handover-v1-1", "mkv-integration-basic-loomai-inbox-v1-3").stream()
            .map(id -> versions.findById(id).orElseThrow())
            .map(version -> new PluginPin(version.getPluginId(), version.getId(), codec.hash(codec.tree(version.getManifestJson())))).toList();
        var stock = pins.getFirst(); var action = pins.get(2); var targetIds = Set.of(stage.getId(), production.getId());
        var profiles = List.of(new Profile(PROFILE_SCHEMA, "external-stock", "vehicle-stock", stock.pluginId(), stock.versionId(),
            stock.manifestSha256(), targetIds, Set.of("providerBaseUrl", "providerHost", "advertiserId"),
            Set.of("providerKey", "providerSecret", "notificationSecret"), Set.of("providerBaseUrl"), Set.of("advertiserId"), Set.of("provider.example.com")),
            new Profile(PROFILE_SCHEMA, "external-actions", "business-actions", action.pluginId(), action.versionId(), action.manifestSha256(),
                targetIds, Set.of("receiverActionUrl"), Set.of("receiverApiKey"), Set.of("receiverActionUrl"), Set.of(), Set.of("receiver.example.com")));
        var artifact = new DeploymentSourceArtifactEntity(); artifact.setId("art-" + suffix); artifact.setServiceName("runtime");
        artifact.setArtifactType("DOCKER_IMAGE"); artifact.setArtifactRole("RUNTIME");
        artifact.setImageRepository("registry.example.com/synthetic-runtime"); artifact.setImageDigest("sha256:" + "a".repeat(64));
        artifact.setCapabilityManifestJson("{}"); artifact.setCapabilityManifestHash(codec.hash(codec.tree("{}")));
        artifact.setCreatedAt(now); artifact.setPromotedAt(now); artifacts.saveAndFlush(artifact);
        var managed = new ReviewedTopology(1, 2000, 4096, 6144, 1, 2, 1, 1);
        var external = new ReviewedTopology(1, 1750, 3584, 4096, 0, 2, 1, 1);
        var definition = new Definition("1", "Synthetic reviewed dealership AI", "dev-openai-milvus",
            new PluginPin(template.getPluginId(), template.getId(), codec.hash(codec.tree(template.getManifestJson()))), pins,
            List.of(new ArtifactPin(artifact.getId(), reviewed.artifactFingerprint(artifact))),
            List.of(new TargetPermission(stage.getId(), TargetIntent.CUSTOMER_STAGING, reviewed.profileFingerprint(stage)),
                new TargetPermission(production.getId(), TargetIntent.CUSTOMER_PRODUCTION, reviewed.profileFingerprint(production))),
            EnumSet.allOf(Operation.class), List.of(new SettingRule(Setting.DEPLOYMENT_NAME, ValueType.TEXT, 100, Set.of()),
                new SettingRule(Setting.AI_STATE_STORAGE_MODE, ValueType.CHOICE, 100, Set.of("MANAGED_POSTGRES", "CUSTOMER_POSTGRES"))),
            List.of(new BindingSlot("vehicle-stock", BindingKind.EXTERNAL_SERVICE, Set.of("external-stock"), stock.pluginId(), null),
                new BindingSlot("business-actions", BindingKind.EXTERNAL_SERVICE, Set.of("external-actions"), action.pluginId(), null),
                new BindingSlot("ai-state-database", BindingKind.CUSTOMER_AI_STATE, Set.of("CUSTOMER_POSTGRES")),
                new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("synthetic-s3"),
                    "mkp-data-dealership-knowledge-v1", "documentStorageBindingRef")),
            Set.of(DocumentAudience.PUBLIC), EnumSet.allOf(Prerequisite.class), new ReviewedLimits("synthetic-not-capacity-evidence", 1, 1,
                2000, 4096, 6144, 1, 2, 1, 1, 24, 24, 24), managed,
            "synthetic-policy", "synthetic-operating", "synthetic-identity", "synthetic-storage", List.of(),
            Map.of(AiStateStorageMode.MANAGED_POSTGRES, managed, AiStateStorageMode.CUSTOMER_POSTGRES, external), profiles);
        String offerId = "offer-" + suffix;
        authenticate("synthetic-staff", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        entitlements.publish(new PublishOfferRequest(offerId, definition, "synthetic-review-not-live"));
        var invitation = entitlements.invite(offerId, new InviteRequest(customerId, userId, "synthetic-invitation",
            now.plusSeconds(3600), 0, "invite", "Local integration verification"));
        authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION");
        var accepted = entitlements.accept(invitation.id(), new EntitlementCommand(1, "accept", "Local verification"));
        long handlesBefore = count("deployment_provider_resource_handles"), graphsBefore = count("deployment_managed_dependency_graphs");
        var request = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentBootstrapContracts.Request(
            stage.getId(), "External dealership AI", accepted.revision(), "bootstrap", mode.name());
        var result = customerBootstrap.bootstrap(offerId, request);
        assertThat(customerBootstrap.bootstrap(offerId, request)).isEqualTo(result);
        assertThat(result.aiStateStorageMode()).isEqualTo(mode.name());
        var deployment = deployments.findById(result.deploymentId()).orElseThrow();
        assertThat(deployment.getCustomerId()).isEqualTo(customerId);
        assertThat(associations.findById(deployment.getId()).orElseThrow().getOfferId()).isEqualTo(offerId);
        var draft = deploymentService.getActiveDraftForDeploymentForTrustedCaller(deployment.getId());
        assertThat(draft.providerConfig().path("aiStateStorageMode").asText()).isEqualTo(mode.name());
        assertThat(draft.marketplaceDatasetConfig().toString()).contains("dealership-external-stock-source", "MANAGED_EXTERNAL_INTEGRATION_");
        assertThat(composition.evidence(deployment)).hasSize(2);
        assertThat(installs.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId())).hasSize(7);
        assertThat(count("deployment_provider_resource_handles")).isEqualTo(handlesBefore);
        assertThat(count("deployment_managed_dependency_graphs")).isEqualTo(graphsBefore);
        assertThat(jdbc.queryForObject("select count(*) from platform_deployment_releases where deployment_id = ?", Long.class, deployment.getId())).isZero();
        documentStorage.bind(offerId, deployment.getId(),
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDocumentStorageBindingContracts.BindRequest(
                accepted.revision(), "bind-documents", stage.getId(), "document-storage", "synthetic-s3",
                "https://storage.example.com", "test-region", "synthetic-documents", "approved/",
                "synthetic-access-key", "synthetic-secret-key", null, true, false));
        var knowledge = installs.findByDeploymentIdAndPluginId(deployment.getId(), "mkp-data-dealership-knowledge-v1").orElseThrow();
        var knowledgeConfig = (com.fasterxml.jackson.databind.node.ObjectNode) codec.tree(knowledge.getConfigJson()).deepCopy();
        knowledgeConfig.put("documentStorageBindingRef", "slot:document-storage").put("publicContentApprovalConfirmed", true);
        installService.updateInstall(deployment.getId(), knowledge.getId(),
            new com.ai.fabric.platform.backend.marketplace.model.UpdateDeploymentMarketplaceInstallRequest(
                null, "ENABLED", knowledgeConfig, codec.tree(knowledge.getSecretRefsJson())));
        var published = customerVersions.publish(offerId, deployment.getId(),
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishRequest(
                deployments.findById(deployment.getId()).orElseThrow().getActiveDraftId(), stage.getId(), "publish"));
        assertThatThrownBy(() -> externalResolver.resolveLocal(deployment.getId(), published.versionId(), stage.getId()))
            .hasMessageContaining("BINDING_REQUIRED");
        authenticate("synthetic-staff", PlatformRole.PLATFORM_ADMIN, "API_KEY");
        secrets.updateSecret("PLATFORM_ARTIFACT_SIGNING_KEY", "synthetic-local-integration-signing-key-not-live");
        var projections = bindConnections(offerId, deployment.getId(), published.versionId(), stage.getId(), profiles, email, accepted.revision(), "first");
        assertThat(projections).hasSize(2).allSatisfy(projection -> {
            assertThat(projection.scope().customerId()).isEqualTo(customerId);
            assertThat(projection.scope().tenantId()).isEqualTo(deployment.getTenantId());
            assertThat(projection.scope().targetProfileId()).isEqualTo(stage.getId());
            assertThat(projection.secretEnvironment().values()).allMatch(value -> value.startsWith("MANAGED_EXTERNAL_INTEGRATION_"));
        });
        assertThatThrownBy(() -> externalResolver.resolveLocal(deployment.getId(), published.versionId(), production.getId()))
            .hasMessageContaining("BINDING_REQUIRED");
        var candidate = customerVersions.publish(offerId, deployment.getId(),
            new com.ai.fabric.platform.backend.customerselfservice.model.CustomerDeploymentVersionContracts.PublishRequest(
                deployments.findById(deployment.getId()).orElseThrow().getActiveDraftId(), stage.getId(), "publish-candidate"));
        assertThat(candidate.versionId()).isNotEqualTo(published.versionId());
        assertThat(externalResolver.resolveLocal(deployment.getId(), candidate.versionId(), stage.getId()))
            .hasSize(2).allSatisfy(projection -> {
                assertThat(projection.scope().versionId()).isEqualTo(candidate.versionId());
                assertThat(projection.scope().targetProfileId()).isEqualTo(stage.getId());
                var previous = projections.stream().filter(value -> value.scope().bindingSlotId()
                    .equals(projection.scope().bindingSlotId())).findFirst().orElseThrow();
                assertThat(projection.secretEnvironment()).isEqualTo(previous.secretEnvironment());
            });
        var candidateStage = bindConnections(offerId, deployment.getId(), candidate.versionId(), stage.getId(), profiles, email, accepted.revision(), "candidate");
        var candidateProduction = bindConnections(offerId, deployment.getId(), candidate.versionId(), production.getId(), profiles, email, accepted.revision(), "production");
        assertThat(externalResolver.resolveLocal(deployment.getId(), published.versionId(), stage.getId())).isEqualTo(projections);
        var originalReferences = projections.stream().flatMap(projection -> projection.secretEnvironment().values().stream()).toList();
        var candidateReferences = candidateStage.stream().flatMap(projection -> projection.secretEnvironment().values().stream()).toList();
        var productionReferences = candidateProduction.stream().flatMap(projection -> projection.secretEnvironment().values().stream()).toList();
        assertThat(candidateReferences).doesNotContainAnyElementsOf(originalReferences);
        assertThat(productionReferences).doesNotContainAnyElementsOf(originalReferences).doesNotContainAnyElementsOf(candidateReferences);
        assertThat(jdbc.queryForList("select result_json from platform_customer_entitlement_commands where entitlement_id = ?", String.class, accepted.id()))
            .allSatisfy(json -> assertThat(json).doesNotContain("synthetic-provider-key", "synthetic-provider-secret", "synthetic-notification-secret", "synthetic-receiver-secret"));
        assertThat(count("deployment_managed_dependency_graphs")).isEqualTo(graphsBefore);
        assertThat(jdbc.queryForObject("select count(*) from platform_deployment_releases where deployment_id = ?", Long.class, deployment.getId())).isZero();
        verifyNoInteractions(coolify, railway, pinecone, qdrant, zilliz);
    }

    private List<Projection> bindConnections(String offerId, String deploymentId, String versionId, String targetId,
        List<Profile> profiles, String email, long revision, String credentialSuffix) {
        for (Profile profile : profiles) {
            boolean stock = "vehicle-stock".equals(profile.bindingSlotId());
            var config = codec.tree(stock
                ? "{\"providerBaseUrl\":\"https://provider.example.com\",\"providerHost\":\"provider.example.com\",\"advertiserId\":\"synthetic-advertiser\"}"
                : "{\"receiverActionUrl\":\"https://receiver.example.com/api/actions\"}");
            String key = versionId + ":" + targetId + ":" + profile.id();
            authenticate("synthetic-staff", PlatformRole.PLATFORM_ADMIN, "API_KEY");
            var approval = externalBindings.approve(offerId, deploymentId, new ApprovalRequest(versionId, targetId,
                profile.bindingSlotId(), profile.id(), config, codec.tree("{\"advertiserId\":\"synthetic-advertiser\"}"),
                "synthetic-review", "approve:" + key));
            authenticate(email, PlatformRole.CUSTOMER_ADMIN, "SESSION");
            var credentials = codec.tree(stock
                ? "{\"providerKey\":\"synthetic-provider-key-" + credentialSuffix + "\",\"providerSecret\":\"synthetic-provider-secret-" + credentialSuffix + "\",\"notificationSecret\":\"synthetic-notification-secret-" + credentialSuffix + "\"}"
                : "{\"receiverApiKey\":\"synthetic-receiver-secret-" + credentialSuffix + "\"}");
            var bind = new BindRequest(versionId, targetId, profile.bindingSlotId(), profile.id(), approval.approvalId(), revision, "bind:" + key, credentials);
            var bound = externalBindings.bind(offerId, deploymentId, bind);
            assertThat(externalBindings.bind(offerId, deploymentId, bind)).isEqualTo(bound);
            assertThat(bound.status()).isEqualTo("CONFIGURED_LOCAL_CREDENTIALS");
        }
        return externalResolver.resolveLocal(deploymentId, versionId, targetId);
    }

    private DeploymentTargetProfileEntity target(String id, String serverId, String environment) {
        var target = new DeploymentTargetProfileEntity(); target.setId(id); target.setName(id); target.setActive(true);
        target.setProviderType(DeploymentProviderType.COOLIFY); target.setSourceStrategy("IMAGE_SOURCE"); target.setEnvironmentName(environment);
        target.setProviderConfigJson("{\"serverUuid\":\"" + serverId + "\",\"destinationUuid\":\"destination\"}");
        target.setResourceDefaultsJson("{}"); target.setNetworkPolicyJson("{}");
        target.setCreatedAt(Instant.now()); target.setUpdatedAt(Instant.now()); return targets.saveAndFlush(target);
    }

    private void authenticate(String actor, PlatformRole role, String mode) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal(actor, role, actor, mode), null, List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @Test void realExternalTemplateStartsUnboundThenCompilesOnlyReviewedSymbolicConnections() {
        var principal = new PlatformPrincipal("admin@test.platform", PlatformRole.PLATFORM_ADMIN, "Synthetic operator", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null,
            List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))));
        long handlesBefore = count("deployment_provider_resource_handles");
        long graphsBefore = count("deployment_managed_dependency_graphs");
        long bindingsBefore = count("deployment_external_integration_bindings");
        long releasesBefore = count("platform_deployment_releases");
        var created = templates.bootstrapConfiguration("mkp-template-dealership-external-systems-v1",
            new CreateMarketplaceTemplateBootstrapRequest("1.0.0", "External bootstrap verification", "staging",
                "dev-openai-milvus", null, null, null));
        var deployment = deployments.findById(created.id()).orElseThrow();
        var initial = installs.findByDeploymentIdOrderByUpdatedAtDesc(created.id());
        var stock = initial.stream().filter(value -> "mkp-data-dealership-external-stock-v1".equals(value.getPluginId())).findFirst().orElseThrow();
        var actions = initial.stream().filter(value -> "mkp-action-dealership-external-engagement-v1".equals(value.getPluginId())).findFirst().orElseThrow();
        assertThat(stock.getStatus()).isEqualTo("DISABLED");
        assertThat(actions.getStatus()).isEqualTo("DISABLED");
        assertThat(stock.getConfigJson()).isEqualTo("{}");
        var initialDraft = deploymentService.getActiveDraftForDeployment(created.id());
        assertThat(initialDraft.behaviorConfig().path("marketplaceRequirements").path("unresolvedRequiredPluginRefs"))
            .extracting(value -> value.asText()).contains("mkp-data-dealership-external-stock-v1@1.0.0",
                "mkp-action-dealership-external-discovery-v1@1.0.0");
        assertThatThrownBy(() -> compiler.syncDeploymentDraftForTrustedCaller(created.id()))
            .hasMessageContaining("ROUTE_SOURCE_PROJECTION_SOURCE_UNKNOWN");
        var stockVersion = versions.findById(stock.getPluginVersionId()).orElseThrow();
        var actionVersion = versions.findById(actions.getPluginVersionId()).orElseThrow();
        var targets = List.of(new TargetPermission("dtp-coolify-staging-behavior", TargetIntent.CUSTOMER_STAGING, "not-placement-evidence"),
            new TargetPermission("dtp-coolify-production-behavior", TargetIntent.CUSTOMER_PRODUCTION, "not-placement-evidence"));
        var targetIds = Set.of(targets.getFirst().targetProfileId(), targets.getLast().targetProfileId());
        var stockProfile = new Profile(PROFILE_SCHEMA, "external-stock", "vehicle-stock", stock.getPluginId(), stockVersion.getId(),
            codec.hash(codec.tree(stockVersion.getManifestJson())), targetIds, Set.of("providerBaseUrl", "providerHost", "advertiserId"),
            Set.of("providerKey", "providerSecret", "notificationSecret"), Set.of("providerBaseUrl"), Set.of("advertiserId"), Set.of("provider.example.com"));
        var actionProfile = new Profile(PROFILE_SCHEMA, "external-actions", "business-actions", actions.getPluginId(), actionVersion.getId(),
            codec.hash(codec.tree(actionVersion.getManifestJson())), targetIds, Set.of("receiverActionUrl"), Set.of("receiverApiKey"),
            Set.of("receiverActionUrl"), Set.of(), Set.of("receiver.example.com"));
        var definition = new Definition("1", "Symbolic compiler verification only", "dev-openai-milvus",
            new PluginPin("mkp-template-dealership-external-systems-v1", "mkv-template-dealership-external-systems-v1-0", "unused-test-pin"),
            List.of(new PluginPin(stock.getPluginId(), stockVersion.getId(), stockProfile.manifestSha256()),
                new PluginPin(actions.getPluginId(), actionVersion.getId(), actionProfile.manifestSha256())),
            List.of(), targets, Set.of(Operation.READ), List.of(), List.of(
                new BindingSlot(stockProfile.bindingSlotId(), BindingKind.EXTERNAL_SERVICE, Set.of(stockProfile.id()), stock.getPluginId(), null),
                new BindingSlot(actionProfile.bindingSlotId(), BindingKind.EXTERNAL_SERVICE, Set.of(actionProfile.id()), actions.getPluginId(), null)),
            Set.of(), Set.of(), null, null, "not-admission", "not-admission", "not-admission", "not-admission",
            List.of(), java.util.Map.of(), List.of(stockProfile, actionProfile));
        String offerId = "offer-external-bootstrap-verification";
        transactions.executeWithoutResult(status -> {
            offers.saveAndFlush(new CustomerOfferEntity(offerId, codec.write(definition), codec.hash(definition), "local-test", "test", Instant.now()));
            associations.saveAndFlush(new CustomerOfferDeploymentEntity(created.id(), deployment.getCustomerId(), deployment.getTenantId(),
                offerId, targets.getFirst().targetProfileId(), codec.hash(definition), 1, "local-bootstrap", Instant.now()));
            initializer.initialize(deployment);
            var draft = compiler.syncDeploymentDraftForTrustedTemplateBootstrap(created.id());
            assertThat(draft.marketplaceDatasetConfig().toString()).contains("dealership-external-stock-source", "MANAGED_EXTERNAL_INTEGRATION_")
                .doesNotContain("https://provider.example.com", "MANAGED_SIMULATOR", "MANAGED_RECEIVER");
            assertThat(draft.actionsConfig().toString()).contains("dealership_search_inventory", "dealership_request_test_drive");
            assertThat(composition.evidence(deployment)).hasSize(2);
        });
        var configured = installs.findByDeploymentIdOrderByUpdatedAtDesc(created.id());
        configured.stream().filter(value -> Set.of(stock.getPluginId(), actions.getPluginId()).contains(value.getPluginId()))
            .forEach(value -> assertThat(value.getStatus()).isEqualTo("ENABLED"));
        assertThat(count("deployment_provider_resource_handles")).isEqualTo(handlesBefore);
        assertThat(count("deployment_managed_dependency_graphs")).isEqualTo(graphsBefore);
        assertThat(count("deployment_external_integration_bindings")).isEqualTo(bindingsBefore);
        assertThat(count("platform_deployment_releases")).isEqualTo(releasesBefore);
        verifyNoInteractions(coolify, railway, pinecone, qdrant, zilliz);
    }

    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
}
