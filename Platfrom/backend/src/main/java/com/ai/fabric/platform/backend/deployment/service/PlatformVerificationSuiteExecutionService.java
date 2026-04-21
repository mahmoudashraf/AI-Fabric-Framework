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
import com.ai.fabric.platform.backend.deployment.repository.DeploymentHostedVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.ai.fabric.platform.backend.marketplace.model.PlatformManagedInferenceHealthSummary;
import com.ai.fabric.platform.backend.marketplace.service.PlatformManagedInferenceAdminService;
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
                    case "HOSTED_DEPLOYMENT_VERIFICATION" -> executeHostedDeploymentVerification(stage);
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
            completeStage(stage, "PASSED", "Shared inference service is healthy.", details);
            return true;
        }
        completeStage(
            stage,
            "FAILED",
            defaultText(health.driftMessage(), defaultText(health.lastProbeMessage(), "Shared inference service is not healthy.")),
            details
        );
        return false;
    }

    private boolean executeCanonicalRolloutInventory(PlatformVerificationSuiteRunStageEntity stage,
                                                     boolean allowControlPlaneRepair) {
        markStageRunning(stage, "Resolving canonical rollout inventory and secret readiness.");
        DeploymentVerificationRolloutSummary summary = deploymentVerificationRolloutService.listRollouts();
        List<RolloutAssessment> assessments = assessRollouts(summary);
        boolean repaired = false;
        if (allowControlPlaneRepair && assessments.stream().anyMatch(assessment -> assessment.structurallyBlocked)) {
            List<String> selected = assessments.stream()
                .filter(assessment -> assessment.structurallyBlocked)
                .map(assessment -> assessment.rollout.key())
                .toList();
            deploymentVerificationRolloutService.recreateRollouts(selected);
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
            completeStage(stage, "PASSED", "Canonical rollout inventory is present and secret-ready.", details);
            return true;
        }
        completeStage(stage, "FAILED", String.join(" | ", blockers), details);
        return false;
    }

    private boolean executeHostedDeploymentVerification(PlatformVerificationSuiteRunStageEntity stage) throws InterruptedException {
        markStageRunning(stage, "Queueing hosted deployment verification.");
        DeploymentVerificationRolloutSummary rolloutSummary = deploymentVerificationRolloutService.listRollouts();
        DeploymentVerificationRolloutItemSummary rollout = rolloutSummary.items().stream()
            .filter(item -> item.key().equalsIgnoreCase(stage.getTargetRef()))
            .findFirst()
            .orElse(null);
        if (rollout == null || rollout.deploymentId() == null) {
            ObjectNode details = objectMapper.createObjectNode()
                .put("targetRef", defaultText(stage.getTargetRef(), ""));
            completeStage(stage, "FAILED", "Canonical rollout deployment is missing for " + stage.getTargetRef() + ".", details);
            return false;
        }
        DeploymentSecretUsageSummary secretUsage = deploymentService.getDeploymentSecretUsage(rollout.deploymentId());
        if (secretUsage.missingRequiredCount() > 0) {
            ObjectNode details = objectMapper.createObjectNode()
                .put("deploymentId", rollout.deploymentId())
                .put("missingRequiredCount", secretUsage.missingRequiredCount())
                .put("secretSummary", secretUsage.summaryMessage());
            completeStage(stage, "FAILED", "Required deployment secrets are missing for " + rollout.displayName() + ".", details);
            return false;
        }

        DeploymentHostedVerificationDispatchSummary dispatch = deploymentHostedVerificationService.dispatch(
            rollout.deploymentId(),
            new DeploymentHostedVerificationDispatchRequest(rollout.verificationProfile(), false)
        );
        DeploymentHostedVerificationRunEntity hostedRun = awaitHostedVerification(dispatch.run().id());
        ObjectNode details = objectMapper.createObjectNode()
            .put("deploymentId", rollout.deploymentId())
            .put("deploymentName", rollout.displayName())
            .put("verificationProfile", rollout.verificationProfile())
            .put("hostedRunId", hostedRun.getId())
            .put("hostedStatus", hostedRun.getStatus())
            .put("hostedSummaryMessage", defaultText(hostedRun.getSummaryMessage(), ""))
            .put("exitCode", hostedRun.getExitCode() == null ? -1 : hostedRun.getExitCode());
        if ("PASSED".equalsIgnoreCase(hostedRun.getStatus())) {
            completeStage(stage, "PASSED", hostedRun.getSummaryMessage(), details);
            return true;
        }
        completeStage(stage, "FAILED", hostedRun.getSummaryMessage(), details);
        return false;
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
        stage.setStatus(status);
        stage.setSummaryMessage(defaultText(summaryMessage, status));
        stage.setDetailsJson(details == null ? "{}" : details.toString());
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

    private record RolloutAssessment(
        DeploymentVerificationRolloutItemSummary rollout,
        String blockReason,
        boolean structurallyBlocked,
        int missingRequiredSecrets,
        String secretSummary
    ) {
    }
}
