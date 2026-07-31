package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentExportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.DeploymentImportRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ExportMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentBundleModels.ImportMode;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentBundleRestoreInPlaceIntegrationTest {

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentBundleExportImportService bundleService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private VectorizationSourceConnectionRepository sourceConnectionRepository;

    @Autowired
    private VectorizationPlanRepository planRepository;

    @Autowired
    private VectorizationPlanRevisionRepository revisionRepository;

    @Autowired
    private VectorizationRunRepository runRepository;

    @Test
    void restoreInPlacePreservesReferencedRevisionAndRunHistory() {
        DeploymentSummary created = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Restore Vectorization History", "dev", "dev-openai-lucene")
        ));
        DeploymentEntity deployment = deploymentRepository.findById(created.id()).orElseThrow();
        SeededVectorization seeded = seedVectorizationHistory(deployment);

        var export = runAsAdmin(() -> bundleService.exportDeployment(
            deployment.getId(),
            new DeploymentExportRequest(ExportMode.CONFIG_ONLY, "restore history regression", null, true, true)
        ));
        var restored = runAsAdmin(() -> bundleService.importDeployment(new DeploymentImportRequest(
            export.bundle(),
            ImportMode.RESTORE_IN_PLACE,
            deployment.getId(),
            null,
            null,
            null,
            null,
            null,
            null,
            "restore history regression"
        )));

        assertThat(restored.deploymentId()).isEqualTo(deployment.getId());
        assertThat(runRepository.findById(seeded.runId())).isPresent();
        assertThat(revisionRepository.findById(seeded.revisionId())).isPresent();
        assertThat(sourceConnectionRepository.findByDeploymentId(deployment.getId()))
            .get()
            .extracting(VectorizationSourceConnectionEntity::getId)
            .isEqualTo(seeded.connectionId());

        List<VectorizationPlanRevisionEntity> revisions =
            revisionRepository.findByPlanIdOrderByRevisionNumberDesc(seeded.planId());
        assertThat(revisions).hasSize(2);
        assertThat(revisions.get(0).getRevisionNumber()).isEqualTo(2);
        assertThat(revisions.get(0).getId()).isNotEqualTo(seeded.revisionId());

        VectorizationPlanEntity restoredPlan = planRepository.findByDeploymentId(deployment.getId()).orElseThrow();
        assertThat(restoredPlan.getActiveRevisionId()).isEqualTo(revisions.get(0).getId());
        assertThat(restoredPlan.getSyncState()).isEqualTo("BOOTSTRAP_REQUIRED");
        assertThat(restoredPlan.getSyncReasonCodesJson()).contains("IMPORTED_REINDEX_REQUIRED");
    }

    private SeededVectorization seedVectorizationHistory(DeploymentEntity deployment) {
        Instant now = Instant.now();
        String suffix = deployment.getId().substring("dep-".length());
        String connectionId = "vcn-restore-" + suffix;
        String planId = "vpl-restore-" + suffix;
        String revisionId = "vpr-restore-" + suffix;
        String runId = "vrn-restore-" + suffix;

        VectorizationSourceConnectionEntity connection = new VectorizationSourceConnectionEntity();
        connection.setId(connectionId);
        connection.setDeploymentId(deployment.getId());
        connection.setCustomerId(deployment.getCustomerId());
        connection.setTenantId(deployment.getTenantId());
        connection.setName("Restore Source");
        connection.setAdapterType("REST_API");
        connection.setAuthMode("NONE");
        connection.setStatus("ACTIVE");
        connection.setConnectionConfigJson("{\"baseUrl\":\"https://example.test\"}");
        connection.setSecretReferencesJson("{}");
        connection.setDiscoverySummaryJson("{\"entityCounts\":{\"product\":1}}");
        connection.setCreatedAt(now);
        connection.setUpdatedAt(now);
        sourceConnectionRepository.save(connection);

        VectorizationPlanEntity plan = new VectorizationPlanEntity();
        plan.setId(planId);
        plan.setDeploymentId(deployment.getId());
        plan.setCustomerId(deployment.getCustomerId());
        plan.setTenantId(deployment.getTenantId());
        plan.setName("Restore Plan");
        plan.setStatus("ACTIVE");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
        plan.setSyncState("IN_SYNC");
        plan.setSyncReasonCodesJson("[\"READY\"]");
        plan.setSyncReasonDetailsJson("{\"syncState\":\"IN_SYNC\"}");
        plan.setSourceConnectionId(connectionId);
        plan.setActiveRevisionId(revisionId);
        plan.setActiveIndexedOutputHash("restore-output-hash");
        plan.setLastSuccessfulIndexedOutputHash("restore-output-hash");
        plan.setLastRunId(runId);
        plan.setLastSuccessfulRunId(runId);
        plan.setCreatedAt(now);
        plan.setUpdatedAt(now);
        planRepository.save(plan);

        VectorizationPlanRevisionEntity revision = new VectorizationPlanRevisionEntity();
        revision.setId(revisionId);
        revision.setPlanId(planId);
        revision.setDeploymentId(deployment.getId());
        revision.setRevisionNumber(1);
        revision.setStatus("ACTIVE");
        revision.setSourceConnectionId(connectionId);
        revision.setEntityScopeJson("[\"product\"]");
        revision.setMappingConfigJson("{\"product\":{\"sourcePath\":\"products\"}}");
        revision.setExecutionConfigJson("{\"batchSize\":25}");
        revision.setIndexedOutputHash("restore-output-hash");
        revision.setCreatedByActorId("admin@example.com");
        revision.setCreatedAt(now);
        revision.setUpdatedAt(now);
        revisionRepository.save(revision);

        VectorizationRunEntity run = new VectorizationRunEntity();
        run.setId(runId);
        run.setPlanId(planId);
        run.setPlanRevisionId(revisionId);
        run.setDeploymentId(deployment.getId());
        run.setCustomerId(deployment.getCustomerId());
        run.setTenantId(deployment.getTenantId());
        run.setReason("BOOTSTRAP");
        run.setRequestedStatus("COMPLETED");
        run.setStatus("COMPLETED");
        run.setRunnerMode("PLATFORM_MANAGED_AUTO");
        run.setEntityScopeJson("[\"product\"]");
        run.setProgressSummaryJson("{\"indexed\":1}");
        run.setCheckpointSummaryJson("{}");
        run.setErrorSummaryJson("{}");
        run.setExecutionOverridesJson("{}");
        run.setRequestedByActorId("admin@example.com");
        run.setRequestNote("restore history regression");
        run.setCreatedAt(now);
        run.setStartedAt(now);
        run.setCompletedAt(now);
        run.setUpdatedAt(now);
        runRepository.save(run);

        return new SeededVectorization(connectionId, planId, revisionId, runId);
    }

    private <T> T runAsAdmin(Supplier<T> supplier) {
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
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private record SeededVectorization(
        String connectionId,
        String planId,
        String revisionId,
        String runId
    ) {
    }
}
