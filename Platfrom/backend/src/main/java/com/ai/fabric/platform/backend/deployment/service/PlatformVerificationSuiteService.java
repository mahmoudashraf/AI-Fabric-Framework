package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationReleaseGateSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteRunSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageRunSummary;
import com.ai.fabric.platform.backend.deployment.model.ShopifyCompanionVerificationExpectationOverrides;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.PlatformVerificationSuiteRunStageRepository;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PlatformVerificationSuiteService {

    static final List<String> ACTIVE_STATUSES = List.of("QUEUED", "RUNNING");

    private final PlatformVerificationSuiteCatalog catalog;
    private final PlatformVerificationSuiteRunRepository runRepository;
    private final PlatformVerificationSuiteRunStageRepository stageRepository;
    private final PlatformVerificationSuiteExecutionService executionService;
    private final PlatformVerificationSuiteProperties suiteProperties;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public PlatformVerificationSuiteService(PlatformVerificationSuiteCatalog catalog,
                                            PlatformVerificationSuiteRunRepository runRepository,
                                            PlatformVerificationSuiteRunStageRepository stageRepository,
                                            PlatformVerificationSuiteExecutionService executionService,
                                            PlatformVerificationSuiteProperties suiteProperties,
                                            PlatformAuditService platformAuditService,
                                            ObjectMapper objectMapper) {
        this.catalog = catalog;
        this.runRepository = runRepository;
        this.stageRepository = stageRepository;
        this.executionService = executionService;
        this.suiteProperties = suiteProperties;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    public List<PlatformVerificationSuiteDefinitionSummary> listDefinitions() {
        return catalog.listDefinitions();
    }

    public List<PlatformVerificationSuiteRunSummary> listRuns() {
        List<PlatformVerificationSuiteRunEntity> runs = runRepository.findAllByOrderByCreatedAtDesc().stream()
            .limit(suiteProperties.maxRecentRuns())
            .collect(Collectors.toList());
        recoverStaleRuns(runs);
        return runs.stream()
            .map(this::toSummary)
            .toList();
    }

    public PlatformVerificationSuiteRunSummary getRun(String runId) {
        PlatformVerificationSuiteRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Verification suite run not found: " + runId));
        recoverStaleRuns(List.of(run));
        return toSummary(run);
    }

    public PlatformVerificationReleaseGateSummary getReleaseGate() {
        PlatformVerificationSuiteDefinitionSummary definition = catalog.requireDefinition(PlatformVerificationSuiteCatalog.FULL_PLATFORM_RELEASE_READINESS_SUITE_KEY);
        Instant now = Instant.now();
        PlatformVerificationSuiteRunEntity latestRun = runRepository.findTopBySuiteKeyOrderByCreatedAtDesc(definition.key())
            .orElse(null);
        if (latestRun == null) {
            return new PlatformVerificationReleaseGateSummary(
                definition.key(),
                definition.label(),
                false,
                "MISSING",
                "No full platform release readiness run has been recorded yet.",
                suiteProperties.releaseGateFreshness(),
                now,
                null,
                null
            );
        }

        recoverStaleRuns(List.of(latestRun));
        PlatformVerificationSuiteRunSummary latestRunSummary = toSummary(latestRun);
        Instant completedAt = latestRunSummary.completedAt() != null
            ? latestRunSummary.completedAt()
            : latestRunSummary.createdAt();
        Instant expiresAt = completedAt == null ? null : completedAt.plus(suiteProperties.releaseGateFreshness());

        if (ACTIVE_STATUSES.contains(latestRun.getStatus())) {
            return new PlatformVerificationReleaseGateSummary(
                definition.key(),
                definition.label(),
                false,
                "RUNNING",
                "The latest full platform release readiness run is still in progress.",
                suiteProperties.releaseGateFreshness(),
                now,
                expiresAt,
                latestRunSummary
            );
        }

        if (!"PASSED".equalsIgnoreCase(latestRun.getStatus())) {
            return new PlatformVerificationReleaseGateSummary(
                definition.key(),
                definition.label(),
                false,
                "FAILED",
                "The latest full platform release readiness run did not pass. Run the full suite again and resolve the blocking stage before releasing new changes.",
                suiteProperties.releaseGateFreshness(),
                now,
                expiresAt,
                latestRunSummary
            );
        }

        if (expiresAt == null || now.isAfter(expiresAt)) {
            return new PlatformVerificationReleaseGateSummary(
                definition.key(),
                definition.label(),
                false,
                "STALE",
                "The latest full platform release readiness pass is older than the configured freshness window. Run it again before releasing new changes.",
                suiteProperties.releaseGateFreshness(),
                now,
                expiresAt,
                latestRunSummary
            );
        }

        return new PlatformVerificationReleaseGateSummary(
            definition.key(),
            definition.label(),
            true,
            "READY",
            "The latest full platform release readiness run passed within the configured freshness window.",
            suiteProperties.releaseGateFreshness(),
            now,
            expiresAt,
            latestRunSummary
        );
    }

    public PlatformVerificationSuiteDispatchSummary dispatch(String suiteKey,
                                                             PlatformVerificationSuiteDispatchRequest request) {
        PlatformVerificationSuiteDefinitionSummary definition = catalog.requireDefinition(suiteKey);
        recoverStaleRuns(runRepository.findAllByOrderByCreatedAtDesc().stream().limit(suiteProperties.maxRecentRuns()).toList());
        if (runRepository.existsBySuiteKeyAndStatusIn(definition.key(), ACTIVE_STATUSES)) {
            throw new ResponseStatusException(CONFLICT, "A verification suite run is already queued or running for " + definition.label() + ".");
        }

        Instant now = Instant.now();
        PlatformVerificationSuiteRunEntity run = new PlatformVerificationSuiteRunEntity();
        run.setId(generateRunId());
        run.setSuiteKey(definition.key());
        run.setSuiteLabel(definition.label());
        run.setStatus("QUEUED");
        run.setReleaseBlocking(definition.releaseBlocking());
        run.setSummaryMessage("Verification suite is queued on the platform control plane.");
        run.setRequestedByActorId(PlatformSecurityContext.actorIdOrSystem());
        run.setRequestedByRole(PlatformSecurityContext.actorRoleOrSystem());
        run.setCreatedAt(now);
        runRepository.save(run);

        List<PlatformVerificationSuiteRunStageEntity> stages = definition.stages().stream()
            .map(stage -> toStageEntity(run, stage, now, request))
            .toList();
        stageRepository.saveAll(stages);

        boolean allowControlPlaneRepair = request != null && request.allowControlPlaneRepair();
        platformAuditService.record(
            "PLATFORM_VERIFICATION_SUITE_DISPATCHED",
            "PLATFORM_VERIFICATION_SUITE",
            run.getId(),
            new LinkedHashMap<>(Map.of(
                "suiteKey", run.getSuiteKey(),
                "suiteLabel", run.getSuiteLabel(),
                "requestedByActorId", run.getRequestedByActorId(),
                "requestedByRole", run.getRequestedByRole(),
                "allowControlPlaneRepair", allowControlPlaneRepair,
                "shopifyExpectationOverrides", request == null || request.shopifyCompanionExpectations() == null
                    ? ""
                    : request.shopifyCompanionExpectations().toEnvironmentOverrides().toString()
            ))
        );
        executionService.execute(run.getId(), allowControlPlaneRepair);
        return new PlatformVerificationSuiteDispatchSummary(run.getSuiteKey(), run.getSummaryMessage(), toSummary(run, stages));
    }

    public PlatformVerificationSuiteRunSummary cancelRun(String runId) {
        PlatformVerificationSuiteRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Verification suite run not found: " + runId));
        if (!ACTIVE_STATUSES.contains(run.getStatus())) {
            throw new ResponseStatusException(CONFLICT, "Verification suite run is not active: " + runId);
        }
        Instant now = Instant.now();
        List<PlatformVerificationSuiteRunStageEntity> stages = stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId());
        run.setStatus("CANCELLED");
        run.setCompletedAt(now);
        run.setSummaryMessage("Verification suite was cancelled by a platform admin.");
        runRepository.save(run);
        stages.stream()
            .filter(stage -> ACTIVE_STATUSES.contains(stage.getStatus()))
            .forEach(stage -> {
                stage.setStatus("CANCELLED");
                stage.setCompletedAt(now);
                stage.setSummaryMessage("Stage was cancelled by a platform admin.");
                stageRepository.save(stage);
            });
        platformAuditService.record(
            "PLATFORM_VERIFICATION_SUITE_CANCELLED",
            "PLATFORM_VERIFICATION_SUITE",
            run.getId(),
            Map.of(
                "suiteKey", run.getSuiteKey(),
                "status", "CANCELLED"
            )
        );
        return toSummary(run, stages);
    }

    private PlatformVerificationSuiteRunStageEntity toStageEntity(PlatformVerificationSuiteRunEntity run,
                                                                  com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageDefinitionSummary stage,
                                                                  Instant createdAt,
                                                                  PlatformVerificationSuiteDispatchRequest request) {
        PlatformVerificationSuiteRunStageEntity entity = new PlatformVerificationSuiteRunStageEntity();
        entity.setId(generateStageId());
        entity.setSuiteRunId(run.getId());
        entity.setStageOrder(runStageOrder(stage, catalog.requireDefinition(run.getSuiteKey())));
        entity.setStageKey(stage.key());
        entity.setStageLabel(stage.label());
        entity.setStageType(stage.stageType());
        entity.setTargetRef(stage.targetRef());
        entity.setBlocking(stage.blocking());
        entity.setStatus("QUEUED");
        entity.setSummaryMessage("Awaiting verification suite execution.");
        entity.setDetailsJson(initialStageDetails(stage, request).toString());
        entity.setLogOutput("");
        entity.setCreatedAt(createdAt);
        return entity;
    }

    private com.fasterxml.jackson.databind.node.ObjectNode initialStageDetails(
        com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageDefinitionSummary stage,
        PlatformVerificationSuiteDispatchRequest request
    ) {
        com.fasterxml.jackson.databind.node.ObjectNode details = objectMapper.createObjectNode();
        ShopifyCompanionVerificationExpectationOverrides expectations = expectationsForStage(stage.targetRef(), request);
        if (expectations == null
            || expectations.isEmpty()
            || !supportsShopifyExpectationOverrides(stage.targetRef())) {
            return details;
        }
        details.set("scriptEnvironmentOverrides", objectMapper.valueToTree(expectations.toEnvironmentOverrides()));
        return details;
    }

    private ShopifyCompanionVerificationExpectationOverrides expectationsForStage(String targetRef,
                                                                                  PlatformVerificationSuiteDispatchRequest request) {
        if (request == null || !supportsShopifyExpectationOverrides(targetRef)) {
            return null;
        }
        if (PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT.equalsIgnoreCase(targetRef)
            && request.shopifyFirstProductReadinessExpectations() != null) {
            return request.shopifyFirstProductReadinessExpectations();
        }
        return request.shopifyCompanionExpectations();
    }

    private boolean supportsShopifyExpectationOverrides(String targetRef) {
        return PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_COMPANION_VERIFICATION.equalsIgnoreCase(targetRef)
            || PlatformVerificationSuiteScriptContextService.SCRIPT_SHOPIFY_FIRST_PRODUCT_READINESS_AUDIT.equalsIgnoreCase(targetRef);
    }

    private int runStageOrder(com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageDefinitionSummary stage,
                              PlatformVerificationSuiteDefinitionSummary definition) {
        return definition.stages().indexOf(stage) + 1;
    }

    private PlatformVerificationSuiteRunSummary toSummary(PlatformVerificationSuiteRunEntity run) {
        List<PlatformVerificationSuiteRunStageEntity> stages = stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId());
        return toSummary(run, stages);
    }

    private PlatformVerificationSuiteRunSummary toSummary(PlatformVerificationSuiteRunEntity run,
                                                          List<PlatformVerificationSuiteRunStageEntity> stages) {
        return new PlatformVerificationSuiteRunSummary(
            run.getId(),
            run.getSuiteKey(),
            run.getSuiteLabel(),
            run.getStatus(),
            run.isReleaseBlocking(),
            run.getSummaryMessage(),
            run.getRequestedByActorId(),
            run.getRequestedByRole(),
            run.getCreatedAt(),
            run.getStartedAt(),
            run.getCompletedAt(),
            stages.stream().map(this::toStageSummary).toList()
        );
    }

    private PlatformVerificationSuiteStageRunSummary toStageSummary(PlatformVerificationSuiteRunStageEntity stage) {
        return new PlatformVerificationSuiteStageRunSummary(
            stage.getId(),
            stage.getStageOrder(),
            stage.getStageKey(),
            stage.getStageLabel(),
            stage.getStageType(),
            stage.getTargetRef(),
            stage.isBlocking(),
            stage.getStatus(),
            stage.getSummaryMessage(),
            readDetails(stage.getDetailsJson()),
            stage.getLogOutput(),
            stage.getCreatedAt(),
            stage.getStartedAt(),
            stage.getCompletedAt()
        );
    }

    private JsonNode readDetails(String detailsJson) {
        try {
            return objectMapper.readTree(detailsJson == null || detailsJson.isBlank() ? "{}" : detailsJson);
        } catch (Exception ex) {
            return objectMapper.createObjectNode().put("parseError", ex.getMessage());
        }
    }

    private void recoverStaleRuns(List<PlatformVerificationSuiteRunEntity> runs) {
        Instant now = Instant.now();
        for (PlatformVerificationSuiteRunEntity run : runs) {
            if (!ACTIVE_STATUSES.contains(run.getStatus())) {
                continue;
            }
            List<PlatformVerificationSuiteRunStageEntity> stages = stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId());
            if (definitionDrifted(run, stages)) {
                recoverRun(
                    run,
                    stages,
                    now,
                    "SUPERSEDED",
                    "Verification suite was recovered because the active run no longer matches the current suite definition.",
                    "Stage was superseded because the parent verification suite definition changed while the run was active."
                );
                continue;
            }
            Instant baseline = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedAt();
            if (baseline == null || !now.isAfter(baseline.plus(suiteProperties.timeout()))) {
                continue;
            }
            recoverRun(
                run,
                stages,
                now,
                "TIMED_OUT",
                "Verification suite timed out after " + suiteProperties.timeout() + " without a completion signal.",
                "Stage timed out because the parent verification suite exceeded the configured timeout."
            );
        }
    }

    private boolean definitionDrifted(PlatformVerificationSuiteRunEntity run,
                                      List<PlatformVerificationSuiteRunStageEntity> stages) {
        Optional<PlatformVerificationSuiteDefinitionSummary> definition = catalog.listDefinitions().stream()
            .filter(candidate -> candidate.key().equalsIgnoreCase(run.getSuiteKey()))
            .findFirst();
        if (definition.isEmpty()) {
            return true;
        }
        List<String> currentSignature = definition.get().stages().stream()
            .map(stage -> stage.key() + "|" + stage.stageType() + "|" + stage.targetRef() + "|" + stage.blocking())
            .toList();
        List<String> runSignature = stages.stream()
            .map(stage -> stage.getStageKey() + "|" + stage.getStageType() + "|" + stage.getTargetRef() + "|" + stage.isBlocking())
            .toList();
        return !currentSignature.equals(runSignature);
    }

    private void recoverRun(PlatformVerificationSuiteRunEntity run,
                            List<PlatformVerificationSuiteRunStageEntity> stages,
                            Instant now,
                            String recoveredStatus,
                            String runSummary,
                            String stageSummary) {
        run.setStatus(recoveredStatus);
        run.setCompletedAt(now);
        run.setSummaryMessage(runSummary);
        runRepository.save(run);
        stages.stream()
            .filter(stage -> ACTIVE_STATUSES.contains(stage.getStatus()))
            .forEach(stage -> {
                stage.setStatus(recoveredStatus);
                stage.setCompletedAt(now);
                stage.setSummaryMessage(stageSummary);
                stageRepository.save(stage);
            });
        platformAuditService.record(
            "PLATFORM_VERIFICATION_SUITE_RECOVERED",
            "PLATFORM_VERIFICATION_SUITE",
            run.getId(),
            Map.of(
                "suiteKey", run.getSuiteKey(),
                "status", recoveredStatus
            )
        );
    }

    private String generateRunId() {
        return "vsr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String generateStageId() {
        return "vss-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
