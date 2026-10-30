package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.aiworkspace.service.AIWorkspaceReadinessService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
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

    public SupportResourceService(DeploymentRepository deployments, DeploymentReleaseRepository releases,
        AIWorkspaceInstallationRepository apps, PlatformConsumerRepository consumers, AIWorkspaceReadinessService readiness,
        VectorizationSourceConnectionRepository connections, VectorizationPlanRepository plans, VectorizationRunRepository runs) {
        this.deployments = deployments; this.releases = releases; this.apps = apps; this.consumers = consumers;
        this.readiness = readiness; this.connections = connections; this.plans = plans; this.runs = runs;
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
        return switch (ref.type()) {
            case DEPLOYMENT -> deploymentStatus(owner, ref);
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
    private ResourceDiagnostic deploymentStatus(Requester owner, ResourceRef ref) {
        var d = deployment(owner, ref.id());
        var facts = new LinkedHashMap<String, Object>();
        facts.put("observationBasis", "PLATFORM_PERSISTED_STATE");
        facts.put("deploymentStatus", safe(d.getStatus(), Set.of("DRAFT", "ACTIVE", "READY", "APPLIED", "FAILED", "PROVISIONING", "DELETING", "DELETED")));
        var recent = releases.findByDeploymentIdOrderByCreatedAtDesc(d.getId());
        // Keep targets distinct: the most recently attempted release is not necessarily the serving release.
        facts.put("releases", recent.stream().limit(8).map(r -> {
            var release = new LinkedHashMap<String, Object>();
            release.put("releaseId", r.getId());
            put(release, "targetProfileId", r.getTargetProfileId());
            release.put("status", safe(r.getStatus(), Set.of("PENDING", "VALIDATING", "APPLYING", "PROVISIONING", "VERIFYING", "APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED", "PRE_APPLY_BLOCKED", "FAILED")));
            release.put("verificationStatus", safe(r.getVerificationStatus(), Set.of("PENDING", "RUNNING", "PASSED", "FAILED", "NOT_RUN", "BLOCKED")));
            put(release, "appliedAt", r.getAppliedAt());
            put(release, "observedAt", r.getUpdatedAt());
            return Map.copyOf(release);
        }).toList());
        facts.put("liveHealthProbed", false);
        put(facts, "stateUpdatedAt", d.getUpdatedAt());
        return new ResourceDiagnostic(ref, recent.isEmpty() ? Availability.INCOMPLETE : Availability.CURRENT, Map.copyOf(facts));
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
        facts.put("guidance", "Use the app readiness screen for explicit route and HTTPS verification.");
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
        "PRIVATE_ASSERTION_KEY", "PRIVATE_ASSERTION_POLICY", "RUNTIME_CORS", "ANONYMOUS_BOOTSTRAP", "PUBLIC_TOKEN_POLICY");
    private static String safe(String value, Set<String> allowed) { return allowed.contains(value == null ? "" : value) ? value : "UNKNOWN"; }
    private static void put(Map<String, Object> map, String key, Object value) { if (value != null) map.put(key, value); }
}
