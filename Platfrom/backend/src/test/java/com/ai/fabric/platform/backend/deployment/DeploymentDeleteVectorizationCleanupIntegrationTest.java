package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentManagedVectorResourceEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDeletionOperationRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentManagedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationCheckpointEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationFailureBucketEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerRegistrationEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunnerSessionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationStepEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationCheckpointRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationFailureBucketRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerRegistrationRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunnerSessionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationStepRepository;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationManagedSecretNames;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.auth.bootstrap-admin-enabled=false",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentDeleteVectorizationCleanupIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private DeploymentDeletionOperationRepository deletionOperationRepository;

    @Autowired
    private DeploymentManagedVectorResourceRepository managedVectorResourceRepository;

    @Autowired
    private PlatformSecretService platformSecretService;

    @Autowired
    private VectorizationSourceConnectionRepository sourceConnectionRepository;

    @Autowired
    private VectorizationPlanRepository planRepository;

    @Autowired
    private VectorizationPlanRevisionRepository planRevisionRepository;

    @Autowired
    private VectorizationRunRepository runRepository;

    @Autowired
    private VectorizationCheckpointRepository checkpointRepository;

    @Autowired
    private VectorizationFailureBucketRepository failureBucketRepository;

    @Autowired
    private VectorizationRunnerRegistrationRepository registrationRepository;

    @Autowired
    private VectorizationRunnerSessionRepository sessionRepository;

    @Autowired
    private VectorizationVerificationRunRepository verificationRunRepository;

    @Autowired
    private VectorizationVerificationStepRepository verificationStepRepository;

    @Test
    void deleteDeploymentRemovesVectorizationStateAndManagedRunnerSecret() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Delete Vectorization State", "dev", "dev-openai-lucene")
        ));
        DeploymentEntity entity = deploymentRepository.findById(deployment.id()).orElseThrow();
        seedVectorizationState(entity);
        runAsAdmin(() -> {
            deploymentService.archiveDeployment(deployment.id());
            return null;
        });

        String managedSecretName = VectorizationManagedSecretNames.registrationTokenSecretName(deployment.id());
        assertThat(platformSecretService.isSecretPresent(managedSecretName)).isTrue();

        mockMvc.perform(delete("/api/deployments/{deploymentId}", deployment.id())
                .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"));

        waitForDeletion(deployment.id());

        assertThat(deploymentRepository.findById(deployment.id())).isEmpty();
        assertThat(sourceConnectionRepository.findByDeploymentId(deployment.id())).isEmpty();
        assertThat(planRepository.findByDeploymentId(deployment.id())).isEmpty();
        assertThat(planRevisionRepository.findById("vpr-delete-01")).isEmpty();
        assertThat(runRepository.findById("vrn-delete-01")).isEmpty();
        assertThat(checkpointRepository.findByRunIdOrderByUpdatedAtDesc("vrn-delete-01")).isEmpty();
        assertThat(failureBucketRepository.findByRunIdOrderByUpdatedAtDesc("vrn-delete-01")).isEmpty();
        assertThat(registrationRepository.findByDeploymentId(deployment.id())).isEmpty();
        assertThat(sessionRepository.findByRegistrationIdOrderByUpdatedAtDesc("vrr-delete-01")).isEmpty();
        assertThat(verificationRunRepository.findById("vvr-delete-01")).isEmpty();
        assertThat(verificationStepRepository.findByVerificationRunIdOrderByCreatedAtAsc("vvr-delete-01")).isEmpty();
        assertThat(platformSecretService.isSecretPresent(managedSecretName)).isFalse();
    }

    @Test
    void hardDeleteFailureIsRecordedAndDeploymentRemainsMarkedForFollowUp() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Delete Failure State", "dev", "dev-openai-pinecone")
        ));
        DeploymentEntity entity = deploymentRepository.findById(deployment.id()).orElseThrow();
        runAsAdmin(() -> {
            deploymentService.archiveDeployment(deployment.id());
            return null;
        });
        seedManagedVectorResource(entity);

        mockMvc.perform(
                delete("/api/deployments/{deploymentId}", deployment.id())
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {"hardDelete":true,"reason":"integration failure path"}
                        """)
            )
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.status").value("QUEUED"));

        waitForDeletionFailure(deployment.id());

        DeploymentEntity failedDeployment = deploymentRepository.findById(deployment.id()).orElseThrow();
        assertThat(failedDeployment.getDeletionStatus()).isEqualTo("FAILED");
        assertThat(failedDeployment.getDeletionFailureMessage()).contains("Qdrant collection cleanup requires both collection name and base URL.");

        List<DeploymentManagedVectorResourceEntity> resources = managedVectorResourceRepository.findByDeploymentIdOrderByUpdatedAtDesc(deployment.id());
        assertThat(resources).hasSize(1);
        assertThat(resources.get(0).getDeletionStatus()).isEqualTo("FAILED");

        var operation = deletionOperationRepository.findTop200ByOrderByCreatedAtDesc().stream()
            .filter(item -> deployment.id().equals(item.getDeploymentId()))
            .findFirst()
            .orElseThrow();
        assertThat(operation.getStatus()).isEqualTo("FAILED");
        assertThat(operation.getErrorMessage()).contains("Qdrant collection cleanup requires both collection name and base URL.");
        assertThat(operation.getResultDetailsJson()).contains("\"errorType\"");
        assertThat(operation.getResultDetailsJson()).contains("Qdrant collection cleanup requires both collection name and base URL.");
    }

    private void waitForDeletion(String deploymentId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            if (deploymentRepository.findById(deploymentId).isEmpty()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Deployment was not deleted within the expected time window: " + deploymentId);
    }

    private void waitForDeletionFailure(String deploymentId) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            DeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElse(null);
            if (deployment != null && "FAILED".equals(deployment.getDeletionStatus())) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Deployment deletion did not fail within the expected time window: " + deploymentId);
    }

    private void seedManagedVectorResource(DeploymentEntity deployment) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        DeploymentManagedVectorResourceEntity resource = new DeploymentManagedVectorResourceEntity();
        resource.setId("mvr-delete-fail-01");
        resource.setDeploymentId(deployment.getId());
        resource.setVendor("qdrant");
        resource.setVectorStrategy("qdrant");
        resource.setVectorProvisioningMode("PLATFORM_MANAGED");
        resource.setManagedMode("MANAGED_CLOUD_CLUSTER");
        resource.setResourceType("COLLECTION");
        resource.setResourceName("delete-failure-collection");
        resource.setResourceReference(null);
        resource.setEndpoint(null);
        resource.setResourceStatus("ACTIVE");
        resource.setProvisioningState("READY");
        resource.setSecretReferenceNamesJson("[]");
        resource.setDetailsJson("{}");
        resource.setCreatedAt(now);
        resource.setUpdatedAt(now);
        managedVectorResourceRepository.save(resource);
    }

    private void seedVectorizationState(DeploymentEntity deployment) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        VectorizationSourceConnectionEntity connection = new VectorizationSourceConnectionEntity();
        connection.setId("vcn-delete-01");
        connection.setDeploymentId(deployment.getId());
        connection.setCustomerId(deployment.getCustomerId());
        connection.setTenantId(deployment.getTenantId());
        connection.setName("Delete Source");
        connection.setAdapterType("REST_API");
        connection.setAuthMode("API_KEY");
        connection.setStatus("READY");
        connection.setConnectionConfigJson("{\"baseUrl\":\"https://example.test\"}");
        connection.setSecretReferencesJson("{\"apiKeySecretName\":\"CONNECTOR_API_KEY\"}");
        connection.setDiscoverySummaryJson("{\"entityCounts\":{\"product\":2}}");
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);
        sourceConnectionRepository.save(connection);

        VectorizationPlanEntity plan = new VectorizationPlanEntity();
        plan.setId("vpn-delete-01");
        plan.setDeploymentId(deployment.getId());
        plan.setCustomerId(deployment.getCustomerId());
        plan.setTenantId(deployment.getTenantId());
        plan.setName("Delete Plan");
        plan.setStatus("ACTIVE");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
        plan.setSyncState("IN_SYNC");
        plan.setSyncReasonCodesJson("[\"READY\"]");
        plan.setSyncReasonDetailsJson("{\"syncState\":\"IN_SYNC\"}");
        plan.setSourceConnectionId(connection.getId());
        plan.setActiveRevisionId("vpr-delete-01");
        plan.setActiveIndexedOutputHash("hash-delete-active");
        plan.setLastSuccessfulIndexedOutputHash("hash-delete-active");
        plan.setLastRunId("vrn-delete-01");
        plan.setLastSuccessfulRunId("vrn-delete-01");
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planRepository.save(plan);

        VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
        revision.setId("vpr-delete-01");
        revision.setPlanId(plan.getId());
        revision.setDeploymentId(deployment.getId());
        revision.setRevisionNumber(1);
        revision.setStatus("ACTIVE");
        revision.setSourceConnectionId(connection.getId());
        revision.setEntityScopeJson("[\"product\"]");
        revision.setMappingConfigJson("{\"product\":{\"sourcePath\":\"products\"}}");
        revision.setExecutionConfigJson("{\"batchSize\":25}");
        revision.setIndexedOutputHash("hash-delete-active");
        revision.setCreatedByActorId("system");
        revision.setCreatedAt(now);
        revision.setUpdatedAt(now);
        planRevisionRepository.save(revision);

        VectorizationRunnerRegistrationEntity registration = new VectorizationRunnerRegistrationEntity();
        registration.setId("vrr-delete-01");
        registration.setDeploymentId(deployment.getId());
        registration.setCustomerId(deployment.getCustomerId());
        registration.setTenantId(deployment.getTenantId());
        registration.setRunnerMode("PLATFORM_MANAGED_AUTO");
        registration.setStatus("ACTIVE");
        registration.setTokenHash("hash-delete-token");
        registration.setTokenHint("hint");
        registration.setTokenExpiresAt(now.plus(2, ChronoUnit.DAYS));
        registration.setLastConnectedAt(now);
        registration.setRunnerInstanceId("vectorization-runner-" + deployment.getId());
        registration.setProductVersion("Platform-V3");
        registration.setCompatibilityVersion("2026.04");
        registration.setCreatedAt(now);
        registration.setUpdatedAt(now);
        registrationRepository.save(registration);

        platformSecretService.upsertManagedSecret(
            VectorizationManagedSecretNames.registrationTokenSecretName(deployment.getId()),
            "runner-registration-token",
            Map.of("deploymentId", deployment.getId(), "registrationId", registration.getId())
        );

        VectorizationRunnerSessionEntity session = new VectorizationRunnerSessionEntity();
        session.setId("vrs-delete-01");
        session.setRegistrationId(registration.getId());
        session.setDeploymentId(deployment.getId());
        session.setStatus("ACTIVE");
        session.setSessionTokenHash("session-delete-hash");
        session.setRunnerInstanceId(registration.getRunnerInstanceId());
        session.setProductVersion("Platform-V3");
        session.setCompatibilityVersion("2026.04");
        session.setRunId("vrn-delete-01");
        session.setLeaseExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        session.setExpiresAt(now.plus(1, ChronoUnit.DAYS));
        session.setLastHeartbeatAt(now);
        session.setCreatedAt(now);
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        VectorizationRunEntity run = new VectorizationRunEntity();
        run.setId("vrn-delete-01");
        run.setPlanId(plan.getId());
        run.setPlanRevisionId(revision.getId());
        run.setDeploymentId(deployment.getId());
        run.setCustomerId(deployment.getCustomerId());
        run.setTenantId(deployment.getTenantId());
        run.setReason("BOOTSTRAP");
        run.setRequestedStatus("COMPLETED");
        run.setStatus("COMPLETED");
        run.setRunnerMode("PLATFORM_MANAGED_AUTO");
        run.setEntityScopeJson("[\"product\"]");
        run.setProgressSummaryJson("{\"indexed\":2}");
        run.setCheckpointSummaryJson("{\"checkpointType\":\"PAGE\"}");
        run.setErrorSummaryJson("{}");
        run.setExecutionOverridesJson("{\"sampleLimit\":2}");
        run.setClaimedByRegistrationId(registration.getId());
        run.setClaimedBySessionId(session.getId());
        run.setRunnerInstanceId(registration.getRunnerInstanceId());
        run.setProductVersion("Platform-V3");
        run.setCompatibilityVersion("2026.04");
        run.setLeaseExpiresAt(now.plus(30, ChronoUnit.MINUTES));
        run.setRequestedByActorId("admin@example.com");
        run.setRequestNote("delete cleanup regression");
        run.setCreatedAt(now);
        run.setStartedAt(now);
        run.setCompletedAt(now.plus(2, ChronoUnit.MINUTES));
        run.setUpdatedAt(now);
        runRepository.save(run);

        VectorizationCheckpointEntity checkpoint = new VectorizationCheckpointEntity();
        checkpoint.setId("vck-delete-01");
        checkpoint.setRunId(run.getId());
        checkpoint.setEntityType("product");
        checkpoint.setCheckpointType("PAGE");
        checkpoint.setCheckpointValue("1");
        checkpoint.setProgressJson("{\"processed\":2}");
        checkpoint.setDetailsJson("{\"page\":1}");
        checkpoint.setCreatedAt(now);
        checkpoint.setUpdatedAt(now);
        checkpointRepository.save(checkpoint);

        VectorizationFailureBucketEntity failureBucket = new VectorizationFailureBucketEntity();
        failureBucket.setId("vfb-delete-01");
        failureBucket.setRunId(run.getId());
        failureBucket.setEntityType("product");
        failureBucket.setErrorCode("WARN_SAMPLE");
        failureBucket.setSummary("sample warning");
        failureBucket.setSampleJson("{\"id\":\"sample-1\"}");
        failureBucket.setOccurrences(1);
        failureBucket.setCreatedAt(now);
        failureBucket.setUpdatedAt(now);
        failureBucketRepository.save(failureBucket);

        VectorizationVerificationRunEntity verificationRun = new VectorizationVerificationRunEntity();
        verificationRun.setId("vvr-delete-01");
        verificationRun.setDeploymentId(deployment.getId());
        verificationRun.setCustomerId(deployment.getCustomerId());
        verificationRun.setTenantId(deployment.getTenantId());
        verificationRun.setVerificationType("SAMPLE_VECTORIZATION_SMOKE");
        verificationRun.setExecutionMode("LIVE_RUN");
        verificationRun.setStatus("PASSED");
        verificationRun.setEntityScopeJson("[\"product\"]");
        verificationRun.setSummaryJson("{\"status\":\"PASSED\"}");
        verificationRun.setLinkedVectorizationRunId(run.getId());
        verificationRun.setRequestedByActorId("admin@example.com");
        verificationRun.setRequestNote("delete cleanup regression");
        verificationRun.setCreatedAt(now);
        verificationRun.setStartedAt(now);
        verificationRun.setCompletedAt(now.plus(1, ChronoUnit.MINUTES));
        verificationRun.setUpdatedAt(now);
        verificationRunRepository.save(verificationRun);

        VectorizationVerificationStepEntity verificationStep = new VectorizationVerificationStepEntity();
        verificationStep.setId("vvs-delete-01");
        verificationStep.setVerificationRunId(verificationRun.getId());
        verificationStep.setStepKey("sample_vectorization");
        verificationStep.setStepName("Sample vectorization");
        verificationStep.setStatus("PASSED");
        verificationStep.setDetailsJson("{\"records\":2}");
        verificationStep.setCreatedAt(now);
        verificationStep.setUpdatedAt(now);
        verificationStepRepository.save(verificationStep);
    }

    private <T> T runAsAdmin(Supplier<T> supplier) {
        authenticateAdmin();
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAdmin() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "admin@example.com",
            PlatformRole.PLATFORM_ADMIN,
            "Platform Admin",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
