package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceReadinessService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.*;
import org.springframework.stereotype.Service;

import java.util.*;

import static com.ai.fabric.platform.backend.support.SupportContracts.*;

/** Explicit projections: never serialize entities, configuration, error text or provider responses. */
@Service
public class SupportResourceService {
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final AIWorkspaceInstallationRepository apps;
    private final PlatformConsumerRepository consumers;
    private final AIWorkspaceReadinessService readiness;
    private final VectorizationSourceConnectionRepository connections;
    private final VectorizationPlanRepository plans;
    private final VectorizationRunRepository runs;
    private final SupportDocumentStatusService documents;
    private final DeploymentDraftRepository drafts;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final ObjectMapper mapper;

    public SupportResourceService(DeploymentRepository deployments, DeploymentReleaseRepository releases,
        AIWorkspaceInstallationRepository apps, PlatformConsumerRepository consumers, AIWorkspaceReadinessService readiness,
        VectorizationSourceConnectionRepository connections, VectorizationPlanRepository plans, VectorizationRunRepository runs,
        SupportDocumentStatusService documents, DeploymentDraftRepository drafts, DeploymentVersionRepository versions,
        DeploymentTargetProfileRepository targets, ObjectMapper mapper) {
        this.deployments = deployments; this.releases = releases; this.apps = apps; this.consumers = consumers;
        this.readiness = readiness; this.connections = connections; this.plans = plans; this.runs = runs; this.documents = documents;
        this.drafts = drafts; this.versions = versions; this.targets = targets; this.mapper = mapper;
    }

    public void requireOwned(Requester owner, List<ResourceRef> refs) {
        for (var ref : refs) {
            switch (ref.type()) {
                case DEPLOYMENT -> deployment(owner, ref.id());
                case ENGAGE_APP -> app(owner, ref.id());
                case CONNECTION -> connection(owner, ref.id());
            }
        }
    }

    public ResourceDiagnostic read(Requester owner, ResourceRef ref) {
        return read(owner, ref, List.of(), null);
    }

    public ResourceDiagnostic read(Requester owner, ResourceRef ref, List<ResourceRef> selected, String principalId) {
        return switch (ref.type()) {
            case DEPLOYMENT -> deploymentStatus(owner, ref, selected, principalId);
            case ENGAGE_APP -> engageReadiness(owner, ref);
            case CONNECTION -> connectionStatus(owner, ref);
        };
    }

    private DeploymentEntity deployment(Requester owner, String id) {
        return deployments.findById(id).filter(d -> owner.customerId().equals(d.getCustomerId()) && d.getArchivedAt() == null)
            .orElseThrow(SupportPolicy::denied);
    }
    private AIWorkspaceInstallationEntity app(Requester owner, String id) {
        var app = apps.findByCustomerIdAndInstallationId(owner.customerId(), id).orElseThrow(SupportPolicy::denied);
        var consumer = consumers.findById(app.getConsumerEntityId()).orElseThrow(SupportPolicy::denied);
        if (!owner.customerId().equals(consumer.getCustomerId())) throw SupportPolicy.denied();
        if (consumer.getBoundDeploymentId() != null) deployment(owner, consumer.getBoundDeploymentId());
        return app;
    }
    private VectorizationSourceConnectionEntity connection(Requester owner, String id) {
        var source = connections.findById(id).filter(c -> owner.customerId().equals(c.getCustomerId())).orElseThrow(SupportPolicy::denied);
        var deployment = deployment(owner, source.getDeploymentId());
        if (!Objects.equals(deployment.getTenantId(), source.getTenantId())) throw SupportPolicy.denied();
        return source;
    }
    private ResourceDiagnostic deploymentStatus(Requester owner, ResourceRef ref, List<ResourceRef> selected, String principalId) {
        var d = deployment(owner, ref.id());
        var facts = new LinkedHashMap<String, Object>();
        facts.put("observationBasis", "PLATFORM_PERSISTED_STATE");
        facts.put("deploymentStatus", safe(d.getStatus(), Set.of("DRAFT", "VERSION_PUBLISHED", "APPLY_REQUESTED", "VERIFYING", "VERIFICATION_FAILED", "APPLY_FAILED", "ACTIVE", "READY", "APPLIED", "FAILED", "PROVISIONING", "DELETING", "DELETED")));
        var recent = releases.findByDeploymentIdOrderByCreatedAtDesc(d.getId());
        // Keep targets distinct: the most recently attempted release is not necessarily the serving release.
        facts.put("releases", recent.stream().limit(8).map(r -> {
            var release = new LinkedHashMap<String, Object>();
            release.put("releaseId", r.getId());
            put(release, "targetProfileId", r.getTargetProfileId());
            put(release, "versionId", r.getDeploymentVersionId());
            if (r.getTargetProfileId() != null) targets.findById(r.getTargetProfileId()).ifPresent(target ->
                release.put("environment", safe(target.getEnvironmentName(), Set.of("staging", "production"))));
            release.put("status", safe(r.getStatus(), Set.of("PENDING", "VALIDATING", "APPLYING", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED", "PRE_APPLY_BLOCKED", "FAILED")));
            release.put("verificationStatus", safe(r.getVerificationStatus(), Set.of("PENDING", "RUNNING", "PASSED", "FAILED", "NOT_RUN", "BLOCKED")));
            put(release, "appliedAt", r.getAppliedAt());
            put(release, "observedAt", r.getUpdatedAt());
            return Map.copyOf(release);
        }).toList());
        facts.put("liveHealthProbed", false);
        var integration = integrationStatus(owner, ref);
        facts.put("integrationConfiguration", Map.of("availability", integration.availability(), "facts", integration.facts()));
        if (principalId != null) {
            // Only a selected app supplies target/release authority; never follow the latest release or global runtime URL.
            var targets = new LinkedHashSet<DeploymentTargetReference>();
            for (var resource : selected) {
                if (resource.type() != ResourceType.ENGAGE_APP) continue;
                var selectedApp = app(owner, resource.id());
                var consumer = consumers.findById(selectedApp.getConsumerEntityId()).orElseThrow(SupportPolicy::denied);
                if (d.getId().equals(consumer.getBoundDeploymentId()) && consumer.getBoundTargetProfileId() != null
                    && consumer.getBoundReleaseId() != null) {
                    targets.add(new DeploymentTargetReference(d.getId(), consumer.getBoundTargetProfileId(), consumer.getBoundReleaseId()));
                }
            }
            facts.put("documentIndexing", targets.isEmpty()
                ? List.of(Map.of("availability", "NOT_APPLICABLE", "code", "SELECT_CONNECTED_APP_FOR_EXACT_TARGET"))
                : targets.stream().map(target -> documents.read(d, target, principalId)).toList());
        }
        put(facts, "stateUpdatedAt", d.getUpdatedAt());
        return new ResourceDiagnostic(ref, recent.isEmpty() ? Availability.INCOMPLETE : Availability.CURRENT, Map.copyOf(facts));
    }

    private ResourceDiagnostic integrationStatus(Requester owner, ResourceRef ref) {
        if (ref.type() != ResourceType.DEPLOYMENT) throw SupportPolicy.denied();
        var deployment = deployment(owner, ref.id());
        var draft = drafts.findTopByDeploymentIdOrderByRevisionNumberDesc(deployment.getId()).orElse(null);
        if (draft == null) return new ResourceDiagnostic(ref, Availability.UNAVAILABLE, Map.of("code", "NO_SAVED_DRAFT"));
        var routes = json(draft.getRoutingConfigJson()).path("actions");
        var definitions = new ArrayList<Map<String, Object>>();
        int total = 0, reads = 0, writes = 0;
        for (var action : json(draft.getActionsConfigJson()).path("actions")) {
            total++;
            String access = safe(action.path("accessMode").asText(), Set.of("READ", "READ_WRITE", "WRITE_ONLY"));
            if ("READ".equals(access) || "READ_WRITE".equals(access)) reads++;
            if ("WRITE_ONLY".equals(access) || "READ_WRITE".equals(access)) writes++;
            if (definitions.size() < 40) definitions.add(Map.of(
                "actionId", action.path("name").asText().substring(0, Math.min(128, action.path("name").asText().length())),
                "accessMode", access,
                "confirmationRequired", action.path("requiresConfirmation").asBoolean(false),
                "connectionRouteConfigured", action.path("route").isObject() || routes.path(action.path("name").asText()).isObject()));
        }
        var facts = new LinkedHashMap<String, Object>();
        facts.put("observationBasis", "SAVED_DRAFT_CONFIGURATION");
        facts.put("draftRevision", draft.getRevisionNumber());
        facts.put("capabilityCount", total); facts.put("readCapabilityCount", reads); facts.put("writeCapabilityCount", writes);
        facts.put("capabilities", definitions); facts.put("capabilitiesTruncated", total > definitions.size());
        facts.put("providerConnectivityProbed", false); facts.put("actionsExecuted", false);
        facts.put("guidance", "These are saved draft definitions, not proof that the serving release or upstream API works. Use Check setup for connection verification and Build with AI in AI capabilities for requested draft edits. No credentials or raw configuration are returned.");
        put(facts, "stateUpdatedAt", draft.getUpdatedAt());
        return new ResourceDiagnostic(ref, Availability.INCOMPLETE, Map.copyOf(facts));
    }

    private ResourceDiagnostic handoverStatus(Requester owner, ResourceRef ref) {
        if (ref.type() != ResourceType.ENGAGE_APP) throw SupportPolicy.denied();
        var installation = app(owner, ref.id());
        var consumer = consumers.findById(installation.getConsumerEntityId()).orElseThrow(SupportPolicy::denied);
        var facts = new LinkedHashMap<String, Object>();
        facts.put("observationBasis", "EXACT_APP_ASSIGNED_RELEASE_CONFIGURATION");
        facts.put("agentPresenceProbed", false); facts.put("messageDeliveryProbed", false); facts.put("ticketProviderProbed", false);
        facts.put("guidance", "Configuration does not establish agent availability or message delivery. Use the actual conversation or My requests for delivery receipts. Draft edits belong in Build with AI; human transfer remains the conversation's handover control.");
        if (consumer.getBoundDeploymentId() == null || consumer.getBoundReleaseId() == null || consumer.getBoundTargetProfileId() == null) {
            facts.put("code", "NO_VERIFIED_ASSIGNMENT");
            return new ResourceDiagnostic(ref, Availability.INCOMPLETE, Map.copyOf(facts));
        }
        deployment(owner, consumer.getBoundDeploymentId());
        var release = releases.findById(consumer.getBoundReleaseId()).filter(r -> consumer.getBoundDeploymentId().equals(r.getDeploymentId())
            && consumer.getBoundTargetProfileId().equals(r.getTargetProfileId())).orElseThrow(SupportPolicy::denied);
        facts.put("releaseId", release.getId()); facts.put("targetProfileId", consumer.getBoundTargetProfileId());
        if (!"APPLIED_VERIFIED".equals(release.getStatus()) || !"PASSED".equals(release.getVerificationStatus())) {
            facts.put("code", "ASSIGNED_RELEASE_NOT_VERIFIED");
            return new ResourceDiagnostic(ref, Availability.INCOMPLETE, Map.copyOf(facts));
        }
        var version = versions.findById(release.getDeploymentVersionId()).filter(v -> consumer.getBoundDeploymentId().equals(v.getDeploymentId()))
            .orElseThrow(SupportPolicy::denied);
        var handover = json(version.getBehaviorConfigJson()).path("conversationHandover");
        var config = json(installation.getConfigurationJson());
        facts.put("handoverEnabledInAssignedRelease", handover.path("enabled").asBoolean(false));
        facts.put("handoverVisibleInApp", config.path("capabilities").path("handover").asBoolean(false));
        facts.put("ticketsVisibleInApp", config.path("capabilities").path("tickets").asBoolean(false));
        facts.put("teamVisibleInApp", config.path("support").path("teamVisible").asBoolean(false));
        facts.put("userCanResumeAi", handover.path("userCanResumeAi").asBoolean(false));
        put(facts, "observedReleaseAt", release.getUpdatedAt());
        return new ResourceDiagnostic(ref, Availability.CURRENT, Map.copyOf(facts));
    }

    private JsonNode json(String value) {
        try {
            var result = mapper.readTree(value == null ? "{}" : value);
            if (result == null || !result.isObject()) throw SupportPolicy.unavailable();
            return result;
        }
        catch (Exception error) { throw SupportPolicy.unavailable(); }
    }
    private ResourceDiagnostic engageReadiness(Requester owner, ResourceRef ref) {
        var app = app(owner, ref.id());
        var result = readiness.evaluateLocal(app);
        var checks = result.checks().stream().limit(40).map(c -> Map.of(
            "code", safe(c.code(), READINESS_CODES), "status", safe(c.status(), Set.of("PASSED", "BLOCKED")))).toList();
        var facts = new LinkedHashMap<String, Object>();
        facts.put("installationStatus", app.getStatus() == null ? "UNKNOWN" : app.getStatus().name());
        facts.put("localReadinessPassed", result.ready());
        facts.put("checks", checks);
        facts.put("deliveryProbed", false);
        facts.put("providerConnectivityProbed", false);
        facts.put("observationBasis", "LOCAL_CONFIGURATION_ONLY");
        var handover = handoverStatus(owner, ref);
        facts.put("handoverConfiguration", Map.of("availability", handover.availability(), "facts", handover.facts()));
        facts.put("guidance", "Use explicit app readiness verification for support-provider, route and HTTPS checks. A blocked local-only external check is unverified, not proof of a failed live provider probe.");
        return new ResourceDiagnostic(ref, Availability.INCOMPLETE, Map.copyOf(facts));
    }
    private ResourceDiagnostic connectionStatus(Requester owner, ResourceRef ref) {
        var source = connection(owner, ref.id());
        var facts = new LinkedHashMap<String, Object>();
        facts.put("connectionStatus", safe(source.getStatus(), Set.of("ACTIVE", "DRAFT", "READY", "DISABLED", "ERROR", "FAILED", "CONNECTED")));
        put(facts, "connectionUpdatedAt", source.getUpdatedAt());
        var plan = plans.findByDeploymentId(source.getDeploymentId())
            .filter(p -> source.getId().equals(p.getSourceConnectionId()) && owner.customerId().equals(p.getCustomerId())
                && source.getTenantId().equals(p.getTenantId())).orElse(null);
        if (plan == null) {
            facts.put("code", "SYNC_PLAN_NOT_CONFIGURED");
            return new ResourceDiagnostic(ref, Availability.INCOMPLETE, Map.copyOf(facts));
        }
        facts.put("syncState", safe(plan.getSyncState(), Set.of("UNKNOWN", "IN_SYNC", "RUNNING", "SOURCE_EMPTY",
            "REINDEX_DEFERRED", "MANUALLY_CONFIRMED", "OUT_OF_DATE", "BOOTSTRAP_REQUIRED", "MIGRATION_REQUIRED", "NOT_CONFIGURED")));
        put(facts, "stateUpdatedAt", plan.getUpdatedAt());
        if (plan.getLastRunId() != null) {
            runs.findById(plan.getLastRunId()).filter(r -> source.getDeploymentId().equals(r.getDeploymentId())
                && plan.getId().equals(r.getPlanId()) && owner.customerId().equals(r.getCustomerId())
                && source.getTenantId().equals(r.getTenantId())).ifPresent(r -> {
                    facts.put("lastRunStatus", safe(r.getStatus(), RUN_STATUSES));
                    put(facts, "lastRunCompletedAt", r.getCompletedAt());
                });
        }
        if (plan.getLastSuccessfulRunId() != null) {
            runs.findById(plan.getLastSuccessfulRunId()).filter(r -> source.getDeploymentId().equals(r.getDeploymentId())
                && plan.getId().equals(r.getPlanId()) && owner.customerId().equals(r.getCustomerId())
                && source.getTenantId().equals(r.getTenantId()) && "COMPLETED".equals(r.getStatus()))
                .ifPresent(r -> put(facts, "lastSuccessfulSyncAt", r.getCompletedAt()));
        }
        facts.put("liveProviderProbed", false);
        return new ResourceDiagnostic(ref, Availability.CURRENT, Map.copyOf(facts));
    }
    private static final Set<String> RUN_STATUSES = Set.of("PENDING", "QUEUED", "RUNNING", "CLAIMED", "COMPLETED", "FAILED", "CANCELLED", "PAUSED", "PAUSE_REQUESTED", "RESUME_REQUESTED", "RETRY_REQUESTED", "CANCEL_REQUESTED");
    private static final Set<String> READINESS_CODES = Set.of("CONSUMER", "ASSIGNMENT", "ASSETS", "EXPERIENCE_PACK",
        "CONNECTION_PROFILE", "ORIGINS", "AUTHENTICATION_PROFILE", "APP_DOMAIN", "APP_DELIVERY", "RUNTIME_URL",
        "EXTERNAL_IDENTITY_BROKER", "EXTERNAL_IDENTITY_SCOPES", "EXTERNAL_IDENTITY_BINDING", "PUBLIC_TOKEN_KEY",
        "PRIVATE_ASSERTION_KEY", "PRIVATE_ASSERTION_POLICY", "RUNTIME_CORS", "ANONYMOUS_BOOTSTRAP", "PUBLIC_TOKEN_POLICY", "SUPPORT_SPACE");
    private static String safe(String value, Set<String> allowed) { return allowed.contains(value == null ? "" : value) ? value : "UNKNOWN"; }
    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
}
