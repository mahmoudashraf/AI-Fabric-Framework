package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentEntityConfigMigrationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.externalintegration.ExternalIntegrationCompositionService;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentMarketplaceDraftCompilerTemplateSyncTest {
    private static final String DEPLOYMENT_ID = "deployment-template-sync";
    private static final String DRAFT_ID = "draft-template-sync";
    private static final String TEMPLATE_ID = "mkp-template-record-workspace";
    private static final String ACTION_ID = "mkp-action-record-read";
    private static final String ACTION_REF = ACTION_ID + "@1.0.0";
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final DeploymentDraftValidationService validation = mock(DeploymentDraftValidationService.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentMarketplacePluginInstallRepository installRepository =
        mock(DeploymentMarketplacePluginInstallRepository.class);
    private final MarketplaceCatalogService catalog = mock(MarketplaceCatalogService.class);
    private final MarketplaceEntitlementService entitlements = mock(MarketplaceEntitlementService.class);
    private final ExternalIntegrationCompositionService integrations = mock(ExternalIntegrationCompositionService.class);
    private final List<DeploymentMarketplacePluginInstallEntity> installs = new ArrayList<>();
    private DeploymentMarketplaceDraftCompilerService compiler;
    private DeploymentDraftResponse draft;

    @BeforeEach
    void setUp() {
        compiler = new DeploymentMarketplaceDraftCompilerService(
            deployments, validation, mock(DeploymentEntityConfigMigrationService.class),
            deploymentRepository, installRepository, catalog, new MarketplaceManifestService(mapper),
            entitlements, mock(MarketplaceDatasetHandleResolver.class),
            mock(PlatformManagedInferenceEndpointService.class),
            mock(PlatformManagedInferenceServiceService.class), mapper, integrations
        );
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId(DEPLOYMENT_ID);
        when(deploymentRepository.findById(DEPLOYMENT_ID)).thenReturn(Optional.of(deployment));
        when(installRepository.findByDeploymentIdOrderByUpdatedAtDesc(DEPLOYMENT_ID))
            .thenAnswer(invocation -> List.copyOf(installs));
        when(integrations.profiles(deployment)).thenReturn(List.of());
        when(integrations.compileInput(any(), any())).thenAnswer(invocation -> invocation.getArgument(1));
        when(entitlements.evaluate(any(), any()))
            .thenReturn(new MarketplaceEntitlementService.EntitlementEvaluation(null, true, List.of()));
        when(validation.validate(any())).thenReturn(
            new DraftValidationResponse(DRAFT_ID, DEPLOYMENT_ID, true, 0, 0, NOW, List.of())
        );

        registerPlugin(TEMPLATE_ID, "TEMPLATE", """
            {
              "schemaVersion": 1,
              "pluginType": "TEMPLATE",
              "compatibility": {"requiredCapabilities": ["templates"]},
              "pricing": {"pricingModel": "FREE"},
              "contributions": {"template": {
                "templateId": "custom-start-from-scratch",
                "requiredPluginRefs": ["mkp-action-record-read@1.0.0"],
                "entityConfig": {"ai-entities": {
                  "record": {
                    "indexing": {"enabled": true, "max-characters": 8000},
                    "searchable-fields": [{"name": "title", "destinations": ["SEMANTIC_SEARCH"]}],
                    "metadata-fields": [{
                      "name": "tenantId", "data-type": "ID", "required": true,
                      "destinations": ["VECTOR_METADATA"]
                    }]
                  },
                  "optional-record": {
                    "indexing": {"enabled": true},
                    "searchable-fields": [{"name": "title", "destinations": ["SEMANTIC_SEARCH"]}],
                    "metadata-fields": [{
                      "name": "tenantId", "data-type": "ID", "required": true,
                      "destinations": ["VECTOR_METADATA"]
                    }]
                  }
                }},
                "deploymentBehavior": {
                  "type": "CONVERSATIONAL", "contractVersion": 1,
                  "defaultExecutionExtensions": ["GOVERNED_RESOLVER"],
                  "allowedChannelBindings": ["BACKEND_API", "MAX_MODE"]
                },
                "shell": {
                  "greeting": {"title": "Records", "message": "Explore your records."},
                  "enabledModuleIds": ["record-summary"],
                  "enabledCardIds": ["record-card"],
                  "starterPrompts": [
                    {"id": "find-record", "label": "Find a record", "query": "Find relevant records."}
                  ],
                  "defaultConversationMode": "conversational"
                }
              }}
            }
            """);
        registerPlugin(ACTION_ID, "ACTION", """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "contributions": {"actions": [{
                "actionId": "lookup_record", "readOnly": true,
                "adapterType": "connector-http", "route": {"path": "/records"}
              }]}
            }
            """);
        draft = response(mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(),
            (ObjectNode) new DeploymentBehaviorCatalogService(mapper).defaultConfig("CONVERSATIONAL"));
        when(deployments.getActiveDraftForDeployment(DEPLOYMENT_ID)).thenAnswer(invocation -> draft);
        when(deployments.getActiveDraftForDeploymentForTrustedCaller(DEPLOYMENT_ID)).thenAnswer(invocation -> draft);
        when(deployments.updateDraft(eq(DRAFT_ID), any())).thenAnswer(invocation -> persist(invocation.getArgument(1)));
        when(deployments.updateDraftForTrustedCaller(eq(DRAFT_ID), any()))
            .thenAnswer(invocation -> persist(invocation.getArgument(1)));
    }

    @Test
    void initialBootstrapComposesTemplateEntityShellExecutionDefaultsAndRequiredPluginRefs() {
        DeploymentDraftResponse bootstrapped = compiler.syncDeploymentDraftForTrustedTemplateBootstrap(DEPLOYMENT_ID);

        assertThat(bootstrapped.entityConfig().path("ai-entities").path("record")
            .path("indexing").path("max-characters").asInt()).isEqualTo(8000);
        assertThat(bootstrapped.entityConfig().path("ai-entities").path("record")
            .path("marketplacePluginId").asText()).isEqualTo(TEMPLATE_ID);
        assertThat(bootstrapped.entityConfig().path("ai-entities").path("optional-record").isObject()).isTrue();
        assertThat(bootstrapped.shellConfig().path("greeting").path("title").asText()).isEqualTo("Records");
        assertThat(bootstrapped.shellConfig().path("modules")).hasSize(1);
        assertThat(bootstrapped.shellConfig().path("cards")).hasSize(1);
        assertThat(bootstrapped.shellConfig().path("starterPrompts").path(0).path("query").asText())
            .isEqualTo("Find relevant records.");
        assertThat(bootstrapped.shellConfig().path("defaultConversationMode").asText()).isEqualTo("conversational");
        assertThat(bootstrapped.behaviorConfig().path("executionExtensions"))
            .extracting(JsonNode::asText).containsExactly("GOVERNED_RESOLVER");
        assertThat(bootstrapped.behaviorConfig().path("channelBindings"))
            .extracting(JsonNode::asText).containsExactly("BACKEND_API", "MAX_MODE");
        assertThat(bootstrapped.behaviorConfig().path("marketplaceRequirements").path("requiredPluginRefs"))
            .extracting(JsonNode::asText).containsExactly(ACTION_REF);
        assertThat(bootstrapped.behaviorConfig().path("marketplaceRequirements").path("unresolvedRequiredPluginRefs"))
            .isEmpty();
        assertThat(bootstrapped.actionsConfig().path("actions"))
            .extracting(action -> action.path("name").asText()).containsExactly("lookup_record");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postBootstrapSyncPreservesCustomerEntityShellAndExecutionChoices(boolean trustedCaller) {
        compiler.syncDeploymentDraftForTrustedTemplateBootstrap(DEPLOYMENT_ID);
        ObjectNode entities = (ObjectNode) draft.entityConfig().path("ai-entities");
        ((ObjectNode) entities.path("record").path("indexing")).put("max-characters", 2400);
        entities.remove("optional-record");
        ((ArrayNode) entities.path("record").path("searchable-fields")).removeAll()
            .addObject().put("name", "summary").putArray("destinations").add("SEMANTIC_SEARCH");
        ObjectNode shell = (ObjectNode) draft.shellConfig();
        ((ObjectNode) shell.path("greeting")).put("title", "Customer workspace")
            .put("message", "Welcome to our records.");
        ((ArrayNode) shell.path("modules")).removeAll();
        ((ArrayNode) shell.path("cards")).removeAll();
        ((ObjectNode) shell.path("starterPrompts").path(0)).put("label", "My records")
            .put("query", "Find records for my team.");
        shell.put("defaultConversationMode", "executor");
        ObjectNode behavior = (ObjectNode) draft.behaviorConfig();
        behavior.putArray("executionExtensions").add("HUMAN_REVIEW");
        behavior.putArray("channelBindings").add("BACKEND_API");

        JsonNode expectedEntity = withoutMarketplaceProvenance(draft.entityConfig());
        JsonNode expectedShell = withoutMarketplaceProvenance(shell);
        JsonNode expectedBehavior = behavior.deepCopy();
        ((ObjectNode) expectedBehavior).remove("marketplaceRequirements");

        DeploymentDraftResponse synced = sync(trustedCaller);
        assertSoftly(softly -> {
            softly.assertThat(withoutMarketplaceProvenance(synced.entityConfig()))
                .as("customer entity configuration after sync").isEqualTo(expectedEntity);
            softly.assertThat(withoutMarketplaceProvenance(synced.shellConfig()))
                .as("customer shell configuration after sync").isEqualTo(expectedShell);
            softly.assertThat(synced.behaviorConfig())
                .as("customer execution and channel choices after sync").isEqualTo(expectedBehavior);
        });
        DeploymentDraftResponse syncedAgain = sync(trustedCaller);
        assertThat(withoutMarketplaceProvenance(syncedAgain.entityConfig())).isEqualTo(expectedEntity);
        assertThat(withoutMarketplaceProvenance(syncedAgain.shellConfig())).isEqualTo(expectedShell);
        assertThat(syncedAgain.behaviorConfig()).isEqualTo(expectedBehavior);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void postBootstrapSyncDoesNotRestoreTemplateRequirementsAfterRequiredPluginRemoval(boolean trustedCaller) {
        compiler.syncDeploymentDraftForTrustedTemplateBootstrap(DEPLOYMENT_ID);
        assertThat(draft.behaviorConfig().path("marketplaceRequirements").path("requiredPluginRefs"))
            .extracting(JsonNode::asText).containsExactly(ACTION_REF);
        installs.removeIf(install -> ACTION_ID.equals(install.getPluginId()));
        ((ObjectNode) draft.behaviorConfig()).putArray("executionExtensions");

        DeploymentDraftResponse synced = sync(trustedCaller);

        assertThat(synced.behaviorConfig().has("marketplaceRequirements")).isFalse();
        assertThat(synced.behaviorConfig().path("executionExtensions")).isEmpty();
        assertThat(synced.actionsConfig().path("actions")).isEmpty();
        assertThat(installs).extracting(DeploymentMarketplacePluginInstallEntity::getPluginId)
            .containsExactly(TEMPLATE_ID);
        assertThat(sync(trustedCaller).behaviorConfig().has("marketplaceRequirements")).isFalse();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void templateUninstallBeforeFirstSyncPreservesCustomerConfiguration(boolean trustedCaller) {
        compiler.syncDeploymentDraftForTrustedTemplateBootstrap(DEPLOYMENT_ID);
        ((ObjectNode) draft.entityConfig().path("ai-entities").path("record").path("indexing"))
            .put("max-characters", 1600);
        ((ObjectNode) draft.shellConfig().path("greeting")).put("title", "Independent workspace");
        ((ObjectNode) draft.shellConfig().path("starterPrompts").path(0))
            .put("query", "Find my team's records.");
        ((ObjectNode) draft.behaviorConfig()).putArray("executionExtensions").add("HUMAN_REVIEW");
        JsonNode expectedEntity = withoutMarketplaceProvenance(draft.entityConfig());
        JsonNode expectedShell = withoutMarketplaceProvenance(draft.shellConfig());
        ObjectNode expectedBehavior = draft.behaviorConfig().deepCopy();
        expectedBehavior.remove("marketplaceRequirements");
        installs.removeIf(install -> TEMPLATE_ID.equals(install.getPluginId()));

        DeploymentDraftResponse synced = sync(trustedCaller);

        assertSoftly(softly -> {
            softly.assertThat(synced.entityConfig())
                .as("template entities become customer configuration after uninstall").isEqualTo(expectedEntity);
            softly.assertThat(synced.shellConfig())
                .as("greeting, prompts, modules and cards survive template uninstall").isEqualTo(expectedShell);
            softly.assertThat(synced.behaviorConfig())
                .as("customer execution choices survive without template requirements").isEqualTo(expectedBehavior);
        });
        assertThat(synced.actionsConfig().path("actions"))
            .extracting(action -> action.path("name").asText()).containsExactly("lookup_record");
        assertThat(installs).extracting(DeploymentMarketplacePluginInstallEntity::getPluginId)
            .containsExactly(ACTION_ID);
        DeploymentDraftResponse syncedAgain = sync(trustedCaller);
        assertThat(syncedAgain.entityConfig()).isEqualTo(expectedEntity);
        assertThat(syncedAgain.shellConfig()).isEqualTo(expectedShell);
        assertThat(syncedAgain.behaviorConfig()).isEqualTo(expectedBehavior);
    }

    private DeploymentDraftResponse sync(boolean trustedCaller) {
        return trustedCaller ? compiler.syncDeploymentDraftForTrustedCaller(DEPLOYMENT_ID)
            : compiler.syncDeploymentDraft(DEPLOYMENT_ID);
    }

    private void registerPlugin(String pluginId, String pluginType, String manifest) {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId(pluginId);
        plugin.setPluginType(pluginType);
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId(pluginId + "-version-1");
        version.setPluginId(pluginId);
        version.setVersion("1.0.0");
        version.setManifestJson(manifest);
        when(catalog.requirePluginEntity(pluginId)).thenReturn(plugin);
        when(catalog.requirePluginVersionEntityById(version.getId())).thenReturn(version);
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId(pluginId + "-install");
        install.setDeploymentId(DEPLOYMENT_ID);
        install.setPluginId(pluginId);
        install.setPluginVersionId(version.getId());
        install.setStatus("ENABLED");
        install.setConfigJson("{}");
        install.setSecretRefsJson("{}");
        installs.add(install);
    }

    private DeploymentDraftResponse persist(UpdateDeploymentDraftRequest request) {
        draft = response(request.actionsConfig(), request.entityConfig(), request.shellConfig(), request.behaviorConfig());
        return draft;
    }

    private DeploymentDraftResponse response(JsonNode actions, JsonNode entity, JsonNode shell, JsonNode behavior) {
        return new DeploymentDraftResponse(
            DRAFT_ID, DEPLOYMENT_ID, 1, "DRAFT", actions, entity, mapper.createObjectNode(),
            mapper.createObjectNode(), mapper.createObjectNode(), mapper.createObjectNode(),
            mapper.createObjectNode(), shell, mapper.createObjectNode(), NOW, NOW,
            EntityConfigContractService.CONTRACT_VERSION_V04, behavior
        );
    }

    private JsonNode withoutMarketplaceProvenance(JsonNode config) {
        JsonNode copy = config.deepCopy();
        removeMarketplaceProvenance(copy);
        return copy;
    }

    private void removeMarketplaceProvenance(JsonNode config) {
        // Assert customer configuration independently of how bootstrap provenance is retained.
        if (config instanceof ObjectNode object) {
            object.remove(List.of("marketplaceManaged", "marketplacePluginId",
                "marketplaceInstallId", "marketplacePluginVersion"));
        }
        config.forEach(this::removeMarketplaceProvenance);
    }
}
