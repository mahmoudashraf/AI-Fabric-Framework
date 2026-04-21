package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVerificationSuiteProperties;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.PlatformVerificationSuiteRunStageEntity;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDefinitionSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchRequest;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteDispatchSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteRunSummary;
import com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageRunSummary;
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
            .map(stage -> toStageEntity(run, stage, now))
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
                "allowControlPlaneRepair", allowControlPlaneRepair
            ))
        );
        executionService.execute(run.getId(), allowControlPlaneRepair);
        return new PlatformVerificationSuiteDispatchSummary(run.getSuiteKey(), run.getSummaryMessage(), toSummary(run, stages));
    }

    private PlatformVerificationSuiteRunStageEntity toStageEntity(PlatformVerificationSuiteRunEntity run,
                                                                  com.ai.fabric.platform.backend.deployment.model.PlatformVerificationSuiteStageDefinitionSummary stage,
                                                                  Instant createdAt) {
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
        entity.setDetailsJson("{}");
        entity.setCreatedAt(createdAt);
        return entity;
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
            Instant baseline = run.getStartedAt() != null ? run.getStartedAt() : run.getCreatedAt();
            if (baseline == null || !now.isAfter(baseline.plus(suiteProperties.timeout()))) {
                continue;
            }
            run.setStatus("TIMED_OUT");
            run.setCompletedAt(now);
            run.setSummaryMessage("Verification suite timed out after " + suiteProperties.timeout() + " without a completion signal.");
            runRepository.save(run);
            List<PlatformVerificationSuiteRunStageEntity> stages = stageRepository.findBySuiteRunIdOrderByStageOrderAsc(run.getId());
            stages.stream()
                .filter(stage -> ACTIVE_STATUSES.contains(stage.getStatus()))
                .forEach(stage -> {
                    stage.setStatus("TIMED_OUT");
                    stage.setCompletedAt(now);
                    stage.setSummaryMessage("Stage timed out because the parent verification suite exceeded the configured timeout.");
                    stageRepository.save(stage);
                });
            platformAuditService.record(
                "PLATFORM_VERIFICATION_SUITE_RECOVERED",
                "PLATFORM_VERIFICATION_SUITE",
                run.getId(),
                Map.of(
                    "suiteKey", run.getSuiteKey(),
                    "status", "TIMED_OUT"
                )
            );
        }
    }

    private String generateRunId() {
        return "vsr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private String generateStageId() {
        return "vss-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
