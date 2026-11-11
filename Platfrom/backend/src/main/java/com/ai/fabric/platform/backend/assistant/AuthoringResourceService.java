package com.ai.fabric.platform.backend.assistant;

import com.ai.fabric.platform.backend.aiworkspace.model.*;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceInstallationService;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.specialist.SpecialistAssistanceAuthorityService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDraftValidationService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAiBehaviourProfiles;
import com.ai.fabric.platform.backend.marketplace.model.CustomerPluginLibraryContracts;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.service.CustomerPluginLibraryService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.secret.entity.PlatformSecretScopeType;
import com.ai.fabric.platform.backend.secret.repository.PlatformSecretRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Adapters to existing resource services, never to publication or infrastructure APIs. */
@Service
public class AuthoringResourceService {
    private static final Set<String> BRAIN_FIELDS = Set.of("actionsConfig", "entityConfig", "routingConfig", "providerConfig",
        "securityConfig", "promptConfig", "knowledgeSourceConfig", "shellConfig", "marketplaceDatasetConfig", "behaviorConfig");
    private final DeploymentRepository deployments;
    private final DeploymentService brains;
    private final DeploymentAccessService access;
    private final DeploymentDraftValidationService validation;
    private final CustomerPluginLibraryService plugins;
    private final MarketplacePluginRepository pluginRepository;
    private final AIWorkspaceInstallationService apps;
    private final AIWorkspaceInstallationRepository appRepository;
    private final DeploymentBehaviorCatalogService behaviors;
    private final SpecialistAssistanceAuthorityService specialistAuthority;
    private final PlatformSecretRepository secrets;
    private final AuthoringChanges changes;
    private final ObjectMapper mapper;

    public AuthoringResourceService(DeploymentRepository deployments, DeploymentService brains, DeploymentAccessService access,
        DeploymentDraftValidationService validation, CustomerPluginLibraryService plugins, MarketplacePluginRepository pluginRepository,
        AIWorkspaceInstallationService apps, AIWorkspaceInstallationRepository appRepository,
        DeploymentBehaviorCatalogService behaviors, PlatformSecretRepository secrets, AuthoringChanges changes, ObjectMapper mapper,
        SpecialistAssistanceAuthorityService specialistAuthority) {
        this.deployments = deployments; this.brains = brains; this.access = access; this.validation = validation;
        this.plugins = plugins; this.pluginRepository = pluginRepository; this.apps = apps; this.appRepository = appRepository;
        this.behaviors = behaviors; this.secrets = secrets; this.changes = changes; this.mapper = mapper;
        this.specialistAuthority = specialistAuthority;
    }

    public Snapshot load(String kind, String id, String task) {
        if (!PlatformSecurityContext.isAuthenticated()) throw new ResponseStatusException(UNAUTHORIZED, "Sign in to configure this resource.");
        List<String> paths = paths(kind, task);
        var catalogue = mapper.createObjectNode();
        AuthoringResourceContracts.populate(mapper, catalogue, task);
        catalogue.set("allowedPaths", mapper.valueToTree(paths));
        catalogue.put("changeContract", "SET deep-merges objects. REMOVE deletes an exact field. UPSERT/REMOVE_ITEM edit a named array item; key matches name for actions/params, id for installed knowledge sources, sourceKey for plugin knowledgeSources, datasetId for datasets. Never replace whole action catalogues. Credential values are not editable. A draft save is not publish, install, indexing or connection success.");
        return switch (kind) {
            case "DEPLOYMENT_DRAFT" -> {
                var brain = access.requireDeploymentEditorAccess(deployments.findById(id).orElseThrow(() -> missing()));
                var draft = brains.getActiveDraftForDeployment(id);
                ObjectNode config = mapper.valueToTree(draft);
                config.retain(BRAIN_FIELDS);
                catalogue.set("behaviorTypes", mapper.valueToTree(behaviors.list()));
                catalogue.set("llmProviders", mapper.valueToTree(ManagedDeploymentProfileCatalog.SUPPORTED_LLM_PROVIDERS));
                catalogue.set("embeddingProviders", mapper.valueToTree(ManagedDeploymentProfileCatalog.SUPPORTED_EMBEDDING_PROVIDERS));
                catalogue.set("aiBehaviourProfiles", mapper.valueToTree(DeploymentAiBehaviourProfiles.catalogue()));
                catalogue.set("aiBehaviourConfigurationExample", DeploymentAiBehaviourProfiles.select(
                    mapper.createObjectNode(), mapper.createObjectNode()).path("aiBehaviour"));
                if ("SPECIALISTS".equals(task)) {
                    var authority = catalogue.putObject("authorizedSpecialistCapabilities");
                    try {
                        var available = specialistAuthority.resolve(id, config.path("actionsConfig"), config.path("knowledgeSourceConfig"));
                        authority.put("available", true).set("readActionScopes", mapper.valueToTree(available.readActionScopes()));
                        authority.set("vectorSpaces", mapper.valueToTree(available.vectorSpaces()));
                    } catch (ResponseStatusException invalidAuthority) {
                        authority.put("available", false).put("message", invalidAuthority.getReason());
                    }
                }
                catalogue.set("secretReferences", mapper.valueToTree(java.util.stream.Stream.concat(
                    secrets.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_MANAGED, id).stream(),
                    secrets.findByScopeTypeAndDeploymentIdOrderByUpdatedAtDesc(PlatformSecretScopeType.DEPLOYMENT_OVERRIDE, id).stream())
                    .filter(s -> id.equals(s.getDeploymentId())).map(s -> s.getName()).distinct().toList()));
                catalogue.put("actionContract", "actionsConfig.actions entries: name,displayName,description,category,accessMode READ|READ_WRITE|WRITE_ONLY,requiresConfirmation,confirmationMessage,anonymousAllowed,groundingEligible,groundingVectorSpaces optional,readActionResolutionEligible,params [{name,description,type:STRING|INTEGER|NUMBER|BOOLEAN|OBJECT|ARRAY|UNKNOWN,required,askUser,visibility:USER|INTERNAL,items,properties,allowedValues,defaultValue,resolveFrom}],resultPresentationHint optional enum DEFAULT|LIST|DETAIL|STATUS. resultPresentationHint is never prose: omit it unless needed or select an exact enum value; put descriptions in description. Routes are routingConfig.actions[actionName] or existing inline route; keep existing route contract. READ needs no vector space. Derive exact endpoint/body/response from supplied docs; do not invent trusted IDs or credentials. A WRITE_ONLY/READ_WRITE user-facing action retains declared final confirmation; do not infer business effects from HTTP verb alone.");
                catalogue.put("behaviorContract", "For promptConfig.aiBehaviour use schemaVersion loomai-ai-behaviour-v1 and revision:1. Each group tone/answerBreadth/answerLength/nextSteps is an OBJECT {presetId,revision:1,instructions}, never a string ID. The catalogue option's id maps to this object's presetId, not an id field. Follow aiBehaviourConfigurationExample's exact shape, choosing the requested catalogue options and exact instructions; the example's default selections are guidance only, not replacements for the user's requested choices. Use presetId custom for edited instructions. Include all four groups when creating a profile configuration; preserve groups not requested for an existing configuration. Existing behaviorConfig.type is fixed for this resource; specialists must use registered/installed authority. No generated Java workers, arbitrary nodes, public search or new recovery policies.");
                catalogue.put("humanSupportContract", "Preserve existing installed conversationHandover.actionBinding/channel/channelBindingId and all secret references. A missing Desk binding must be selected through the Human support page, never invented. Existing handover intake.fields may have at most 6 declared fields. queueTimeoutSeconds is 30..3600; retain explicit confirmation/recovery and no idle auto-resume. Existing humanReview.actionProposals configuration requires the HUMAN_REVIEW execution extension. Enabling a supported extension in the draft is not installing or assigning the provider.");
                catalogue.put("specialistContract", "CONVERSATIONAL specialistAssistance: {enabled,areas:[{id,name,purpose,instructions,vectorSpaces,readActions}],resolutions:[{id,name,objective,areaIds,allowParallel}],limits:{maxDecisions,maxWorkerInvocations,maxParallelWorkers,deadlineSeconds}}. Maximum8 areas/resolutions/decisions/invocations, parallel1..4, deadline5..120s. enabled agrees with SPECIALIST_ASSISTANCE executionExtensions. Only already-installed authorized READ actions/vector spaces can be selected; compiled/contentHash are server-owned and must not be generated. Sequential/parallel resolutions reference defined area IDs. Other deployment types keep the registered specialistBundles/triggers contract from their existing configuration and catalogue.");
                yield new Snapshot(brain.getCustomerId(), brain.getName(), config, catalogue, paths, draft.id(), !"ARCHIVED".equals(brain.getStatus()), "");
            }
            case "PRIVATE_DATA_PLUGIN" -> {
                var entity = pluginRepository.findById(id).orElseThrow(() -> missing());
                if (entity.getOwnerCustomerId() == null) throw missing();
                var plugin = plugins.get(id, entity.getOwnerCustomerId());
                var config = mapper.createObjectNode().set("manifest", plugin.manifest());
                catalogue.put("sourceContracts", "Private DATA definitions support HTTP_JSON (API_KEY or FORM_TOKEN_EXCHANGE) and bound S3_COMPATIBLE_OBJECT_STORAGE. No customer SQL/folder/code adapters. Reusable definition fields differ from installation inputs. Keep manifest pluginId/version/type and existing schema. Stable record IDs, ownership, pagination and deletion semantics must be documented; missing details remain questions.");
                yield new Snapshot(plugin.ownerCustomerId(), plugin.displayName(), config, catalogue, paths, Long.toString(plugin.revision()),
                    !"ARCHIVED".equals(plugin.status()), "Archived plugin drafts are read-only.");
            }
            case "ENGAGE_APP" -> {
                var entity = appRepository.findByInstallationId(id).orElseThrow(() -> missing());
                var app = apps.get(entity.getCustomerId(), id);
                ObjectNode config = mapper.createObjectNode();
                config.put("displayName", app.displayName()).set("configuration", app.configuration());
                config.set("authConfiguration", app.authConfiguration());
                config.set("allowedOrigins", mapper.valueToTree(app.allowedOrigins()));
                catalogue.set("selectedExperiencePack", mapper.valueToTree(apps.authoringContract(
                    app.experiencePackCode(), app.experiencePackVersion())));
                catalogue.put("packCode", app.experiencePackCode());
                catalogue.put("packVersion", app.experiencePackVersion());
                catalogue.put("presentationContract", "Use the selected pack's existing configuration. Branding, starters, toolGroups default/contextual, tool rail, registered result renderers, attachments/auth/handover presentation may be configured only where supported. Do not generate JavaScript, change consumer/assignment, activate a domain, disable an app or alter identity-provider redirects.");
                yield new Snapshot(app.customerId(), app.displayName(), config, catalogue, paths, Long.toString(app.rowVersion()),
                    !"ACTIVE".equals(app.status()), "Disable the app explicitly before saving configuration changes. Preview remains available.");
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported configuration resource.");
        };
    }

    public JsonNode check(String kind, String id, Snapshot snapshot, JsonNode candidate) {
        if ("DEPLOYMENT_DRAFT".equals(kind)) {
            var brain = deployments.findById(id).orElseThrow(() -> missing());
            var copy = new DeploymentDraftEntity();
            copy.setId(snapshot.version()); copy.setDeploymentId(id);
            copy.setEntityConfigContractVersion("AI_ENTITY_CONFIG_V0_4");
            copy.setActionsConfigJson(candidate.path("actionsConfig").toString());
            copy.setEntityConfigJson(candidate.path("entityConfig").toString());
            copy.setRoutingConfigJson(candidate.path("routingConfig").toString());
            copy.setProviderConfigJson(candidate.path("providerConfig").toString());
            copy.setSecurityConfigJson(candidate.path("securityConfig").toString());
            copy.setPromptConfigJson(candidate.path("promptConfig").toString());
            copy.setKnowledgeSourceConfigJson(candidate.path("knowledgeSourceConfig").toString());
            copy.setShellConfigJson(candidate.path("shellConfig").toString());
            copy.setMarketplaceDatasetConfigJson(candidate.path("marketplaceDatasetConfig").toString());
            copy.setBehaviorConfigJson(candidate.path("behaviorConfig").toString());
            return changes.project(mapper.valueToTree(validation.validate(copy, brain.getBehaviorType())));
        }
        if ("PRIVATE_DATA_PLUGIN".equals(kind)) {
            var current = plugins.get(id, snapshot.customerId());
            return mapper.valueToTree(plugins.check(id, snapshot.customerId(),
                new CustomerPluginLibraryContracts.Save(candidate.path("manifest"), current.revision())));
        }
        return mapper.createObjectNode().put("configurationChecked", false)
            .put("message", "The existing app save service checks the complete selected-pack contract. Preview does not claim connectivity.");
    }

    @Transactional
    public Saved save(String kind, String id, String task, JsonNode requestedChanges) {
        Snapshot current = load(kind, id, task);
        if (!current.editable()) throw new ResponseStatusException(CONFLICT, current.readOnlyReason());
        var result = changes.apply(current.configuration(), requestedChanges, current.allowedPaths());
        if ("DEPLOYMENT_DRAFT".equals(kind)) McpAuthoringPolicy.requirePreserved(current.configuration(), result.candidate());
        return persist(kind, id, current, requestedChanges, result);
    }

    @Transactional
    public Saved undo(String kind, String id, String task, JsonNode diff) {
        Snapshot current = load(kind, id, task);
        if (!current.editable()) throw new ResponseStatusException(CONFLICT, current.readOnlyReason());
        return persist(kind, id, current, changes.inverse(diff), changes.undo(current.configuration(), diff, current.allowedPaths()));
    }

    private Saved persist(String kind, String id, Snapshot current, JsonNode requestedChanges, AuthoringChanges.Result result) {
        JsonNode findings = check(kind, id, current, result.candidate());
        JsonNode receipt;
        if ("DEPLOYMENT_DRAFT".equals(kind)) {
            var config = result.candidate();
            Set<String> touched = new java.util.HashSet<>();
            requestedChanges.forEach(change -> touched.add(change.path("path").asText().split("/", 3)[1]));
            var saved = brains.updateDraft(current.version(), new UpdateDeploymentDraftRequest(
                touched.contains("actionsConfig") ? config.get("actionsConfig") : null,
                touched.contains("entityConfig") ? config.get("entityConfig") : null,
                touched.contains("routingConfig") ? config.get("routingConfig") : null,
                touched.contains("providerConfig") ? config.get("providerConfig") : null,
                touched.contains("securityConfig") ? config.get("securityConfig") : null,
                touched.contains("promptConfig") ? config.get("promptConfig") : null,
                touched.contains("knowledgeSourceConfig") ? config.get("knowledgeSourceConfig") : null,
                touched.contains("shellConfig") ? config.get("shellConfig") : null,
                touched.contains("marketplaceDatasetConfig") ? config.get("marketplaceDatasetConfig") : null,
                touched.contains("behaviorConfig") ? config.get("behaviorConfig") : null));
            receipt = mapper.createObjectNode().put("resourceId", id).put("draftId", saved.id())
                .put("savedAt", saved.updatedAt().toString()).put("published", false);
        } else if ("PRIVATE_DATA_PLUGIN".equals(kind)) {
            var plugin = plugins.get(id, current.customerId());
            var saved = plugins.update(id, current.customerId(), new CustomerPluginLibraryContracts.Save(
                result.candidate().path("manifest"), plugin.revision()));
            receipt = mapper.createObjectNode().put("resourceId", id).put("revision", saved.revision()).put("published", false);
        } else {
            var app = apps.get(current.customerId(), id); var config = result.candidate();
            var saved = apps.update(current.customerId(), id, new UpdateAIWorkspaceInstallationRequest(
                app.consumerId(), config.path("displayName").asText(), app.experiencePackCode(), app.experiencePackVersion(),
                app.connectionMode(), app.connectionProfileCode(), app.connectionProfileVersion(), app.connectionConfiguration(),
                mapper.convertValue(config.path("allowedOrigins"), mapper.getTypeFactory().constructCollectionType(List.class, String.class)),
                config.path("configuration"), app.deliveryMode(), app.appSlug(),
                app.domains().stream().filter(domain -> "CUSTOMER_CUSTOM".equals(domain.type())).map(AIWorkspaceDomainSummary::hostname).toList(),
                app.interactionPolicy(), app.authProfileCode(), app.authProfileVersion(), config.path("authConfiguration"), app.rowVersion()));
            receipt = mapper.createObjectNode().put("resourceId", id).put("revision", saved.rowVersion()).put("published", false);
        }
        return new Saved(result.diff(), findings, receipt, changes.project(result.candidate()));
    }

    public List<String> paths(String kind, String task) {
        if ("PRIVATE_DATA_PLUGIN".equals(kind) && "DATA_PLUGIN".equals(task)) return List.of("/manifest");
        if ("ENGAGE_APP".equals(kind) && "ENGAGE".equals(task)) return List.of("/displayName", "/configuration", "/authConfiguration", "/allowedOrigins");
        if (!"DEPLOYMENT_DRAFT".equals(kind)) throw new ResponseStatusException(BAD_REQUEST, "The configuration task does not match this resource.");
        return switch (task) {
            case "ACTIONS" -> List.of("/actionsConfig", "/routingConfig");
            case "CONNECTIONS" -> List.of("/routingConfig");
            case "KNOWLEDGE" -> List.of("/entityConfig", "/knowledgeSourceConfig", "/marketplaceDatasetConfig");
            case "BEHAVIOUR" -> List.of("/promptConfig");
            case "MODELS" -> List.of("/providerConfig");
            case "HUMAN_SUPPORT" -> List.of("/behaviorConfig/conversationHandover", "/behaviorConfig/humanReview", "/behaviorConfig/executionExtensions");
            case "SPECIALISTS" -> List.of("/behaviorConfig/specialistAssistance", "/behaviorConfig/specialistBundles", "/behaviorConfig/triggers", "/behaviorConfig/executionExtensions");
            case "KIT" -> List.of();
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported configuration task.");
        };
    }
    private ResponseStatusException missing() { return new ResponseStatusException(NOT_FOUND, "Configuration resource not found."); }
    public record Snapshot(String customerId, String displayName, JsonNode configuration, ObjectNode catalogue,
                           List<String> allowedPaths, String version, boolean editable, String readOnlyReason) { }
    public record Saved(JsonNode diff, JsonNode findings, JsonNode receipt, JsonNode configuration) { }
}
