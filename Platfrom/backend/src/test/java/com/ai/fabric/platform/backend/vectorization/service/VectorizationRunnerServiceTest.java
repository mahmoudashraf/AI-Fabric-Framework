package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreVectorizationEventService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreVectorizationLedgerService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationExecutionBundleSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerCompletionRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSessionRequest;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationCheckpointRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationFailureBucketRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorizationRunnerServiceTest {

    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
    private final VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
    private final VectorizationSourceConnectionRepository connectionRepository = mock(VectorizationSourceConnectionRepository.class);
    private final VectorizationRunRepository runRepository = mock(VectorizationRunRepository.class);
    private final VectorizationRunnerRegistrationRepository registrationRepository = mock(VectorizationRunnerRegistrationRepository.class);
    private final VectorizationRunnerSessionRepository sessionRepository = mock(VectorizationRunnerSessionRepository.class);
    private final VectorizationCheckpointRepository checkpointRepository = mock(VectorizationCheckpointRepository.class);
    private final VectorizationFailureBucketRepository failureBucketRepository = mock(VectorizationFailureBucketRepository.class);
    private final PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
    private final VectorizationService vectorizationService = mock(VectorizationService.class);
    private final ShopifyStoreVectorizationLedgerService shopifyStoreVectorizationLedgerService = mock(ShopifyStoreVectorizationLedgerService.class);
    private final ShopifyStoreVectorizationEventService shopifyStoreVectorizationEventService = mock(ShopifyStoreVectorizationEventService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VectorizationJsonSupport jsonSupport = new VectorizationJsonSupport(objectMapper);
    private final VectorizationTokenService tokenService = new VectorizationTokenService();
    private final PlatformVectorizationProperties properties = new PlatformVectorizationProperties(null, null, null, 0, null, null);

    @Test
    void createSessionRejectsExpiredRegistrationToken() {
        String registrationToken = "registration-token";
        VectorizationRunnerRegistrationEntity registration = registration(
            tokenService.hashToken(registrationToken),
            Instant.now().minusSeconds(30)
        );
        when(registrationRepository.findByTokenHash(tokenService.hashToken(registrationToken))).thenReturn(Optional.of(registration));

        VectorizationRunnerService service = service();

        assertThatThrownBy(() -> service.createSession(new VectorizationRunnerSessionRequest(
            registrationToken,
            "runner-a",
            properties.requiredProductVersion(),
            properties.requiredCompatibilityVersion()
        )))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("expired");

        verify(sessionRepository, never()).save(any());
    }

    @Test
    void fetchExecutionBundleResolvesPlatformManagedSourceAuthAndRuntimeTarget() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(run("PLATFORM_MANAGED_AUTO")));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision("vcn-1")));
        when(connectionRepository.findById("vcn-1")).thenReturn(Optional.of(connection("""
            {
              "platformSecretRefs": {
                "apiKey": "SOURCE_API_KEY"
              }
            }
            """)));
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment()));
        when(platformSecretService.resolveSecret("SOURCE_API_KEY")).thenReturn("source-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME))
            .thenReturn("runtime-signing-secret");

        VectorizationExecutionBundleSummary summary = service().fetchExecutionBundle("session-token", "vrn-1");

        assertThat(summary.deploymentId()).isEqualTo("dep-1");
        assertThat(summary.sourceAuth().path("apiKey").asText()).isEqualTo("source-secret");
        assertThat(summary.localSecretAliases().size()).isZero();
        assertThat(summary.targetVerifiedAuthContext().path("subjectId").asText()).isEqualTo("system:platform-vectorization-runner");
        assertThat(summary.targetDescriptor().path("baseUrl").asText()).isEqualTo("https://runtime.dep-1.example");
        assertThat(summary.targetDescriptor().path("batchPath").asText()).isEqualTo("/api/ai/data-sync/batch");
        assertThat(summary.targetDescriptor().path("authHeader").asText()).isEqualTo("X-AIFABRIC-RUNTIME-API-KEY");
        assertThat(summary.targetDescriptor().path("workStatusPath").asText())
            .isEqualTo("/api/admin/indexing/work/{workId}");
        assertThat(summary.targetDescriptor().path("privateAuthorizationHeader").asText())
            .isEqualTo("X-AIFABRIC-RUNTIME-AUTHORIZATION");
        assertThat(summary.targetAuth().path("apiKey").asText()).isEqualTo("runtime-secret");
        assertThat(summary.targetAuth().path("privateAuthorization").asText())
            .startsWith("Bearer rpa1.");
    }

    @Test
    void fetchExecutionBundleUsesLocalAliasesForCustomerManagedRemoteRunner() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("CUSTOMER_MANAGED_REMOTE")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(run("CUSTOMER_MANAGED_REMOTE")));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision("vcn-1")));
        when(connectionRepository.findById("vcn-1")).thenReturn(Optional.of(connection("""
            {
              "platformSecretRefs": {
                "apiKey": "SOURCE_API_KEY"
              },
              "localSecretAliases": {
                "apiKey": "SOURCE_API_KEY"
              }
            }
            """)));
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment()));
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME))
            .thenReturn("runtime-signing-secret");

        VectorizationExecutionBundleSummary summary = service().fetchExecutionBundle("session-token", "vrn-1");

        assertThat(summary.sourceAuth().size()).isZero();
        assertThat(summary.localSecretAliases().path("apiKey").asText()).isEqualTo("SOURCE_API_KEY");
        verify(platformSecretService, never()).resolveSecret("SOURCE_API_KEY");
    }

    @Test
    void fetchExecutionBundleMergesRunExecutionOverrides() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(runWithOverrides("PLATFORM_MANAGED_AUTO", """
            {"batchSize":1,"maxPagesPerEntity":1}
            """)));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision("vcn-1")));
        when(connectionRepository.findById("vcn-1")).thenReturn(Optional.of(connection("""
            {
              "platformSecretRefs": {
                "apiKey": "SOURCE_API_KEY"
              }
            }
            """)));
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment()));
        when(platformSecretService.resolveSecret("SOURCE_API_KEY")).thenReturn("source-secret");
        when(platformSecretService.resolveSecret("AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY")).thenReturn("runtime-secret");
        when(platformSecretService.resolveSecret(RuntimePrivateAssertionSigningService.SECRET_NAME))
            .thenReturn("runtime-signing-secret");

        VectorizationExecutionBundleSummary summary = service().fetchExecutionBundle("session-token", "vrn-1");

        assertThat(summary.executionConfig().path("batchSize").asInt()).isEqualTo(1);
        assertThat(summary.executionConfig().path("maxPagesPerEntity").asInt()).isEqualTo(1);
    }

    @Test
    void completeRunPersistsFailureBuckets() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(run("PLATFORM_MANAGED_AUTO")));
        when(planRepository.findById("vpl-1")).thenReturn(Optional.empty());

        ObjectNode progress = objectMapper.createObjectNode();
        progress.put("processedRecords", 10);
        ObjectNode errorSummary = objectMapper.createObjectNode();
        errorSummary.put("exception", "mapping failed");
        ArrayNode buckets = objectMapper.createArrayNode();
        buckets.addObject()
            .put("entityType", "product")
            .put("errorCode", "MAPPING_ERROR")
            .put("summary", "Missing id")
            .put("occurrences", 2);

        service().completeRun(new VectorizationRunnerCompletionRequest(
            "session-token",
            "vrn-1",
            "FAILED",
            progress,
            errorSummary,
            buckets
        ));

        verify(failureBucketRepository).deleteByRunId("vrn-1");
        verify(failureBucketRepository).save(any());
    }

    @Test
    void completeRunBackfillsMissingRevisionIndexedOutputHash() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(run("PLATFORM_MANAGED_AUTO")));
        when(planRepository.findById("vpl-1")).thenReturn(Optional.of(plan("active-hash")));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision("vcn-1")));

        service().completeRun(new VectorizationRunnerCompletionRequest(
            "session-token",
            "vrn-1",
            "COMPLETED",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createArrayNode()
        ));

        verify(revisionRepository).save(any());
        verify(planRepository).save(any());
    }

    @Test
    void completeRunPrefersActiveIndexedOutputHashWhenRevisionHashIsStale() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(run("PLATFORM_MANAGED_AUTO")));
        when(planRepository.findById("vpl-1")).thenReturn(Optional.of(plan("active-hash")));
        VectorizationPlanRevisionEntity revision = revision("vcn-1");
        revision.setIndexedOutputHash("stale-hash");
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision));

        service().completeRun(new VectorizationRunnerCompletionRequest(
            "session-token",
            "vrn-1",
            "COMPLETED",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createArrayNode()
        ));

        verify(revisionRepository).save(argThat(saved ->
            "active-hash".equals(saved.getIndexedOutputHash())
                && saved.getUpdatedAt() != null
        ));
        verify(planRepository).save(argThat(saved ->
            "vrn-1".equals(saved.getLastSuccessfulRunId())
                && "active-hash".equals(saved.getLastSuccessfulIndexedOutputHash())
        ));
        verify(shopifyStoreVectorizationLedgerService).refreshForCompletedRun(argThat(run -> "vrn-1".equals(run.getId())), argThat(scope -> scope.equals(List.of("product"))));
        verify(shopifyStoreVectorizationEventService).markRunCompletion("vrn-1", true, "Shopify vectorization run completed successfully.", null);
    }

    @Test
    void completeRunMarksShopifyEventFailureWhenLedgerRefreshFails() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(run("PLATFORM_MANAGED_AUTO")));
        when(planRepository.findById("vpl-1")).thenReturn(Optional.of(plan("active-hash")));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision("vcn-1")));
        org.mockito.Mockito.doThrow(new IllegalStateException("ledger refresh exploded"))
            .when(shopifyStoreVectorizationLedgerService)
            .refreshForCompletedRun(any(), any());

        service().completeRun(new VectorizationRunnerCompletionRequest(
            "session-token",
            "vrn-1",
            "COMPLETED",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createArrayNode()
        ));

        verify(shopifyStoreVectorizationEventService).markRunCompletion(
            "vrn-1",
            false,
            "ledger refresh exploded",
            "LEDGER_REFRESH_FAILED"
        );
    }

    @Test
    void completeRunCancelsOlderSupersededInFlightRuns() {
        when(sessionRepository.findBySessionTokenHash(tokenService.hashToken("session-token")))
            .thenReturn(Optional.of(activeSession("PLATFORM_MANAGED_AUTO")));
        VectorizationRunEntity completedRun = run("PLATFORM_MANAGED_AUTO");
        completedRun.setCreatedAt(Instant.parse("2026-04-19T18:41:56Z"));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(completedRun));
        when(planRepository.findById("vpl-1")).thenReturn(Optional.of(plan("active-hash")));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision("vcn-1")));

        VectorizationRunEntity staleRun = run("PLATFORM_MANAGED_AUTO");
        staleRun.setId("vrn-old");
        staleRun.setPlanRevisionId("vpr-old");
        staleRun.setStatus("RUNNING");
        staleRun.setRequestedStatus("CANCEL_REQUESTED");
        staleRun.setCreatedAt(Instant.parse("2026-04-19T17:13:46Z"));
        when(runRepository.findByDeploymentIdOrderByCreatedAtDesc("dep-1")).thenReturn(List.of(completedRun, staleRun));

        service().completeRun(new VectorizationRunnerCompletionRequest(
            "session-token",
            "vrn-1",
            "COMPLETED",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createArrayNode()
        ));

        verify(runRepository, atLeastOnce()).save(argThat(run ->
            "vrn-old".equals(run.getId())
                && "CANCELLED".equals(run.getStatus())
                && "CANCELLED".equals(run.getRequestedStatus())
                && run.getCompletedAt() != null
                && jsonSupport.readObject(run.getErrorSummaryJson()).path("supersededByRunId").asText().equals("vrn-1")
        ));
    }

    private VectorizationRunnerService service() {
        return new VectorizationRunnerService(
            properties,
            auditService,
            deploymentRepository,
            planRepository,
            revisionRepository,
            connectionRepository,
            runRepository,
            registrationRepository,
            sessionRepository,
            checkpointRepository,
            failureBucketRepository,
            objectMapper,
            jsonSupport,
            tokenService,
            vectorizationService,
            platformSecretService,
            shopifyStoreVectorizationLedgerService,
            shopifyStoreVectorizationEventService
        );
    }

    private VectorizationRunnerRegistrationEntity registration(String tokenHash, Instant expiresAt) {
        VectorizationRunnerRegistrationEntity entity = new VectorizationRunnerRegistrationEntity();
        entity.setId("vrr-1");
        entity.setDeploymentId("dep-1");
        entity.setCustomerId("cus-1");
        entity.setTenantId("ten-1");
        entity.setRunnerMode("PLATFORM_MANAGED_AUTO");
        entity.setStatus("ACTIVE");
        entity.setTokenHash(tokenHash);
        entity.setTokenExpiresAt(expiresAt);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private VectorizationRunnerSessionEntity activeSession(String runnerMode) {
        VectorizationRunnerSessionEntity entity = new VectorizationRunnerSessionEntity();
        entity.setId("vrs-1");
        entity.setRegistrationId("vrr-1");
        entity.setDeploymentId("dep-1");
        entity.setStatus("ACTIVE");
        entity.setSessionTokenHash(tokenService.hashToken("session-token"));
        entity.setRunnerInstanceId("runner-a");
        entity.setProductVersion(properties.requiredProductVersion());
        entity.setCompatibilityVersion(properties.requiredCompatibilityVersion());
        entity.setExpiresAt(Instant.now().plusSeconds(300));
        entity.setLastHeartbeatAt(Instant.now());
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        if ("CUSTOMER_MANAGED_REMOTE".equals(runnerMode)) {
            entity.setRunId("vrn-1");
        }
        return entity;
    }

    private VectorizationRunEntity run(String runnerMode) {
        return runWithOverrides(runnerMode, "{}");
    }

    private VectorizationRunEntity runWithOverrides(String runnerMode, String executionOverridesJson) {
        VectorizationRunEntity entity = new VectorizationRunEntity();
        entity.setId("vrn-1");
        entity.setPlanId("vpl-1");
        entity.setPlanRevisionId("vpr-1");
        entity.setDeploymentId("dep-1");
        entity.setCustomerId("cus-1");
        entity.setTenantId("ten-1");
        entity.setReason("BOOTSTRAP");
        entity.setRequestedStatus("RUNNING");
        entity.setStatus("RUNNING");
        entity.setRunnerMode(runnerMode);
        entity.setEntityScopeJson("""
            ["product"]
            """);
        entity.setProgressSummaryJson("{}");
        entity.setCheckpointSummaryJson("{}");
        entity.setErrorSummaryJson("{}");
        entity.setExecutionOverridesJson(executionOverridesJson);
        entity.setClaimedBySessionId("vrs-1");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private VectorizationPlanRevisionEntity revision(String sourceConnectionId) {
        VectorizationPlanRevisionEntity entity = new VectorizationPlanRevisionEntity();
        entity.setId("vpr-1");
        entity.setPlanId("vpl-1");
        entity.setDeploymentId("dep-1");
        entity.setRevisionNumber(1);
        entity.setStatus("ACTIVE");
        entity.setSourceConnectionId(sourceConnectionId);
        entity.setEntityScopeJson("""
            ["product"]
            """);
        entity.setMappingConfigJson("""
            {"entityMappings":{"product":{"recordIdField":"id"}}}
            """);
        entity.setExecutionConfigJson("""
            {"batchSize":100}
            """);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private VectorizationPlanEntity plan(String activeIndexedOutputHash) {
        VectorizationPlanEntity entity = new VectorizationPlanEntity();
        entity.setId("vpl-1");
        entity.setDeploymentId("dep-1");
        entity.setActiveIndexedOutputHash(activeIndexedOutputHash);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private VectorizationSourceConnectionEntity connection(String secretReferencesJson) {
        VectorizationSourceConnectionEntity entity = new VectorizationSourceConnectionEntity();
        entity.setId("vcn-1");
        entity.setDeploymentId("dep-1");
        entity.setCustomerId("cus-1");
        entity.setTenantId("ten-1");
        entity.setName("Primary source");
        entity.setAdapterType("REST_API");
        entity.setAuthMode("API_KEY");
        entity.setStatus("READY");
        entity.setConnectionConfigJson("""
            {"baseUrl":"https://source.example","path":"/records"}
            """);
        entity.setSecretReferencesJson(secretReferencesJson);
        entity.setDiscoverySummaryJson("""
            {"countsByEntityType":{"product":12},"countMethodByEntityType":{"product":"EXACT"}}
            """);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }

    private DeploymentEntity deployment() {
        DeploymentEntity entity = new DeploymentEntity();
        entity.setId("dep-1");
        entity.setName("Vectorization test");
        entity.setEnvironmentName("dev");
        entity.setTemplateId("dev-openai-pinecone");
        entity.setStatus("ACTIVE");
        entity.setCustomerId("cus-1");
        entity.setTenantId("ten-1");
        entity.setRuntimeBaseUrl("https://runtime.dep-1.example");
        entity.setConnectorBaseUrl("https://connector.dep-1.example");
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        return entity;
    }
}
