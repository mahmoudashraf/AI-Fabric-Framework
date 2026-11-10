package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceCatalogSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceDomainType;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationSummary;
import com.ai.fabric.platform.backend.aiworkspace.model.UpdateAIWorkspaceInstallationRequest;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAiBehaviourProfiles;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.model.CustomerPluginLibraryContracts;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.CustomerPluginLibraryService;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceManifestService;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real resource adapter and JSON changes; only existing persistence/access services are fixtures. */
class AuthoringResourceServiceTest {
    private static final String OWNER = "customer-owner";
    private static final String BRAIN = "brain-one";
    private static final String DRAFT = "draft-one";
    private static final String PLUGIN = "private-data-one";
    private static final String APP = "app-one";
    private static final Instant NOW = Instant.parse("2026-11-10T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentService brains = mock(DeploymentService.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final DeploymentDraftValidationService validation = mock(DeploymentDraftValidationService.class);
    private final CustomerPluginLibraryService plugins = mock(CustomerPluginLibraryService.class);
    private final MarketplacePluginRepository pluginRepository = mock(MarketplacePluginRepository.class);
    private final AIWorkspaceInstallationService apps = mock(AIWorkspaceInstallationService.class);
    private final AIWorkspaceInstallationRepository appRepository = mock(AIWorkspaceInstallationRepository.class);
    private final DeploymentBehaviorCatalogService behaviors = mock(DeploymentBehaviorCatalogService.class);
    private final PlatformSecretRepository secrets = mock(PlatformSecretRepository.class);
    private final AuthoringChanges changes = new AuthoringChanges(mapper);
    private final AuthoringResourceService resources = resources(plugins);

    @BeforeEach void authenticate() { signIn(PlatformRole.PLATFORM_ADMIN); }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }

    @Test void unauthenticatedResourceReadsStopBeforeRepositoriesOrExistingServices() {
        SecurityContextHolder.clearContext();
        assertStatus(HttpStatus.UNAUTHORIZED, () -> resources.load("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS"));
        verifyNoInteractions(deployments, brains, plugins, pluginRepository, apps, appRepository);
    }

    @Test void draftAccessDenialIsNotBypassedByTheAuthoringAdapter() {
        var entity = new DeploymentEntity();
        when(deployments.findById(BRAIN)).thenReturn(Optional.of(entity));
        when(access.requireDeploymentEditorAccess(entity)).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        assertStatus(HttpStatus.FORBIDDEN, () -> resources.load("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS"));
        verifyNoInteractions(brains, validation);
    }

    @Test void anActionEditCallsExistingUpdateDraftWithOnlyActionsAndPreservesRichInputsAndSiblingActions() throws Exception {
        var draft = brainFixture();
        var saved = resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig/actions","operation":"UPSERT","key":"lookup_order",
              "value":{"name":"lookup_order","description":"Current order details"}}]
            """));
        var expected = draft.actionsConfig().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/actions/0")).put("description", "Current order details");
        assertThat(capturedDraftUpdate()).isEqualTo(new UpdateDeploymentDraftRequest(
            expected, null, null, null, null, null, null, null, null, null));
        assertThat(saved.configuration().path("actionsConfig")).isEqualTo(expected);
        assertThat(saved.configuration().path("routingConfig")).isEqualTo(draft.routingConfig());
        assertThat(expected.at("/actions/0/params/0/visibility").asText()).isEqualTo("INTERNAL");
        assertThat(expected.at("/actions/0/params/0/resolveFrom")).isEqualTo(draft.actionsConfig().at("/actions/0/params/0/resolveFrom"));
        assertThat(expected.at("/actions/0/params/1/items")).isEqualTo(draft.actionsConfig().at("/actions/0/params/1/items"));
        assertThat(expected.at("/actions/1")).isEqualTo(draft.actionsConfig().at("/actions/1"));
        assertThat(saved.receipt().path("draftId").asText()).isEqualTo(DRAFT);
        assertThat(saved.receipt().path("published").asBoolean()).isFalse();
        verify(access).requireDeploymentEditorAccess(any());
    }

    @Test void routingOnlyEditDoesNotResubmitTheActionCatalogueOrAnyOtherDraftField() throws Exception {
        var draft = brainFixture();
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "CONNECTIONS", json("""
            [{"path":"/routingConfig/actions/lookup_order/response/message","operation":"SET","value":"Order loaded"}]
            """));
        var expected = draft.routingConfig().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/actions/lookup_order/response")).put("message", "Order loaded");
        assertThat(capturedDraftUpdate()).isEqualTo(new UpdateDeploymentDraftRequest(
            null, null, expected, null, null, null, null, null, null, null));
    }

    @Test void aCombinedActionAndRouteEditSubmitsExactlyThoseTwoFields() throws Exception {
        var draft = brainFixture();
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig/actions","operation":"UPSERT","key":"lookup_order","value":{"name":"lookup_order","displayName":"Order status"}},
             {"path":"/routingConfig/actions/lookup_order/response/message","operation":"SET","value":"Loaded"}]
            """));
        var request = capturedDraftUpdate();
        assertThat(request.actionsConfig().at("/actions/0/displayName").asText()).isEqualTo("Order status");
        assertThat(request.actionsConfig().at("/actions/0/params")).isEqualTo(draft.actionsConfig().at("/actions/0/params"));
        assertThat(request.routingConfig().at("/actions/lookup_order/response/message").asText()).isEqualTo("Loaded");
        assertThat(request).isEqualTo(new UpdateDeploymentDraftRequest(
            request.actionsConfig(), null, request.routingConfig(), null, null, null, null, null, null, null));
    }

    @Test void validationReceivesTheCompleteCandidateButFindingsDoNotBecomeAnExtraDraftSaveApproval() throws Exception {
        var draft = brainFixture();
        var findings = new DraftValidationResponse(DRAFT, BRAIN, false, 1, 0, NOW, List.of(
            new DraftValidationIssue("ERROR", "actions", "MISSING_ROUTE", "$.routingConfig.actions", "Route still needs configuration.")));
        when(validation.validate(any(), anyString())).thenReturn(findings);
        var saved = resources.save("DEPLOYMENT_DRAFT", BRAIN, "BEHAVIOUR", json("""
            [{"path":"/promptConfig/systemPrompt","operation":"SET","value":"Updated instructions"}]
            """));
        var checked = ArgumentCaptor.forClass(DeploymentDraftEntity.class);
        verify(validation).validate(checked.capture(), eq("SMART_BRAIN"));
        assertThat(json(checked.getValue().getActionsConfigJson())).isEqualTo(draft.actionsConfig());
        assertThat(json(checked.getValue().getRoutingConfigJson())).isEqualTo(draft.routingConfig());
        assertThat(json(checked.getValue().getEntityConfigJson())).isEqualTo(draft.entityConfig());
        assertThat(json(checked.getValue().getProviderConfigJson())).isEqualTo(draft.providerConfig());
        assertThat(json(checked.getValue().getSecurityConfigJson())).isEqualTo(draft.securityConfig());
        assertThat(json(checked.getValue().getKnowledgeSourceConfigJson())).isEqualTo(draft.knowledgeSourceConfig());
        assertThat(json(checked.getValue().getShellConfigJson())).isEqualTo(draft.shellConfig());
        assertThat(json(checked.getValue().getMarketplaceDatasetConfigJson())).isEqualTo(draft.marketplaceDatasetConfig());
        assertThat(json(checked.getValue().getBehaviorConfigJson())).isEqualTo(draft.behaviorConfig());
        assertThat(checked.getValue().getEntityConfigContractVersion()).isEqualTo("AI_ENTITY_CONFIG_V0_4");
        assertThat(saved.findings()).isEqualTo(mapper.valueToTree(findings));
        var request = capturedDraftUpdate();
        assertThat(request).isEqualTo(new UpdateDeploymentDraftRequest(
            null, null, null, null, null, request.promptConfig(), null, null, null, null));
        assertThat(request.promptConfig().path("systemPrompt").asText()).isEqualTo("Updated instructions");
        assertThat(request.promptConfig().path("other")).isEqualTo(draft.promptConfig().path("other"));
    }

    @Test void outOfTaskChangesCannotReachExistingDraftMutationServices() throws Exception {
        brainFixture();
        assertStatus(HttpStatus.BAD_REQUEST, () -> resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS",
            json("""
                [{"path":"/providerConfig/llmModel","operation":"SET","value":"another-model"}]
                """)));
        verify(brains, never()).updateDraft(anyString(), any());
        verifyNoInteractions(validation);
    }

    @Test void catalogueUsesActualConnectorAccessModes() throws Exception {
        brainFixture();
        var catalogue = resources.load("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS").catalogue();
        assertThat(catalogue.path("actionContract").asText()).contains("READ_WRITE", "WRITE_ONLY");
    }

    @Test void catalogueNamesParameterVisibilityRatherThanInventingASourceField() throws Exception {
        brainFixture();
        var contract = resources.load("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS").catalogue().path("actionContract").asText();
        assertThat(contract).contains("visibility", "USER", "INTERNAL").doesNotContain("askUser,source,");
    }

    @Test void modelCatalogueIsTheExistingManagedProviderCatalogueAndModelEditsOnlyWriteProviderConfig() throws Exception {
        var draft = brainFixture();
        var snapshot = resources.load("DEPLOYMENT_DRAFT", BRAIN, "MODELS");
        assertThat(snapshot.allowedPaths()).containsExactly("/providerConfig");
        assertThat(snapshot.catalogue().path("llmProviders")).isEqualTo(mapper.valueToTree(ManagedDeploymentProfileCatalog.SUPPORTED_LLM_PROVIDERS));
        assertThat(snapshot.catalogue().path("embeddingProviders")).isEqualTo(mapper.valueToTree(ManagedDeploymentProfileCatalog.SUPPORTED_EMBEDDING_PROVIDERS));
        assertThat(snapshot.catalogue().path("aiBehaviourProfiles")).isEqualTo(mapper.valueToTree(DeploymentAiBehaviourProfiles.catalogue()));
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "MODELS", json("""
            [{"path":"/providerConfig/llmModel","operation":"SET","value":"requested-model-id"}]
            """));
        var expected = draft.providerConfig().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected).put("llmModel", "requested-model-id");
        assertThat(capturedDraftUpdate()).isEqualTo(new UpdateDeploymentDraftRequest(
            null, null, null, expected, null, null, null, null, null, null));
    }

    @Test void deployedKnowledgeSourceUpsertUsesIdAndPreservesOtherSources() throws Exception {
        var draft = brainFixture();
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "KNOWLEDGE", json("""
            [{"path":"/knowledgeSourceConfig/sources","operation":"UPSERT","key":"orders",
              "value":{"id":"orders","displayName":"Order knowledge"}}]
            """));
        var expected = draft.knowledgeSourceConfig().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/sources/0")).put("displayName", "Order knowledge");
        assertThat(capturedDraftUpdate()).isEqualTo(new UpdateDeploymentDraftRequest(
            null, null, null, null, null, null, expected, null, null, null));
    }

    @Test void marketplaceDatasetUpsertUsesDatasetIdWithoutAddingAnInventedIdOrDroppingSiblings() throws Exception {
        var draft = brainFixture();
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "KNOWLEDGE", json("""
            [{"path":"/marketplaceDatasetConfig/datasets","operation":"UPSERT","key":"orders",
              "value":{"datasetId":"orders","displayName":"Order documents"}}]
            """));
        var expected = draft.marketplaceDatasetConfig().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/datasets/0")).put("displayName", "Order documents");
        assertThat(capturedDraftUpdate()).isEqualTo(new UpdateDeploymentDraftRequest(
            null, null, null, null, null, null, null, null, expected, null));
        assertThat(expected.at("/datasets/0").has("id")).isFalse();
    }

    @Test void partialActionParameterUpsertDoesNotReplaceTheExistingRichParamsArray() throws Exception {
        var draft = brainFixture();
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig/actions","operation":"UPSERT","key":"lookup_order",
              "value":{"name":"lookup_order","params":[{"name":"orderReference","description":"Updated reference"}]}}]
            """));
        var expected = draft.actionsConfig().at("/actions/0/params").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.get(0)).put("description", "Updated reference");
        assertThat(capturedDraftUpdate().actionsConfig().at("/actions/0/params")).isEqualTo(expected);
    }

    @Test void partialParameterEditsMatchNameRatherThanInheritingAnotherParametersTypeAndInternalEvidenceRules() throws Exception {
        var draft = brainFixture();
        resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig/actions","operation":"UPSERT","key":"lookup_order",
              "value":{"name":"lookup_order","params":[{"name":"filters","description":"Filter current orders"}]}}]
            """));
        var expected = draft.actionsConfig().at("/actions/0/params").deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.get(1)).put("description", "Filter current orders");
        assertThat(capturedDraftUpdate().actionsConfig().at("/actions/0/params")).isEqualTo(expected);
    }

    @Test void rootSetActionsConfigMergesPartialActionsAndNestedParamsByIdentityWithoutDroppingSiblings() throws Exception {
        var draft = brainFixture();
        var saved = resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig","operation":"SET","value":{"actions":[
              {"name":"lookup_order","params":[{"name":"filters","description":"Updated filters"}]},
              {"name":"lookup_customer","accessMode":"READ","params":[]}
            ]}}]
            """));
        var expected = draft.actionsConfig().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/actions/0/params/1")).put("description", "Updated filters");
        ((com.fasterxml.jackson.databind.node.ArrayNode) expected.path("actions")).add(
            json("{\"name\":\"lookup_customer\",\"accessMode\":\"READ\",\"params\":[]}"));
        assertThat(capturedDraftUpdate()).isEqualTo(new UpdateDeploymentDraftRequest(
            expected, null, null, null, null, null, null, null, null, null));
        assertThat(saved.configuration().path("actionsConfig")).isEqualTo(expected);
        assertThat(saved.configuration().path("routingConfig")).isEqualTo(draft.routingConfig());
        assertThat(expected.at("/actions/0/params/0/defaultValue").asText()).isEqualTo("9007199254740993");
    }

    @Test void rootSetWithEmptyNamedActionsCannotClearTheCatalogueButExplicitRemoveItemCanRemoveOneAction() throws Exception {
        var draft = brainFixture();
        var retained = resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig","operation":"SET","value":{"actions":[]}}]
            """));
        assertThat(retained.configuration().path("actionsConfig")).isEqualTo(draft.actionsConfig());
        var removed = resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig/actions","operation":"REMOVE_ITEM","key":"lookup_order"}]
            """));
        assertThat(removed.configuration().path("actionsConfig").path("actions")).hasSize(1);
        assertThat(removed.configuration().at("/actionsConfig/actions/0")).isEqualTo(draft.actionsConfig().at("/actions/1"));
        var requests = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(brains, times(2)).updateDraft(eq(DRAFT), requests.capture());
        assertThat(requests.getAllValues().get(0)).isEqualTo(new UpdateDeploymentDraftRequest(
            draft.actionsConfig(), null, null, null, null, null, null, null, null, null));
        assertThat(requests.getAllValues().get(1)).isEqualTo(new UpdateDeploymentDraftRequest(
            removed.configuration().path("actionsConfig"), null, null, null, null, null, null, null, null, null));
    }

    @Test void resourceUndoRestoresExactObjectFieldsInsteadOfKeepingNewAssistantFieldsFromDeepMerge() throws Exception {
        var draft = brainFixture();
        var saved = resources.save("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", json("""
            [{"path":"/actionsConfig/actions","operation":"UPSERT","key":"lookup_order",
              "value":{"name":"lookup_order","description":"Assistant wording","addedByAssistant":true,
                "futureActionField":{"addedNestedField":{"keep":false}}}}]
            """));
        assertThat(saved.configuration().at("/actionsConfig/actions/0/addedByAssistant").asBoolean()).isTrue();
        assertThat(saved.configuration().at("/actionsConfig/actions/0/futureActionField/addedNestedField").isObject()).isTrue();
        var current = new DeploymentDraftResponse(draft.id(), draft.deploymentId(), draft.revisionNumber(), draft.status(),
            saved.configuration().path("actionsConfig"), draft.entityConfig(), draft.routingConfig(), draft.providerConfig(),
            draft.securityConfig(), draft.promptConfig(), draft.knowledgeSourceConfig(), draft.shellConfig(),
            draft.marketplaceDatasetConfig(), draft.createdAt(), NOW.plusSeconds(1), draft.entityConfigContractVersion(), draft.behaviorConfig());
        when(brains.getActiveDraftForDeployment(BRAIN)).thenReturn(current);
        var restored = resources.undo("DEPLOYMENT_DRAFT", BRAIN, "ACTIONS", saved.diff());
        assertThat(restored.configuration().path("actionsConfig")).isEqualTo(draft.actionsConfig());
        assertThat(restored.configuration().at("/actionsConfig/actions/0/addedByAssistant").isMissingNode()).isTrue();
        assertThat(restored.configuration().at("/actionsConfig/actions/0/futureActionField/addedNestedField").isMissingNode()).isTrue();
        var requests = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(brains, times(2)).updateDraft(eq(DRAFT), requests.capture());
        assertThat(requests.getAllValues().get(1)).isEqualTo(new UpdateDeploymentDraftRequest(
            draft.actionsConfig(), null, null, null, null, null, null, null, null, null));
        assertThat(restored.receipt().path("published").asBoolean()).isFalse();
    }

    @Test void activeEngageCanBePreviewedButCannotBeImplicitlyDisabledOrSaved() throws Exception {
        var app = engageFixture("ACTIVE");
        var snapshot = resources.load("ENGAGE_APP", APP, "ENGAGE");
        assertThat(snapshot.editable()).isFalse();
        assertThat(snapshot.readOnlyReason()).contains("Preview remains available");
        assertThat(snapshot.configuration().path("configuration")).isEqualTo(app.configuration());
        var findings = resources.check("ENGAGE_APP", APP, snapshot, snapshot.configuration());
        assertThat(findings.path("configurationChecked").asBoolean()).isFalse();
        assertThat(findings.path("message").asText()).contains("does not claim connectivity");
        assertStatus(HttpStatus.CONFLICT, () -> resources.save("ENGAGE_APP", APP, "ENGAGE",
            json("""
                [{"path":"/displayName","operation":"SET","value":"Previewed name"}]
                """)));
        verify(apps, never()).update(anyString(), anyString(), any());
        verify(apps, never()).disable(anyString(), anyString());
        verifyNoInteractions(brains, plugins, validation);
    }

    @Test void engageRefinementPreservesCustomerCustomDomainsAndAllUntouchedIdentityConnectionAndAuthFields() throws Exception {
        var app = engageFixture("DRAFT");
        resources.save("ENGAGE_APP", APP, "ENGAGE", json("""
            [{"path":"/displayName","operation":"SET","value":"Updated app label"}]
            """));
        var captured = ArgumentCaptor.forClass(UpdateAIWorkspaceInstallationRequest.class);
        verify(apps).update(eq(OWNER), eq(APP), captured.capture());
        assertThat(captured.getValue()).isEqualTo(new UpdateAIWorkspaceInstallationRequest(
            app.consumerId(), "Updated app label", app.experiencePackCode(), app.experiencePackVersion(),
            app.connectionMode(), app.connectionProfileCode(), app.connectionProfileVersion(), app.connectionConfiguration(),
            app.allowedOrigins(), app.configuration(), app.deliveryMode(), app.appSlug(), List.of("support.customer.example"),
            app.interactionPolicy(), app.authProfileCode(), app.authProfileVersion(), app.authConfiguration(), app.rowVersion()));
    }

    @Test void engageUsesExistingConfigurationAuthConfigurationAndAllowedOriginsPathsNotGuessedAppSettings() throws Exception {
        var app = engageFixture("DRAFT");
        var snapshot = resources.load("ENGAGE_APP", APP, "ENGAGE");
        assertThat(snapshot.allowedPaths()).containsExactly("/displayName", "/configuration", "/authConfiguration", "/allowedOrigins");
        resources.save("ENGAGE_APP", APP, "ENGAGE", json("""
            [{"path":"/configuration/theme/accentColor","operation":"SET","value":"#117e6d"},
             {"path":"/authConfiguration","operation":"SET","value":{}},
             {"path":"/allowedOrigins","operation":"SET","value":["https://new.customer.example"]}]
            """));
        var request = ArgumentCaptor.forClass(UpdateAIWorkspaceInstallationRequest.class);
        verify(apps).update(eq(OWNER), eq(APP), request.capture());
        var expectedConfiguration = app.configuration().deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expectedConfiguration.path("theme")).put("accentColor", "#117e6d");
        assertThat(request.getValue()).isEqualTo(new UpdateAIWorkspaceInstallationRequest(
            app.consumerId(), app.displayName(), app.experiencePackCode(), app.experiencePackVersion(),
            app.connectionMode(), app.connectionProfileCode(), app.connectionProfileVersion(), app.connectionConfiguration(),
            List.of("https://new.customer.example"), expectedConfiguration, app.deliveryMode(), app.appSlug(),
            List.of("support.customer.example"), app.interactionPolicy(), app.authProfileCode(), app.authProfileVersion(),
            app.authConfiguration(), app.rowVersion()));
        assertStatus(HttpStatus.BAD_REQUEST, () -> resources.save("ENGAGE_APP", APP, "ENGAGE",
            json("""
                [{"path":"/domains","operation":"SET","value":[]}]
                """)));
        verify(apps, times(1)).update(anyString(), anyString(), any());
    }

    @Test void privatePluginLookupDelegatesToTheExistingOwnerScopedLibraryService() throws Exception {
        var entity = pluginEntity();
        when(pluginRepository.findById(PLUGIN)).thenReturn(Optional.of(entity));
        var customerAccess = mock(CustomerOfferEntitlementService.class);
        when(customerAccess.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor(OWNER, "owner-user"));
        var library = realLibrary(customerAccess);
        signIn(PlatformRole.CUSTOMER_ADMIN);
        var snapshot = resources(library).load("PRIVATE_DATA_PLUGIN", PLUGIN, "DATA_PLUGIN");
        assertThat(snapshot.customerId()).isEqualTo(OWNER);
        assertThat(snapshot.configuration().path("manifest")).isEqualTo(json(entity.getDraftManifestJson()));
        assertThat(snapshot.version()).isEqualTo("7");
        verify(customerAccess).requireCustomerActor();
    }

    @Test void anotherCustomersPrivatePluginCannotBeReadOrSavedEvenWhenTheRepositoryFindsIt() throws Exception {
        when(pluginRepository.findById(PLUGIN)).thenReturn(Optional.of(pluginEntity()));
        var customerAccess = mock(CustomerOfferEntitlementService.class);
        when(customerAccess.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("other-customer", "other-user"));
        var library = spy(realLibrary(customerAccess));
        var ownedResources = resources(library);
        signIn(PlatformRole.CUSTOMER_ADMIN);
        assertStatus(HttpStatus.FORBIDDEN, () -> ownedResources.load("PRIVATE_DATA_PLUGIN", PLUGIN, "DATA_PLUGIN"));
        assertStatus(HttpStatus.FORBIDDEN, () -> ownedResources.save("PRIVATE_DATA_PLUGIN", PLUGIN, "DATA_PLUGIN",
            json("""
                [{"path":"/manifest/displayName","operation":"SET","value":"Foreign edit"}]
                """)));
        verify(library, never()).update(anyString(), anyString(), any());
        verify(pluginRepository, never()).saveAndFlush(any());
    }

    @Test void globallyOwnedPluginsAreNotPresentedAsCustomerPrivateDrafts() throws Exception {
        var entity = pluginEntity();
        entity.setOwnerCustomerId(null);
        when(pluginRepository.findById(PLUGIN)).thenReturn(Optional.of(entity));
        assertStatus(HttpStatus.NOT_FOUND, () -> resources.load("PRIVATE_DATA_PLUGIN", PLUGIN, "DATA_PLUGIN"));
        verifyNoInteractions(plugins);
    }

    @Test void privatePluginKnowledgeSourcesUseSourceKeyAndSaveWithCurrentOwnerAndRevision() throws Exception {
        var entity = pluginEntity();
        when(pluginRepository.findById(PLUGIN)).thenReturn(Optional.of(entity));
        var manifest = json(entity.getDraftManifestJson());
        when(plugins.get(PLUGIN, OWNER)).thenReturn(pluginSummary(manifest, 7), pluginSummary(manifest, 8), pluginSummary(manifest, 9));
        when(plugins.check(eq(PLUGIN), eq(OWNER), any())).thenReturn(new CustomerPluginLibraryContracts.Check(true, 8, List.of(), false));
        when(plugins.update(eq(PLUGIN), eq(OWNER), any())).thenReturn(pluginSummary(manifest, 10));
        var saved = resources.save("PRIVATE_DATA_PLUGIN", PLUGIN, "DATA_PLUGIN", json("""
            [{"path":"/manifest/contributions/knowledgeSources","operation":"UPSERT","key":"orders",
              "value":{"sourceKey":"orders","displayName":"Order documents"}}]
            """));
        var expected = manifest.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) expected.at("/contributions/knowledgeSources/0")).put("displayName", "Order documents");
        verify(plugins).check(PLUGIN, OWNER, new CustomerPluginLibraryContracts.Save(expected, 8L));
        verify(plugins).update(PLUGIN, OWNER, new CustomerPluginLibraryContracts.Save(expected, 9L));
        assertThat(saved.configuration().path("manifest")).isEqualTo(expected);
        assertThat(saved.receipt().path("revision").asLong()).isEqualTo(10);
        assertThat(saved.findings().path("connectionTested").asBoolean()).isFalse();
        assertThat(expected.at("/contributions/knowledgeSources/0").has("id")).isFalse();
    }

    private AuthoringResourceService resources(CustomerPluginLibraryService library) {
        return new AuthoringResourceService(deployments, brains, access, validation, library, pluginRepository,
            apps, appRepository, behaviors, secrets, changes, mapper,
            mock(com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService.class));
    }

    private DeploymentDraftResponse brainFixture() throws Exception {
        var entity = new DeploymentEntity();
        entity.setId(BRAIN); entity.setCustomerId(OWNER); entity.setName("Order Brain");
        entity.setStatus("DRAFT"); entity.setBehaviorType("SMART_BRAIN");
        when(deployments.findById(BRAIN)).thenReturn(Optional.of(entity));
        when(access.requireDeploymentEditorAccess(entity)).thenReturn(entity);
        var config = json("""
            {"actionsConfig":{"actions":[
              {"name":"lookup_order","accessMode":"READ","description":"Original",
               "params":[{"name":"orderReference","type":"STRING","required":true,"visibility":"INTERNAL","askUser":false,
                 "defaultValue":9007199254740993,"allowedValues":["one","two"],"resolveFrom":{"source":"ATTACHMENT_METADATA","metadataKeys":["id"]},
                 "evidenceBound":true,"evidenceKeys":["id"],"evidenceFallbackPolicy":"CLARIFY","future":{"keep":true}},
                 {"name":"filters","type":"ARRAY","items":{"type":"OBJECT","properties":{"status":{"type":"STRING"}},"requiredProperties":["status"]}}],
               "futureActionField":{"preserved":true}},
              {"name":"send_callback","accessMode":"WRITE_ONLY","requiresConfirmation":true,"futureSiblingField":7}]},
             "routingConfig":{"actions":{"lookup_order":{"method":"POST","path":"/orders/lookup",
               "request":{"body":{"id":"{{params.orderReference}}"}},"response":{"message":"Original","result":{"items":"{{body.orders}}"}}}}},
             "entityConfig":{"entities":{"order":{"vectorSpace":"orders"}}},"providerConfig":{"llmModel":"gpt-4o-mini"},
             "securityConfig":{"authz":{"enabled":true}},"promptConfig":{"systemPrompt":"Original","other":{"keep":true}},
             "knowledgeSourceConfig":{"sources":[{"id":"orders","datasetRef":"orders","future":{"keep":true}},{"id":"keep-source"}]},
             "marketplaceDatasetConfig":{"datasets":[{"datasetId":"orders","ingestionMode":"EXTERNAL_DOCUMENT_STORAGE","future":{"keep":true}},{"datasetId":"keep-dataset"}]},
             "shellConfig":{"existing":true},"behaviorConfig":{"type":"SMART_BRAIN","humanReview":{"enabled":false}}}
            """);
        var draft = new DeploymentDraftResponse(DRAFT, BRAIN, 5, "MODIFIED",
            config.path("actionsConfig"), config.path("entityConfig"), config.path("routingConfig"),
            config.path("providerConfig"), config.path("securityConfig"), config.path("promptConfig"),
            config.path("knowledgeSourceConfig"), config.path("shellConfig"), config.path("marketplaceDatasetConfig"),
            NOW, NOW, "AI_ENTITY_CONFIG_V0_4", config.path("behaviorConfig"));
        when(brains.getActiveDraftForDeployment(BRAIN)).thenReturn(draft);
        when(brains.updateDraft(eq(DRAFT), any())).thenReturn(draft);
        when(behaviors.list()).thenReturn(List.of());
        when(validation.validate(any(), anyString())).thenReturn(new DraftValidationResponse(DRAFT, BRAIN, true, 0, 0, NOW, List.of()));
        return draft;
    }

    private UpdateDeploymentDraftRequest capturedDraftUpdate() {
        var captured = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(brains).updateDraft(eq(DRAFT), captured.capture());
        return captured.getValue();
    }

    private AIWorkspaceInstallationSummary engageFixture(String status) throws Exception {
        var entity = new AIWorkspaceInstallationEntity(); entity.setCustomerId(OWNER); entity.setInstallationId(APP);
        when(appRepository.findByInstallationId(APP)).thenReturn(Optional.of(entity));
        var app = new AIWorkspaceInstallationSummary("internal-app", APP, OWNER, "consumer-one", "Customer support", status,
            "support", "1.0.0", "public-runtime-anonymous", "runtime-anonymous-direct", "1.0.0",
            json("{\"runtimeAudience\":\"existing-audience\"}"), List.of("https://customer.example"),
            json("{\"theme\":{\"accentColor\":\"#116cca\"},\"futurePresentation\":{\"keep\":true}}"),
            "standalone", "support", "https://support.customer.example", "public", "none", "1.0.0", json("{}"),
            List.of(domain("app.platform.example", AIWorkspaceDomainType.PLATFORM_DEFAULT),
                domain("support.customer.example", AIWorkspaceDomainType.CUSTOMER_CUSTOM)),
            BRAIN, "release-existing", "assignment-existing", false, List.of(), 11, NOW, NOW, "ACTIVE".equals(status) ? NOW : null, null);
        when(apps.get(OWNER, APP)).thenReturn(app);
        when(apps.catalog()).thenReturn(new AIWorkspaceCatalogSummary(true, "READY", List.of(), List.of(), List.of()));
        when(apps.update(eq(OWNER), eq(APP), any())).thenReturn(app);
        return app;
    }

    private AIWorkspaceDomainSummary domain(String hostname, AIWorkspaceDomainType type) {
        return new AIWorkspaceDomainSummary(hostname, "https://" + hostname, type.name(), "VERIFIED",
            "_verify." + hostname, "verification-value", hostname, "platform.example", null, NOW);
    }

    private MarketplacePluginEntity pluginEntity() throws Exception {
        var plugin = new MarketplacePluginEntity();
        plugin.setId(PLUGIN); plugin.setOwnerCustomerId(OWNER); plugin.setVisibility("CUSTOMER_PRIVATE");
        plugin.setPluginType("DATA"); plugin.setStatus("DRAFT"); plugin.setDisplayName("Private order data");
        plugin.setDraftRevision(7); plugin.setUpdatedAt(NOW);
        plugin.setDraftManifestJson("""
            {"pluginId":"private-orders","pluginType":"DATA","version":"1.0.0","displayName":"Private order data",
             "contributions":{"knowledgeSources":[
               {"sourceKey":"orders","sourceType":"deployment-private-vector","future":{"keep":true}},
               {"sourceKey":"keep-plugin-source"}]},"futureManifest":{"keep":true}}
            """);
        return plugin;
    }

    private CustomerPluginLibraryContracts.Summary pluginSummary(JsonNode manifest, long revision) {
        return new CustomerPluginLibraryContracts.Summary(PLUGIN, OWNER, "CUSTOMER_PRIVATE", "Private order data", "DATA", "DRAFT",
            manifest, revision, List.of(), new CustomerPluginLibraryContracts.Usage(0, 0, List.of(), List.of()), true,
            null, "CUSTOMER", "HTTP_JSON", NOW);
    }

    private CustomerPluginLibraryService realLibrary(CustomerOfferEntitlementService customerAccess) {
        return new CustomerPluginLibraryService(pluginRepository, mock(MarketplacePluginVersionRepository.class),
            mock(DeploymentMarketplacePluginInstallRepository.class), deployments, mock(DeploymentReleaseRepository.class),
            mock(DeploymentVersionRepository.class), mock(MarketplaceManifestService.class), customerAccess,
            mock(PlatformAuditService.class), mapper);
    }

    private JsonNode json(String source) throws Exception { return mapper.readTree(source); }

    private void assertStatus(HttpStatus status, org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode()).isEqualTo(status));
    }

    private void signIn(PlatformRole role) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal("actor-test", role, "Fixture", "SESSION"), null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        SecurityContextHolder.setContext(context);
    }
}
