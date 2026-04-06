package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.TenantScopedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.ManagedDeploymentProfileCatalog;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationStepEntity;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationVerificationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPreviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationRunDetailsSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationStepSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationStepRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class VectorizationVerificationService {

    private static final Set<String> VERIFICATION_TYPES = Set.of(
        "CONTROL_PLANE_READINESS",
        "RUNNER_PROVISIONING_SMOKE",
        "SOURCE_DISCOVERY_SMOKE",
        "SAMPLE_VECTORIZATION_SMOKE",
        "SYNC_STATE_AND_REINDEX_SMOKE",
        "TENANT_SHARED_ISOLATION_SMOKE",
        "RUNNER_COMPATIBILITY_SMOKE"
    );
    private static final Set<String> TERMINAL_STATUSES = Set.of("PASSED", "FAILED", "CANCELLED");
    private static final Set<String> LINKED_RUN_ACTIVE_STATUSES = Set.of(
        "QUEUED",
        "CLAIMED",
        "RUNNING",
        "PAUSE_REQUESTED",
        "RESUME_REQUESTED",
        "RETRY_REQUESTED"
    );

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformAuditService platformAuditService;
    private final TenantScopedVectorResourceRepository tenantScopedVectorResourceRepository;
    private final VectorizationVerificationRunRepository verificationRunRepository;
    private final VectorizationVerificationStepRepository verificationStepRepository;
    private final VectorizationRunRepository runRepository;
    private final VectorizationService vectorizationService;
    private final VectorizationRuntimeCoverageClient runtimeCoverageClient;
    private final VectorizationVerificationProbeService vectorizationVerificationProbeService;
    private final VectorizationJsonSupport jsonSupport;

    public VectorizationVerificationService(DeploymentRepository deploymentRepository,
                                            DeploymentAccessService deploymentAccessService,
                                            PlatformAuditService platformAuditService,
                                            TenantScopedVectorResourceRepository tenantScopedVectorResourceRepository,
                                            VectorizationVerificationRunRepository verificationRunRepository,
                                            VectorizationVerificationStepRepository verificationStepRepository,
                                            VectorizationRunRepository runRepository,
                                            VectorizationService vectorizationService,
                                            VectorizationRuntimeCoverageClient runtimeCoverageClient,
                                            VectorizationVerificationProbeService vectorizationVerificationProbeService,
                                            VectorizationJsonSupport jsonSupport) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
        this.tenantScopedVectorResourceRepository = tenantScopedVectorResourceRepository;
        this.verificationRunRepository = verificationRunRepository;
        this.verificationStepRepository = verificationStepRepository;
        this.runRepository = runRepository;
        this.vectorizationService = vectorizationService;
        this.runtimeCoverageClient = runtimeCoverageClient;
        this.vectorizationVerificationProbeService = vectorizationVerificationProbeService;
        this.jsonSupport = jsonSupport;
    }

    @Transactional
    public List<VectorizationVerificationRunSummary> listRuns(String deploymentId) {
        requireDeploymentOperator(deploymentId);
        return verificationRunRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
            .map(this::refreshLinkedRunState)
            .map(this::summarizeRun)
            .toList();
    }

    @Transactional
    public VectorizationVerificationRunSummary createRun(String deploymentId,
                                                         CreateVectorizationVerificationRunRequest request) {
        DeploymentEntity deployment = requireDeploymentOperator(deploymentId);
        String verificationType = normalizeVerificationType(request.verificationType());
        Instant now = Instant.now();
        VectorizationVerificationRunEntity verificationRun = new VectorizationVerificationRunEntity();
        verificationRun.setId(generateId("vvr"));
        verificationRun.setDeploymentId(deployment.getId());
        verificationRun.setCustomerId(deployment.getCustomerId());
        verificationRun.setTenantId(deployment.getTenantId());
        verificationRun.setVerificationType(verificationType);
        verificationRun.setExecutionMode(executionModeFor(verificationType));
        verificationRun.setStatus("RUNNING");
        verificationRun.setEntityScopeJson(jsonSupport.write(writeEntityScope(request.entityTypes())));
        verificationRun.setSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
        verificationRun.setRequestedByActorId(currentActorId());
        verificationRun.setRequestNote(trimToNull(request.note()));
        verificationRun.setCreatedAt(now);
        verificationRun.setStartedAt(now);
        verificationRun.setUpdatedAt(now);
        verificationRunRepository.save(verificationRun);

        VectorizationVerificationRunEntity persisted = switch (verificationType) {
            case "CONTROL_PLANE_READINESS" -> executeControlPlaneReadiness(deploymentId, verificationRun, now);
            case "RUNNER_PROVISIONING_SMOKE" -> executeRunnerProvisioningSmoke(deploymentId, verificationRun, now);
            case "SOURCE_DISCOVERY_SMOKE" -> triggerDiscoverySmoke(deploymentId, verificationRun, request.entityTypes(), now);
            case "SAMPLE_VECTORIZATION_SMOKE" -> triggerSampleVectorizationSmoke(deployment, verificationRun, request.entityTypes(), now);
            case "SYNC_STATE_AND_REINDEX_SMOKE" -> executeSyncStateAndReindexSmoke(deploymentId, verificationRun, now);
            case "TENANT_SHARED_ISOLATION_SMOKE" -> executeTenantSharedIsolationSmoke(deployment, verificationRun, request.counterpartDeploymentId(), request.entityTypes(), now);
            case "RUNNER_COMPATIBILITY_SMOKE" -> executeRunnerCompatibilitySmoke(deploymentId, verificationRun, now);
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization verification type: " + request.verificationType());
        };

        platformAuditService.record(
            "VECTORIZATION_VERIFICATION_CREATED",
            "DEPLOYMENT",
            deploymentId,
            java.util.Map.of(
                "deploymentId", deploymentId,
                "verificationRunId", persisted.getId(),
                "verificationType", verificationType,
                "executionMode", persisted.getExecutionMode()
            )
        );

        return summarizeRun(persisted);
    }

    @Transactional
    public VectorizationVerificationRunDetailsSummary getRunDetails(String deploymentId, String verificationRunId) {
        requireDeploymentOperator(deploymentId);
        VectorizationVerificationRunEntity verificationRun = requireRun(deploymentId, verificationRunId);
        VectorizationVerificationRunEntity refreshed = refreshLinkedRunState(verificationRun);
        List<VectorizationVerificationStepSummary> steps = verificationStepRepository.findByVerificationRunIdOrderByCreatedAtAsc(refreshed.getId()).stream()
            .map(this::summarizeStep)
            .toList();
        VectorizationRunSummary linkedRun = null;
        if (StringUtils.hasText(refreshed.getLinkedVectorizationRunId())) {
            linkedRun = runRepository.findById(refreshed.getLinkedVectorizationRunId())
                .map(vectorizationService::summarizeRun)
                .orElse(null);
        }
        return new VectorizationVerificationRunDetailsSummary(
            deploymentId,
            summarizeRun(refreshed),
            steps,
            linkedRun
        );
    }

    private VectorizationVerificationRunEntity executeControlPlaneReadiness(String deploymentId,
                                                                            VectorizationVerificationRunEntity verificationRun,
                                                                            Instant now) {
        VectorizationOverviewSummary overview;
        try {
            overview = vectorizationService.getOverview(deploymentId);
            recordStep(verificationRun, "overview_loadable", "Overview loads", "PASSED", jsonSupport.objectNode()
                .put("deploymentId", deploymentId)
                .put("activeVersionId", overview.activeVersionId() == null ? "" : overview.activeVersionId()));
        } catch (ResponseStatusException ex) {
            return failVerification(verificationRun, now, "OVERVIEW_UNAVAILABLE", ex.getReason());
        }

        try {
            VectorizationPreviewSummary preview = vectorizationService.preview(deploymentId);
            ObjectNode details = jsonSupport.objectNode();
            details.put("syncState", preview.syncState());
            details.set("reindexOptions", asObject(preview.reindexOptions()));
            recordStep(verificationRun, "preview_loadable", "Preview loads", "PASSED", details);
        } catch (ResponseStatusException ex) {
            return failVerification(verificationRun, now, "PREVIEW_UNAVAILABLE", ex.getReason());
        }

        boolean failed = false;
        failed |= !assertCondition(verificationRun, "plan_present", "Plan configured", overview.plan() != null,
            detail("planStatus", overview.plan() == null ? "MISSING" : overview.plan().status()));
        failed |= !assertCondition(verificationRun, "source_connection_ready", "Source connection ready",
            overview.sourceConnection() != null && "READY".equalsIgnoreCase(overview.sourceConnection().status()),
            detail("sourceConnectionStatus", overview.sourceConnection() == null ? "MISSING" : overview.sourceConnection().status()));
        failed |= !assertCondition(verificationRun, "active_revision_present", "Active revision present",
            overview.plan() != null && overview.plan().activeRevision() != null,
            detail("activeRevisionId", overview.plan() == null || overview.plan().activeRevision() == null ? "" : overview.plan().activeRevision().id()));
        failed |= !assertCondition(verificationRun, "sync_state_resolved", "Sync state resolved",
            overview.plan() != null && StringUtils.hasText(overview.plan().syncState()),
            detail("syncState", overview.plan() == null ? "" : overview.plan().syncState()));

        return completeVerification(
            verificationRun,
            now,
            failed ? "FAILED" : "PASSED",
            jsonSupport.objectNode()
                .put("planPresent", overview.plan() != null)
                .put("sourceConnectionPresent", overview.sourceConnection() != null)
                .put("runnerPresent", overview.runner() != null)
                .put("syncState", overview.plan() == null ? "" : overview.plan().syncState())
        );
    }

    private VectorizationVerificationRunEntity executeRunnerProvisioningSmoke(String deploymentId,
                                                                              VectorizationVerificationRunEntity verificationRun,
                                                                              Instant now) {
        VectorizationOverviewSummary overview = vectorizationService.getOverview(deploymentId);
        VectorizationRunnerSummary runner = overview.runner();

        boolean failed = false;
        failed |= !assertCondition(verificationRun, "runner_registered", "Runner registration exists",
            runner != null, detail("registrationStatus", runner == null ? "MISSING" : runner.registrationStatus()));

        if (runner == null) {
            return completeVerification(
                verificationRun,
                now,
                "FAILED",
                jsonSupport.objectNode().put("reason", "RUNNER_REGISTRATION_MISSING")
            );
        }

        failed |= !assertCondition(verificationRun, "runner_registration_active", "Runner registration is active",
            "ACTIVE".equalsIgnoreCase(runner.registrationStatus()),
            detail("registrationStatus", runner.registrationStatus()));
        failed |= !assertCondition(verificationRun, "runner_compatibility_current", "Runner compatibility is current",
            "CURRENT".equalsIgnoreCase(runner.compatibilityStatus()),
            detail("compatibilityStatus", runner.compatibilityStatus()));
        failed |= !assertCondition(verificationRun, "runner_session_connected", "Runner has an active session",
            runner.lastSessionExpiresAt() != null && runner.lastSessionExpiresAt().isAfter(Instant.now()),
            detail("lastSessionExpiresAt", runner.lastSessionExpiresAt() == null ? "" : runner.lastSessionExpiresAt().toString()));

        if ("PLATFORM_MANAGED_AUTO".equalsIgnoreCase(runner.runnerMode())) {
            failed |= !assertCondition(verificationRun, "platform_managed_runner_connected", "Platform-managed runner connected",
                StringUtils.hasText(runner.runnerInstanceId()),
                detail("runnerInstanceId", runner.runnerInstanceId() == null ? "" : runner.runnerInstanceId()));
        }

        ObjectNode summary = jsonSupport.objectNode();
        summary.put("runnerMode", runner.runnerMode());
        summary.put("registrationStatus", runner.registrationStatus());
        summary.put("compatibilityStatus", runner.compatibilityStatus());
        summary.put("runnerInstanceId", runner.runnerInstanceId() == null ? "" : runner.runnerInstanceId());
        if (runner.lastSessionExpiresAt() != null) {
            summary.put("lastSessionExpiresAt", runner.lastSessionExpiresAt().toString());
        }
        return completeVerification(verificationRun, now, failed ? "FAILED" : "PASSED", summary);
    }

    private VectorizationVerificationRunEntity triggerDiscoverySmoke(String deploymentId,
                                                                     VectorizationVerificationRunEntity verificationRun,
                                                                     List<String> entityTypes,
                                                                     Instant now) {
        VectorizationRunSummary run = vectorizationService.createRun(
            deploymentId,
            new CreateVectorizationRunRequest("DISCOVERY", entityTypes, "Created by vectorization verification smoke.", jsonSupport.objectNode())
        );
        verificationRun.setLinkedVectorizationRunId(run.id());
        verificationRun.setEntityScopeJson(jsonSupport.write(writeStringArray(run.entityScope())));
        verificationRun.setSummaryJson(jsonSupport.write(jsonSupport.objectNode()
            .put("linkedVectorizationRunId", run.id())
            .put("linkedRunStatus", run.status())
            .put("verificationKind", "DISCOVERY_SMOKE")));
        verificationRun.setUpdatedAt(now);
        verificationRunRepository.save(verificationRun);
        recordStep(
            verificationRun,
            "linked_vectorization_run",
            "Linked discovery run created",
            "RUNNING",
            jsonSupport.objectNode()
                .put("linkedVectorizationRunId", run.id())
                .put("linkedRunStatus", run.status())
        );
        return verificationRun;
    }

    private VectorizationVerificationRunEntity triggerSampleVectorizationSmoke(DeploymentEntity deployment,
                                                                               VectorizationVerificationRunEntity verificationRun,
                                                                               List<String> entityTypes,
                                                                               Instant now) {
        VectorizationOverviewSummary overview = vectorizationService.getOverview(deployment.getId());
        VectorizationPreviewSummary preview = vectorizationService.preview(deployment.getId());
        VectorizationPlanSummary plan = overview.plan();
        VectorizationRunnerSummary runner = overview.runner();

        if (plan == null || plan.activeRevision() == null) {
            return failVerification(verificationRun, now, "PLAN_NOT_READY", "Vectorization plan with an active revision is required.");
        }
        if (overview.sourceConnection() == null || !"READY".equalsIgnoreCase(overview.sourceConnection().status())) {
            return failVerification(verificationRun, now, "SOURCE_CONNECTION_NOT_READY", "A ready vectorization source connection is required.");
        }
        if (runner == null || !"ACTIVE".equalsIgnoreCase(runner.registrationStatus())) {
            return failVerification(verificationRun, now, "RUNNER_NOT_READY", "A connected vectorization runner is required for sample verification.");
        }

        List<String> scope = normalizedRequestedScope(entityTypes, plan.activeRevision().entityScope(), preview.reindexOptions());
        if (scope.isEmpty()) {
            return failVerification(verificationRun, now, "ENTITY_SCOPE_REQUIRED", "Select at least one configured entity for sample vectorization.");
        }
        if (allSelectedEntitiesSourceEmpty(preview.sourceDiscovery(), scope)) {
            return failVerification(verificationRun, now, "SOURCE_EMPTY", "Selected entities have no discovered source rows for sample vectorization.");
        }

        ObjectNode executionOverrides = jsonSupport.objectNode();
        executionOverrides.put("batchSize", 1);
        executionOverrides.put("pageSize", 1);
        executionOverrides.put("maxPagesPerEntity", 1);
        executionOverrides.put("maxRecordsPerEntity", 1);
        executionOverrides.put("verificationType", "SAMPLE_VECTORIZATION_SMOKE");

        JsonNode initialCounts = runtimeCoverageClient.fetchCounts(deployment);
        String reason = sampleRunReason(preview.syncState());
        VectorizationRunSummary run = vectorizationService.createRun(
            deployment.getId(),
            new CreateVectorizationRunRequest(reason, scope, "Created by vectorization sample smoke verification.", executionOverrides)
        );
        verificationRun.setLinkedVectorizationRunId(run.id());
        verificationRun.setEntityScopeJson(jsonSupport.write(writeStringArray(run.entityScope())));
        verificationRun.setSummaryJson(jsonSupport.write(jsonSupport.objectNode()
            .put("linkedVectorizationRunId", run.id())
            .put("linkedRunStatus", run.status())
            .put("verificationKind", "SAMPLE_VECTORIZATION_SMOKE")
            .put("requestedRunReason", reason)
            .set("initialLiveCounts", defaultObject(initialCounts))));
        verificationRun.setUpdatedAt(now);
        verificationRunRepository.save(verificationRun);
        ObjectNode linkedRunDetails = jsonSupport.objectNode()
            .put("linkedVectorizationRunId", run.id())
            .put("linkedRunStatus", run.status())
            .put("requestedRunReason", reason);
        linkedRunDetails.set("entityScope", writeStringArray(run.entityScope()));
        linkedRunDetails.set("executionOverrides", executionOverrides);
        recordStep(
            verificationRun,
            "linked_vectorization_run",
            "Linked sample vectorization run created",
            "RUNNING",
            linkedRunDetails
        );
        return verificationRun;
    }

    private VectorizationVerificationRunEntity executeSyncStateAndReindexSmoke(String deploymentId,
                                                                               VectorizationVerificationRunEntity verificationRun,
                                                                               Instant now) {
        VectorizationOverviewSummary overview = vectorizationService.getOverview(deploymentId);
        VectorizationPreviewSummary preview = vectorizationService.preview(deploymentId);
        VectorizationPlanSummary plan = overview.plan();
        JsonNode reindexOptions = defaultObject(preview.reindexOptions());

        boolean failed = false;
        failed |= !assertCondition(
            verificationRun,
            "plan_present",
            "Plan configured",
            plan != null && plan.activeRevision() != null,
            jsonSupport.objectNode().put("syncState", plan == null ? "" : plan.syncState())
        );
        failed |= !assertCondition(
            verificationRun,
            "sync_state_matches_preview",
            "Preview sync state matches overview",
            plan != null && StringUtils.hasText(plan.syncState()) && plan.syncState().equalsIgnoreCase(preview.syncState()),
            jsonSupport.objectNode()
                .put("overviewSyncState", plan == null ? "" : plan.syncState())
                .put("previewSyncState", preview.syncState())
        );
        failed |= !assertCondition(
            verificationRun,
            "reindex_options_present",
            "Reindex options are available",
            reindexOptions.path("supportsSelectedEntities").asBoolean(false)
                && reindexOptions.path("supportsFullDeployment").asBoolean(false)
                && reindexOptions.path("supportsDefer").asBoolean(false),
            asObject(reindexOptions)
        );
        failed |= !assertCondition(
            verificationRun,
            "sync_reason_details_present",
            "Sync reason details are persisted",
            plan != null && plan.syncReasonDetails() != null && plan.syncReasonDetails().isObject(),
            plan == null ? jsonSupport.objectNode() : asObject(plan.syncReasonDetails())
        );

        ObjectNode summary = jsonSupport.objectNode();
        summary.put("syncState", plan == null ? preview.syncState() : plan.syncState());
        summary.set("reindexOptions", asObject(reindexOptions));
        if (plan != null && plan.syncReasonDetails() != null) {
            summary.set("syncReasonDetails", asObject(plan.syncReasonDetails()));
        }
        return completeVerification(verificationRun, now, failed ? "FAILED" : "PASSED", summary);
    }

    private VectorizationVerificationRunEntity executeRunnerCompatibilitySmoke(String deploymentId,
                                                                               VectorizationVerificationRunEntity verificationRun,
                                                                               Instant now) {
        VectorizationOverviewSummary overview = vectorizationService.getOverview(deploymentId);
        VectorizationRunnerSummary runner = overview.runner();
        boolean failed = false;
        failed |= !assertCondition(
            verificationRun,
            "runner_present",
            "Runner registration exists",
            runner != null,
            detail("registrationStatus", runner == null ? "MISSING" : runner.registrationStatus())
        );
        if (runner == null) {
            return completeVerification(verificationRun, now, "FAILED", jsonSupport.objectNode().put("reason", "RUNNER_REGISTRATION_MISSING"));
        }
        failed |= !assertCondition(
            verificationRun,
            "runner_product_version_present",
            "Runner product version reported",
            StringUtils.hasText(runner.productVersion()),
            detail("productVersion", runner.productVersion())
        );
        failed |= !assertCondition(
            verificationRun,
            "runner_compatibility_version_present",
            "Runner compatibility version reported",
            StringUtils.hasText(runner.compatibilityVersion()),
            detail("compatibilityVersion", runner.compatibilityVersion())
        );
        failed |= !assertCondition(
            verificationRun,
            "runner_compatibility_accepted",
            "Runner compatibility is not incompatible",
            !"INCOMPATIBLE".equalsIgnoreCase(runner.compatibilityStatus()),
            detail("compatibilityStatus", runner.compatibilityStatus())
        );
        ObjectNode summary = jsonSupport.objectNode();
        summary.put("productVersion", runner.productVersion() == null ? "" : runner.productVersion());
        summary.put("compatibilityVersion", runner.compatibilityVersion() == null ? "" : runner.compatibilityVersion());
        summary.put("compatibilityStatus", runner.compatibilityStatus() == null ? "" : runner.compatibilityStatus());
        summary.put("registrationStatus", runner.registrationStatus() == null ? "" : runner.registrationStatus());
        return completeVerification(verificationRun, now, failed ? "FAILED" : "PASSED", summary);
    }

    private VectorizationVerificationRunEntity executeTenantSharedIsolationSmoke(DeploymentEntity deployment,
                                                                                 VectorizationVerificationRunEntity verificationRun,
                                                                                 String counterpartDeploymentId,
                                                                                 List<String> entityTypes,
                                                                                 Instant now) {
        requirePlatformAdmin();
        TenantScopedVectorResourceEntity primaryHandle = requireActiveSharedHandle(deployment);
        DeploymentEntity counterpart = resolveCounterpartDeployment(primaryHandle, deployment, counterpartDeploymentId);
        TenantScopedVectorResourceEntity counterpartHandle = requireActiveSharedHandle(counterpart);
        String entityType = resolveIsolationEntityType(deployment, counterpart, entityTypes);

        boolean sameRoot = StringUtils.hasText(primaryHandle.getRootResourceValue())
            && primaryHandle.getRootResourceValue().equalsIgnoreCase(counterpartHandle.getRootResourceValue())
            && primaryHandle.getVectorStrategy().equalsIgnoreCase(counterpartHandle.getVectorStrategy());
        if (!sameRoot) {
            return failVerification(verificationRun, now, "COUNTERPART_ROOT_MISMATCH", "Counterpart deployment does not share the same tenant-scoped provider root.");
        }
        if (deployment.getTenantId() == null || deployment.getTenantId().equals(counterpart.getTenantId())) {
            return failVerification(verificationRun, now, "COUNTERPART_TENANT_INVALID", "Tenant isolation smoke requires a different tenant under the same customer.");
        }

        recordStep(
            verificationRun,
            "counterpart_resolved",
            "Counterpart deployment resolved",
            "PASSED",
            jsonSupport.objectNode()
                .put("counterpartDeploymentId", counterpart.getId())
                .put("counterpartTenantId", counterpart.getTenantId())
                .put("sharedRoot", primaryHandle.getRootResourceValue() == null ? "" : primaryHandle.getRootResourceValue())
        );
        recordStep(
            verificationRun,
            "tenant_handles_distinct",
            "Tenant handles are distinct",
            primaryHandle.getTenantHandle() != null && !primaryHandle.getTenantHandle().equalsIgnoreCase(counterpartHandle.getTenantHandle()) ? "PASSED" : "FAILED",
            jsonSupport.objectNode()
                .put("primaryTenantHandle", primaryHandle.getTenantHandle() == null ? "" : primaryHandle.getTenantHandle())
                .put("counterpartTenantHandle", counterpartHandle.getTenantHandle() == null ? "" : counterpartHandle.getTenantHandle())
        );

        String sharedRecordId = "tenant-isolation-" + verificationRun.getId();
        String primaryContent = "tenant-isolation-primary-" + deployment.getId() + "-" + verificationRun.getId();
        String counterpartContent = "tenant-isolation-counterpart-" + counterpart.getId() + "-" + verificationRun.getId();
        boolean primaryUpserted = false;
        boolean counterpartUpserted = false;
        boolean primaryCleaned = false;
        boolean counterpartCleaned = false;

        try {
            JsonNode primaryUpsert = vectorizationVerificationProbeService.upsertSentinel(
                deployment,
                entityType,
                sharedRecordId,
                primaryContent,
                Map.of(
                    "verificationRunId", verificationRun.getId(),
                    "tenantIsolationOwner", deployment.getId(),
                    "tenantId", deployment.getTenantId()
                ),
                verificationRun.getId() + "-primary-upsert"
            );
            primaryUpserted = primaryUpsert.path("success").asBoolean(false);
            if (!primaryUpserted) {
                return failVerification(verificationRun, now, "PRIMARY_SENTINEL_UPSERT_FAILED", "Primary sentinel upsert did not succeed.");
            }
            recordStep(verificationRun, "primary_sentinel_upserted", "Primary sentinel upserted", "PASSED", asObject(primaryUpsert));

            JsonNode counterpartUpsertResponse = vectorizationVerificationProbeService.upsertSentinel(
                counterpart,
                entityType,
                sharedRecordId,
                counterpartContent,
                Map.of(
                    "verificationRunId", verificationRun.getId(),
                    "tenantIsolationOwner", counterpart.getId(),
                    "tenantId", counterpart.getTenantId()
                ),
                verificationRun.getId() + "-counterpart-upsert"
            );
            counterpartUpserted = counterpartUpsertResponse.path("success").asBoolean(false);
            if (!counterpartUpserted) {
                return failVerification(verificationRun, now, "COUNTERPART_SENTINEL_UPSERT_FAILED", "Counterpart sentinel upsert did not succeed.");
            }
            recordStep(verificationRun, "counterpart_sentinel_upserted", "Counterpart sentinel upserted", "PASSED", asObject(counterpartUpsertResponse));

            IsolationObservation observation = awaitIsolationObservation(
                deployment,
                counterpart,
                entityType,
                sharedRecordId,
                primaryContent,
                counterpartContent,
                Duration.ofSeconds(30)
            );
            if (!observation.isolated()) {
                upsertStep(
                    verificationRun,
                    "isolation_verified",
                    "Tenant isolation verified",
                    "FAILED",
                    observation.details()
                );
                return failVerification(verificationRun, Instant.now(), "TENANT_ISOLATION_FAILED", "Tenant-shared isolation verification observed cross-tenant visibility.");
            }
            upsertStep(
                verificationRun,
                "isolation_verified",
                "Tenant isolation verified",
                "PASSED",
                observation.details()
            );
        } finally {
            if (primaryUpserted) {
                JsonNode delete = vectorizationVerificationProbeService.deleteSentinel(
                    deployment,
                    entityType,
                    sharedRecordId,
                    verificationRun.getId() + "-primary-delete"
                );
                primaryCleaned = delete.path("success").asBoolean(false);
                upsertStep(verificationRun, "primary_cleanup", "Primary sentinel cleanup", primaryCleaned ? "PASSED" : "FAILED", asObject(delete));
            }
            if (counterpartUpserted) {
                JsonNode delete = vectorizationVerificationProbeService.deleteSentinel(
                    counterpart,
                    entityType,
                    sharedRecordId,
                    verificationRun.getId() + "-counterpart-delete"
                );
                counterpartCleaned = delete.path("success").asBoolean(false);
                upsertStep(verificationRun, "counterpart_cleanup", "Counterpart sentinel cleanup", counterpartCleaned ? "PASSED" : "FAILED", asObject(delete));
            }
        }

        if (!primaryCleaned || !counterpartCleaned) {
            return failVerification(verificationRun, now, "TENANT_ISOLATION_CLEANUP_FAILED", "Tenant isolation verification completed, but cleanup did not succeed.");
        }

        ObjectNode summary = jsonSupport.objectNode();
        summary.put("entityType", entityType);
        summary.put("counterpartDeploymentId", counterpart.getId());
        summary.put("sharedRoot", primaryHandle.getRootResourceValue() == null ? "" : primaryHandle.getRootResourceValue());
        summary.put("primaryTenantId", deployment.getTenantId() == null ? "" : deployment.getTenantId());
        summary.put("counterpartTenantId", counterpart.getTenantId() == null ? "" : counterpart.getTenantId());
        return completeVerification(verificationRun, now, "PASSED", summary);
    }

    private VectorizationVerificationRunEntity refreshLinkedRunState(VectorizationVerificationRunEntity verificationRun) {
        if (!StringUtils.hasText(verificationRun.getLinkedVectorizationRunId())
            || TERMINAL_STATUSES.contains(verificationRun.getStatus())) {
            return verificationRun;
        }
        return switch (verificationRun.getVerificationType()) {
            case "SOURCE_DISCOVERY_SMOKE" -> refreshDiscoveryLinkedRunState(verificationRun);
            case "SAMPLE_VECTORIZATION_SMOKE" -> refreshSampleLinkedRunState(verificationRun);
            default -> verificationRun;
        };
    }

    private VectorizationVerificationRunEntity refreshDiscoveryLinkedRunState(VectorizationVerificationRunEntity verificationRun) {
        Optional<VectorizationRunSummary> linkedRunOptional = runRepository.findById(verificationRun.getLinkedVectorizationRunId())
            .map(vectorizationService::summarizeRun);
        Instant now = Instant.now();
        if (linkedRunOptional.isEmpty()) {
            upsertStep(
                verificationRun,
                "linked_vectorization_run",
                "Linked discovery run created",
                "FAILED",
                jsonSupport.objectNode().put("reason", "LINKED_RUN_MISSING")
            );
            return completeVerification(
                verificationRun,
                now,
                "FAILED",
                jsonSupport.objectNode().put("reason", "LINKED_RUN_MISSING")
            );
        }
        VectorizationRunSummary linkedRun = linkedRunOptional.get();
        if (LINKED_RUN_ACTIVE_STATUSES.contains(linkedRun.status())) {
            return persistActiveLinkedRun(verificationRun, linkedRun, "Linked discovery run created");
        }
        String verificationStatus = switch (linkedRun.status()) {
            case "COMPLETED" -> "PASSED";
            case "CANCELLED" -> "CANCELLED";
            default -> "FAILED";
        };
        ObjectNode linkedRunDetails = jsonSupport.objectNode();
        linkedRunDetails.put("linkedVectorizationRunId", linkedRun.id());
        linkedRunDetails.put("linkedRunStatus", linkedRun.status());
        linkedRunDetails.set("progressSummary", linkedRun.progressSummary());
        linkedRunDetails.set("errorSummary", linkedRun.errorSummary());
        upsertStep(
            verificationRun,
            "linked_vectorization_run",
            "Linked discovery run created",
            verificationStatus,
            linkedRunDetails
        );
        return completeVerification(
            verificationRun,
            now,
            verificationStatus,
            linkedRunDetails
        );
    }

    private VectorizationVerificationRunEntity refreshSampleLinkedRunState(VectorizationVerificationRunEntity verificationRun) {
        Optional<VectorizationRunSummary> linkedRunOptional = runRepository.findById(verificationRun.getLinkedVectorizationRunId())
            .map(vectorizationService::summarizeRun);
        Instant now = Instant.now();
        if (linkedRunOptional.isEmpty()) {
            upsertStep(
                verificationRun,
                "linked_vectorization_run",
                "Linked sample vectorization run created",
                "FAILED",
                jsonSupport.objectNode().put("reason", "LINKED_RUN_MISSING")
            );
            return completeVerification(verificationRun, now, "FAILED", jsonSupport.objectNode().put("reason", "LINKED_RUN_MISSING"));
        }
        VectorizationRunSummary linkedRun = linkedRunOptional.get();
        if (LINKED_RUN_ACTIVE_STATUSES.contains(linkedRun.status())) {
            return persistActiveLinkedRun(verificationRun, linkedRun, "Linked sample vectorization run created");
        }

        if ("CANCELLED".equalsIgnoreCase(linkedRun.status())) {
            upsertStep(
                verificationRun,
                "linked_vectorization_run",
                "Linked sample vectorization run created",
                "CANCELLED",
                linkedRunDetails(linkedRun)
            );
            return completeVerification(verificationRun, now, "CANCELLED", linkedRunDetails(linkedRun));
        }
        if (!"COMPLETED".equalsIgnoreCase(linkedRun.status())) {
            upsertStep(
                verificationRun,
                "linked_vectorization_run",
                "Linked sample vectorization run created",
                "FAILED",
                linkedRunDetails(linkedRun)
            );
            return completeVerification(verificationRun, now, "FAILED", linkedRunDetails(linkedRun));
        }

        JsonNode progress = defaultObject(linkedRun.progressSummary());
        int processedRecords = progress.path("processedRecords").asInt(0);
        int succeededRecords = progress.path("succeededRecords").asInt(0);
        int failedRecords = progress.path("failedRecords").asInt(0);
        boolean wroteSample = processedRecords > 0 && succeededRecords > 0 && failedRecords == 0;
        upsertStep(
            verificationRun,
            "sample_vectorization_result",
            "Sample vectorization writes succeeded",
            wroteSample ? "PASSED" : "FAILED",
            linkedRunDetails(linkedRun)
        );

        VectorizationPreviewSummary preview = vectorizationService.preview(verificationRun.getDeploymentId());
        upsertStep(
            verificationRun,
            "sync_state_after_sample",
            "Sync state remains evaluable after sample run",
            StringUtils.hasText(preview.syncState()) ? "PASSED" : "FAILED",
            jsonSupport.objectNode()
                .put("syncState", preview.syncState())
                .set("reindexOptions", asObject(preview.reindexOptions()))
        );

        ObjectNode summary = linkedRunDetails(linkedRun);
        summary.put("processedRecords", processedRecords);
        summary.put("succeededRecords", succeededRecords);
        summary.put("failedRecords", failedRecords);
        summary.put("syncState", preview.syncState());
        summary.set("postLiveCounts", defaultObject(runtimeCoverageClient.fetchCounts(
            deploymentRepository.findById(verificationRun.getDeploymentId()).orElse(null)
        )));
        return completeVerification(verificationRun, now, wroteSample ? "PASSED" : "FAILED", summary);
    }

    private boolean assertCondition(VectorizationVerificationRunEntity verificationRun,
                                    String stepKey,
                                    String stepName,
                                    boolean condition,
                                    JsonNode details) {
        recordStep(verificationRun, stepKey, stepName, condition ? "PASSED" : "FAILED", details);
        return condition;
    }

    private VectorizationVerificationRunEntity failVerification(VectorizationVerificationRunEntity verificationRun,
                                                                Instant now,
                                                                String reason,
                                                                String message) {
        return completeVerification(
            verificationRun,
            now,
            "FAILED",
            jsonSupport.objectNode()
                .put("reason", reason)
                .put("message", message == null ? "" : message)
        );
    }

    private VectorizationVerificationRunEntity completeVerification(VectorizationVerificationRunEntity verificationRun,
                                                                    Instant now,
                                                                    String status,
                                                                    JsonNode summary) {
        verificationRun.setStatus(status);
        verificationRun.setSummaryJson(jsonSupport.write(defaultObject(summary)));
        verificationRun.setCompletedAt(TERMINAL_STATUSES.contains(status) ? now : null);
        verificationRun.setUpdatedAt(now);
        return verificationRunRepository.save(verificationRun);
    }

    private void recordStep(VectorizationVerificationRunEntity verificationRun,
                            String stepKey,
                            String stepName,
                            String status,
                            JsonNode details) {
        Instant now = Instant.now();
        VectorizationVerificationStepEntity step = new VectorizationVerificationStepEntity();
        step.setId(generateId("vvs"));
        step.setVerificationRunId(verificationRun.getId());
        step.setStepKey(stepKey);
        step.setStepName(stepName);
        step.setStatus(status);
        step.setDetailsJson(jsonSupport.write(defaultObject(details)));
        step.setCreatedAt(now);
        step.setUpdatedAt(now);
        verificationStepRepository.save(step);
    }

    private void upsertStep(VectorizationVerificationRunEntity verificationRun,
                            String stepKey,
                            String stepName,
                            String status,
                            JsonNode details) {
        Instant now = Instant.now();
        VectorizationVerificationStepEntity step = verificationStepRepository.findByVerificationRunIdAndStepKey(verificationRun.getId(), stepKey)
            .orElseGet(() -> {
                VectorizationVerificationStepEntity created = new VectorizationVerificationStepEntity();
                created.setId(generateId("vvs"));
                created.setVerificationRunId(verificationRun.getId());
                created.setStepKey(stepKey);
                created.setStepName(stepName);
                created.setCreatedAt(now);
                return created;
            });
        step.setStepName(stepName);
        step.setStatus(status);
        step.setDetailsJson(jsonSupport.write(defaultObject(details)));
        step.setUpdatedAt(now);
        verificationStepRepository.save(step);
    }

    private VectorizationVerificationRunEntity requireRun(String deploymentId, String verificationRunId) {
        VectorizationVerificationRunEntity run = verificationRunRepository.findById(verificationRunId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vectorization verification run not found: " + verificationRunId));
        if (!deploymentId.equals(run.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "Vectorization verification run not found: " + verificationRunId);
        }
        return run;
    }

    private DeploymentEntity requireDeploymentOperator(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentOperatorAccess(deployment);
    }

    private String normalizeVerificationType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!VERIFICATION_TYPES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization verification type: " + value);
        }
        return normalized;
    }

    private String executionModeFor(String verificationType) {
        return switch (verificationType) {
            case "SOURCE_DISCOVERY_SMOKE", "SAMPLE_VECTORIZATION_SMOKE", "TENANT_SHARED_ISOLATION_SMOKE" -> "ACTIVE";
            default -> "READ_ONLY";
        };
    }

    private ArrayNode writeEntityScope(List<String> entityTypes) {
        return writeStringArray(entityTypes);
    }

    private ArrayNode writeStringArray(List<String> values) {
        ArrayNode node = jsonSupport.arrayNode();
        if (values == null) {
            return node;
        }
        values.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .forEach(node::add);
        return node;
    }

    private ObjectNode detail(String key, String value) {
        ObjectNode node = jsonSupport.objectNode();
        node.put(key, value == null ? "" : value);
        return node;
    }

    private VectorizationVerificationRunEntity persistActiveLinkedRun(VectorizationVerificationRunEntity verificationRun,
                                                                      VectorizationRunSummary linkedRun,
                                                                      String stepName) {
        Instant now = Instant.now();
        verificationRun.setStatus("RUNNING");
        verificationRun.setSummaryJson(jsonSupport.write(jsonSupport.objectNode()
            .put("linkedVectorizationRunId", linkedRun.id())
            .put("linkedRunStatus", linkedRun.status())
            .put("linkedRunDurationMs", linkedRunDurationMs(linkedRun))
            .set("progressSummary", defaultObject(linkedRun.progressSummary()))));
        verificationRun.setUpdatedAt(now);
        verificationRunRepository.save(verificationRun);
        upsertStep(
            verificationRun,
            "linked_vectorization_run",
            stepName,
            "RUNNING",
            jsonSupport.objectNode()
                .put("linkedVectorizationRunId", linkedRun.id())
                .put("linkedRunStatus", linkedRun.status())
                .put("linkedRunDurationMs", linkedRunDurationMs(linkedRun))
                .set("progressSummary", defaultObject(linkedRun.progressSummary()))
        );
        return verificationRun;
    }

    private ObjectNode linkedRunDetails(VectorizationRunSummary linkedRun) {
        ObjectNode details = jsonSupport.objectNode();
        details.put("linkedVectorizationRunId", linkedRun.id());
        details.put("linkedRunStatus", linkedRun.status());
        details.put("linkedRunDurationMs", linkedRunDurationMs(linkedRun));
        details.set("progressSummary", defaultObject(linkedRun.progressSummary()));
        details.set("errorSummary", defaultObject(linkedRun.errorSummary()));
        return details;
    }

    private long linkedRunDurationMs(VectorizationRunSummary linkedRun) {
        Instant startedAt = linkedRun.startedAt() == null ? linkedRun.createdAt() : linkedRun.startedAt();
        Instant endedAt = linkedRun.completedAt() == null ? Instant.now() : linkedRun.completedAt();
        if (startedAt == null || endedAt == null || endedAt.isBefore(startedAt)) {
            return 0L;
        }
        return java.time.Duration.between(startedAt, endedAt).toMillis();
    }

    private List<String> normalizedRequestedScope(List<String> requested, JsonNode activeRevisionScope, JsonNode reindexOptions) {
        List<String> requestedScope = readArrayValues(writeStringArray(requested));
        if (!requestedScope.isEmpty()) {
            return requestedScope;
        }
        List<String> revisionScope = readArrayValues(activeRevisionScope);
        if (!revisionScope.isEmpty()) {
            return revisionScope;
        }
        JsonNode availableEntities = defaultObject(reindexOptions).path("availableEntities");
        return readArrayValues(availableEntities);
    }

    private boolean allSelectedEntitiesSourceEmpty(JsonNode sourceDiscovery, List<String> scope) {
        JsonNode counts = defaultObject(sourceDiscovery).path("countsByEntityType");
        if (!counts.isObject() || scope == null || scope.isEmpty()) {
            return false;
        }
        boolean sawSelected = false;
        for (String entityType : scope) {
            if (!StringUtils.hasText(entityType)) {
                continue;
            }
            sawSelected = true;
            if (counts.path(entityType).asLong(0L) > 0L) {
                return false;
            }
        }
        return sawSelected;
    }

    private String sampleRunReason(String syncState) {
        String normalized = syncState == null ? "" : syncState.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "BOOTSTRAP_REQUIRED", "SOURCE_EMPTY" -> "BOOTSTRAP";
            case "OUT_OF_DATE", "REINDEX_DEFERRED", "MANUALLY_CONFIRMED" -> "REINDEX";
            default -> "REFRESH";
        };
    }

    private void requirePlatformAdmin() {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) {
            throw new ResponseStatusException(FORBIDDEN, "Platform admin access is required for tenant-shared isolation verification.");
        }
    }

    private TenantScopedVectorResourceEntity requireActiveSharedHandle(DeploymentEntity deployment) {
        return tenantScopedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.getId()).stream()
            .filter(record -> "ACTIVE".equalsIgnoreCase(record.getResourceStatus()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Deployment does not have an active tenant-scoped shared-vector handle."));
    }

    private DeploymentEntity resolveCounterpartDeployment(TenantScopedVectorResourceEntity primaryHandle,
                                                          DeploymentEntity deployment,
                                                          String requestedCounterpartDeploymentId) {
        if (StringUtils.hasText(requestedCounterpartDeploymentId)) {
            DeploymentEntity explicit = requireDeploymentOperator(requestedCounterpartDeploymentId.trim());
            if (!deployment.getCustomerId().equals(explicit.getCustomerId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Tenant isolation counterpart must belong to the same customer.");
            }
            if (deployment.getId().equals(explicit.getId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Tenant isolation counterpart must be a different deployment.");
            }
            if (Objects.equals(deployment.getTenantId(), explicit.getTenantId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Tenant isolation counterpart must belong to a different tenant.");
            }
            return explicit;
        }
        return tenantScopedVectorResourceRepository
            .findByVectorStrategyIgnoreCaseAndRootResourceValueIgnoreCaseAndResourceStatusIgnoreCase(
                primaryHandle.getVectorStrategy(),
                primaryHandle.getRootResourceValue(),
                "ACTIVE"
            )
            .stream()
            .filter(candidate -> deployment.getCustomerId().equals(candidate.getCustomerId()))
            .filter(candidate -> !deployment.getId().equals(candidate.getDeploymentId()))
            .filter(candidate -> StringUtils.hasText(candidate.getDeploymentId()))
            .filter(candidate -> !Objects.equals(candidate.getTenantId(), deployment.getTenantId()))
            .map(candidate -> deploymentRepository.findById(candidate.getDeploymentId()).orElse(null))
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "No shared-root counterpart deployment could be resolved for tenant isolation verification."));
    }

    private String resolveIsolationEntityType(DeploymentEntity primary,
                                              DeploymentEntity counterpart,
                                              List<String> requestedEntityTypes) {
        List<String> requested = readArrayValues(writeStringArray(requestedEntityTypes));
        if (!requested.isEmpty()) {
            return requested.get(0);
        }
        JsonNode primaryEntities = defaultObject(vectorizationService.preview(primary.getId()).reindexOptions()).path("availableEntities");
        JsonNode counterpartEntities = defaultObject(vectorizationService.preview(counterpart.getId()).reindexOptions()).path("availableEntities");
        Set<String> counterpartSet = Set.copyOf(readArrayValues(counterpartEntities));
        return readArrayValues(primaryEntities).stream()
            .filter(counterpartSet::contains)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "No shared configured entity type is available for tenant isolation verification."));
    }

    private IsolationObservation awaitIsolationObservation(DeploymentEntity primary,
                                                           DeploymentEntity counterpart,
                                                           String entityType,
                                                           String sharedRecordId,
                                                           String primaryContent,
                                                           String counterpartContent,
                                                           Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        IsolationObservation lastObservation = new IsolationObservation(false, jsonSupport.objectNode());
        while (Instant.now().isBefore(deadline)) {
            JsonNode primaryVectors = scanForSentinel(primary, entityType, sharedRecordId, primaryContent, counterpartContent);
            JsonNode counterpartVectors = scanForSentinel(counterpart, entityType, sharedRecordId, counterpartContent, primaryContent);
            boolean primarySeesOwn = primaryVectors.path("foundOwn").asBoolean(false);
            boolean primarySeesForeign = primaryVectors.path("foundForeign").asBoolean(false);
            boolean counterpartSeesOwn = counterpartVectors.path("foundOwn").asBoolean(false);
            boolean counterpartSeesForeign = counterpartVectors.path("foundForeign").asBoolean(false);
            ObjectNode details = jsonSupport.objectNode();
            details.put("entityType", entityType);
            details.put("sharedRecordId", sharedRecordId);
            details.put("primarySeesOwn", primarySeesOwn);
            details.put("primarySeesForeign", primarySeesForeign);
            details.put("counterpartSeesOwn", counterpartSeesOwn);
            details.put("counterpartSeesForeign", counterpartSeesForeign);
            details.set("primaryProbe", primaryVectors);
            details.set("counterpartProbe", counterpartVectors);
            lastObservation = new IsolationObservation(
                primarySeesOwn && counterpartSeesOwn && !primarySeesForeign && !counterpartSeesForeign,
                details
            );
            if (lastObservation.isolated()) {
                return lastObservation;
            }
            try {
                Thread.sleep(1500L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return lastObservation;
    }

    private JsonNode scanForSentinel(DeploymentEntity deployment,
                                     String entityType,
                                     String sharedRecordId,
                                     String expectedContent,
                                     String foreignContent) {
        ObjectNode details = jsonSupport.objectNode();
        details.put("foundOwn", false);
        details.put("foundForeign", false);
        int offset = 0;
        for (int page = 0; page < 10; page++) {
            JsonNode response = vectorizationVerificationProbeService.fetchRuntimeVectors(deployment, entityType, 200, offset);
            JsonNode vectors = response.path("vectors");
            if (vectors.isArray()) {
                for (JsonNode vector : vectors) {
                    String entityId = vector.path("entityId").asText("");
                    String content = vector.path("content").asText("");
                    if (sharedRecordId.equals(entityId) && expectedContent.equals(content)) {
                        details.put("foundOwn", true);
                    }
                    if (sharedRecordId.equals(entityId) && foreignContent.equals(content)) {
                        details.put("foundForeign", true);
                    }
                }
            }
            if (!response.path("hasMore").asBoolean(false)) {
                break;
            }
            offset = response.path("nextOffset").asInt(offset + 200);
        }
        return details;
    }

    private JsonNode defaultObject(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? jsonSupport.objectNode() : node;
    }

    private ObjectNode asObject(JsonNode node) {
        if (node != null && node.isObject()) {
            return (ObjectNode) node.deepCopy();
        }
        return jsonSupport.objectNode();
    }

    private String currentActorId() {
        return PlatformSecurityContext.currentPrincipal() == null ? null : PlatformSecurityContext.currentPrincipal().actorId();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private List<String> readArrayValues(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            String text = item.asText("").trim();
            if (!text.isBlank() && !values.contains(text)) {
                values.add(text);
            }
        });
        return values;
    }

    private VectorizationVerificationRunSummary summarizeRun(VectorizationVerificationRunEntity entity) {
        return new VectorizationVerificationRunSummary(
            entity.getId(),
            entity.getVerificationType(),
            entity.getExecutionMode(),
            entity.getStatus(),
            jsonSupport.readStringList(entity.getEntityScopeJson()),
            jsonSupport.readTree(entity.getSummaryJson()),
            entity.getLinkedVectorizationRunId(),
            entity.getCreatedAt(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getUpdatedAt()
        );
    }

    private VectorizationVerificationStepSummary summarizeStep(VectorizationVerificationStepEntity entity) {
        return new VectorizationVerificationStepSummary(
            entity.getId(),
            entity.getStepKey(),
            entity.getStepName(),
            entity.getStatus(),
            jsonSupport.readTree(entity.getDetailsJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private record IsolationObservation(boolean isolated, ObjectNode details) {
    }
}
