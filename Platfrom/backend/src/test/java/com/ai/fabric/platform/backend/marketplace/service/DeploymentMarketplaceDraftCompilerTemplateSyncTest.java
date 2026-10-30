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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
    void specialistTemplateBootstrapsDefaultsButKeepsCustomerEditsAndDisabling() throws Exception {
        var version = catalog.requirePluginVersionEntityById(TEMPLATE_ID + "-version-1");
        ObjectNode manifest = (ObjectNode) mapper.readTree(version.getManifestJson());
        ObjectNode templateBehavior = (ObjectNode) manifest.at("/contributions/template/deploymentBehavior");
        templateBehavior.withArray("defaultExecutionExtensions").add("SPECIALIST_ASSISTANCE");
        templateBehavior.set("specialistAssistance", com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceFixtures.behavior(mapper)
            .path("specialistAssistance"));
        version.setManifestJson(manifest.toString());

        var bootstrapped = compiler.syncDeploymentDraftForTrustedTemplateBootstrap(DEPLOYMENT_ID);
        assertThat(bootstrapped.behaviorConfig().at("/specialistAssistance/enabled").asBoolean()).isTrue();
        assertThat(bootstrapped.behaviorConfig().path("executionExtensions")).extracting(JsonNode::asText)
            .contains("SPECIALIST_ASSISTANCE");
        ((ObjectNode) draft.behaviorConfig().at("/specialistAssistance/areas/0")).put("instructions", "Our own support instructions.");
        var customized = sync(true);
        assertThat(customized.behaviorConfig().at("/specialistAssistance/areas/0/instructions").asText())
            .isEqualTo("Our own support instructions.");
        ((ObjectNode) draft.behaviorConfig().path("specialistAssistance")).put("enabled", false);
        ((ObjectNode) draft.behaviorConfig()).putArray("executionExtensions");
        assertThat(sync(true).behaviorConfig().path("executionExtensions")).isEmpty();
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

    @Test
    void freshHandoverActivationCompilesOneCompleteValidatedUpdateAndPreservesOtherExtensions() {
        installHandoverPlugins();
        ((ObjectNode) draft.behaviorConfig()).putArray("executionExtensions").add("HUMAN_REVIEW");
        assertThat(draft.behaviorConfig().has("conversationHandover")).isFalse();

        DeploymentDraftResponse compiled = compiler.syncDeploymentDraftForTrustedCaller(
            DEPLOYMENT_ID, Set.of("CONVERSATION_HANDOVER"));

        var contract = new DeploymentBehaviorCatalogService(mapper).validate(compiled.behaviorConfig(), "CONVERSATIONAL");
        assertThat(contract.valid()).as(contract.message()).isTrue();
        assertThat(compiled.behaviorConfig().path("executionExtensions"))
            .extracting(JsonNode::asText).containsExactly("HUMAN_REVIEW", "CONVERSATION_HANDOVER");
        assertThat(compiled.behaviorConfig().path("conversationHandover").path("actionBinding")
            .path("implementation").asText()).isEqualTo("SERVER_OWNED_RUNTIME_ACTION");
        assertThat(compiled.behaviorConfig().path("conversationHandover").path("activeIdleAutoResume").asBoolean()).isFalse();
        verify(deployments, times(1)).updateDraftForTrustedCaller(eq(DRAFT_ID), any());
    }

    @Test
    void requestingHandoverWithoutAnApprovedChannelStillFailsBeforeWritingTheDraft() {
        assertThatThrownBy(() -> compiler.syncDeploymentDraftForTrustedCaller(DEPLOYMENT_ID, Set.of("CONVERSATION_HANDOVER")))
            .hasMessageContaining("requires one enabled reviewed conversation-channel integration");
        verify(deployments, never()).updateDraftForTrustedCaller(any(), any());
    }

    @Test
    void requestingHandoverWithoutTheCanonicalActionStillFailsBeforeWritingTheDraft() {
        installHandoverPlugins();
        installs.removeIf(install -> "mkp-action-conversation-handover".equals(install.getPluginId()));
        assertThatThrownBy(() -> compiler.syncDeploymentDraftForTrustedCaller(DEPLOYMENT_ID, Set.of("CONVERSATION_HANDOVER")))
            .hasMessageContaining("requires the canonical request_conversation_handover action plugin");
        verify(deployments, never()).updateDraftForTrustedCaller(any(), any());
    }

    private void installHandoverPlugins() {
        installs.clear();
        registerPlugin("mkp-action-conversation-handover", "ACTION", """
            {
              "schemaVersion": 1, "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"}, "permissions": {"contributesActions": true},
              "contributions": {"actions": [{
                "actionId": "request_conversation_handover", "description": "Request human support.",
                "readOnly": false, "confirmationRequired": true, "anonymousAllowed": true,
                "terminal": true, "adapterType": "conversation-control", "operation": "REQUEST_HANDOVER"
              }]}
            }
            """);
        registerPlugin("mkp-integration-loomai-desk", "INTEGRATION", """
            {
              "schemaVersion": 1, "pluginType": "INTEGRATION",
              "compatibility": {"requiredCapabilities": ["integrations"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesIntegrations": true, "requiresExternalHttpExecution": true, "requiresDeploymentSecrets": true},
              "installForm": [
                {"id": "providerBaseUrl", "label": "Provider URL", "type": "url", "required": true},
                {"id": "providerApiKeySecretRef", "label": "Provider key", "type": "secretRef", "required": true},
                {"id": "runtimeCallbackSigningSecretRef", "label": "Runtime signature", "type": "secretRef", "required": true},
                {"id": "providerWebhookSigningSecretRef", "label": "Provider signature", "type": "secretRef", "required": true}
              ],
              "contributions": {"conversationChannel": {
                "contractVersion": "LOOMAI_CONVERSATION_CHANNEL_V1", "bindingId": "desk-test-binding",
                "adapterImplementationId": "loomai-basic-inbox", "adapterVersion": "1",
                "compatibleBehaviorTypes": ["CONVERSATIONAL"],
                "capabilities": ["DISPATCH", "CLAIM", "INBOUND_MESSAGE", "OUTBOUND_MESSAGE", "RETURN_TO_AI", "CLOSE", "AVAILABILITY"],
                "providerBaseUrlField": "providerBaseUrl", "providerApiKeySecretRefField": "providerApiKeySecretRef",
                "runtimeCallbackSigningSecretRefField": "runtimeCallbackSigningSecretRef",
                "providerWebhookSigningSecretRefField": "providerWebhookSigningSecretRef",
                "requiredRuntimeCapabilityIds": ["conversation-control", "conversation-channel-outbox", "conversation-event-stream"],
                "requiredMigrationIds": ["loomai-conversation-handover-v1"], "verificationPackIds": ["conversation-handover-v1"],
                "configuration": {
                  "modelInitiatedPolicy": "REQUIRE_USER_CONFIRMATION", "userCanCancelPending": true,
                  "userCanResumeAi": true, "activeIdleAutoResume": false, "queueTimeoutSeconds": 900,
                  "queueTimeoutOutcome": "EXPIRED_REQUIRES_EXPLICIT_RECOVERY", "transcriptProjection": "SAFE_TEXT_AND_ATTACHMENT_REFS",
                  "intake": {"anonymousAllowed": true, "fields": []}
                }
              }}
            }
            """);
        var channel = installs.stream().filter(install -> "mkp-integration-loomai-desk".equals(install.getPluginId())).findFirst().orElseThrow();
        channel.setConfigJson("{\"providerBaseUrl\":\"https://desk.example.test\"}");
        channel.setSecretRefsJson("""
            {"providerApiKeySecretRef":"DESK_PROVIDER_KEY","runtimeCallbackSigningSecretRef":"DESK_RUNTIME_SIGNATURE","providerWebhookSigningSecretRef":"DESK_PROVIDER_SIGNATURE"}
            """);
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
