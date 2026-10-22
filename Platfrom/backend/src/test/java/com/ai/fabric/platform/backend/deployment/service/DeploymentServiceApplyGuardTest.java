package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionReleaseCoordinator;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromptRevisionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeploymentServiceApplyGuardTest {
    private DeploymentRepository deploymentRepository;
    private DeploymentDraftRepository draftRepository;
    private DeploymentVersionRepository versionRepository;
    private DeploymentReleaseRepository releaseRepository;
    private DeploymentProvisioningService deploymentProvisioningService;
    private DeploymentReleaseExecutionService deploymentReleaseExecutionService;
    private DeploymentOperationApprovalService deploymentOperationApprovalService;
    private DeploymentSecretUsageService deploymentSecretUsageService;
    private DeploymentAccessService deploymentAccessService;
    private PlatformAuditService platformAuditService;
    private DeploymentSourceArtifactService sourceArtifactService;
    private DeploymentSourceCapabilityManifestService capabilityManifestService;
    private ObjectMapper objectMapper;
    private DeploymentService service;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;

    @BeforeEach
    void setup() {
        deploymentRepository = mock(DeploymentRepository.class);
        draftRepository = mock(DeploymentDraftRepository.class);
        DeploymentPromptRevisionRepository promptRevisionRepository = mock(DeploymentPromptRevisionRepository.class);
        versionRepository = mock(DeploymentVersionRepository.class);
        releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentVerificationRunRepository verificationRunRepository = mock(DeploymentVerificationRunRepository.class);
        DeploymentConfigCompiler deploymentConfigCompiler = mock(DeploymentConfigCompiler.class);
        DeploymentDraftValidationService deploymentDraftValidationService = mock(DeploymentDraftValidationService.class);
        deploymentProvisioningService = mock(DeploymentProvisioningService.class);
        RailwayProvisioningPlanService railwayProvisioningPlanService = mock(RailwayProvisioningPlanService.class);
        DeploymentReleaseVerificationService deploymentReleaseVerificationService = mock(DeploymentReleaseVerificationService.class);
        deploymentReleaseExecutionService = mock(DeploymentReleaseExecutionService.class);
        DeploymentReleaseRecoveryService deploymentReleaseRecoveryService = mock(DeploymentReleaseRecoveryService.class);
        DeploymentServiceConfigModelService deploymentServiceConfigModelService = mock(DeploymentServiceConfigModelService.class);
        DeploymentServiceNavigationService deploymentServiceNavigationService = mock(DeploymentServiceNavigationService.class);
        DeploymentProductionReadinessScorecardService deploymentProductionReadinessScorecardService = mock(DeploymentProductionReadinessScorecardService.class);
        DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
        deploymentSecretUsageService = mock(DeploymentSecretUsageService.class);
        DeploymentSecurityGovernanceService deploymentSecurityGovernanceService = mock(DeploymentSecurityGovernanceService.class);
        DeploymentSourceOfTruthService deploymentSourceOfTruthService = mock(DeploymentSourceOfTruthService.class);
        deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        deploymentOperationApprovalService = mock(DeploymentOperationApprovalService.class);
        DeploymentDeletionService deploymentDeletionService = mock(DeploymentDeletionService.class);
        DeploymentCuratedModuleCatalogService deploymentCuratedModuleCatalogService = mock(DeploymentCuratedModuleCatalogService.class);
        PlatformCustomerTenantService platformCustomerTenantService = mock(PlatformCustomerTenantService.class);
        PlatformCustomerConsumerService platformCustomerConsumerService = mock(PlatformCustomerConsumerService.class);
        platformAuditService = mock(PlatformAuditService.class);

        objectMapper = new ObjectMapper();
        sourceArtifactService = mock(DeploymentSourceArtifactService.class);
        capabilityManifestService =
            new DeploymentSourceCapabilityManifestService(objectMapper);
        service = new DeploymentService(
            deploymentRepository,
            draftRepository,
            promptRevisionRepository,
            versionRepository,
            releaseRepository,
            verificationRunRepository,
            deploymentConfigCompiler,
            deploymentDraftValidationService,
            deploymentProvisioningService,
            railwayProvisioningPlanService,
            deploymentReleaseVerificationService,
            deploymentReleaseExecutionService,
            deploymentReleaseRecoveryService,
            deploymentServiceConfigModelService,
            deploymentServiceNavigationService,
            deploymentProductionReadinessScorecardService,
            deploymentProviderConnectivityService,
            deploymentSecretUsageService,
            deploymentSecurityGovernanceService,
            deploymentSourceOfTruthService,
            new DeploymentSourceResolver(provisioningProperties()),
            deploymentAccessService,
            deploymentAssignmentService,
            deploymentOperationApprovalService,
            deploymentDeletionService,
            deploymentCuratedModuleCatalogService,
            platformCustomerTenantService,
            platformCustomerConsumerService,
            provisioningProperties(),
            platformAuditService,
            new com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService(objectMapper),
            new DeploymentBehaviorCatalogService(objectMapper),
            sourceArtifactService,
            capabilityManifestService,
            mock(DeploymentRuntimeAuthorityService.class),
            mock(CustomerOfferDeploymentRepository.class),
            objectMapper
        );
        ReflectionTestUtils.setField(service, "offerBoundary", mock(CustomerSelfServiceOperationBoundary.class));

        deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setName("Smoke deployment");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("PROVISIONING");
        deployment.setCustomerId("cust-internal");
        deployment.setTenantId("ten-smoke");
        deployment.setActiveVersionId("ver-123");
        deployment.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));

        version = new DeploymentVersionEntity();
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
    }

    @Test
    void applyVersionRejectsOverlappingRelease() {
        assertThatThrownBy(() -> service.applyVersion("dep-123", "ver-123"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> {
                ResponseStatusException responseStatusException = (ResponseStatusException) ex;
                assertThat(responseStatusException.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(responseStatusException.getReason()).contains("apply in progress");
            });

        verifyNoInteractions(deploymentReleaseExecutionService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING", "APPLIED_VERIFICATION_FAILED"})
    void manualVerificationCannotCompeteWithApply(String status) {
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        when(deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        var release = releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123").orElseThrow();
        release.setStatus(status);
        when(releaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-123", "ver-123"))
            .thenReturn(Optional.of(release));
        when(deploymentReleaseExecutionService.isApplyExecuting("rel-123"))
            .thenReturn("APPLIED_VERIFICATION_FAILED".equals(status));

        assertThatThrownBy(() -> service.rerunVerification("dep-123"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(error.getReason()).isEqualTo("RELEASE_APPLY_IN_PROGRESS");
            });
        verify(platformAuditService, never()).record(any(), any(), any(), any());
    }

    @Test
    void scopedSecretUsageRequiresOperatorBeforeVersionOrTargetLookup() {
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(deploymentAccessService)
            .requireDeploymentOperatorAccess(deployment);
        assertThatThrownBy(() -> service.getScopedDeploymentSecretUsage("dep-123", "ver-123", "target-staging"))
            .isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(versionRepository, deploymentProvisioningService, deploymentSecretUsageService);
    }

    @Test
    void scopedSecretUsageUsesOnlyExactPublishedVersionAndActiveTarget() {
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        var draft = new DeploymentDraftEntity(); draft.setId("draft"); draft.setDeploymentId("dep-123");
        deployment.setActiveDraftId("draft");
        when(draftRepository.findById("draft")).thenReturn(Optional.of(draft));
        var target = new DeploymentTargetProfileEntity(); target.setId("target-staging"); target.setActive(true);
        when(deploymentProvisioningService.selectedTargetProfile("target-staging")).thenReturn(target);
        service.getScopedDeploymentSecretUsage("dep-123", "ver-123", "target-staging");
        verify(deploymentSecretUsageService).build(deployment, draft, version, "target-staging");
        verifyNoMoreInteractions(deploymentSecretUsageService);
        verifyNoInteractions(deploymentReleaseExecutionService, deploymentOperationApprovalService, platformAuditService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "unpublished"})
    void scopedSecretUsageRejectsForeignOrUnpublishedVersion(String change) {
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        if ("foreign".equals(change)) version.setDeploymentId("another-deployment");
        else version.setStatus("DRAFT");
        assertThatThrownBy(() -> service.getScopedDeploymentSecretUsage("dep-123", "ver-123", "target-staging"))
            .hasMessageContaining("SECRET_USAGE_PUBLISHED_VERSION_SCOPE_REQUIRED");
        verifyNoInteractions(deploymentProvisioningService, deploymentSecretUsageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", " target-staging", "target-staging "})
    void scopedSecretUsageNeverDefaultsOrNormalizesRequestedTarget(String targetId) {
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        assertThatThrownBy(() -> service.getScopedDeploymentSecretUsage("dep-123", "ver-123", targetId))
            .hasMessageContaining("SECRET_USAGE_PUBLISHED_VERSION_AND_TARGET_REQUIRED");
        verifyNoInteractions(versionRepository, deploymentProvisioningService, deploymentSecretUsageService);
    }

    @Test
    void scopedSecretUsageRejectsInactiveOrDifferentTarget() {
        when(deploymentRepository.findById("dep-123")).thenReturn(Optional.of(deployment));
        var target = new DeploymentTargetProfileEntity(); target.setId("target-staging"); target.setActive(false);
        when(deploymentProvisioningService.selectedTargetProfile("target-staging")).thenReturn(target);
        assertThatThrownBy(() -> service.getScopedDeploymentSecretUsage("dep-123", "ver-123", "target-staging"))
            .hasMessageContaining("SECRET_USAGE_ACTIVE_TARGET_REQUIRED");
        target.setActive(true); target.setId("another-target");
        assertThatThrownBy(() -> service.getScopedDeploymentSecretUsage("dep-123", "ver-123", "target-staging"))
            .hasMessageContaining("SECRET_USAGE_ACTIVE_TARGET_REQUIRED");
        verifyNoInteractions(deploymentSecretUsageService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-capability", "missing-build"})
    void tokenArtifactRejectionPrecedesApprovalConsumptionAndReleasePersistence(String change) throws Exception {
        var artifact = prepareTokenApply();
        if ("missing-build".equals(change)) {
            artifact.setBuildRunId(null);
            var manifest = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.readTree(artifact.getCapabilityManifestJson());
            ((com.fasterxml.jackson.databind.node.ArrayNode) manifest.path("capabilities"))
                .add(MilvusTokenRuntimeArtifactGate.CAPABILITY);
            var normalized = capabilityManifestService.normalize(manifest);
            artifact.setCapabilityManifestJson(normalized.manifest().toString());
            artifact.setCapabilityManifestHash(normalized.hash());
        }
        var gate = spy(new MilvusTokenRuntimeArtifactGate(sourceArtifactService, capabilityManifestService));
        ReflectionTestUtils.setField(service, "milvusTokenArtifacts", gate);
        var admission = mock(AdmissionReleaseCoordinator.class);
        ReflectionTestUtils.setField(service, "admissionReleaseCoordinator", admission);

        assertThatThrownBy(() -> service.applyVersion("dep-123", "ver-123", "approval", "target-staging", "runtime"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");

        verify(gate).require(version, objectMapper.readTree(version.getProviderConfigJson()), "runtime");
        verifyNoInteractions(deploymentOperationApprovalService, admission,
            deploymentReleaseExecutionService, platformAuditService);
        verify(releaseRepository, never()).save(any());
        verify(releaseRepository, never()).saveAndFlush(any());
        verify(deploymentRepository, never()).save(any());
        verify(deploymentProvisioningService).selectedTargetProfile("target-staging");
        verifyNoMoreInteractions(deploymentProvisioningService);
        assertThat(deployment.getStatus()).isEqualTo("PROVISIONING");
    }

    private DeploymentSourceArtifactEntity prepareTokenApply() throws Exception {
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-123")).thenReturn(Optional.empty());
        deployment.setApprovalRequiredForApply(true);
        deployment.setBehaviorType("CONVERSATIONAL");
        version.setAiFabricFrameworkVersion("0.8.41");
        version.setProviderConfigJson("""
            {"vectorStrategy":"milvus","vectorProvisioningMode":"EXTERNAL_EXISTING","vectorStoragePosture":"SHARED",
             "milvusExternalBindingRequired":true,"milvusAuthenticationType":"TOKEN"}
            """);
        var behaviors = new DeploymentBehaviorCatalogService(objectMapper);
        var behavior = behaviors.defaultConfig("CONVERSATIONAL");
        version.setBehaviorConfigJson(behavior.toString());
        var requirements = behaviors.releaseRequirements(behavior);
        var manifest = objectMapper.createObjectNode().put("schemaVersion", DeploymentSourceCapabilityManifestService.SCHEMA_VERSION)
            .put("aiFabricVersion", version.getAiFabricFrameworkVersion());
        manifest.set("supportedBehaviorTypes", objectMapper.valueToTree(java.util.List.of(requirements.behaviorType())));
        manifest.set("supportedActivationSources", objectMapper.valueToTree(requirements.activationSources()));
        manifest.set("supportedChannelBindings", objectMapper.valueToTree(requirements.channelBindings()));
        manifest.set("supportedExecutionExtensions", objectMapper.valueToTree(requirements.executionExtensions()));
        manifest.set("capabilities", objectMapper.valueToTree(requirements.capabilities()));
        manifest.set("endpointClasses", objectMapper.valueToTree(requirements.endpointClasses()));
        manifest.set("migrationIds", objectMapper.valueToTree(requirements.migrationIds()));
        manifest.set("verificationPackIds", objectMapper.valueToTree(requirements.verificationPackIds()));
        var normalized = capabilityManifestService.normalize(manifest);
        var artifact = new DeploymentSourceArtifactEntity();
        artifact.setArtifactRole("RUNTIME"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setImageDigest("sha256:" + "a".repeat(64)); artifact.setGitCommitSha("b".repeat(40));
        artifact.setBuildRunId("reviewed-build"); artifact.setPromotionChannel("staging");
        artifact.setPromotedAt(Instant.parse("2026-10-18T00:00:00Z"));
        artifact.setCapabilityManifestJson(normalized.manifest().toString());
        artifact.setCapabilityManifestHash(normalized.hash());
        when(sourceArtifactService.require("runtime")).thenReturn(artifact);
        var target = new DeploymentTargetProfileEntity();
        target.setId("target-staging"); target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging"); target.setSourceStrategy("IMAGE_SOURCE");
        target.setResourceDefaultsJson("{\"sourceStrategy\":\"IMAGE_SOURCE\"}");
        when(deploymentProvisioningService.selectedTargetProfile("target-staging")).thenReturn(target);
        ReflectionTestUtils.setField(service, "retirementGates", mock(TargetRetirementGateService.class));
        return artifact;
    }

    @Test
    void productionQualifiedArtifactPassesStagingChannelGuardWithoutSkippingLaterChecks() throws Exception {
        var artifact = prepareTokenApply();
        artifact.setPromotionChannel("production");
        ReflectionTestUtils.setField(service, "milvusTokenArtifacts",
            new MilvusTokenRuntimeArtifactGate(sourceArtifactService, capabilityManifestService));

        assertThatThrownBy(() -> service.applyVersion("dep-123", "ver-123", "approval", "target-staging", "runtime"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("MILVUS_TOKEN_RUNTIME_ARTIFACT_REQUIRED");

        verifyNoInteractions(deploymentOperationApprovalService, deploymentReleaseExecutionService, platformAuditService);
        verify(releaseRepository, never()).save(any());
        verify(releaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void stagingArtifactCannotReachProductionApprovalOrProvisioning() throws Exception {
        prepareTokenApply();
        var target = deploymentProvisioningService.selectedTargetProfile("target-staging");
        target.setEnvironmentName("production");

        assertThatThrownBy(() -> service.applyVersion("dep-123", "ver-123", "approval", "target-staging", "runtime"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("promotion channel staging")
            .hasMessageContaining("target environment production");

        verifyNoInteractions(deploymentOperationApprovalService, deploymentReleaseExecutionService, platformAuditService);
        verify(releaseRepository, never()).save(any());
        verify(releaseRepository, never()).saveAndFlush(any());
    }

    @Test
    void productionChannelStillRequiresActualPromotionBeforeStagingApply() throws Exception {
        var artifact = prepareTokenApply();
        artifact.setPromotionChannel("production");
        artifact.setPromotedAt(null);

        assertThatThrownBy(() -> service.applyVersion("dep-123", "ver-123", "approval", "target-staging", "runtime"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("reviewed and promoted before apply");

        verifyNoInteractions(deploymentOperationApprovalService, deploymentReleaseExecutionService, platformAuditService);
        verify(releaseRepository, never()).save(any());
        verify(releaseRepository, never()).saveAndFlush(any());
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
