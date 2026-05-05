package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentHostedVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentHostedVerificationDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSecretUsageSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationScriptContextSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceHealthSummary;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceAdminService;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunDetailsSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PlatformVerificationSuiteExecutionService {

    private final PlatformVerificationSuiteRunRepository runRepository;
    private final PlatformVerificationSuiteRunStageRepository stageRepository;
    private final PlatformVerificationSuiteCatalog catalog;
    private final PlatformVerificationSuiteProperties suiteProperties;
    private final PlatformManagedInferenceAdminService platformManagedInferenceAdminService;
    private final DeploymentVerificationRolloutService deploymentVerificationRolloutService;
    private final DeploymentService deploymentService;
    private final DeploymentHostedVerificationService deploymentHostedVerificationService;
    private final DeploymentHostedVerificationRunRepository deploymentHostedVerificationRunRepository;
    private final PlatformVerificationSuiteScriptContextService scriptContextService;
    private final PlatformVerificationScriptRunnerService scriptRunnerService;
    private final VectorizationService vectorizationService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PlatformVerificationSuiteExecutionService(PlatformVerificationSuiteRunRepository runRepository,
                                                     PlatformVerificationSuiteRunStageRepository stageRepository,
                                                     PlatformVerificationSuiteCatalog catalog,
                                                     PlatformVerificationSuiteProperties suiteProperties,
                                                     PlatformManagedInferenceAdminService platformManagedInferenceAdminService,
                                                     DeploymentVerificationRolloutService deploymentVerificationRolloutService,
                                                     DeploymentService deploymentService,
                                                     DeploymentHostedVerificationService deploymentHostedVerificationService,
                                                     DeploymentHostedVerificationRunRepository deploymentHostedVerificationRunRepository,
                                                     PlatformVerificationSuiteScriptContextService scriptContextService,
                                                     PlatformVerificationScriptRunnerService scriptRunnerService,
                                                     VectorizationService vectorizationService,
                                                     PlatformAuditService platformAuditService,
                                                     ObjectMapper objectMapper) {
        this.runRepository = runRepository;
        this.stageRepository = stageRepository;
        this.catalog = catalog;
        this.suiteProperties = suiteProperties;
        this.platformManagedInferenceAdminService = platformManagedInferenceAdminService;
        this.deploymentVerificationRolloutService = deploymentVerificationRolloutService;
        this.deploymentService = deploymentService;
        this.deploymentHostedVerificationService = deploymentHostedVerificationService;
        this.deploymentHostedVerificationRunRepository = deploymentHostedVerificationRunRepository;
        this.scriptContextService = scriptContextService;
        this.scriptRunnerService = scriptRunnerService;
        this.vectorizationService = vectorizationService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Async("verificationSuiteExecutor")
    public void execute(String runId, boolean allowControlPlaneRepair) {
        executeInline(runId, allowControlPlaneRepair);
    }

    void executeInline(String runId, boolean allowControlPlaneRepair) {
        Optional<PlatformVerificationSuiteRunEntity> optionalRun = runRepository.findById(runId);
        if (optionalRun.isEmpty()) {
            return;
        }
        PlatformVerificationSuiteRunEntity run = optionalRun.get();
        List<PlatformVerificationSuiteRunStageEntity> stages = stageRepository.findBySuiteRunIdOrderByStageOrderAsc(runId);
        Instant startedAt = Instant.now();
        run.setStatus("RUNNING");
        run.setStartedAt(startedAt);
        run.setSummaryMessage("Verification suite is running on the platform control plane.");
        runRepository.save(run);

        try {
            for (PlatformVerificationSuiteRunStageEntity stage : stages) {
                boolean passed = switch (stage.getStageType()) {
                    case "INFERENCE_SERVICE_HEALTH" -> executeSharedInferenceHealth(stage, allowControlPlaneRepair);
                    case "CANONICAL_ROLLOUTS" -> executeCanonicalRolloutInventory(stage, allowControlPlaneRepair);
                    case "SCRIPT_VERIFICATION" -> executeScriptVerification(stage);
                    case "HOSTED_DEPLOYMENT_VERIFICATION" -> executeHostedDeploymentVerification(stage, allowControlPlaneRepair);
                    default -> failUnsupportedStage(stage);
                };
                if (!passed && stage.isBlocking()) {
                    blockRemainingStages(stages, stage.getStageOrder(), stage.getSummaryMessage());
                    completeRun(run, "FAILED", "Verification suite stopped at " + stage.getStageLabel() + ": " + stage.getSummaryMessage());
                    return;
                }
            }
            completeRun(run, "PASSED", "Verification suite completed successfully.");
        } catch (Exception ex) {
            List<PlatformVerificationSuiteRunStageEntity> latestStages = stageRepository.findBySuiteRunIdOrderByStageOrderAsc(runId);
            PlatformVerificationSuiteRunStageEntity activeStage = latestStages.stream()
                .filter(stage -> "RUNNING".equals(stage.getStatus()))
                .findFirst()
                .orElse(null);
            if (activeStage != null) {
                completeStage(activeStage, "FAILED", "Stage failed before completion: " + defaultText(ex.getMessage(), "unexpected error"), objectMapper.createObjectNode());
                blockRemainingStages(latestStages, activeStage.getStageOrder(), activeStage.getSummaryMessage());
            }
            completeRun(run, "FAILED", "Verification suite failed before completion: " + defaultText(ex.getMessage(), "unexpected error"));
        }
    }

    private boolean failUnsupportedStage(PlatformVerificationSuiteRunStageEntity stage) {
        ObjectNode details = objectMapper.createObjectNode()
            .put("stageType", stage.getStageType());
        completeStage(stage, "FAILED", "Unsupported stage type: " + stage.getStageType(), details);
        return false;
    }

    private boolean executeSharedInferenceHealth(PlatformVerificationSuiteRunStageEntity stage,
                                                 boolean allowControlPlaneRepair) {
        markStageRunning(stage, "Checking shared inference service health.");
        PlatformManagedInferenceHealthSummary health = platformManagedInferenceAdminService.getHealth(stage.getTargetRef());
        boolean repaired = false;
        if (!sharedInferenceHealthy(health.status()) && allowControlPlaneRepair) {
            platformManagedInferenceAdminService.reconcile(stage.getTargetRef());
            health = platformManagedInferenceAdminService.getHealth(stage.getTargetRef());
            repaired = true;
        }
        ObjectNode details = objectMapper.createObjectNode()
            .put("serviceRef", health.serviceRef())
            .put("status", health.status())
            .put("driftStatus", defaultText(health.driftStatus(), "UNKNOWN"))
            .put("driftMessage", defaultText(health.driftMessage(), ""))
                .put("lastProbeStatus", defaultText(health.lastProbeStatus(), "UNKNOWN"))
                .put("lastProbeMessage", defaultText(health.lastProbeMessage(), ""))
                .put("repairAttempted", repaired);
        if (sharedInferenceHealthy(health.status())) {
            completeStage(stage, "PASSED", "Shared inference service is healthy.", details, "");
            return true;
        }
        completeStage(
            stage,
            "FAILED",
            defaultText(health.driftMessage(), defaultText(health.lastProbeMessage(), "Shared inference service is not healthy.")),
            details,
            ""
        );
        return false;
    }

    private boolean executeCanonicalRolloutInventory(PlatformVerificationSuiteRunStageEntity stage,
                                                     boolean allowControlPlaneRepair) {
        markStageRunning(stage, "Resolving canonical rollout inventory and secret readiness.");
        DeploymentVerificationRolloutSummary summary = deploymentVerificationRolloutService.listRollouts();
        List<RolloutAssessment> assessments = assessRollouts(summary);
        boolean repaired = false;
        List<String> repairKeys = allowControlPlaneRepair
            ? assessments.stream()
                .filter(this::shouldRepairCanonicalRollout)
                .map(assessment -> assessment.rollout.key())
                .toList()
            : List.of();
        if (!repairKeys.isEmpty()) {
            deploymentVerificationRolloutService.recreateRollouts(repairKeys);
            summary = deploymentVerificationRolloutService.listRollouts();
            assessments = assessRollouts(summary);
            repaired = true;
        }

        ArrayNode items = objectMapper.createArrayNode();
        List<String> blockers = new ArrayList<>();
        for (RolloutAssessment assessment : assessments) {
            ObjectNode item = objectMapper.createObjectNode()
                .put("key", assessment.rollout.key())
                .put("displayName", assessment.rollout.displayName())
                .put("deploymentId", defaultText(assessment.rollout.deploymentId(), ""))
                .put("exists", assessment.rollout.exists())
                .put("archived", assessment.rollout.archived())
                .put("verificationReady", assessment.rollout.verificationReady())
                .put("deploymentStatus", defaultText(assessment.rollout.deploymentStatus(), "UNKNOWN"))
                .put("latestReleaseStatus", defaultText(assessment.rollout.latestReleaseStatus(), "UNKNOWN"))
                .put("latestVerificationStatus", defaultText(assessment.rollout.latestVerificationStatus(), "UNKNOWN"))
                .put("runtimeBaseUrl", defaultText(assessment.rollout.runtimeBaseUrl(), ""))
                .put("missingRequiredSecrets", assessment.missingRequiredSecrets)
                .put("secretSummary", defaultText(assessment.secretSummary, ""))
                .put("blockReason", defaultText(assessment.blockReason, ""));
            ArrayNode missingPrerequisites = item.putArray("missingPrerequisites");
            assessment.rollout.missingPrerequisites().forEach(missingPrerequisites::add);
            items.add(item);
            if (assessment.blockReason != null) {
                blockers.add(assessment.rollout.displayName() + ": " + assessment.blockReason);
            }
        }

        ObjectNode details = objectMapper.createObjectNode()
            .put("summaryMessage", summary.summaryMessage())
            .put("repairAttempted", repaired)
            .set("items", items);
        if (blockers.isEmpty()) {
            completeStage(stage, "PASSED", "Canonical rollout inventory is present and secret-ready.", details, "");
            return true;
        }
        completeStage(stage, "FAILED", String.join(" | ", blockers), details, "");
        return false;
    }

    private boolean shouldRepairCanonicalRollout(RolloutAssessment assessment) {
        if (assessment == null || assessment.rollout == null) {
            return true;
        }
        if (assessment.structurallyBlocked) {
            return true;
        }
        if (assessment.missingRequiredSecrets > 0 || assessment.rollout.verificationReady()) {
            return false;
        }
        if (isRolloutApplyInProgress(assessment.rollout)) {
            return false;
        }
        return isRolloutTerminalFailure(assessment.rollout);
    }

    private boolean isRolloutApplyInProgress(DeploymentVerificationRolloutItemSummary rollout) {
        String releaseStatus = normalize(rollout.latestReleaseStatus());
        String provisioningStatus = normalize(rollout.latestProvisioningStatus());
        String verificationStatus = normalize(rollout.latestVerificationStatus());
        return List.of("APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING").contains(releaseStatus)
            || List.of("QUEUED", "RUNNING", "AWAITING_CONFIRMATION").contains(provisioningStatus)
            || "RUNNING".equals(verificationStatus);
    }

    private boolean isRolloutTerminalFailure(DeploymentVerificationRolloutItemSummary rollout) {
        String releaseStatus = normalize(rollout.latestReleaseStatus());
        String provisioningStatus = normalize(rollout.latestProvisioningStatus());
        String verificationStatus = normalize(rollout.latestVerificationStatus());
        return List.of("FAILED", "PRE_APPLY_BLOCKED", "APPLIED_VERIFICATION_FAILED", "CANCELED", "CANCELLED").contains(releaseStatus)
            || List.of("FAILED", "BLOCKED", "CANCELED", "CANCELLED").contains(provisioningStatus)
            || "FAILED".equals(verificationStatus);
    }

    private String normalize(String value) {
        return defaultText(value, "").trim().toUpperCase();
    }

    private boolean executeScriptVerification(PlatformVerificationSuiteRunStageEntity stage) throws InterruptedException, java.io.IOException {
        markStageRunning(stage, "Running allowlisted platform verification script.");
        Map<String, String> environmentOverrides = readScriptEnvironmentOverrides(stage);
        PlatformVerificationScriptContextSummary context = scriptContextService.build(stage.getTargetRef(), environmentOverrides);
        PlatformVerificationScriptRunnerService.ScriptRunResult result = scriptRunnerService.run(context);
        boolean retryAttempted = false;
        if (shouldRetryScriptVerification(stage, result)) {
            retryAttempted = true;
            result = scriptRunnerService.run(context);
        }
        ObjectNode details = objectMapper.createObjectNode()
            .put("scriptPath", context.scriptPath())
            .put("targetRef", defaultText(stage.getTargetRef(), ""))
            .put("exitCode", result.exitCode() == null ? -1 : result.exitCode())
            .put("timedOut", result.timedOut())
            .put("retryAttempted", retryAttempted);
        if (!environmentOverrides.isEmpty()) {
            details.set("environmentOverrides", objectMapper.valueToTree(environmentOverrides));
        }
        String summary = switch (result.status()) {
            case "PASSED" -> "Verification script completed successfully.";
            case "TIMED_OUT" -> "Verification script timed out before completion.";
            default -> extractFailureHeadline(result.output());
        };
        completeStage(stage, result.status(), summary, details, result.output());
        return result.passed();
    }

    private Map<String, String> readScriptEnvironmentOverrides(PlatformVerificationSuiteRunStageEntity stage) {
        try {
            JsonNode root = objectMapper.readTree(defaultText(stage.getDetailsJson(), "{}"));
            JsonNode overridesNode = root.path("scriptEnvironmentOverrides");
            if (!overridesNode.isObject()) {
                return Map.of();
            }
            java.util.LinkedHashMap<String, String> overrides = new java.util.LinkedHashMap<>();
            overridesNode.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && !value.isNull()) {
                    String text = value.asText("");
                    if (!text.isBlank()) {
                        overrides.put(entry.getKey(), text.trim());
                    }
                }
            });
            return Map.copyOf(overrides);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private boolean shouldRetryScriptVerification(PlatformVerificationSuiteRunStageEntity stage,
                                                  PlatformVerificationScriptRunnerService.ScriptRunResult result) {
        return PlatformVerificationSuiteScriptContextService.SCRIPT_MARKETPLACE_INSTALL_FLOW.equalsIgnoreCase(defaultText(stage.getTargetRef(), ""))
            && !result.passed();
    }

    private boolean executeHostedDeploymentVerification(PlatformVerificationSuiteRunStageEntity stage,
                                                        boolean allowControlPlaneRepair) throws InterruptedException {
        markStageRunning(stage, "Queueing hosted deployment verification.");
        DeploymentVerificationRolloutSummary rolloutSummary = deploymentVerificationRolloutService.listRollouts();
        DeploymentVerificationRolloutItemSummary rollout = rolloutSummary.items().stream()
            .filter(item -> item.key().equalsIgnoreCase(stage.getTargetRef()))
            .findFirst()
            .orElse(null);
        if (rollout == null || rollout.deploymentId() == null) {
            ObjectNode details = objectMapper.createObjectNode()
                .put("targetRef", defaultText(stage.getTargetRef(), ""));
            completeStage(stage, "FAILED", "Canonical rollout deployment is missing for " + stage.getTargetRef() + ".", details, "");
            return false;
        }
        DeploymentSecretUsageSummary secretUsage = deploymentService.getDeploymentSecretUsage(rollout.deploymentId());
        if (secretUsage.missingRequiredCount() > 0) {
            ObjectNode details = objectMapper.createObjectNode()
                .put("deploymentId", rollout.deploymentId())
                .put("missingRequiredCount", secretUsage.missingRequiredCount())
                .put("secretSummary", secretUsage.summaryMessage());
            completeStage(stage, "FAILED", "Required deployment secrets are missing for " + rollout.displayName() + ".", details, "");
            return false;
        }

        ObjectNode details = objectMapper.createObjectNode()
            .put("deploymentId", rollout.deploymentId())
            .put("deploymentName", rollout.displayName())
            .put("verificationProfile", rollout.verificationProfile());

        VectorizationRepairResult vectorizationRepair = ensureVectorizationReadyForHostedVerification(
            rollout.deploymentId(),
            allowControlPlaneRepair
        );
        details.set("vectorizationRepair", vectorizationRepair.details());
        if (!vectorizationRepair.ready()) {
            completeStage(stage, "FAILED", vectorizationRepair.summaryMessage(), details, "");
            return false;
        }

        DeploymentHostedVerificationDispatchSummary dispatch = deploymentHostedVerificationService.dispatch(
            rollout.deploymentId(),
            new DeploymentHostedVerificationDispatchRequest(rollout.verificationProfile(), false)
        );
        DeploymentHostedVerificationRunEntity hostedRun = awaitHostedVerification(dispatch.run().id());
        details
            .put("hostedRunId", hostedRun.getId())
            .put("hostedStatus", hostedRun.getStatus())
            .put("hostedSummaryMessage", defaultText(hostedRun.getSummaryMessage(), ""))
            .put("exitCode", hostedRun.getExitCode() == null ? -1 : hostedRun.getExitCode());
        if ("PASSED".equalsIgnoreCase(hostedRun.getStatus())) {
            completeStage(stage, "PASSED", hostedRun.getSummaryMessage(), details, "");
            return true;
        }
        completeStage(stage, "FAILED", hostedRun.getSummaryMessage(), details, "");
        return false;
    }

    private VectorizationRepairResult ensureVectorizationReadyForHostedVerification(String deploymentId,
                                                                                    boolean allowControlPlaneRepair) throws InterruptedException {
        VectorizationOverviewSummary overview = vectorizationService.getOverview(deploymentId);
        VectorizationPlanSummary plan = overview.plan();
        if (plan == null) {
            ObjectNode details = objectMapper.createObjectNode()
                .put("deploymentId", deploymentId)
                .put("vectorizationConfigured", false);
            return new VectorizationRepairResult(true, "Vectorization is not configured for this deployment.", details);
        }

        String syncState = defaultText(plan.syncState(), "UNKNOWN");
        if (vectorizationReady(syncState)) {
            ObjectNode details = summarizeVectorizationRepair(overview, null, false, "Vectorization is already ready for hosted verification.");
            return new VectorizationRepairResult(true, "Vectorization is already ready for hosted verification.", details);
        }

        if ("RUNNING".equalsIgnoreCase(syncState)) {
            return awaitVectorizationReady(deploymentId, plan.lastRunId(), false, "Waiting for an active vectorization run to finish before hosted verification.");
        }

        if (!allowControlPlaneRepair) {
            ObjectNode details = summarizeVectorizationRepair(overview, null, false, "Vectorization is not ready and control-plane repair is disabled.");
            return new VectorizationRepairResult(false, "Vectorization is not ready for hosted verification: " + syncState, details);
        }

        String repairReason = vectorizationRepairReason(syncState);
        if (repairReason == null) {
            ObjectNode details = summarizeVectorizationRepair(overview, null, true, "Vectorization is not ready and no governed repair path is available for the current sync state.");
            return new VectorizationRepairResult(false, "Vectorization requires manual operator review before hosted verification: " + syncState, details);
        }

        VectorizationRunSummary repairRun = vectorizationService.createRun(
            deploymentId,
            new CreateVectorizationRunRequest(
                repairReason,
                null,
                "Platform verification suite control-plane repair for syncState=" + syncState,
                null
            )
        );
        return awaitVectorizationReady(
            deploymentId,
            repairRun.id(),
            true,
            "Vectorization repair run completed before hosted verification."
        );
    }

    private VectorizationRepairResult awaitVectorizationReady(String deploymentId,
                                                              String runId,
                                                              boolean repairAttempted,
                                                              String successMessage) throws InterruptedException {
        Instant deadline = Instant.now().plus(suiteProperties.hostedStageTimeout());
        VectorizationRunDetailsSummary runDetails = null;
        while (Instant.now().isBefore(deadline)) {
            VectorizationOverviewSummary overview = vectorizationService.getOverview(deploymentId);
            if (runId != null && !runId.isBlank()) {
                runDetails = vectorizationService.getRunDetails(deploymentId, runId);
            }
            String syncState = overview.plan() == null ? "UNKNOWN" : defaultText(overview.plan().syncState(), "UNKNOWN");
            String runStatus = runDetails == null ? null : defaultText(runDetails.run().status(), "UNKNOWN");
            if (vectorizationReady(syncState) && (runStatus == null || "COMPLETED".equalsIgnoreCase(runStatus))) {
                ObjectNode details = summarizeVectorizationRepair(overview, runDetails == null ? null : runDetails.run(), repairAttempted, successMessage);
                return new VectorizationRepairResult(true, successMessage, details);
            }
            if (runStatus != null && List.of("FAILED", "CANCELLED").contains(runStatus.toUpperCase())) {
                ObjectNode details = summarizeVectorizationRepair(
                    overview,
                    runDetails.run(),
                    repairAttempted,
                    "Vectorization repair run did not complete successfully."
                );
                return new VectorizationRepairResult(false, "Vectorization repair run failed with status " + runStatus + ".", details);
            }
            Thread.sleep(suiteProperties.pollInterval().toMillis());
        }
        VectorizationOverviewSummary latestOverview = vectorizationService.getOverview(deploymentId);
        ObjectNode details = summarizeVectorizationRepair(
            latestOverview,
            runDetails == null ? null : runDetails.run(),
            repairAttempted,
            "Vectorization did not become ready before the hosted verification timeout."
        );
        return new VectorizationRepairResult(
            false,
            "Timed out waiting for vectorization to become ready for hosted verification.",
            details
        );
    }

    private ObjectNode summarizeVectorizationRepair(VectorizationOverviewSummary overview,
                                                    VectorizationRunSummary run,
                                                    boolean repairAttempted,
                                                    String summaryMessage) {
        ObjectNode details = objectMapper.createObjectNode()
            .put("deploymentId", overview.deploymentId())
            .put("repairAttempted", repairAttempted)
            .put("summaryMessage", defaultText(summaryMessage, ""));
        if (overview.plan() != null) {
            details.put("syncState", defaultText(overview.plan().syncState(), "UNKNOWN"));
            details.put("lastRunId", defaultText(overview.plan().lastRunId(), ""));
            details.put("lastSuccessfulRunId", defaultText(overview.plan().lastSuccessfulRunId(), ""));
            if (overview.plan().syncReasonDetails() != null) {
                details.set("syncReasonDetails", overview.plan().syncReasonDetails());
            }
        }
        if (overview.runner() != null) {
            details.put("runnerRegistrationStatus", defaultText(overview.runner().registrationStatus(), "UNKNOWN"));
            details.put("runnerLastHeartbeatAt", overview.runner().lastSessionHeartbeatAt() == null ? "" : overview.runner().lastSessionHeartbeatAt().toString());
            details.put("runnerLastSessionExpiresAt", overview.runner().lastSessionExpiresAt() == null ? "" : overview.runner().lastSessionExpiresAt().toString());
        }
        if (run != null) {
            ObjectNode runNode = details.putObject("run");
            runNode.put("id", defaultText(run.id(), ""));
            runNode.put("reason", defaultText(run.reason(), ""));
            runNode.put("status", defaultText(run.status(), "UNKNOWN"));
            runNode.put("requestedStatus", defaultText(run.requestedStatus(), "UNKNOWN"));
            if (run.progressSummary() != null) {
                runNode.set("progressSummary", run.progressSummary());
            }
            if (run.errorSummary() != null) {
                runNode.set("errorSummary", run.errorSummary());
            }
        }
        return details;
    }

    private boolean vectorizationReady(String syncState) {
        return List.of("IN_SYNC", "SOURCE_EMPTY", "MANUALLY_CONFIRMED").contains(defaultText(syncState, "UNKNOWN").toUpperCase());
    }

    private String vectorizationRepairReason(String syncState) {
        return switch (defaultText(syncState, "UNKNOWN").toUpperCase()) {
            case "BOOTSTRAP_REQUIRED", "SOURCE_EMPTY" -> "BOOTSTRAP";
            case "OUT_OF_DATE", "REINDEX_DEFERRED" -> "REINDEX";
            default -> null;
        };
    }

    private record VectorizationRepairResult(
        boolean ready,
        String summaryMessage,
        ObjectNode details
    ) {
    }

    private DeploymentHostedVerificationRunEntity awaitHostedVerification(String hostedRunId) throws InterruptedException {
        Instant deadline = Instant.now().plus(suiteProperties.hostedStageTimeout());
        while (Instant.now().isBefore(deadline)) {
            DeploymentHostedVerificationRunEntity run = deploymentHostedVerificationRunRepository.findById(hostedRunId)
                .orElseThrow(() -> new IllegalStateException("Hosted verification run disappeared: " + hostedRunId));
            if (!PlatformVerificationSuiteService.ACTIVE_STATUSES.contains(run.getStatus())) {
                return run;
            }
            Thread.sleep(suiteProperties.pollInterval().toMillis());
        }
        throw new IllegalStateException("Hosted verification run timed out before completion: " + hostedRunId);
    }

    private List<RolloutAssessment> assessRollouts(DeploymentVerificationRolloutSummary summary) {
        Map<String, DeploymentVerificationRolloutItemSummary> byKey = summary.items().stream()
            .collect(Collectors.toMap(DeploymentVerificationRolloutItemSummary::key, item -> item));
        return PlatformVerificationSuiteCatalog.CANONICAL_ROLLOUT_ORDER.stream()
            .map(key -> assessRollout(byKey.get(key), key))
            .toList();
    }

    private RolloutAssessment assessRollout(DeploymentVerificationRolloutItemSummary rollout, String key) {
        if (rollout == null) {
            return new RolloutAssessment(
                new DeploymentVerificationRolloutItemSummary(
                    key,
                    key,
                    "",
                    "vector",
                    false,
                    null,
                    "dev",
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    "Canonical rollout is missing.",
                    List.of("MISSING_DEPLOYMENT")
                ),
                "Canonical rollout is missing.",
                true,
                0,
                null
            );
        }
        if (!rollout.exists() || rollout.deploymentId() == null) {
            return new RolloutAssessment(rollout, "Canonical deployment is missing.", true, 0, null);
        }
        if (rollout.archived()) {
            return new RolloutAssessment(rollout, "Canonical deployment is archived.", true, 0, null);
        }
        if (rollout.runtimeBaseUrl() == null || rollout.runtimeBaseUrl().isBlank()) {
            return new RolloutAssessment(rollout, "Runtime URL is not available yet.", true, 0, null);
        }
        DeploymentSecretUsageSummary secretUsage = deploymentService.getDeploymentSecretUsage(rollout.deploymentId());
        if (secretUsage.missingRequiredCount() > 0) {
            return new RolloutAssessment(
                rollout,
                "Missing required deployment secrets.",
                false,
                secretUsage.missingRequiredCount(),
                secretUsage.summaryMessage()
            );
        }
        return new RolloutAssessment(rollout, null, false, 0, secretUsage.summaryMessage());
    }

    private void markStageRunning(PlatformVerificationSuiteRunStageEntity stage, String summaryMessage) {
        stage.setStatus("RUNNING");
        stage.setStartedAt(Instant.now());
        stage.setSummaryMessage(summaryMessage);
        stageRepository.save(stage);
    }

    private void completeStage(PlatformVerificationSuiteRunStageEntity stage,
                               String status,
                               String summaryMessage,
                               ObjectNode details) {
        completeStage(stage, status, summaryMessage, details, "");
    }

    private void completeStage(PlatformVerificationSuiteRunStageEntity stage,
                               String status,
                               String summaryMessage,
                               ObjectNode details,
                               String logOutput) {
        stage.setStatus(status);
        stage.setSummaryMessage(defaultText(summaryMessage, status));
        stage.setDetailsJson(details == null ? "{}" : details.toString());
        stage.setLogOutput(defaultText(logOutput, ""));
        stage.setCompletedAt(Instant.now());
        stageRepository.save(stage);
    }

    private void blockRemainingStages(List<PlatformVerificationSuiteRunStageEntity> stages,
                                      int afterStageOrder,
                                      String reason) {
        Instant now = Instant.now();
        stages.stream()
            .filter(stage -> stage.getStageOrder() > afterStageOrder)
            .filter(stage -> "QUEUED".equals(stage.getStatus()))
            .forEach(stage -> {
                stage.setStatus("BLOCKED");
                stage.setCompletedAt(now);
                stage.setSummaryMessage("Blocked by an earlier stage failure: " + reason);
                stageRepository.save(stage);
            });
    }

    private void completeRun(PlatformVerificationSuiteRunEntity run,
                             String status,
                             String summaryMessage) {
        run.setStatus(status);
        run.setSummaryMessage(summaryMessage);
        run.setCompletedAt(Instant.now());
        runRepository.save(run);
        platformAuditService.record(
            "PLATFORM_VERIFICATION_SUITE_COMPLETED",
            "PLATFORM_VERIFICATION_SUITE",
            run.getId(),
            Map.of(
                "suiteKey", run.getSuiteKey(),
                "status", status
            )
        );
    }

    private boolean sharedInferenceHealthy(String status) {
        return List.of("ACTIVE", "READY", "PASSED", "SUCCESS").contains(defaultText(status, "").toUpperCase());
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String extractFailureHeadline(String output) {
        if (output == null || output.isBlank()) {
            return "Verification script failed.";
        }
        return output.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> line.startsWith("FAIL:") || line.startsWith("Assertion failed") || line.startsWith("Missing "))
            .findFirst()
            .orElse("Verification script failed.");
    }

    private record RolloutAssessment(
        DeploymentVerificationRolloutItemSummary rollout,
        String blockReason,
        boolean structurallyBlocked,
        int missingRequiredSecrets,
        String secretSummary
    ) {
    }
}
