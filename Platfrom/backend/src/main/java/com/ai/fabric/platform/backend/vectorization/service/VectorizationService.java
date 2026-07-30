package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.RotateVectorizationRunnerTokenRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpdateVectorizationSyncStateRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpsertVectorizationPlanRequest;
import com.ai.fabric.platform.backend.vectorization.model.UpsertVectorizationSourceConnectionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanRevisionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPreviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunDetailsSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationCheckpointSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationFailureBucketSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerTokenSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationCheckpointRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationFailureBucketRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class VectorizationService {

    private static final Set<String> RUNNER_MODES = Set.of("PLATFORM_MANAGED_AUTO", "PLATFORM_MANAGED_NONE", "CUSTOMER_MANAGED_REMOTE");
    private static final Set<String> RUN_REASONS = Set.of("BOOTSTRAP", "REINDEX", "REFRESH", "DISCOVERY");
    private static final Set<String> SYNC_ACTIONS = Set.of("DEFER_REINDEX", "MANUAL_CONFIRM", "CLEAR_OVERRIDE");
    private static final Set<String> INLINE_SECRET_FIELD_MARKERS = Set.of("password", "token", "secret", "clientsecret", "apikey", "api-key");

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformAuditService platformAuditService;
    private final PlatformVectorizationProperties properties;
    private final VectorizationPlanRepository planRepository;
    private final VectorizationPlanRevisionRepository revisionRepository;
    private final VectorizationSourceConnectionRepository connectionRepository;
    private final VectorizationRunRepository runRepository;
    private final VectorizationCheckpointRepository checkpointRepository;
    private final VectorizationFailureBucketRepository failureBucketRepository;
    private final VectorizationRunnerRegistrationRepository registrationRepository;
    private final VectorizationRunnerSessionRepository sessionRepository;
    private final VectorizationJsonSupport jsonSupport;
    private final VectorizationIndexedOutputHashService hashService;
    private final VectorizationRuntimeCoverageClient runtimeCoverageClient;
    private final VectorizationTokenService tokenService;
    private final VectorizationRunnerProvisioningService runnerProvisioningService;
    private final PlatformSecretService platformSecretService;

    public VectorizationService(DeploymentRepository deploymentRepository,
                                DeploymentVersionRepository deploymentVersionRepository,
                                DeploymentAccessService deploymentAccessService,
                                PlatformAuditService platformAuditService,
                                PlatformVectorizationProperties properties,
                                VectorizationPlanRepository planRepository,
                                VectorizationPlanRevisionRepository revisionRepository,
                                VectorizationSourceConnectionRepository connectionRepository,
                                VectorizationRunRepository runRepository,
                                VectorizationCheckpointRepository checkpointRepository,
                                VectorizationFailureBucketRepository failureBucketRepository,
                                VectorizationRunnerRegistrationRepository registrationRepository,
                                VectorizationRunnerSessionRepository sessionRepository,
                                VectorizationJsonSupport jsonSupport,
                                VectorizationIndexedOutputHashService hashService,
                                VectorizationRuntimeCoverageClient runtimeCoverageClient,
                                VectorizationTokenService tokenService,
                                VectorizationRunnerProvisioningService runnerProvisioningService,
                                PlatformSecretService platformSecretService) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
        this.properties = properties;
        this.planRepository = planRepository;
        this.revisionRepository = revisionRepository;
        this.connectionRepository = connectionRepository;
        this.runRepository = runRepository;
        this.checkpointRepository = checkpointRepository;
        this.failureBucketRepository = failureBucketRepository;
        this.registrationRepository = registrationRepository;
        this.sessionRepository = sessionRepository;
        this.jsonSupport = jsonSupport;
        this.hashService = hashService;
        this.runtimeCoverageClient = runtimeCoverageClient;
        this.tokenService = tokenService;
        this.runnerProvisioningService = runnerProvisioningService;
        this.platformSecretService = platformSecretService;
    }

    @Transactional(readOnly = true)
    public VectorizationOverviewSummary getOverview(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        return buildOverview(deployment);
    }

    /**
     * Builds vectorization overview data for an internal workflow that already resolved and authorized the deployment.
     * Public/controller paths must use getOverview so deployment edit access is checked.
     */
    @Transactional(readOnly = true)
    public VectorizationOverviewSummary getOverviewForTrustedCaller(DeploymentEntity deployment) {
        return buildOverview(deployment);
    }

    private VectorizationOverviewSummary buildOverview(DeploymentEntity deployment) {
        String deploymentId = deployment.getId();
        DeploymentVersionEntity activeVersion = activeVersion(deployment);
        VectorizationSourceConnectionEntity connection = connectionRepository.findByDeploymentId(deploymentId).orElse(null);
        VectorizationPlanEntity plan = planRepository.findByDeploymentId(deploymentId).orElse(null);
        VectorizationPlanRevisionEntity revision = activeRevision(plan);
        List<VectorizationRunEntity> recentRuns = limitRuns(runRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId));
        VectorizationRunnerRegistrationEntity registration = registrationRepository.findByDeploymentId(deploymentId).orElse(null);
        VectorizationRunnerSessionEntity latestSession = latestSession(registration);
        Evaluation evaluation = evaluate(plan, revision, connection, activeVersion, deployment, recentRuns);

        return new VectorizationOverviewSummary(
            deployment.getId(),
            deployment.getCustomerId(),
            deployment.getTenantId(),
            activeVersion == null ? null : activeVersion.getId(),
            activeVersion == null ? null : activeVersion.getVersionLabel(),
            activeVersion == null ? null : activeVersion.getConfigHash(),
            summarizeConnection(connection),
            summarizePlan(evaluation.plan(), revision),
            summarizeRunner(registration, latestSession),
            recentRuns.stream().map(this::summarizeRun).toList()
        );
    }

    @Transactional
    public VectorizationSourceConnectionSummary upsertSourceConnection(String deploymentId,
                                                                      UpsertVectorizationSourceConnectionRequest request) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        return upsertSourceConnectionForDeployment(deployment, request);
    }

    /**
     * Upserts vectorization source connection data for an internal workflow that already resolved and authorized the deployment.
     * Public/controller paths must use upsertSourceConnection so deployment editor access is checked.
     */
    @Transactional
    public VectorizationSourceConnectionSummary upsertSourceConnectionForTrustedCaller(DeploymentEntity deployment,
                                                                                      UpsertVectorizationSourceConnectionRequest request) {
        return upsertSourceConnectionForDeployment(deployment, request);
    }

    private VectorizationSourceConnectionSummary upsertSourceConnectionForDeployment(DeploymentEntity deployment,
                                                                                   UpsertVectorizationSourceConnectionRequest request) {
        assertNoInlineSecrets(request.connectionConfig());

        Instant now = Instant.now();
        String deploymentId = deployment.getId();
        VectorizationSourceConnectionEntity entity = connectionRepository.findByDeploymentId(deploymentId).orElseGet(() -> {
            VectorizationSourceConnectionEntity created = new VectorizationSourceConnectionEntity();
            created.setId(generateId("vcn"));
            created.setDeploymentId(deployment.getId());
            created.setCustomerId(deployment.getCustomerId());
            created.setTenantId(deployment.getTenantId());
            created.setCreatedAt(now);
            return created;
        });
        entity.setName(request.name().trim());
        entity.setAdapterType(normalizeText(request.adapterType()));
        entity.setAuthMode(normalizeText(request.authMode()));
        entity.setStatus("READY");
        entity.setConnectionConfigJson(jsonSupport.write(defaultObject(request.connectionConfig())));
        entity.setSecretReferencesJson(jsonSupport.write(defaultObject(request.secretReferences())));
        entity.setDiscoverySummaryJson(jsonSupport.write(defaultObject(request.discoverySummary())));
        entity.setUpdatedAt(now);
        connectionRepository.save(entity);

        planRepository.findByDeploymentId(deploymentId).ifPresent(plan -> {
            plan.setSourceConnectionId(entity.getId());
            plan.setUpdatedAt(now);
            planRepository.save(plan);
        });

        platformAuditService.record(
            "VECTORIZATION_SOURCE_CONNECTION_UPSERTED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "deploymentId", deploymentId,
                "connectionId", entity.getId(),
                "adapterType", entity.getAdapterType(),
                "authMode", entity.getAuthMode()
            )
        );

        return summarizeConnection(entity);
    }

    @Transactional
    public VectorizationPlanSummary upsertPlan(String deploymentId, UpsertVectorizationPlanRequest request) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        return upsertPlanForDeployment(deployment, request);
    }

    /**
     * Upserts vectorization plan data for an internal workflow that already resolved and authorized the deployment.
     * Public/controller paths must use upsertPlan so deployment editor access is checked.
     */
    @Transactional
    public VectorizationPlanSummary upsertPlanForTrustedCaller(DeploymentEntity deployment,
                                                              UpsertVectorizationPlanRequest request) {
        return upsertPlanForDeployment(deployment, request);
    }

    private VectorizationPlanSummary upsertPlanForDeployment(DeploymentEntity deployment,
                                                            UpsertVectorizationPlanRequest request) {
        String deploymentId = deployment.getId();
        DeploymentVersionEntity activeVersion = activeVersion(deployment);
        VectorizationPlanEntity plan = ensurePlan(deployment);
        VectorizationSourceConnectionEntity connection = requireConnectionForDeployment(deploymentId, request.sourceConnectionId());
        Instant now = Instant.now();
        String indexedOutputHash = hashService.compute(activeVersion);

        plan.setName(request.name().trim());
        plan.setRunnerMode(normalizeRunnerMode(request.runnerMode()));
        plan.setSourceConnectionId(connection == null ? null : connection.getId());
        plan.setActiveIndexedOutputHash(indexedOutputHash);
        plan.setUpdatedAt(now);
        planRepository.save(plan);

        int nextRevision = revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(plan.getId())
            .map(existing -> existing.getRevisionNumber() + 1)
            .orElse(1);
        VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
        revision.setId(generateId("vpr"));
        revision.setPlanId(plan.getId());
        revision.setDeploymentId(deployment.getId());
        revision.setRevisionNumber(nextRevision);
        revision.setStatus("ACTIVE");
        revision.setSourceConnectionId(connection == null ? null : connection.getId());
        revision.setEntityScopeJson(jsonSupport.write(defaultEntityScope(request.entityScope(), activeVersion)));
        revision.setMappingConfigJson(jsonSupport.write(defaultObject(request.mappingConfig())));
        revision.setExecutionConfigJson(jsonSupport.write(defaultObject(request.executionConfig())));
        revision.setIndexedOutputHash(indexedOutputHash);
        revision.setCreatedByActorId(currentActorId());
        revision.setCreatedAt(now);
        revision.setUpdatedAt(now);
        revisionRepository.save(revision);

        plan.setActiveRevisionId(revision.getId());
        plan.setUpdatedAt(now);
        Evaluation evaluation = applyEvaluation(plan, revision, connection, activeVersion, deployment, runRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId));

        platformAuditService.record(
            "VECTORIZATION_PLAN_UPSERTED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "deploymentId", deploymentId,
                "planId", plan.getId(),
                "revisionId", revision.getId(),
                "runnerMode", plan.getRunnerMode()
            )
        );

        return summarizePlan(evaluation.plan(), revision);
    }

    @Transactional(readOnly = true)
    public VectorizationPreviewSummary preview(String deploymentId) {
        DeploymentEntity deployment = requireDeploymentEditor(deploymentId);
        DeploymentVersionEntity activeVersion = activeVersion(deployment);
        VectorizationPlanEntity plan = planRepository.findByDeploymentId(deploymentId).orElse(null);
        VectorizationPlanRevisionEntity revision = activeRevision(plan);
        VectorizationSourceConnectionEntity connection = connectionRepository.findByDeploymentId(deploymentId).orElse(null);
        Evaluation evaluation = evaluate(plan, revision, connection, activeVersion, deployment, runRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId));
        ObjectNode reindexOptions = jsonSupport.objectNode();
        ArrayNode entities = jsonSupport.arrayNode();
        configuredEntityTypes(activeVersion).forEach(entities::add);
        reindexOptions.set("availableEntities", entities);
        reindexOptions.put("supportsSelectedEntities", true);
        reindexOptions.put("supportsFullDeployment", true);
        reindexOptions.put("supportsDefer", true);

        return new VectorizationPreviewSummary(
            deploymentId,
            evaluation.plan() == null ? "BOOTSTRAP_REQUIRED" : evaluation.plan().getSyncState(),
            connection == null ? jsonSupport.objectNode() : jsonSupport.readObject(connection.getDiscoverySummaryJson()),
            revision == null ? jsonSupport.arrayNode() : jsonSupport.readTree(revision.getEntityScopeJson()),
            revision == null ? jsonSupport.objectNode() : jsonSupport.readObject(revision.getMappingConfigJson()),
            revision == null ? jsonSupport.objectNode() : jsonSupport.readObject(revision.getExecutionConfigJson()),
            reindexOptions,
            evaluation.plan() == null ? null : evaluation.plan().getActiveIndexedOutputHash()
        );
    }

    @Transactional
    public VectorizationRunSummary createRun(String deploymentId, CreateVectorizationRunRequest request) {
        DeploymentEntity deployment = requireDeploymentOperator(deploymentId);
        return createRunForDeployment(deployment, request, currentActorId());
    }

    /**
     * Queues a vectorization run for an internal workflow that already resolved and authorized the deployment.
     * Public/controller paths must use createRun so deployment operator access is checked.
     */
    @Transactional
    public VectorizationRunSummary createRunForTrustedCaller(String deploymentId,
                                                             CreateVectorizationRunRequest request,
                                                             String requestedByActorId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return createRunForDeployment(deployment, request, requestedByActorId);
    }

    private VectorizationRunSummary createRunForDeployment(DeploymentEntity deployment,
                                                           CreateVectorizationRunRequest request,
                                                           String requestedByActorId) {
        VectorizationPlanEntity plan = planRepository.findByDeploymentId(deployment.getId())
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Vectorization plan is not configured for this deployment."));
        VectorizationPlanRevisionEntity revision = activeRevision(plan);
        if (revision == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Vectorization plan has no active revision.");
        }
        String reason = normalizeReason(request.reason());
        Instant now = Instant.now();
        VectorizationRunEntity run = new VectorizationRunEntity();
        run.setId(generateId("vrn"));
        run.setPlanId(plan.getId());
        run.setPlanRevisionId(revision.getId());
        run.setDeploymentId(deployment.getId());
        run.setCustomerId(deployment.getCustomerId());
        run.setTenantId(deployment.getTenantId());
        run.setReason(reason);
        run.setRequestedStatus("QUEUED");
        run.setStatus("QUEUED");
        run.setRunnerMode(plan.getRunnerMode());
        run.setEntityScopeJson(jsonSupport.write(resolveRunScope(request.entityTypes(), revision)));
        run.setProgressSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
        run.setCheckpointSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
        run.setErrorSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
        run.setExecutionOverridesJson(jsonSupport.write(defaultObject(request.executionOverrides())));
        run.setRequestedByActorId(trimToNull(requestedByActorId));
        run.setRequestNote(trimToNull(request.note()));
        run.setCreatedAt(now);
        run.setUpdatedAt(now);
        runRepository.save(run);

        plan.setLastRunId(run.getId());
        plan.setUpdatedAt(now);
        planRepository.save(plan);

        platformAuditService.record(
            "VECTORIZATION_RUN_CREATED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "deploymentId", deployment.getId(),
                "runId", run.getId(),
                "reason", reason,
                "runnerMode", run.getRunnerMode()
            )
        );

        return summarizeRun(run);
    }

    @Transactional(readOnly = true)
    public VectorizationRunDetailsSummary getRunDetails(String deploymentId, String runId) {
        requireDeploymentOperator(deploymentId);
        VectorizationRunEntity run = requireRun(deploymentId, runId);
        return new VectorizationRunDetailsSummary(
            deploymentId,
            summarizeRun(run),
            checkpointRepository.findByRunIdOrderByUpdatedAtDesc(runId).stream().map(this::summarizeCheckpoint).toList(),
            failureBucketRepository.findByRunIdOrderByUpdatedAtDesc(runId).stream().map(this::summarizeFailureBucket).toList()
        );
    }

    @Transactional
    public VectorizationRunSummary updateRunCommand(String deploymentId, String runId, String command) {
        requireDeploymentOperator(deploymentId);
        VectorizationRunEntity run = requireRun(deploymentId, runId);
        Instant now = Instant.now();
        String normalized = normalizeText(command);
        switch (normalized) {
            case "PAUSE" -> run.setRequestedStatus("PAUSE_REQUESTED");
            case "RESUME" -> run.setRequestedStatus("RESUME_REQUESTED");
            case "CANCEL" -> run.setRequestedStatus("CANCEL_REQUESTED");
            case "RETRY" -> {
                preservePendingDataSyncWorkForRetry(run);
                run.setRequestedStatus("RETRY_REQUESTED");
                run.setStatus("QUEUED");
                run.setStartedAt(null);
                run.setCompletedAt(null);
                run.setLeaseExpiresAt(null);
                run.setClaimedByRegistrationId(null);
                run.setClaimedBySessionId(null);
                run.setRunnerInstanceId(null);
                run.setErrorSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
                run.setProgressSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
                run.setCheckpointSummaryJson(jsonSupport.write(jsonSupport.objectNode()));
                checkpointRepository.deleteByRunId(run.getId());
                failureBucketRepository.deleteByRunId(run.getId());
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization run command: " + command);
        }
        run.setUpdatedAt(now);
        runRepository.save(run);
        return summarizeRun(run);
    }

    private void preservePendingDataSyncWorkForRetry(
        VectorizationRunEntity run
    ) {
        ObjectNode overrides = jsonSupport.readObject(
            run.getExecutionOverridesJson()
        );
        overrides.remove("pendingDataSyncWork");

        JsonNode failures = jsonSupport.readTree(run.getErrorSummaryJson())
            .path("dataSync")
            .path("failures");
        if (failures.isArray()) {
            ArrayNode pendingWork = jsonSupport.arrayNode();
            Set<String> seenWorkIds = new LinkedHashSet<>();
            failures.forEach(failure -> {
                String workId = trimToNull(
                    failure.path("indexingWorkId").asText(null)
                );
                boolean durable = failure.path("durableHandoffAccepted")
                    .asBoolean(false);
                boolean requiresReconciliation =
                    "RECONCILE_DURABLE_WORK".equals(
                        normalizeText(
                            failure.path("retryDisposition").asText(null)
                        )
                    );
                if (!durable
                    || !requiresReconciliation
                    || workId == null
                    || !seenWorkIds.add(workId)) {
                    return;
                }
                ObjectNode item = pendingWork.addObject();
                item.put("workId", workId);
                putIfText(item, "vectorSpace", failure.path("vectorSpace"));
                putIfText(item, "entityId", failure.path("entityId"));
                putIfText(
                    item,
                    "indexingStatus",
                    failure.path("indexingStatus")
                );
                putIfText(
                    item,
                    "providerRequestId",
                    failure.path("providerRequestId")
                );
            });
            if (!pendingWork.isEmpty()) {
                overrides.set("pendingDataSyncWork", pendingWork);
            }
        }
        run.setExecutionOverridesJson(jsonSupport.write(overrides));
    }

    private void putIfText(
        ObjectNode target,
        String fieldName,
        JsonNode value
    ) {
        String text = trimToNull(value == null ? null : value.asText(null));
        if (text != null) {
            target.put(fieldName, text);
        }
    }

    @Transactional
    public VectorizationPlanSummary updateSyncState(String deploymentId, UpdateVectorizationSyncStateRequest request) {
        DeploymentEntity deployment = requireDeploymentOperator(deploymentId);
        DeploymentVersionEntity activeVersion = activeVersion(deployment);
        VectorizationPlanEntity plan = planRepository.findByDeploymentId(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Vectorization plan is not configured for this deployment."));
        VectorizationPlanRevisionEntity revision = activeRevision(plan);
        VectorizationSourceConnectionEntity connection = connectionRepository.findByDeploymentId(deploymentId).orElse(null);
        String action = normalizeText(request.action());
        if (!SYNC_ACTIONS.contains(action)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization sync action: " + request.action());
        }
        Instant now = Instant.now();
        if ("DEFER_REINDEX".equals(action)) {
            plan.setDeferredReindexAt(now);
            plan.setDeferredReindexNote(trimToNull(request.reason()));
            plan.setDeferredReindexHash(hashService.compute(activeVersion));
        } else if ("MANUAL_CONFIRM".equals(action)) {
            plan.setManuallyConfirmedAt(now);
            plan.setManualConfirmationActorId(currentActorId());
            plan.setManualConfirmationNote(trimToNull(request.reason()));
            plan.setManualConfirmationHash(hashService.compute(activeVersion));
        } else {
            plan.setDeferredReindexAt(null);
            plan.setDeferredReindexNote(null);
            plan.setDeferredReindexHash(null);
            plan.setManuallyConfirmedAt(null);
            plan.setManualConfirmationActorId(null);
            plan.setManualConfirmationNote(null);
            plan.setManualConfirmationHash(null);
        }
        plan.setUpdatedAt(now);
        Evaluation evaluation = applyEvaluation(plan, revision, connection, activeVersion, deployment, runRepository.findByDeploymentIdOrderByCreatedAtDesc(deploymentId));

        platformAuditService.record(
            "VECTORIZATION_SYNC_STATE_UPDATED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "deploymentId", deploymentId,
                "planId", plan.getId(),
                "action", action
            )
        );

        return summarizePlan(evaluation.plan(), revision);
    }

    @Transactional
    public VectorizationRunnerTokenSummary rotateRunnerToken(String deploymentId, RotateVectorizationRunnerTokenRequest request) {
        DeploymentEntity deployment = requireDeploymentAdmin(deploymentId);
        VectorizationPlanEntity plan = ensurePlan(deployment);
        Instant now = Instant.now();
        String runnerMode = normalizeRunnerMode(request.runnerMode() != null ? request.runnerMode() : plan.getRunnerMode());
        VectorizationRunnerRegistrationEntity registration = registrationRepository.findByDeploymentId(deploymentId).orElseGet(() -> {
            VectorizationRunnerRegistrationEntity created = new VectorizationRunnerRegistrationEntity();
            created.setId(generateId("vrr"));
            created.setDeploymentId(deployment.getId());
            created.setCustomerId(deployment.getCustomerId());
            created.setTenantId(deployment.getTenantId());
            created.setCreatedAt(now);
            return created;
        });
        String registrationToken = tokenService.generateToken();
        registration.setRunnerMode(runnerMode);
        registration.setStatus("ACTIVE");
        registration.setTokenHash(tokenService.hashToken(registrationToken));
        registration.setTokenHint(tokenService.tokenHint(registrationToken));
        registration.setTokenExpiresAt(now.plus(request.validityHours() != null
            ? java.time.Duration.ofHours(Math.max(1, request.validityHours()))
            : properties.registrationTokenTtl()));
        registration.setUpdatedAt(now);
        registrationRepository.save(registration);

        String managedSecretName = VectorizationManagedSecretNames.registrationTokenSecretName(deploymentId);
        if ("PLATFORM_MANAGED_AUTO".equalsIgnoreCase(runnerMode)) {
            platformSecretService.upsertManagedSecret(
                managedSecretName,
                registrationToken,
                Map.of(
                    "deploymentId", deploymentId,
                    "registrationId", registration.getId(),
                    "purpose", "VECTORIZATION_RUNNER_REGISTRATION"
                )
            );
        } else {
            runnerProvisioningService.clearManagedRegistrationSecret(deploymentId);
        }

        sessionRepository.findByRegistrationIdOrderByUpdatedAtDesc(registration.getId()).forEach(session -> {
            session.setStatus("REVOKED");
            session.setUpdatedAt(now);
            sessionRepository.save(session);
        });

        plan.setRunnerMode(runnerMode);
        plan.setUpdatedAt(now);
        planRepository.save(plan);

        platformAuditService.record(
            "VECTORIZATION_RUNNER_TOKEN_ROTATED",
            "DEPLOYMENT",
            deploymentId,
            Map.of(
                "deploymentId", deploymentId,
                "registrationId", registration.getId(),
                "runnerMode", runnerMode
            )
        );

        return new VectorizationRunnerTokenSummary(
            registration.getId(),
            runnerMode,
            registrationToken,
            registration.getTokenHint(),
            registration.getTokenExpiresAt()
        );
    }

    private DeploymentEntity requireDeploymentEditor(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentEditorAccess(deployment);
    }

    private DeploymentEntity requireDeploymentOperator(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentOperatorAccess(deployment);
    }

    private DeploymentEntity requireDeploymentAdmin(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentAdminAccess(deployment);
    }

    private VectorizationRunEntity requireRun(String deploymentId, String runId) {
        VectorizationRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vectorization run not found: " + runId));
        if (!deploymentId.equals(run.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "Vectorization run not found: " + runId);
        }
        return run;
    }

    private VectorizationPlanEntity ensurePlan(DeploymentEntity deployment) {
        return planRepository.findByDeploymentId(deployment.getId()).orElseGet(() -> {
            Instant now = Instant.now();
            VectorizationPlanEntity plan = new VectorizationPlanEntity();
            plan.setId(generateId("vpl"));
            plan.setDeploymentId(deployment.getId());
            plan.setCustomerId(deployment.getCustomerId());
            plan.setTenantId(deployment.getTenantId());
            plan.setName(deployment.getName() + " vectorization");
            plan.setStatus("ACTIVE");
            plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
            plan.setSyncState("BOOTSTRAP_REQUIRED");
            plan.setSyncReasonCodesJson(jsonSupport.writeList(List.of("PLAN_CREATED")));
            plan.setSyncReasonDetailsJson(jsonSupport.write(jsonSupport.objectNode()));
            plan.setCreatedAt(now);
            plan.setUpdatedAt(now);
            return planRepository.save(plan);
        });
    }

    private VectorizationSourceConnectionEntity requireConnectionForDeployment(String deploymentId, String sourceConnectionId) {
        if (StringUtils.hasText(sourceConnectionId)) {
            VectorizationSourceConnectionEntity entity = connectionRepository.findById(sourceConnectionId.trim())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vectorization source connection not found: " + sourceConnectionId));
            if (!deploymentId.equals(entity.getDeploymentId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Vectorization source connection does not belong to this deployment.");
            }
            return entity;
        }
        return connectionRepository.findByDeploymentId(deploymentId).orElse(null);
    }

    private DeploymentVersionEntity activeVersion(DeploymentEntity deployment) {
        if (deployment == null || !StringUtils.hasText(deployment.getActiveVersionId())) {
            return null;
        }
        return deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
    }

    private VectorizationPlanRevisionEntity activeRevision(VectorizationPlanEntity plan) {
        if (plan == null || !StringUtils.hasText(plan.getActiveRevisionId())) {
            return null;
        }
        return revisionRepository.findById(plan.getActiveRevisionId()).orElse(null);
    }

    private List<VectorizationRunEntity> limitRuns(List<VectorizationRunEntity> runs) {
        if (runs == null || runs.size() <= properties.maxRecentRuns()) {
            return runs == null ? List.of() : runs;
        }
        return runs.subList(0, properties.maxRecentRuns());
    }

    private VectorizationRunnerSessionEntity latestSession(VectorizationRunnerRegistrationEntity registration) {
        if (registration == null) {
            return null;
        }
        return sessionRepository.findByRegistrationIdOrderByUpdatedAtDesc(registration.getId()).stream()
            .findFirst()
            .orElse(null);
    }

    private Evaluation applyEvaluation(VectorizationPlanEntity plan,
                                       VectorizationPlanRevisionEntity revision,
                                       VectorizationSourceConnectionEntity connection,
                                       DeploymentVersionEntity activeVersion,
                                       DeploymentEntity deployment,
                                       List<VectorizationRunEntity> runs) {
        Evaluation evaluation = evaluate(plan, revision, connection, activeVersion, deployment, runs);
        if (evaluation.plan() != null) {
            planRepository.save(evaluation.plan());
        }
        return evaluation;
    }

    private Evaluation evaluate(VectorizationPlanEntity plan,
                                VectorizationPlanRevisionEntity revision,
                                VectorizationSourceConnectionEntity connection,
                                DeploymentVersionEntity activeVersion,
                                DeploymentEntity deployment,
                                List<VectorizationRunEntity> runs) {
        if (plan == null) {
            return new Evaluation(null);
        }
        String activeIndexedOutputHash = hashService.compute(activeVersion);
        plan.setActiveIndexedOutputHash(activeIndexedOutputHash);

        ObjectNode reasonDetails = jsonSupport.objectNode();
        ArrayNode reasonCodes = jsonSupport.arrayNode();
        JsonNode liveCounts = runtimeCoverageClient.fetchCounts(deployment);
        JsonNode discovery = connection == null ? jsonSupport.objectNode() : jsonSupport.readObject(connection.getDiscoverySummaryJson());
        JsonNode expectedCounts = discovery.path("countsByEntityType").isObject() ? discovery.path("countsByEntityType") : jsonSupport.objectNode();

        reasonDetails.set("expectedCountsByEntityType", expectedCounts);
        reasonDetails.set("liveCountsByEntityType", liveCounts);
        if (activeIndexedOutputHash != null) {
            reasonDetails.put("activeIndexedOutputHash", activeIndexedOutputHash);
        }
        if (plan.getLastSuccessfulIndexedOutputHash() != null) {
            reasonDetails.put("lastSuccessfulIndexedOutputHash", plan.getLastSuccessfulIndexedOutputHash());
        }

        String syncState;
        VectorizationRunEntity activeRun = runs == null ? null : runs.stream()
            .filter(run -> Set.of("QUEUED", "CLAIMED", "RUNNING", "PAUSE_REQUESTED", "RESUME_REQUESTED", "RETRY_REQUESTED").contains(run.getStatus())
                || Set.of("QUEUED", "PAUSE_REQUESTED", "RESUME_REQUESTED", "RETRY_REQUESTED", "CANCEL_REQUESTED").contains(run.getRequestedStatus()))
            .findFirst()
            .orElse(null);

        if (activeRun != null) {
            syncState = "RUNNING";
            reasonCodes.add("RUN_IN_PROGRESS");
            reasonDetails.put("activeRunId", activeRun.getId());
        } else if (allExpectedCountsZero(expectedCounts)) {
            syncState = "SOURCE_EMPTY";
            reasonCodes.add("SOURCE_EMPTY");
        } else if (plan.getDeferredReindexAt() != null && activeIndexedOutputHash != null
            && activeIndexedOutputHash.equals(plan.getDeferredReindexHash())
            && !activeIndexedOutputHash.equals(plan.getLastSuccessfulIndexedOutputHash())) {
            syncState = "REINDEX_DEFERRED";
            reasonCodes.add("REINDEX_DEFERRED");
        } else if (manualConfirmApplies(plan, activeIndexedOutputHash)) {
            syncState = "MANUALLY_CONFIRMED";
            reasonCodes.add("MANUALLY_CONFIRMED");
        } else if (activeIndexedOutputHash != null
            && plan.getLastSuccessfulIndexedOutputHash() != null
            && !activeIndexedOutputHash.equals(plan.getLastSuccessfulIndexedOutputHash())) {
            syncState = "OUT_OF_DATE";
            reasonCodes.add("INDEXED_OUTPUT_DRIFT");
        } else if (bootstrapRequired(expectedCounts, liveCounts)) {
            syncState = "BOOTSTRAP_REQUIRED";
            reasonCodes.add("BOOTSTRAP_REQUIRED");
        } else {
            syncState = "IN_SYNC";
            reasonCodes.add("IN_SYNC");
        }

        plan.setSyncState(syncState);
        plan.setSyncReasonCodesJson(jsonSupport.write(reasonCodes));
        plan.setSyncReasonDetailsJson(jsonSupport.write(reasonDetails));
        plan.setUpdatedAt(Instant.now());
        return new Evaluation(plan);
    }

    private boolean manualConfirmApplies(VectorizationPlanEntity plan, String activeIndexedOutputHash) {
        return plan.getManuallyConfirmedAt() != null
            && activeIndexedOutputHash != null
            && activeIndexedOutputHash.equals(plan.getManualConfirmationHash());
    }

    private boolean bootstrapRequired(JsonNode expectedCounts, JsonNode liveCounts) {
        if (!expectedCounts.isObject()) {
            return liveCounts == null || liveCounts.size() == 0;
        }
        boolean anyPositiveExpected = false;
        var fields = expectedCounts.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            long expected = entry.getValue().asLong(0);
            if (expected <= 0) {
                continue;
            }
            anyPositiveExpected = true;
            long live = liveCounts.path(entry.getKey()).asLong(0);
            if (live <= 0) {
                return true;
            }
        }
        return !anyPositiveExpected && (liveCounts == null || liveCounts.size() == 0);
    }

    private boolean allExpectedCountsZero(JsonNode expectedCounts) {
        if (!expectedCounts.isObject() || expectedCounts.size() == 0) {
            return false;
        }
        var fields = expectedCounts.fields();
        while (fields.hasNext()) {
            if (fields.next().getValue().asLong(0) > 0) {
                return false;
            }
        }
        return true;
    }

    private JsonNode resolveRunScope(List<String> requestedEntities, VectorizationPlanRevisionEntity revision) {
        if (requestedEntities != null && !requestedEntities.isEmpty()) {
            ArrayNode arrayNode = jsonSupport.arrayNode();
            requestedEntities.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .forEach(arrayNode::add);
            return arrayNode;
        }
        return revision == null ? jsonSupport.arrayNode() : jsonSupport.readTree(revision.getEntityScopeJson());
    }

    private JsonNode defaultEntityScope(JsonNode entityScope, DeploymentVersionEntity activeVersion) {
        if (entityScope != null && !entityScope.isNull() && !entityScope.isMissingNode()) {
            return entityScope;
        }
        ArrayNode defaults = jsonSupport.arrayNode();
        configuredEntityTypes(activeVersion).forEach(defaults::add);
        return defaults;
    }

    private List<String> configuredEntityTypes(DeploymentVersionEntity activeVersion) {
        if (activeVersion == null) {
            return List.of();
        }
        JsonNode entityConfig = jsonSupport.readObject(activeVersion.getEntityConfigJson()).path("ai-entities");
        List<String> entityTypes = new ArrayList<>();
        if (entityConfig.isObject()) {
            entityConfig.fieldNames().forEachRemaining(name -> {
                if (name != null && !name.isBlank()) {
                    entityTypes.add(name);
                }
            });
        }
        return entityTypes.stream().distinct().sorted().toList();
    }

    private void assertNoInlineSecrets(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String normalizedKey = entry.getKey() == null ? "" : entry.getKey().replaceAll("[_\\-]", "").toLowerCase(Locale.ROOT);
                JsonNode value = entry.getValue();
                if (value != null && value.isTextual() && INLINE_SECRET_FIELD_MARKERS.stream().anyMatch(normalizedKey::contains)
                    && StringUtils.hasText(value.asText())) {
                    throw new ResponseStatusException(BAD_REQUEST, "Inline secret values are blocked. Use secret references instead.");
                }
                assertNoInlineSecrets(value);
            });
        } else if (node.isArray()) {
            node.forEach(this::assertNoInlineSecrets);
        }
    }

    private String normalizeRunnerMode(String value) {
        String normalized = normalizeText(value);
        if (!RUNNER_MODES.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization runner mode: " + value);
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        String normalized = normalizeText(value);
        if (!RUN_REASONS.contains(normalized)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization run reason: " + value);
        }
        return normalized;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private JsonNode defaultObject(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? jsonSupport.objectNode() : node;
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

    public VectorizationSourceConnectionSummary summarizeConnection(VectorizationSourceConnectionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new VectorizationSourceConnectionSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getName(),
            entity.getAdapterType(),
            entity.getAuthMode(),
            entity.getStatus(),
            jsonSupport.readTree(entity.getConnectionConfigJson()),
            jsonSupport.readTree(entity.getSecretReferencesJson()),
            jsonSupport.readTree(entity.getDiscoverySummaryJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public VectorizationPlanSummary summarizePlan(VectorizationPlanEntity entity, VectorizationPlanRevisionEntity revision) {
        if (entity == null) {
            return null;
        }
        return new VectorizationPlanSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getName(),
            entity.getStatus(),
            entity.getRunnerMode(),
            entity.getSyncState(),
            jsonSupport.readStringList(entity.getSyncReasonCodesJson()),
            jsonSupport.readTree(entity.getSyncReasonDetailsJson()),
            entity.getActiveIndexedOutputHash(),
            entity.getLastSuccessfulIndexedOutputHash(),
            entity.getActiveRevisionId(),
            entity.getSourceConnectionId(),
            entity.getLastRunId(),
            entity.getLastSuccessfulRunId(),
            entity.getManuallyConfirmedAt(),
            entity.getDeferredReindexAt(),
            summarizeRevision(revision),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public VectorizationPlanRevisionSummary summarizeRevision(VectorizationPlanRevisionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new VectorizationPlanRevisionSummary(
            entity.getId(),
            entity.getRevisionNumber(),
            entity.getStatus(),
            entity.getSourceConnectionId(),
            jsonSupport.readTree(entity.getEntityScopeJson()),
            jsonSupport.readTree(entity.getMappingConfigJson()),
            jsonSupport.readTree(entity.getExecutionConfigJson()),
            entity.getIndexedOutputHash(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public VectorizationRunSummary summarizeRun(VectorizationRunEntity entity) {
        List<String> scope = jsonSupport.readStringList(entity.getEntityScopeJson());
        return new VectorizationRunSummary(
            entity.getId(),
            entity.getReason(),
            entity.getRequestedStatus(),
            entity.getStatus(),
            entity.getRunnerMode(),
            scope,
            jsonSupport.readTree(entity.getProgressSummaryJson()),
            jsonSupport.readTree(entity.getCheckpointSummaryJson()),
            jsonSupport.readTree(entity.getErrorSummaryJson()),
            entity.getClaimedByRegistrationId(),
            entity.getClaimedBySessionId(),
            entity.getRunnerInstanceId(),
            entity.getProductVersion(),
            entity.getCompatibilityVersion(),
            entity.getLeaseExpiresAt(),
            entity.getCreatedAt(),
            entity.getStartedAt(),
            entity.getCompletedAt(),
            entity.getUpdatedAt()
        );
    }

    public VectorizationCheckpointSummary summarizeCheckpoint(com.ai.fabric.platform.backend.vectorization.entity.VectorizationCheckpointEntity entity) {
        return new VectorizationCheckpointSummary(
            entity.getId(),
            entity.getEntityType(),
            entity.getCheckpointType(),
            entity.getCheckpointValue(),
            jsonSupport.readTree(entity.getProgressJson()),
            jsonSupport.readTree(entity.getDetailsJson()),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public VectorizationFailureBucketSummary summarizeFailureBucket(com.ai.fabric.platform.backend.vectorization.entity.VectorizationFailureBucketEntity entity) {
        return new VectorizationFailureBucketSummary(
            entity.getId(),
            entity.getEntityType(),
            entity.getErrorCode(),
            entity.getSummary(),
            jsonSupport.readTree(entity.getSampleJson()),
            entity.getOccurrences(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    public VectorizationRunnerSummary summarizeRunner(VectorizationRunnerRegistrationEntity registration,
                                                     VectorizationRunnerSessionEntity session) {
        if (registration == null) {
            return null;
        }
        String effectiveRunnerInstanceId = StringUtils.hasText(registration.getRunnerInstanceId())
            ? registration.getRunnerInstanceId()
            : session == null ? null : session.getRunnerInstanceId();
        String effectiveProductVersion = StringUtils.hasText(registration.getProductVersion())
            ? registration.getProductVersion()
            : session == null ? null : session.getProductVersion();
        String effectiveCompatibilityVersion = StringUtils.hasText(registration.getCompatibilityVersion())
            ? registration.getCompatibilityVersion()
            : session == null ? null : session.getCompatibilityVersion();
        String compatibilityStatus = evaluateCompatibility(effectiveProductVersion, effectiveCompatibilityVersion);
        return new VectorizationRunnerSummary(
            registration.getId(),
            registration.getRunnerMode(),
            registration.getStatus(),
            compatibilityStatus,
            registration.getTokenHint(),
            registration.getTokenExpiresAt(),
            effectiveRunnerInstanceId,
            effectiveProductVersion,
            effectiveCompatibilityVersion,
            registration.getLastConnectedAt(),
            session == null ? null : session.getLastHeartbeatAt(),
            session == null ? null : session.getExpiresAt()
        );
    }

    String evaluateCompatibility(String productVersion, String compatibilityVersion) {
        if (!StringUtils.hasText(compatibilityVersion)
            || !properties.requiredCompatibilityVersion().equals(compatibilityVersion.trim())) {
            return "INCOMPATIBLE";
        }
        if (!StringUtils.hasText(productVersion)
            || !properties.requiredProductVersion().equals(productVersion.trim())) {
            return "OUTDATED";
        }
        return "CURRENT";
    }

    record Evaluation(VectorizationPlanEntity plan) {
    }
}
