package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.*;

import static org.springframework.http.HttpStatus.*;

/** Reviews workspace-required configuration; publication still uses the normal deployment service. */
@Service
public class AIWorkspaceDeploymentUpdateService {
    private static final Set<String> WORKSPACE_SECURITY = Set.of("corsAllowedOrigins", "aiWorkspaceManagedCorsOrigins",
        "aiWorkspaceManagedExternalIdentity", "externalIdentityBrokerEnabled", "externalIdentityProvider",
        "externalIdentityIssuer", "externalIdentityJwkSetUri", "externalIdentityAudience", "externalIdentityGrantedScopes",
        "externalIdentitySupabasePublicKey");
    private final AIWorkspaceInstallationRepository installations;
    private final PlatformConsumerRepository consumers;
    private final DeploymentRepository deployments;
    private final DeploymentDraftRepository drafts;
    private final PlatformCustomerConsumerService customerService;
    private final AIWorkspaceAssignmentService assignments;
    private final AIWorkspaceDeploymentSecurityProjectionService projection;
    private final DeploymentService deploymentService;
    private final ObjectMapper mapper;
    private final EntityManager entities;

    public AIWorkspaceDeploymentUpdateService(AIWorkspaceInstallationRepository installations,
        PlatformConsumerRepository consumers, DeploymentRepository deployments, DeploymentDraftRepository drafts,
        PlatformCustomerConsumerService customerService, AIWorkspaceAssignmentService assignments,
        AIWorkspaceDeploymentSecurityProjectionService projection, DeploymentService deploymentService, ObjectMapper mapper, EntityManager entities) {
        this.installations = installations; this.consumers = consumers; this.deployments = deployments; this.drafts = drafts;
        this.customerService = customerService; this.assignments = assignments; this.projection = projection;
        this.deploymentService = deploymentService; this.mapper = mapper; this.entities = entities;
    }

    @Transactional
    public Review review(String customerId, String installationId) {
        Context context = context(customerId, installationId);
        projection.synchronizeDeployment(context.deployment().getId());
        DeploymentDraftEntity draft = drafts.findByIdForUpdate(context.deployment().getActiveDraftId())
            .filter(value -> context.deployment().getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> conflict("The deployment has no editable draft."));
        if ("PUBLISHED".equals(draft.getStatus())) throw conflict("The active draft is already published.");
        var baseline = drafts.findById(context.assignment().version().getSourceDraftId())
            .filter(value -> value.getDeploymentId().equals(draft.getDeploymentId()) && "PUBLISHED".equals(value.getStatus()))
            .orElseThrow(() -> conflict("The assigned release's configuration snapshot is unavailable."));
        var current = sections(draft);
        var previous = sections(baseline);
        List<String> changed = current.keySet().stream().filter(key -> !current.get(key).equals(previous.get(key))).toList();
        JsonNode security = current.get("Security"), live = context.assignment().securityConfig();
        Set<String> fields = new TreeSet<>(); security.fieldNames().forEachRemaining(fields::add); live.fieldNames().forEachRemaining(fields::add);
        List<String> securityFields = fields.stream().filter(key -> !Objects.equals(security.get(key), live.get(key))).toList();
        var desiredOrigins = csv(security.path("corsAllowedOrigins").asText());
        var liveOrigins = csv(live.path("corsAllowedOrigins").asText());
        var apps = sharedApps(context.consumer()).stream().map(AIWorkspaceInstallationEntity::getDisplayName).toList();
        return new Review(customerId, installationId, context.consumer().getConsumerId(), context.deployment().getId(),
            context.deployment().getName(), context.assignment().deployment().getEnvironmentName(), draft.getId(),
            fingerprint(context, draft), context.consumer().getBindingRevision(), context.assignment().release().getId(),
            context.assignment().release().getTargetProfileId(), context.assignment().release().getSourceArtifactId(),
            changed, securityFields, desiredOrigins.stream().filter(value -> !liveOrigins.contains(value)).toList(),
            liveOrigins.stream().filter(value -> !desiredOrigins.contains(value)).toList(), apps,
            changed.stream().anyMatch(key -> !"Security".equals(key)) || securityFields.stream().anyMatch(key -> !WORKSPACE_SECURITY.contains(key)));
    }

    @Transactional
    public DeploymentVersionSummary publish(String customerId, String installationId, ReviewedPublish request) {
        if (request == null || request.draftId() == null || request.fingerprint() == null
            || !request.fingerprint().matches("[a-f0-9]{64}")) throw new ResponseStatusException(BAD_REQUEST, "A reviewed draft is required.");
        Context context = context(customerId, installationId);
        DeploymentDraftEntity draft = drafts.findByIdForUpdate(request.draftId())
            .filter(value -> context.deployment().getId().equals(value.getDeploymentId()))
            .orElseThrow(() -> conflict("The reviewed draft is unavailable."));
        if (!"PUBLISHED".equals(draft.getStatus())) {
            if (!draft.getId().equals(context.deployment().getActiveDraftId())) throw conflict("The active draft changed. Review again.");
            projection.synchronizeDeployment(context.deployment().getId());
        }
        // This protects approval of an exact configuration and assignment, not AI semantic output.
        if (request.expectedBindingRevision() != context.consumer().getBindingRevision()
            || !request.fingerprint().equals(fingerprint(context, draft))) throw conflict("The draft, app or assignment changed. Review again.");
        if ("PUBLISHED".equals(draft.getStatus())) {
            return deploymentService.listVersions(context.deployment().getId()).stream()
                .filter(version -> draft.getId().equals(version.sourceDraftId())).findFirst()
                .orElseThrow(() -> conflict("Publication needs reconciliation; do not publish another draft."));
        }
        return deploymentService.publishDraft(draft.getId());
    }

    private Context context(String customerId, String installationId) {
        customerService.listConsumers(customerId); // Existing customer/account authorization, before resource lookup.
        var installation = installations.findByCustomerIdAndInstallationId(customerId, installationId)
            .filter(value -> value.getStatus() != AIWorkspaceInstallationStatus.DISABLED)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Owned app not found."));
        var observed = consumers.findById(installation.getConsumerEntityId()).orElseThrow(() -> conflict("App connection is unavailable."));
        var consumer = consumers.findBindingForUpdate(customerId, observed.getConsumerId())
            .filter(value -> value.getId().equals(installation.getConsumerEntityId()))
            .orElseThrow(() -> conflict("App connection changed."));
        entities.refresh(consumer); entities.refresh(installation);
        if (!consumer.getId().equals(installation.getConsumerEntityId()) || installation.getStatus() == AIWorkspaceInstallationStatus.DISABLED
            || !"ACTIVE".equals(consumer.getStatus()) || consumer.getBoundDeploymentId() == null) throw conflict("App connection changed or is not deployed.");
        var deployment = deployments.findByIdForUpdate(consumer.getBoundDeploymentId())
            .filter(value -> customerId.equals(value.getCustomerId()))
            .orElseThrow(() -> conflict("Assigned deployment is unavailable."));
        try { return new Context(installation, consumer, deployment, assignments.resolveCurrent(consumer)); }
        catch (IllegalStateException error) { throw conflict("A verified app assignment is required before updating its deployment."); }
    }

    private List<AIWorkspaceInstallationEntity> sharedApps(PlatformConsumerEntity consumer) {
        var connectionIds = consumers.findByBoundDeploymentIdOrderByUpdatedAtDesc(consumer.getBoundDeploymentId()).stream()
            .filter(value -> consumer.getCustomerId().equals(value.getCustomerId()))
            .map(PlatformConsumerEntity::getId).distinct().toList();
        if (connectionIds.isEmpty()) return List.of();
        return installations.findByConsumerEntityIdInAndStatusIn(connectionIds,
            List.of(AIWorkspaceInstallationStatus.ACTIVE, AIWorkspaceInstallationStatus.DRAFT)).stream()
            .sorted(Comparator.comparing(AIWorkspaceInstallationEntity::getId)).toList();
    }

    private String fingerprint(Context context, DeploymentDraftEntity draft) {
        try {
            var content = mapper.createObjectNode();
            content.set("configuration", mapper.valueToTree(sections(draft)));
            content.put("draftId", draft.getId()); content.put("revision", draft.getRevisionNumber());
            content.put("entityContract", draft.getEntityConfigContractVersion());
            content.put("consumerId", context.consumer().getId()); content.put("bindingRevision", context.consumer().getBindingRevision());
            content.put("releaseId", context.consumer().getBoundReleaseId()); content.put("targetId", context.consumer().getBoundTargetProfileId());
            content.put("installationId", context.installation().getId()); content.put("installationVersion", context.installation().getRowVersion());
            content.set("sharedApps", mapper.valueToTree(sharedApps(context.consumer()).stream()
                .map(app -> List.of(app.getId(), Long.toString(app.getRowVersion()), app.getStatus().name())).toList()));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(content)));
        } catch (Exception error) { throw new IllegalStateException("Unable to identify the reviewed configuration.", error); }
    }

    private Map<String, JsonNode> sections(DeploymentDraftEntity draft) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        result.put("Actions", json(draft.getActionsConfigJson())); result.put("Entities", json(draft.getEntityConfigJson()));
        result.put("Routing", json(draft.getRoutingConfigJson())); result.put("Providers", json(draft.getProviderConfigJson()));
        result.put("Security", json(draft.getSecurityConfigJson())); result.put("Prompts", json(draft.getPromptConfigJson()));
        result.put("Knowledge sources", json(draft.getKnowledgeSourceConfigJson())); result.put("Workspace shell", json(draft.getShellConfigJson()));
        result.put("Marketplace datasets", json(draft.getMarketplaceDatasetConfigJson())); result.put("Behaviour", json(draft.getBehaviorConfigJson()));
        return result;
    }
    private JsonNode json(String raw) {
        try {
            JsonNode value = mapper.readTree(raw == null ? "{}" : raw);
            if (value == null) throw new IllegalArgumentException();
            return value;
        }
        catch (Exception error) { throw conflict("The configuration cannot be reviewed."); }
    }
    private List<String> csv(String raw) { return Arrays.stream(raw.split(",")).map(String::trim).filter(value -> !value.isEmpty()).distinct().sorted().toList(); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(CONFLICT, message); }
    private record Context(AIWorkspaceInstallationEntity installation, PlatformConsumerEntity consumer,
                           DeploymentEntity deployment, AIWorkspaceAssignmentService.Assignment assignment) { }
    public record ReviewedPublish(String draftId, String fingerprint, long expectedBindingRevision) { }
    public record Review(String customerId, String installationId, String consumerId, String deploymentId,
                         String deploymentName, String environment, String draftId, String fingerprint,
                         long expectedBindingRevision, String releaseId, String targetProfileId, String sourceArtifactId,
                         List<String> changedSections, List<String> changedSecurityFields, List<String> addedOrigins,
                         List<String> removedOrigins, List<String> affectedApps, boolean unrelatedChanges) { }
}
