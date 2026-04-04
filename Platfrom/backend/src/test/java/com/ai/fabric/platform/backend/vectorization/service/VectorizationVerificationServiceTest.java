package com.ai.fabric.platform.backend.vectorization.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.TenantScopedVectorResourceRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationRunEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationVerificationStepEntity;
import com.ai.fabric.platform.backend.vectorization.model.CreateVectorizationVerificationRunRequest;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanRevisionSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPlanSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationPreviewSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationSourceConnectionSummary;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationRunRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationVerificationStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorizationVerificationServiceTest {

    private final DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
    private final com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository deploymentRepository = mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository.class);
    private final PlatformAuditService auditService = mock(PlatformAuditService.class);
    private final TenantScopedVectorResourceRepository tenantScopedVectorResourceRepository = mock(TenantScopedVectorResourceRepository.class);
    private final VectorizationVerificationRunRepository verificationRunRepository = mock(VectorizationVerificationRunRepository.class);
    private final VectorizationVerificationStepRepository verificationStepRepository = mock(VectorizationVerificationStepRepository.class);
    private final VectorizationRunRepository runRepository = mock(VectorizationRunRepository.class);
    private final VectorizationService vectorizationService = mock(VectorizationService.class);
    private final VectorizationRuntimeCoverageClient runtimeCoverageClient = mock(VectorizationRuntimeCoverageClient.class);
    private final VectorizationVerificationProbeService vectorizationVerificationProbeService = mock(VectorizationVerificationProbeService.class);
    private final VectorizationJsonSupport jsonSupport = new VectorizationJsonSupport(new ObjectMapper());

    @Test
    void createControlPlaneVerificationPersistsPassedRun() {
        DeploymentEntity deployment = deployment();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(verificationRunRepository.save(any(VectorizationVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationStepRepository.save(any(VectorizationVerificationStepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vectorizationService.getOverview("dep-1")).thenReturn(overview());
        when(vectorizationService.preview("dep-1")).thenReturn(
            new VectorizationPreviewSummary(
                "dep-1",
                "IN_SYNC",
                objectNode(),
                objectNode(),
                objectNode(),
                objectNode(),
                objectNode(),
                "hash-1"
            )
        );

        VectorizationVerificationService service = service();

        var summary = service.createRun("dep-1", new CreateVectorizationVerificationRunRequest(
            "CONTROL_PLANE_READINESS",
            List.of("product"),
            null,
            null
        ));

        assertThat(summary.status()).isEqualTo("PASSED");
        assertThat(summary.executionMode()).isEqualTo("READ_ONLY");
        verify(verificationStepRepository, atLeast(4)).save(any(VectorizationVerificationStepEntity.class));
    }

    @Test
    void createDiscoverySmokeQueuesLinkedVectorizationRun() {
        DeploymentEntity deployment = deployment();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(verificationRunRepository.save(any(VectorizationVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationStepRepository.save(any(VectorizationVerificationStepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(vectorizationService.createRun(any(), any())).thenReturn(new VectorizationRunSummary(
            "vrn-1",
            "DISCOVERY",
            "QUEUED",
            "QUEUED",
            "PLATFORM_MANAGED_AUTO",
            List.of("product"),
            objectNode(),
            objectNode(),
            objectNode(),
            null,
            null,
            null,
            null,
            null,
            null,
            Instant.now(),
            null,
            null,
            Instant.now()
        ));

        VectorizationVerificationService service = service();

        var summary = service.createRun("dep-1", new CreateVectorizationVerificationRunRequest(
            "SOURCE_DISCOVERY_SMOKE",
            List.of("product"),
            "smoke",
            null
        ));

        assertThat(summary.status()).isEqualTo("RUNNING");
        assertThat(summary.executionMode()).isEqualTo("ACTIVE");
        assertThat(summary.linkedVectorizationRunId()).isEqualTo("vrn-1");
    }

    @Test
    void getRunDetailsRefreshesLinkedRunToPassed() {
        DeploymentEntity deployment = deployment();
        when(deploymentRepository.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);

        VectorizationVerificationRunEntity verificationRun = new VectorizationVerificationRunEntity();
        verificationRun.setId("vvr-1");
        verificationRun.setDeploymentId("dep-1");
        verificationRun.setCustomerId("cus-1");
        verificationRun.setTenantId("ten-1");
        verificationRun.setVerificationType("SOURCE_DISCOVERY_SMOKE");
        verificationRun.setExecutionMode("ACTIVE");
        verificationRun.setStatus("RUNNING");
        verificationRun.setEntityScopeJson("[\"product\"]");
        verificationRun.setSummaryJson("{}");
        verificationRun.setLinkedVectorizationRunId("vrn-1");
        verificationRun.setCreatedAt(Instant.now());
        verificationRun.setStartedAt(Instant.now());
        verificationRun.setUpdatedAt(Instant.now());

        VectorizationVerificationStepEntity step = new VectorizationVerificationStepEntity();
        step.setId("vvs-1");
        step.setVerificationRunId("vvr-1");
        step.setStepKey("linked_vectorization_run");
        step.setStepName("Linked discovery run created");
        step.setStatus("RUNNING");
        step.setDetailsJson("{}");
        step.setCreatedAt(Instant.now());
        step.setUpdatedAt(Instant.now());

        VectorizationRunEntity linkedRun = new VectorizationRunEntity();
        linkedRun.setId("vrn-1");

        when(verificationRunRepository.findById("vvr-1")).thenReturn(Optional.of(verificationRun));
        when(verificationRunRepository.save(any(VectorizationVerificationRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(verificationStepRepository.findByVerificationRunIdAndStepKey("vvr-1", "linked_vectorization_run")).thenReturn(Optional.of(step));
        when(verificationStepRepository.findByVerificationRunIdOrderByCreatedAtAsc("vvr-1")).thenReturn(List.of(step));
        when(verificationStepRepository.save(any(VectorizationVerificationStepEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(runRepository.findById("vrn-1")).thenReturn(Optional.of(linkedRun));
        when(vectorizationService.summarizeRun(linkedRun)).thenReturn(new VectorizationRunSummary(
            "vrn-1",
            "DISCOVERY",
            "COMPLETED",
            "COMPLETED",
            "PLATFORM_MANAGED_AUTO",
            List.of("product"),
            objectNode(),
            objectNode(),
            objectNode(),
            null,
            null,
            "runner-a",
            "1.0.0",
            "1.0",
            null,
            Instant.now(),
            Instant.now(),
            Instant.now(),
            Instant.now()
        ));

        VectorizationVerificationService service = service();

        var details = service.getRunDetails("dep-1", "vvr-1");

        assertThat(details.verificationRun().status()).isEqualTo("PASSED");
        assertThat(details.linkedVectorizationRun()).isNotNull();
        assertThat(details.linkedVectorizationRun().status()).isEqualTo("COMPLETED");
    }

    private VectorizationVerificationService service() {
        return new VectorizationVerificationService(
            deploymentRepository,
            deploymentAccessService,
            auditService,
            tenantScopedVectorResourceRepository,
            verificationRunRepository,
            verificationStepRepository,
            runRepository,
            vectorizationService,
            runtimeCoverageClient,
            vectorizationVerificationProbeService,
            jsonSupport
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("cus-1");
        deployment.setTenantId("ten-1");
        deployment.setName("Deployment");
        return deployment;
    }

    private VectorizationOverviewSummary overview() {
        return new VectorizationOverviewSummary(
            "dep-1",
            "cus-1",
            "ten-1",
            "ver-1",
            "Version 1",
            "hash-1",
            new VectorizationSourceConnectionSummary(
                "vcn-1",
                "dep-1",
                "Primary source",
                "REST_API",
                "API_KEY",
                "READY",
                objectNode(),
                objectNode(),
                objectNode(),
                Instant.now(),
                Instant.now()
            ),
            new VectorizationPlanSummary(
                "vpl-1",
                "dep-1",
                "Onboarding vectorization",
                "ACTIVE",
                "PLATFORM_MANAGED_AUTO",
                "IN_SYNC",
                List.of("IN_SYNC"),
                objectNode(),
                "hash-1",
                "hash-1",
                "vpr-1",
                "vcn-1",
                null,
                null,
                null,
                null,
                new VectorizationPlanRevisionSummary(
                    "vpr-1",
                    1,
                    "ACTIVE",
                    "vcn-1",
                    objectNode(),
                    objectNode(),
                    objectNode(),
                    "hash-1",
                    Instant.now(),
                    Instant.now()
                ),
                Instant.now(),
                Instant.now()
            ),
            new VectorizationRunnerSummary(
                "vrr-1",
                "PLATFORM_MANAGED_AUTO",
                "ACTIVE",
                "CURRENT",
                "hint",
                Instant.now().plusSeconds(3600),
                "runner-a",
                "1.0.0",
                "1.0",
                Instant.now(),
                Instant.now(),
                Instant.now().plusSeconds(300)
            ),
            List.of()
        );
    }

    private ObjectNode objectNode() {
        return new ObjectMapper().createObjectNode();
    }
}
