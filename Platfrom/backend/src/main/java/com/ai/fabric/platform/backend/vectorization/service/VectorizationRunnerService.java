package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreVectorizationEventService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreVectorizationLedgerService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationCheckpointEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationFailureBucketEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationExecutionBundleSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerCheckpointRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerClaimSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerCompletionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerDiscoveryReportRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerHeartbeatRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerHeartbeatSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSessionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSessionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationCheckpointRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationFailureBucketRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class VectorizationRunnerService {

    private static final Set<String> CLAIMABLE_STATUSES = Set.of("QUEUED", "RESUME_REQUESTED", "RETRY_REQUESTED");
    private static final Set<String> COMPLETION_STATUSES = Set.of("COMPLETED", "FAILED", "PAUSED", "CANCELLED");
    private static final Set<String> IN_FLIGHT_STATUSES = Set.of(
        "QUEUED",
        "CLAIMED",
        "RUNNING",
        "PAUSE_REQUESTED",
        "RESUME_REQUESTED",
        "RETRY_REQUESTED"
    );
    private static final Set<String> IN_FLIGHT_REQUESTED_STATUSES = Set.of(
        "QUEUED",
        "PAUSE_REQUESTED",
        "RESUME_REQUESTED",
        "RETRY_REQUESTED",
        "CANCEL_REQUESTED"
    );

    private final PlatformVectorizationProperties properties;
    private final PlatformAuditService platformAuditService;
    private final DeploymentRepository deploymentRepository;
    private final VectorizationPlanRepository planRepository;
    private final VectorizationPlanRevisionRepository revisionRepository;
    private final VectorizationSourceConnectionRepository connectionRepository;
    private final VectorizationRunRepository runRepository;
    private final VectorizationRunnerRegistrationRepository registrationRepository;
    private final VectorizationRunnerSessionRepository sessionRepository;
    private final VectorizationCheckpointRepository checkpointRepository;
    private final VectorizationFailureBucketRepository failureBucketRepository;
    private final VectorizationJsonSupport jsonSupport;
    private final VectorizationTokenService tokenService;
    private final VectorizationService vectorizationService;
    private final PlatformSecretService platformSecretService;
    private final ShopifyStoreVectorizationLedgerService shopifyStoreVectorizationLedgerService;
    private final ShopifyStoreVectorizationEventService shopifyStoreVectorizationEventService;

    public VectorizationRunnerService(PlatformVectorizationProperties properties,
                                      PlatformAuditService platformAuditService,
                                      DeploymentRepository deploymentRepository,
                                      VectorizationPlanRepository planRepository,
                                      VectorizationPlanRevisionRepository revisionRepository,
                                      VectorizationSourceConnectionRepository connectionRepository,
                                      VectorizationRunRepository runRepository,
                                      VectorizationRunnerRegistrationRepository registrationRepository,
                                      VectorizationRunnerSessionRepository sessionRepository,
                                      VectorizationCheckpointRepository checkpointRepository,
                                      VectorizationFailureBucketRepository failureBucketRepository,
                                      VectorizationJsonSupport jsonSupport,
                                      VectorizationTokenService tokenService,
                                      VectorizationService vectorizationService,
                                      PlatformSecretService platformSecretService,
                                      ShopifyStoreVectorizationLedgerService shopifyStoreVectorizationLedgerService,
                                      ShopifyStoreVectorizationEventService shopifyStoreVectorizationEventService) {
        this.properties = properties;
        this.platformAuditService = platformAuditService;
        this.deploymentRepository = deploymentRepository;
        this.planRepository = planRepository;
        this.revisionRepository = revisionRepository;
        this.connectionRepository = connectionRepository;
        this.runRepository = runRepository;
        this.registrationRepository = registrationRepository;
        this.sessionRepository = sessionRepository;
        this.checkpointRepository = checkpointRepository;
        this.failureBucketRepository = failureBucketRepository;
        this.jsonSupport = jsonSupport;
        this.tokenService = tokenService;
        this.vectorizationService = vectorizationService;
        this.platformSecretService = platformSecretService;
        this.shopifyStoreVectorizationLedgerService = shopifyStoreVectorizationLedgerService;
        this.shopifyStoreVectorizationEventService = shopifyStoreVectorizationEventService;
    }

    @Transactional
    public VectorizationRunnerSessionSummary createSession(VectorizationRunnerSessionRequest request) {
        String tokenHash = tokenService.hashToken(request.registrationToken());
        VectorizationRunnerRegistrationEntity registration = registrationRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid runner registration token."));
        if (!"ACTIVE".equalsIgnoreCase(registration.getStatus())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Runner registration is not active.");
        }
        if (registration.getTokenExpiresAt() != null && registration.getTokenExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Runner registration token has expired.");
        }

        String compatibilityStatus = vectorizationService.evaluateCompatibility(request.productVersion(), request.compatibilityVersion());
        if ("INCOMPATIBLE".equalsIgnoreCase(compatibilityStatus)) {
            throw new ResponseStatusException(CONFLICT, "Runner compatibility version is not accepted by the platform.");
        }

        Instant now = Instant.now();
        String sessionToken = tokenService.generateToken();
        VectorizationRunnerSessionEntity session = new VectorizationRunnerSessionEntity();
        session.setId(generateId("vrs"));
        session.setRegistrationId(registration.getId());
        session.setDeploymentId(registration.getDeploymentId());
        session.setStatus("ACTIVE");
        session.setSessionTokenHash(tokenService.hashToken(sessionToken));
        session.setRunnerInstanceId(request.runnerInstanceId().trim());
        session.setProductVersion(trimToNull(request.productVersion()));
        session.setCompatibilityVersion(trimToNull(request.compatibilityVersion()));
        session.setExpiresAt(now.plus(properties.runnerSessionTtl()));
        session.setLastHeartbeatAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        registration.setLastConnectedAt(now);
        registration.setRunnerInstanceId(request.runnerInstanceId().trim());
        registration.setProductVersion(trimToNull(request.productVersion()));
        registration.setCompatibilityVersion(trimToNull(request.compatibilityVersion()));
        registration.setUpdatedAt(now);
        registrationRepository.save(registration);

        platformAuditService.record(
            "VECTORIZATION_RUNNER_SESSION_CREATED",
            "DEPLOYMENT",
            registration.getDeploymentId(),
            Map.of(
                "deploymentId", registration.getDeploymentId(),
                "registrationId", registration.getId(),
                "runnerInstanceId", request.runnerInstanceId().trim(),
                "compatibilityStatus", compatibilityStatus
            )
        );

        return new VectorizationRunnerSessionSummary(
            sessionToken,
            registration.getId(),
            registration.getDeploymentId(),
            registration.getRunnerMode(),
            compatibilityStatus,
            session.getExpiresAt()
        );
    }

    @Transactional
    public VectorizationRunnerClaimSummary claimNextRun(String sessionToken) {
        VectorizationRunnerSessionEntity session = requireActiveSession(sessionToken);
        if (StringUtils.hasText(session.getRunId())) {
            VectorizationRunEntity current = runRepository.findById(session.getRunId()).orElse(null);
            if (current != null && !leaseExpired(current.getLeaseExpiresAt())) {
                return new VectorizationRunnerClaimSummary(session.getStatus(), vectorizationService.summarizeRun(current));
            }
        }

        List<VectorizationRunEntity> candidates = runRepository.findByDeploymentIdAndRequestedStatusInOrderByCreatedAtAsc(
            session.getDeploymentId(),
            List.copyOf(CLAIMABLE_STATUSES)
        );
        Instant now = Instant.now();
        VectorizationRunEntity claimed = candidates.stream()
            .filter(run -> leaseExpired(run.getLeaseExpiresAt()) || session.getId().equals(run.getClaimedBySessionId()))
            .findFirst()
            .orElse(null);
        if (claimed == null) {
            return new VectorizationRunnerClaimSummary(session.getStatus(), null);
        }

        VectorizationRunnerRegistrationEntity registration = registrationRepository.findById(session.getRegistrationId())
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Runner registration not found."));
        claimed.setClaimedByRegistrationId(registration.getId());
        claimed.setClaimedBySessionId(session.getId());
        claimed.setRunnerInstanceId(session.getRunnerInstanceId());
        claimed.setProductVersion(session.getProductVersion());
        claimed.setCompatibilityVersion(session.getCompatibilityVersion());
        claimed.setRequestedStatus("RUNNING");
        claimed.setStatus("RUNNING");
        claimed.setStartedAt(claimed.getStartedAt() == null ? now : claimed.getStartedAt());
        claimed.setLeaseExpiresAt(now.plus(properties.runLeaseTtl()));
        claimed.setUpdatedAt(now);
        runRepository.save(claimed);

        session.setRunId(claimed.getId());
        session.setLeaseExpiresAt(claimed.getLeaseExpiresAt());
        session.setLastHeartbeatAt(now);
        session.setExpiresAt(now.plus(properties.runnerSessionTtl()));
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        return new VectorizationRunnerClaimSummary(session.getStatus(), vectorizationService.summarizeRun(claimed));
    }

    @Transactional(readOnly = true)
    public VectorizationExecutionBundleSummary fetchExecutionBundle(String sessionToken, String runId) {
        VectorizationRunnerSessionEntity session = requireActiveSession(sessionToken);
        VectorizationRunEntity run = requireOwnedRun(session, runId);
        VectorizationPlanRevisionEntity revision = revisionRepository.findById(run.getPlanRevisionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vectorization plan revision not found: " + run.getPlanRevisionId()));
        VectorizationSourceConnectionEntity connection = resolveConnection(revision.getSourceConnectionId(), run.getDeploymentId());
        DeploymentEntity deployment = deploymentRepository.findById(run.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + run.getDeploymentId()));
        JsonNode effectiveExecutionConfig = jsonSupport.mergeObjects(
            jsonSupport.readTree(revision.getExecutionConfigJson()),
            jsonSupport.readTree(run.getExecutionOverridesJson())
        );

        JsonNode secretRefs = connection == null ? jsonSupport.objectNode() : jsonSupport.readObject(connection.getSecretReferencesJson());
        JsonNode sourceAuth = "CUSTOMER_MANAGED_REMOTE".equalsIgnoreCase(run.getRunnerMode())
            ? jsonSupport.objectNode()
            : resolvePlatformManagedSourceAuth(secretRefs);
        JsonNode localAliases = "CUSTOMER_MANAGED_REMOTE".equalsIgnoreCase(run.getRunnerMode())
            ? secretRefs.path("localSecretAliases").deepCopy()
            : jsonSupport.objectNode();

        ObjectNode connectionDescriptor = jsonSupport.objectNode();
        if (connection != null) {
            connectionDescriptor.put("connectionId", connection.getId());
            connectionDescriptor.put("adapterType", connection.getAdapterType());
            connectionDescriptor.put("authMode", connection.getAuthMode());
            connectionDescriptor.put("name", connection.getName());
            connectionDescriptor.set("discoverySummary", jsonSupport.readTree(connection.getDiscoverySummaryJson()));
            connectionDescriptor.set("config", jsonSupport.readTree(connection.getConnectionConfigJson()));
        }

        String targetBaseUrl = trimToNull(deployment.getRuntimeBaseUrl());
        if (!StringUtils.hasText(targetBaseUrl)) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment runtime URL is not available for vectorization execution.");
        }
        String runtimeTrustedBackendApiKey = trimToNull(platformSecretService.resolveSecret(RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME));
        if (!StringUtils.hasText(runtimeTrustedBackendApiKey)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                RuntimePrivateAccessSupport.TRUSTED_BACKEND_SECRET_NAME + " is not configured for vectorization execution."
            );
        }
        ObjectNode targetVerifiedAuthContext = jsonSupport.objectNode();
        targetVerifiedAuthContext.put("subjectId", "system:platform-vectorization-runner");
        targetVerifiedAuthContext.put("subjectType", "SYSTEM_PROCESS");
        targetVerifiedAuthContext.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        targetVerifiedAuthContext.put("callerType", "SYSTEM_PROCESS");
        targetVerifiedAuthContext.put("sessionId", run.getId());
        targetVerifiedAuthContext.put("deploymentId", deployment.getId());
        putIfPresent(targetVerifiedAuthContext, "customerId", deployment.getCustomerId());
        putIfPresent(targetVerifiedAuthContext, "tenantId", deployment.getTenantId());
        targetVerifiedAuthContext.put("issuer", "platform-vectorization-runner");
        targetVerifiedAuthContext.putArray("grantedScopes")
            .add("data-sync:upsert")
            .add("data-sync:delete")
            .add("vectorization:runner");
        ObjectNode targetDescriptor = jsonSupport.objectNode();
        targetDescriptor.put("targetType", "RUNTIME_DATA_SYNC");
        targetDescriptor.put("baseUrl", targetBaseUrl);
        targetDescriptor.put("batchPath", "/api/ai/data-sync/batch");
        targetDescriptor.put("vectorSpacesPath", "/api/ai/data-sync/vector-spaces");
        targetDescriptor.put("authHeader", RuntimePrivateAccessSupport.TRUSTED_BACKEND_API_KEY_HEADER);

        ObjectNode targetAuth = jsonSupport.objectNode();
        targetAuth.put("apiKey", runtimeTrustedBackendApiKey);

        return new VectorizationExecutionBundleSummary(
            run.getDeploymentId(),
            run.getId(),
            run.getReason(),
            revision.getId(),
            run.getRunnerMode(),
            jsonSupport.readTree(revision.getEntityScopeJson()),
            jsonSupport.readTree(revision.getMappingConfigJson()),
            effectiveExecutionConfig,
            connectionDescriptor,
            sourceAuth,
            localAliases,
            targetVerifiedAuthContext,
            targetDescriptor,
            targetAuth
        );
    }

    @Transactional
    public VectorizationRunnerHeartbeatSummary heartbeat(VectorizationRunnerHeartbeatRequest request) {
        VectorizationRunnerSessionEntity session = requireActiveSession(request.sessionToken());
        Instant now = Instant.now();
        Instant sessionExpiry = now.plus(properties.runnerSessionTtl());
        session.setLastHeartbeatAt(now);
        session.setExpiresAt(sessionExpiry);
        session.setUpdatedAt(now);

        String requestedStatus = "RUNNING";
        Instant leaseExpiry = session.getLeaseExpiresAt();
        if (StringUtils.hasText(request.runId())) {
            VectorizationRunEntity run = requireOwnedRun(session, request.runId());
            leaseExpiry = now.plus(properties.runLeaseTtl());
            run.setLeaseExpiresAt(leaseExpiry);
            run.setUpdatedAt(now);
            runRepository.save(run);
            requestedStatus = run.getRequestedStatus();
            session.setRunId(run.getId());
            session.setLeaseExpiresAt(leaseExpiry);
        }

        sessionRepository.save(session);
        return new VectorizationRunnerHeartbeatSummary(session.getStatus(), requestedStatus, sessionExpiry, leaseExpiry);
    }

    @Transactional
    public VectorizationRunSummary reportCheckpoint(VectorizationRunnerCheckpointRequest request) {
        VectorizationRunnerSessionEntity session = requireActiveSession(request.sessionToken());
        VectorizationRunEntity run = requireOwnedRun(session, request.runId());
        Instant now = Instant.now();

        VectorizationCheckpointEntity checkpoint = new VectorizationCheckpointEntity();
        checkpoint.setId(generateId("vcp"));
        checkpoint.setRunId(run.getId());
        checkpoint.setEntityType(trimToNull(request.entityType()));
        checkpoint.setCheckpointType(request.checkpointType().trim().toUpperCase(Locale.ROOT));
        checkpoint.setCheckpointValue(trimToNull(request.checkpointValue()));
        checkpoint.setProgressJson(jsonSupport.write(defaultObject(request.progress())));
        checkpoint.setDetailsJson(jsonSupport.write(defaultObject(request.details())));
        checkpoint.setCreatedAt(now);
        checkpoint.setUpdatedAt(now);
        checkpointRepository.save(checkpoint);

        ObjectNode checkpointSummary = jsonSupport.objectNode();
        checkpointSummary.put("checkpointType", checkpoint.getCheckpointType());
        if (checkpoint.getCheckpointValue() != null) {
            checkpointSummary.put("checkpointValue", checkpoint.getCheckpointValue());
        }
        if (checkpoint.getEntityType() != null) {
            checkpointSummary.put("entityType", checkpoint.getEntityType());
        }
        run.setCheckpointSummaryJson(jsonSupport.write(checkpointSummary));
        run.setProgressSummaryJson(jsonSupport.write(defaultObject(request.progress())));
        run.setLeaseExpiresAt(now.plus(properties.runLeaseTtl()));
        run.setUpdatedAt(now);
        runRepository.save(run);

        session.setLeaseExpiresAt(run.getLeaseExpiresAt());
        session.setLastHeartbeatAt(now);
        session.setExpiresAt(now.plus(properties.runnerSessionTtl()));
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        return vectorizationService.summarizeRun(run);
    }

    @Transactional
    public VectorizationSourceConnectionEntity reportDiscovery(VectorizationRunnerDiscoveryReportRequest request) {
        VectorizationRunnerSessionEntity session = requireActiveSession(request.sessionToken());
        VectorizationSourceConnectionEntity connection = connectionRepository.findById(request.connectionId())
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vectorization source connection not found: " + request.connectionId()));
        if (!session.getDeploymentId().equals(connection.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "Vectorization source connection not found: " + request.connectionId());
        }
        connection.setDiscoverySummaryJson(jsonSupport.write(defaultObject(request.discoverySummary())));
        connection.setUpdatedAt(Instant.now());
        return connectionRepository.save(connection);
    }

    @Transactional
    public VectorizationRunSummary completeRun(VectorizationRunnerCompletionRequest request) {
        VectorizationRunnerSessionEntity session = requireActiveSession(request.sessionToken());
        VectorizationRunEntity run = requireOwnedRun(session, request.runId());
        String finalStatus = request.finalStatus().trim().toUpperCase(Locale.ROOT);
        if (!COMPLETION_STATUSES.contains(finalStatus)) {
            throw new ResponseStatusException(BAD_REQUEST, "Unsupported vectorization completion status: " + request.finalStatus());
        }

        Instant now = Instant.now();
        run.setStatus(finalStatus);
        run.setRequestedStatus(finalStatus);
        run.setProgressSummaryJson(jsonSupport.write(defaultObject(request.progressSummary())));
        run.setErrorSummaryJson(jsonSupport.write(defaultObject(request.errorSummary())));
        run.setCompletedAt(now);
        run.setLeaseExpiresAt(null);
        run.setUpdatedAt(now);
        runRepository.save(run);

        failureBucketRepository.deleteByRunId(run.getId());
        JsonNode failureBuckets = defaultObject(request.failureBuckets());
        if (failureBuckets.isArray()) {
            failureBuckets.forEach(bucket -> recordFailureBucket(
                run.getId(),
                trimToNull(bucket.path("entityType").asText(null)),
                bucket.path("errorCode").asText("UNKNOWN"),
                bucket.path("summary").asText("Unknown failure"),
                bucket
            ));
        }

        session.setRunId(null);
        session.setLeaseExpiresAt(null);
        session.setLastHeartbeatAt(now);
        session.setExpiresAt(now.plus(properties.runnerSessionTtl()));
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        VectorizationPlanEntity plan = planRepository.findById(run.getPlanId()).orElse(null);
        if (plan != null) {
            plan.setLastRunId(run.getId());
            if ("COMPLETED".equals(finalStatus)) {
                VectorizationPlanRevisionEntity revision = revisionRepository.findById(run.getPlanRevisionId()).orElse(null);
                plan.setLastSuccessfulRunId(run.getId());
                String successfulIndexedOutputHash = trimToNull(plan.getActiveIndexedOutputHash());
                if (!StringUtils.hasText(successfulIndexedOutputHash) && revision != null) {
                    successfulIndexedOutputHash = trimToNull(revision.getIndexedOutputHash());
                }
                if (revision != null
                    && StringUtils.hasText(successfulIndexedOutputHash)
                    && !successfulIndexedOutputHash.equals(trimToNull(revision.getIndexedOutputHash()))) {
                    revision.setIndexedOutputHash(successfulIndexedOutputHash);
                    revision.setUpdatedAt(now);
                    revisionRepository.save(revision);
                }
                plan.setLastSuccessfulIndexedOutputHash(successfulIndexedOutputHash);
                cancelSupersededRuns(run, now);
            }
            plan.setUpdatedAt(now);
            planRepository.save(plan);
        }

        if ("COMPLETED".equals(finalStatus)) {
            try {
                shopifyStoreVectorizationLedgerService.refreshForCompletedRun(run, jsonSupport.readStringList(run.getEntityScopeJson()));
                shopifyStoreVectorizationEventService.markRunCompletion(
                    run.getId(),
                    true,
                    "Shopify vectorization run completed successfully.",
                    null
                );
            } catch (RuntimeException ex) {
                shopifyStoreVectorizationEventService.markRunCompletion(
                    run.getId(),
                    false,
                    ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "Shopify sparse ledger refresh failed after run completion."
                        : ex.getMessage(),
                    "LEDGER_REFRESH_FAILED"
                );
                platformAuditService.record(
                    "SHOPIFY_STORE_VECTORIZATION_LEDGER_REFRESH_FAILED",
                    "DEPLOYMENT",
                    run.getDeploymentId(),
                    Map.of(
                        "deploymentId", run.getDeploymentId(),
                        "runId", run.getId()
                    )
                );
            }
        } else {
            shopifyStoreVectorizationEventService.markRunCompletion(
                run.getId(),
                false,
                buildShopifyFailureSummary(request),
                "FAILED".equals(finalStatus)
                    ? "SHOPIFY_VECTOR_RUN_FAILED"
                    : "SHOPIFY_VECTOR_RUN_INTERRUPTED"
            );
        }

        platformAuditService.record(
            "VECTORIZATION_RUN_COMPLETED",
            "DEPLOYMENT",
            run.getDeploymentId(),
            Map.of(
                "deploymentId", run.getDeploymentId(),
                "runId", run.getId(),
                "status", finalStatus
            )
        );

        return vectorizationService.summarizeRun(run);
    }

    private String buildShopifyFailureSummary(VectorizationRunnerCompletionRequest request) {
        String message = trimToNull(defaultObject(request.errorSummary()).path("exception").asText(null));
        if (!StringUtils.hasText(message)) {
            message = trimToNull(defaultObject(request.errorSummary()).path("summary").asText(null));
        }
        return StringUtils.hasText(message) ? message : "Shopify vectorization run did not complete successfully.";
    }

    @Transactional
    public void recordFailureBucket(String runId, String entityType, String errorCode, String summary, JsonNode sample) {
        VectorizationFailureBucketEntity bucket = failureBucketRepository
            .findByRunIdAndEntityTypeAndErrorCodeAndSummary(runId, entityType, errorCode, summary)
            .orElseGet(() -> {
                VectorizationFailureBucketEntity created = new VectorizationFailureBucketEntity();
                created.setId(generateId("vfb"));
                created.setRunId(runId);
                created.setEntityType(entityType);
                created.setErrorCode(errorCode);
                created.setSummary(summary);
                created.setOccurrences(0);
                created.setCreatedAt(Instant.now());
                return created;
            });
        bucket.setSampleJson(jsonSupport.write(defaultObject(sample)));
        bucket.setOccurrences(bucket.getOccurrences() + 1);
        bucket.setUpdatedAt(Instant.now());
        failureBucketRepository.save(bucket);
    }

    private VectorizationRunnerSessionEntity requireActiveSession(String sessionToken) {
        VectorizationRunnerSessionEntity session = sessionRepository.findBySessionTokenHash(tokenService.hashToken(sessionToken))
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Runner session is invalid."));
        if (!"ACTIVE".equalsIgnoreCase(session.getStatus())) {
            throw new ResponseStatusException(UNAUTHORIZED, "Runner session is not active.");
        }
        if (session.getExpiresAt().isBefore(Instant.now())) {
            session.setStatus("EXPIRED");
            session.setUpdatedAt(Instant.now());
            sessionRepository.save(session);
            throw new ResponseStatusException(UNAUTHORIZED, "Runner session has expired.");
        }
        return session;
    }

    private VectorizationRunEntity requireOwnedRun(VectorizationRunnerSessionEntity session, String runId) {
        VectorizationRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Vectorization run not found: " + runId));
        if (!session.getDeploymentId().equals(run.getDeploymentId())) {
            throw new ResponseStatusException(NOT_FOUND, "Vectorization run not found: " + runId);
        }
        if (StringUtils.hasText(run.getClaimedBySessionId()) && !session.getId().equals(run.getClaimedBySessionId()) && !leaseExpired(run.getLeaseExpiresAt())) {
            throw new ResponseStatusException(CONFLICT, "Vectorization run is currently leased to another runner session.");
        }
        return run;
    }

    private boolean leaseExpired(Instant leaseExpiresAt) {
        return leaseExpiresAt == null || leaseExpiresAt.isBefore(Instant.now());
    }

    private void cancelSupersededRuns(VectorizationRunEntity completedRun, Instant now) {
        runRepository.findByDeploymentIdOrderByCreatedAtDesc(completedRun.getDeploymentId()).stream()
            .filter(candidate -> candidate != null && !completedRun.getId().equals(candidate.getId()))
            .filter(this::isInFlight)
            .filter(candidate -> supersededBy(candidate, completedRun))
            .forEach(candidate -> {
                ObjectNode errorSummary = jsonSupport.readObject(candidate.getErrorSummaryJson());
                errorSummary.put("summary", "Superseded by a newer successful vectorization run.");
                errorSummary.put("supersededByRunId", completedRun.getId());
                errorSummary.put("supersededByPlanRevisionId", completedRun.getPlanRevisionId());
                candidate.setStatus("CANCELLED");
                candidate.setRequestedStatus("CANCELLED");
                candidate.setErrorSummaryJson(jsonSupport.write(errorSummary));
                candidate.setLeaseExpiresAt(null);
                candidate.setCompletedAt(now);
                candidate.setUpdatedAt(now);
                runRepository.save(candidate);
            });
    }

    private boolean isInFlight(VectorizationRunEntity run) {
        if (run == null) {
            return false;
        }
        return IN_FLIGHT_STATUSES.contains(run.getStatus())
            || IN_FLIGHT_REQUESTED_STATUSES.contains(run.getRequestedStatus());
    }

    private boolean supersededBy(VectorizationRunEntity candidate, VectorizationRunEntity completedRun) {
        Instant candidateCreatedAt = candidate.getCreatedAt();
        Instant completedCreatedAt = completedRun.getCreatedAt();
        if (candidateCreatedAt != null && completedCreatedAt != null && !candidateCreatedAt.isBefore(completedCreatedAt)) {
            return false;
        }
        if (candidateCreatedAt == null && completedCreatedAt != null) {
            return false;
        }
        return true;
    }

    private VectorizationSourceConnectionEntity resolveConnection(String sourceConnectionId, String deploymentId) {
        if (StringUtils.hasText(sourceConnectionId)) {
            return connectionRepository.findById(sourceConnectionId).orElse(null);
        }
        return connectionRepository.findByDeploymentId(deploymentId).orElse(null);
    }

    private JsonNode resolvePlatformManagedSourceAuth(JsonNode secretRefs) {
        JsonNode platformSecretRefs = secretRefs.path("platformSecretRefs");
        if (!platformSecretRefs.isObject()) {
            return jsonSupport.objectNode();
        }
        ObjectNode resolved = jsonSupport.objectNode();
        platformSecretRefs.fields().forEachRemaining(entry -> {
            String secretName = entry.getValue().asText("").trim();
            if (secretName.isBlank()) {
                return;
            }
            String resolvedValue = platformSecretService.resolveSecret(secretName);
            if (StringUtils.hasText(resolvedValue)) {
                resolved.put(entry.getKey(), resolvedValue);
            }
        });
        return resolved;
    }

    private JsonNode defaultObject(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode() ? jsonSupport.objectNode() : node;
    }

    private void putIfPresent(ObjectNode target, String key, String value) {
        if (target != null && StringUtils.hasText(key) && StringUtils.hasText(value)) {
            target.put(key.trim(), value.trim());
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
