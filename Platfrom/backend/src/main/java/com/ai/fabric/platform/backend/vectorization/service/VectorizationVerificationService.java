package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationStepEntity;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationVerificationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPreviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationRunDetailsSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationVerificationStepSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
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

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class VectorizationVerificationService {

    private static final Set<String> VERIFICATION_TYPES = Set.of(
        "CONTROL_PLANE_READINESS",
        "RUNNER_PROVISIONING_SMOKE",
        "SOURCE_DISCOVERY_SMOKE"
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
    private final VectorizationVerificationRunRepository verificationRunRepository;
    private final VectorizationVerificationStepRepository verificationStepRepository;
    private final VectorizationRunRepository runRepository;
    private final VectorizationRunnerRegistrationRepository registrationRepository;
    private final VectorizationRunnerSessionRepository sessionRepository;
    private final VectorizationService vectorizationService;
    private final VectorizationJsonSupport jsonSupport;

    public VectorizationVerificationService(DeploymentRepository deploymentRepository,
                                            DeploymentAccessService deploymentAccessService,
                                            PlatformAuditService platformAuditService,
                                            VectorizationVerificationRunRepository verificationRunRepository,
                                            VectorizationVerificationStepRepository verificationStepRepository,
                                            VectorizationRunRepository runRepository,
                                            VectorizationRunnerRegistrationRepository registrationRepository,
                                            VectorizationRunnerSessionRepository sessionRepository,
                                            VectorizationService vectorizationService,
                                            VectorizationJsonSupport jsonSupport) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
        this.verificationRunRepository = verificationRunRepository;
        this.verificationStepRepository = verificationStepRepository;
        this.runRepository = runRepository;
        this.registrationRepository = registrationRepository;
        this.sessionRepository = sessionRepository;
        this.vectorizationService = vectorizationService;
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
            new CreateVectorizationRunRequest("DISCOVERY", entityTypes, "Created by vectorization verification smoke.")
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

    private VectorizationVerificationRunEntity refreshLinkedRunState(VectorizationVerificationRunEntity verificationRun) {
        if (!StringUtils.hasText(verificationRun.getLinkedVectorizationRunId())
            || TERMINAL_STATUSES.contains(verificationRun.getStatus())) {
            return verificationRun;
        }
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
            verificationRun.setStatus("RUNNING");
            verificationRun.setSummaryJson(jsonSupport.write(jsonSupport.objectNode()
                .put("linkedVectorizationRunId", linkedRun.id())
                .put("linkedRunStatus", linkedRun.status())
                .set("progressSummary", linkedRun.progressSummary())));
            verificationRun.setUpdatedAt(now);
            verificationRunRepository.save(verificationRun);
            upsertStep(
                verificationRun,
                "linked_vectorization_run",
                "Linked discovery run created",
                "RUNNING",
                jsonSupport.objectNode()
                    .put("linkedVectorizationRunId", linkedRun.id())
                    .put("linkedRunStatus", linkedRun.status())
            );
            return verificationRun;
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
            case "SOURCE_DISCOVERY_SMOKE" -> "ACTIVE";
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
}
