package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromptRevisionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentServiceApplyGuardTest {

    @Test
    void applyVersionRejectsOverlappingRelease() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentDraftRepository draftRepository = mock(DeploymentDraftRepository.class);
        DeploymentPromptRevisionRepository promptRevisionRepository = mock(DeploymentPromptRevisionRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        PublicApiDeploymentRepository publicApiDeploymentRepository = mock(PublicApiDeploymentRepository.class);
        DeploymentConfigCompiler deploymentConfigCompiler = mock(DeploymentConfigCompiler.class);
        DeploymentDraftValidationService deploymentDraftValidationService = mock(DeploymentDraftValidationService.class);
        DeploymentProvisioningService deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        DeploymentReleaseExecutionService deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        DeploymentServiceConfigModelService deploymentServiceConfigModelService = mock(DeploymentServiceConfigModelService.class);
        DeploymentServiceNavigationService deploymentServiceNavigationService = mock(DeploymentServiceNavigationService.class);
        DeploymentProductionReadinessScorecardService deploymentProductionReadinessScorecardService = mock(DeploymentProductionReadinessScorecardService.class);
        DeploymentSecretUsageService deploymentSecretUsageService = mock(DeploymentSecretUsageService.class);
        DeploymentSecurityGovernanceService deploymentSecurityGovernanceService = mock(DeploymentSecurityGovernanceService.class);
        DeploymentSourceOfTruthService deploymentSourceOfTruthService = mock(DeploymentSourceOfTruthService.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        DeploymentOperationApprovalService deploymentOperationApprovalService = mock(DeploymentOperationApprovalService.class);
        DeploymentCuratedModuleCatalogService deploymentCuratedModuleCatalogService = mock(DeploymentCuratedModuleCatalogService.class);
        PlatformAuditService platformAuditService = mock(PlatformAuditService.class);

        DeploymentService service = new DeploymentService(
            deploymentRepository,
            draftRepository,
            promptRevisionRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            publicApiDeploymentRepository,
            deploymentConfigCompiler,
            deploymentDraftValidationService,
            deploymentProvisioningService,
            railwayProvisioningPlanService,
            deploymentReleaseVerificationService,
            deploymentReleaseExecutionService,
            deploymentServiceConfigModelService,
            deploymentServiceNavigationService,
            deploymentProductionReadinessScorecardService,
            deploymentSecretUsageService,
            deploymentSecurityGovernanceService,
            deploymentSourceOfTruthService,
            new DeploymentSourceResolver(provisioningProperties()),
            deploymentAccessService,
            deploymentAssignmentService,
            deploymentOperationApprovalService,
            deploymentCuratedModuleCatalogService,
            provisioningProperties(),
            platformAuditService,
            new ObjectMapper()
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setName("Smoke deployment");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("PROVISIONING");
        deployment.setActiveVersionId("ver-123");
        deployment.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("cfg-123");
        version.setReindexRequired(false);
        version.setPublishedAt(Instant.parse("2026-03-29T00:00:00Z"));

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("PROVISIONING");
        release.setProvisioningStatus("RUNNING");
        release.setVerificationStatus("PENDING");
        release.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-29T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));

        when(deploymentRepository.findByIdForUpdate("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentAccess(deployment)).thenReturn(deployment);
        when(versionRepository.findById("ver-123")).thenReturn(Optional.of(version));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.of(release));

        assertThatThrownBy(() -> service.applyVersion("dep-123", "ver-123"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(responseStatusException.getReason()).contains("apply in progress");
            });

        verifyNoInteractions(deploymentReleaseExecutionService);
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_STUB",
            null,
            null,
            "mahmoudashraf/AI-Fabric-Framework",
            "main",
            "dev",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            32,
            null,
            null,
            false,
            false,
            60_000,
            Duration.ofSeconds(5),
            Duration.ofMinutes(10)
        );
    }
}
