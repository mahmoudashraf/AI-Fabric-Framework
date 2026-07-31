package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformVectorizationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPreviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationCheckpointRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationFailureBucketRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorizationServiceTest {

    private final DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
    private final DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
    private final PlatformAuditService platformAuditService = mock(PlatformAuditService.class);
    private final VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
    private final VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
    private final VectorizationSourceConnectionRepository connectionRepository = mock(VectorizationSourceConnectionRepository.class);
    private final VectorizationRunRepository runRepository = mock(VectorizationRunRepository.class);
    private final VectorizationCheckpointRepository checkpointRepository = mock(VectorizationCheckpointRepository.class);
    private final VectorizationFailureBucketRepository failureBucketRepository = mock(VectorizationFailureBucketRepository.class);
    private final VectorizationRunnerRegistrationRepository registrationRepository = mock(VectorizationRunnerRegistrationRepository.class);
    private final VectorizationRunnerSessionRepository sessionRepository = mock(VectorizationRunnerSessionRepository.class);
    private final VectorizationIndexedOutputHashService hashService = mock(VectorizationIndexedOutputHashService.class);
    private final VectorizationRuntimeCoverageClient runtimeCoverageClient = mock(VectorizationRuntimeCoverageClient.class);
    private final VectorizationTokenService tokenService = mock(VectorizationTokenService.class);
    private final VectorizationRunnerProvisioningService runnerProvisioningService = mock(VectorizationRunnerProvisioningService.class);
    private final PlatformSecretService platformSecretService = mock(PlatformSecretService.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final VectorizationJsonSupport jsonSupport = new VectorizationJsonSupport(objectMapper);
    private final PlatformVectorizationProperties properties = new PlatformVectorizationProperties(null, null, null, 0, null, null);

    @Test
    void previewReturnsEntityScopeAsArray() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("cust-1");
        deployment.setTenantId("ten-1");
        deployment.setActiveVersionId("ver-1");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setEntityConfigJson("""
            {
              "ai-entities": {
                "product": {},
                "policy": {},
                "review": {}
              }
            }
            """);

        VectorizationPlanEntity plan = new VectorizationPlanEntity();
        plan.setId("vpl-1");
        plan.setDeploymentId("dep-1");
        plan.setActiveRevisionId("vpr-1");
        plan.setSyncState("IN_SYNC");
        plan.setLastSuccessfulIndexedOutputHash("hash-1");

        VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
        revision.setId("vpr-1");
        revision.setPlanId("vpl-1");
        revision.setEntityScopeJson("""
            ["policy","product","review"]
            """);
        revision.setMappingConfigJson("{\"entityMappings\":{}}");
        revision.setExecutionConfigJson("{\"batchSize\":25}");

        VectorizationSourceConnectionEntity connection = new VectorizationSourceConnectionEntity();
        connection.setId("vcn-1");
        connection.setDeploymentId("dep-1");
        connection.setDiscoverySummaryJson("""
            {"countsByEntityType":{"product":100,"policy":20,"review":200}}
            """);

        ObjectNode liveCounts = objectMapper.createObjectNode();
        liveCounts.put("product", 100);
        liveCounts.put("policy", 20);
        liveCounts.put("review", 200);

        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentEditorAccess(deployment)).thenReturn(deployment);
        when(deploymentVersionRepository.findById("ver-1")).thenReturn(Optional.of(version));
        when(planRepository.findByDeploymentId("dep-1")).thenReturn(Optional.of(plan));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision));
        when(connectionRepository.findByDeploymentId("dep-1")).thenReturn(Optional.of(connection));
        when(runRepository.findByDeploymentIdOrderByCreatedAtDesc("dep-1")).thenReturn(List.of());
        when(hashService.compute(version)).thenReturn("hash-1");
        when(runtimeCoverageClient.fetchCounts(deployment)).thenReturn(liveCounts);

        VectorizationPreviewSummary preview = service().preview("dep-1");

        assertThat(preview.entityScope().isArray()).isTrue();
        assertThat(jsonSupport.readStringList(jsonSupport.write(preview.entityScope())))
            .containsExactly("policy", "product", "review");
    }

    @Test
    void overviewReportsLegacyActiveVersionAsMigrationRequiredWithoutComputingV04Hash() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-legacy");
        deployment.setCustomerId("cust-1");
        deployment.setTenantId("ten-1");
        deployment.setActiveVersionId("ver-legacy");

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-legacy");
        version.setEntityConfigContractVersion("AI_ENTITY_CONFIG_V0_3");

        VectorizationPlanEntity plan = new VectorizationPlanEntity();
        plan.setId("vpl-legacy");
        plan.setDeploymentId("dep-legacy");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
        plan.setSyncState("IN_SYNC");
        plan.setSyncReasonCodesJson("[]");
        plan.setSyncReasonDetailsJson("{}");
        plan.setLastSuccessfulIndexedOutputHash("legacy-hash");

        when(deploymentRepository.findById("dep-legacy")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentEditorAccess(deployment)).thenReturn(deployment);
        when(deploymentVersionRepository.findById("ver-legacy")).thenReturn(Optional.of(version));
        when(planRepository.findByDeploymentId("dep-legacy")).thenReturn(Optional.of(plan));
        when(connectionRepository.findByDeploymentId("dep-legacy")).thenReturn(Optional.empty());
        when(runRepository.findByDeploymentIdOrderByCreatedAtDesc("dep-legacy")).thenReturn(List.of());
        when(runtimeCoverageClient.fetchCounts(deployment)).thenReturn(objectMapper.createObjectNode());

        var overview = service().getOverview("dep-legacy");

        assertThat(overview.plan().syncState()).isEqualTo("MIGRATION_REQUIRED");
        assertThat(overview.plan().syncReasonCodes())
            .containsExactly("ENTITY_CONFIG_CONTRACT_MIGRATION_REQUIRED");
        assertThat(overview.plan().syncReasonDetails().path("activeEntityConfigContractVersion").asText())
            .isEqualTo("AI_ENTITY_CONFIG_V0_3");
        assertThat(overview.plan().syncReasonDetails().path("requiredEntityConfigContractVersion").asText())
            .isEqualTo("AI_ENTITY_CONFIG_V0_4");
        assertThat(overview.plan().activeIndexedOutputHash()).isNull();
        verify(hashService, never()).compute(version);
    }

    @Test
    void createRunSnapshotsCurrentIndexedOutputHashForCompletion() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("cust-1");
        deployment.setTenantId("ten-1");
        deployment.setActiveVersionId("ver-2");

        DeploymentVersionEntity activeVersion = new DeploymentVersionEntity();
        activeVersion.setId("ver-2");

        VectorizationPlanEntity plan = new VectorizationPlanEntity();
        plan.setId("vpl-1");
        plan.setDeploymentId("dep-1");
        plan.setActiveRevisionId("vpr-1");
        plan.setActiveIndexedOutputHash("old-indexed-output-hash");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");

        VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
        revision.setId("vpr-1");
        revision.setPlanId("vpl-1");
        revision.setEntityScopeJson("[\"product\"]");

        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(deploymentVersionRepository.findById("ver-2")).thenReturn(Optional.of(activeVersion));
        when(planRepository.findByDeploymentId("dep-1")).thenReturn(Optional.of(plan));
        when(revisionRepository.findById("vpr-1")).thenReturn(Optional.of(revision));
        when(hashService.compute(activeVersion)).thenReturn("current-indexed-output-hash");

        service().createRun(
            "dep-1",
            new CreateVectorizationRunRequest("REINDEX", null, "Refresh indexed output", null)
        );

        assertThat(plan.getActiveIndexedOutputHash()).isEqualTo("current-indexed-output-hash");
        verify(planRepository).save(plan);
    }

    @Test
    void summarizeRunnerFallsBackToSessionCompatibilityWhenRegistrationFieldsAreBlank() {
        VectorizationRunnerRegistrationEntity registration = new VectorizationRunnerRegistrationEntity();
        registration.setId("vrr-1");
        registration.setRunnerMode("PLATFORM_MANAGED_AUTO");
        registration.setStatus("ACTIVE");
        registration.setTokenHint("hint");

        VectorizationRunnerSessionEntity session = new VectorizationRunnerSessionEntity();
        session.setRunnerInstanceId("runner-1");
        session.setProductVersion("2026.04.track-b");
        session.setCompatibilityVersion("1");

        VectorizationRunnerSummary summary = service().summarizeRunner(registration, session);

        assertThat(summary.compatibilityStatus()).isEqualTo("CURRENT");
        assertThat(summary.runnerInstanceId()).isEqualTo("runner-1");
        assertThat(summary.productVersion()).isEqualTo("2026.04.track-b");
        assertThat(summary.compatibilityVersion()).isEqualTo("1");
    }

    @Test
    void retryPreservesDurableDataSyncWorkForReconciliation() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        VectorizationRunEntity run = new VectorizationRunEntity();
        run.setId("run-1");
        run.setDeploymentId("dep-1");
        run.setReason("BOOTSTRAP");
        run.setRunnerMode("PLATFORM_MANAGED_AUTO");
        run.setStatus("FAILED");
        run.setRequestedStatus("FAILED");
        run.setEntityScopeJson("[\"product\"]");
        run.setProgressSummaryJson("{\"failedRecords\":1}");
        run.setCheckpointSummaryJson("{\"checkpointType\":\"PAGE\"}");
        run.setExecutionOverridesJson("{\"batchSize\":25}");
        run.setErrorSummaryJson("""
            {
              "dataSync": {
                "failures": [
                  {
                    "indexingWorkId": "71",
                    "indexingStatus": "FAILED_RETRYABLE",
                    "vectorSpace": "product",
                    "entityId": "product-1",
                    "providerRequestId": "sync-1",
                    "durableHandoffAccepted": true,
                    "retryDisposition": "RECONCILE_DURABLE_WORK"
                  },
                  {
                    "indexingWorkId": "72",
                    "durableHandoffAccepted": false,
                    "retryDisposition": "SAFE_RESUBMIT"
                  }
                ]
              }
            }
            """);

        when(deploymentRepository.findById("dep-1"))
            .thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment))
            .thenReturn(deployment);
        when(runRepository.findById("run-1")).thenReturn(Optional.of(run));

        service().updateRunCommand("dep-1", "run-1", "RETRY");

        ObjectNode overrides = jsonSupport.readObject(
            run.getExecutionOverridesJson()
        );
        assertThat(overrides.path("batchSize").asInt()).isEqualTo(25);
        assertThat(overrides.path("pendingDataSyncWork")).hasSize(1);
        assertThat(
            overrides.path("pendingDataSyncWork").get(0).path("workId").asText()
        ).isEqualTo("71");
        assertThat(
            overrides.path("pendingDataSyncWork").get(0)
                .path("vectorSpace").asText()
        ).isEqualTo("product");
        assertThat(jsonSupport.readObject(run.getErrorSummaryJson())).isEmpty();
        assertThat(run.getStatus()).isEqualTo("QUEUED");
        assertThat(run.getRequestedStatus()).isEqualTo("RETRY_REQUESTED");
        verify(checkpointRepository).deleteByRunId("run-1");
        verify(failureBucketRepository).deleteByRunId("run-1");
    }

    private VectorizationService service() {
        return new VectorizationService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentAccessService,
            platformAuditService,
            properties,
            planRepository,
            revisionRepository,
            connectionRepository,
            runRepository,
            checkpointRepository,
            failureBucketRepository,
            registrationRepository,
            sessionRepository,
            jsonSupport,
            hashService,
            runtimeCoverageClient,
            tokenService,
            runnerProvisioningService,
            platformSecretService
        );
    }
}
