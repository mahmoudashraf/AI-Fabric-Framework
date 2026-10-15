package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionActivationRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromotionCommandRepository;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionPromotionRetirementScheduler;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.model.PlatformConsumerSummary;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentPracticalPromotionServiceTest {

    @Test
    void planPromotionRequiresVerifiedCustomerStagingRelease() {
        TestFixture fixture = fixture();
        DeploymentEntity deployment = deployment();
        DeploymentVersionEntity version = version("ver-1", deployment.getId());
        DeploymentReleaseEntity stagingRelease = release(
            "rel-staging",
            deployment.getId(),
            version.getId(),
            "dtp-coolify-prod-staging",
            "APPLIED_VERIFIED",
            "PASSED"
        );

        when(fixture.deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(fixture.deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(fixture.targetProfileRepository.findById("dtp-coolify-prod-staging")).thenReturn(Optional.of(targetProfile("dtp-coolify-prod-staging", "staging")));
        when(fixture.targetProfileRepository.findById("dtp-coolify-production")).thenReturn(Optional.of(targetProfile("dtp-coolify-production", "production")));
        when(fixture.releaseRepository.findById(stagingRelease.getId())).thenReturn(Optional.of(stagingRelease));
        when(fixture.versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        var consumer = consumer("customer-production", deployment.getCustomerId(), deployment.getId(), stagingRelease.getId());
        consumer.setBoundTargetProfileId(stagingRelease.getTargetProfileId());
        when(fixture.consumerRepository.findByCustomerIdAndConsumerIdIgnoreCase(deployment.getCustomerId(), consumer.getConsumerId()))
            .thenReturn(Optional.of(consumer));

        var summary = fixture.service.planPromotion(deployment.getId(), new DeploymentPracticalPromotionRequest(
            consumer.getConsumerId(), 0L, stagingRelease.getId(), "dtp-coolify-production", null, null));

        assertThat(summary.status()).isEqualTo("READY");
        assertThat(summary.versionId()).isEqualTo(version.getId());
        assertThat(summary.stagingReleaseId()).isEqualTo(stagingRelease.getId());
        assertThat(summary.productionTargetProfileId()).isEqualTo("dtp-coolify-production");
    }

    @Test
    void activateProductionConsumerRejectsUnverifiedProductionRelease() {
        TestFixture fixture = fixture();
        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity failedRelease = release(
            "rel-prod",
            deployment.getId(),
            "ver-1",
            "dtp-coolify-production",
            "APPLIED_VERIFICATION_FAILED",
            "FAILED"
        );

        when(fixture.deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(fixture.deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(fixture.releaseRepository.findById(failedRelease.getId())).thenReturn(Optional.of(failedRelease));
        when(fixture.consumerRepository.findBindingForUpdate(deployment.getCustomerId(), "customer-production"))
            .thenReturn(Optional.of(consumer("customer-production", deployment.getCustomerId(), deployment.getId(), "rel-staging")));

        assertThatThrownBy(() -> fixture.service.activateProductionConsumer(
            deployment.getId(),
            new DeploymentPracticalPromotionActivationRequest(failedRelease.getId(), "customer-production", false, "activate", 0L, "activate-operation")
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Release must be verified");
    }

    @Test
    void activateProductionConsumerBindsVerifiedReleaseAndMarksRollbackReserve() {
        TestFixture fixture = fixture();
        DeploymentEntity deployment = deployment();
        DeploymentReleaseEntity productionRelease = release(
            "rel-prod",
            deployment.getId(),
            "ver-1",
            "dtp-coolify-production",
            "APPLIED_VERIFIED",
            "PASSED"
        );
        PlatformConsumerEntity consumer = consumer("customer-production", deployment.getCustomerId(), deployment.getId(), "rel-staging");
        consumer.setBoundTargetProfileId("dtp-coolify-prod-staging");
        productionRelease.setPromotionCandidateJson("{\"stagingReleaseId\":\"rel-staging\"}");
        productionRelease.setPromotionCandidateFingerprint(DeploymentEvidenceFingerprint.hash(new ObjectMapper(),
            new ObjectMapper().createObjectNode().put("stagingReleaseId", "rel-staging")));
        var staging = release("rel-staging", deployment.getId(), "ver-1", "dtp-coolify-prod-staging", "APPLIED_VERIFIED", "PASSED");
        DeploymentProviderResourceHandleEntity productionHandle = handle("h-prod", deployment.getId(), productionRelease.getId(), "dtp-coolify-production", "ACTIVE");
        DeploymentProviderResourceHandleEntity previousHandle = handle("h-old", deployment.getId(), "rel-staging", "dtp-coolify-prod-staging", "ACTIVE");
        DeploymentProviderResourceHandleEntity sourceBinding = handle("h-source", deployment.getId(), null, "dtp-coolify-prod-staging", "CONFIGURED");
        sourceBinding.setResourceKind(DeploymentDocumentStorageBindingService.RESOURCE_KIND);
        PlatformConsumerSummary updatedConsumer = new PlatformConsumerSummary(
            consumer.getConsumerId(),
            consumer.getCustomerId(),
            "Production Consumer",
            null,
            "ACTIVE",
            deployment.getId(),
            "Demo Deployment",
            "staging",
            "ACTIVE",
            productionRelease.getId(),
            productionRelease.getStatus(),
            productionRelease.getTargetProfileId(),
            Instant.now(),
            Instant.now(),
            Instant.now(),
            1L
        );

        when(fixture.deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(fixture.deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(fixture.releaseRepository.findById(productionRelease.getId())).thenReturn(Optional.of(productionRelease));
        when(fixture.releaseRepository.findById(staging.getId())).thenReturn(Optional.of(staging));
        when(fixture.versionRepository.findById("ver-1")).thenReturn(Optional.of(version("ver-1", deployment.getId())));
        when(fixture.targetProfileRepository.findById("dtp-coolify-prod-staging"))
            .thenReturn(Optional.of(targetProfile("dtp-coolify-prod-staging", "staging")));
        when(fixture.targetProfileRepository.findById("dtp-coolify-production"))
            .thenReturn(Optional.of(targetProfile("dtp-coolify-production", "production")));
        when(fixture.consumerRepository.findByCustomerIdAndConsumerIdIgnoreCase(deployment.getCustomerId(), consumer.getConsumerId()))
            .thenReturn(Optional.of(consumer));
        when(fixture.consumerRepository.findBindingForUpdate(deployment.getCustomerId(), consumer.getConsumerId()))
            .thenReturn(Optional.of(consumer));
        when(fixture.consumerService.updateBindingForTrustedPromotion(
            eq(deployment.getCustomerId()),
            eq(consumer.getConsumerId()),
            eq(deployment.getId()),
            eq(productionRelease.getId()),
            eq(productionRelease.getTargetProfileId()),
            eq("activate"),
            eq(0L),
            anyString()
        )).thenReturn(updatedConsumer);
        when(fixture.resourceHandleRepository.findByReleaseIdOrderByUpdatedAtDesc(productionRelease.getId())).thenReturn(List.of(productionHandle));
        when(fixture.resourceHandleRepository.findByReleaseIdOrderByUpdatedAtDesc("rel-staging")).thenReturn(List.of(previousHandle));
        when(fixture.resourceHandleRepository.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
            deployment.getId(), "dtp-coolify-prod-staging")).thenReturn(List.of(previousHandle, sourceBinding));
        when(fixture.resourceHandleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = fixture.service.activateProductionConsumer(
            deployment.getId(),
            new DeploymentPracticalPromotionActivationRequest(productionRelease.getId(), consumer.getConsumerId(), true, "activate", 0L, "activate-operation")
        );

        assertThat(summary.status()).isEqualTo("PRODUCTION_CONSUMER_ACTIVATED");
        assertThat(summary.consumer().boundReleaseId()).isEqualTo(productionRelease.getId());
        assertThat(summary.resources()).extracting("status").contains("ACTIVE", "ROLLBACK_RESERVED");
        assertThat(productionHandle.getStatus()).isEqualTo("ACTIVE");
        assertThat(previousHandle.getStatus()).isEqualTo("SUPERSEDED");
        assertThat(sourceBinding.getStatus()).isEqualTo("CONFIGURED");
        verify(fixture.consumerService).updateBindingForTrustedPromotion(
            deployment.getCustomerId(),
            consumer.getConsumerId(),
            deployment.getId(),
            productionRelease.getId(),
            productionRelease.getTargetProfileId(),
            "activate",
            0L,
            "promotion:" + DeploymentEvidenceFingerprint.hash(new ObjectMapper(), new ObjectMapper().createObjectNode()
                .put("kind", "activate").put("key", "activate-operation"))
        );
        verify(fixture.retirementScheduler).scheduleAfterCommit(
            deployment.getId(), "dtp-coolify-prod-staging", "activate-operation");
    }

    private TestFixture fixture() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository versionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentTargetProfileRepository targetProfileRepository = mock(DeploymentTargetProfileRepository.class);
        DeploymentProviderResourceHandleRepository resourceHandleRepository = mock(DeploymentProviderResourceHandleRepository.class);
        PlatformConsumerRepository consumerRepository = mock(PlatformConsumerRepository.class);
        DeploymentAccessService deploymentAccessService = mock(DeploymentAccessService.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        PlatformCustomerConsumerService consumerService = mock(PlatformCustomerConsumerService.class);
        PlatformAuditService auditService = mock(PlatformAuditService.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var candidates = mock(DeploymentPromotionCandidateService.class);
        var candidate = mapper.createObjectNode().put("stagingReleaseId", "rel-staging");
        when(candidates.resolve(any(), any(), any(), any(), any())).thenReturn(candidate);
        when(candidates.fingerprint(any())).thenReturn(DeploymentEvidenceFingerprint.hash(mapper, candidate));
        var commands = new DeploymentPromotionCommandService(mock(DeploymentPromotionCommandRepository.class), consumerRepository, mapper);
        var retirementScheduler = mock(AdmissionPromotionRetirementScheduler.class);
        DeploymentPracticalPromotionService service = new DeploymentPracticalPromotionService(
            deploymentRepository,
            versionRepository,
            releaseRepository,
            targetProfileRepository,
            resourceHandleRepository,
            consumerRepository,
            deploymentAccessService,
            deploymentService,
            consumerService,
            auditService,
            mapper,
            candidates,
            commands,
            mock(DeploymentTargetContextService.class),
            mock(CustomerSelfServicePromotionBoundary.class),
            retirementScheduler
        );
        return new TestFixture(
            service,
            deploymentRepository,
            versionRepository,
            releaseRepository,
            targetProfileRepository,
            resourceHandleRepository,
            consumerRepository,
            deploymentAccessService,
            consumerService,
            retirementScheduler
        );
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("cus-1");
        deployment.setTenantId("ten-1");
        deployment.setName("Demo Deployment");
        deployment.setEnvironmentName("staging");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("ACTIVE");
        deployment.setCreatedAt(Instant.now());
        deployment.setUpdatedAt(Instant.now());
        return deployment;
    }

    private DeploymentVersionEntity version(String id, String deploymentId) {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId(id);
        version.setDeploymentId(deploymentId);
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setPublishedAt(Instant.now());
        return version;
    }

    private DeploymentReleaseEntity release(String id,
                                            String deploymentId,
                                            String versionId,
                                            String targetProfileId,
                                            String status,
                                            String verificationStatus) {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId(id);
        release.setDeploymentId(deploymentId);
        release.setDeploymentVersionId(versionId);
        release.setTargetProfileId(targetProfileId);
        release.setStatus(status);
        release.setVerificationStatus(verificationStatus);
        release.setProvisioningStatus("ACTIVE");
        release.setProvisioningTarget("COOLIFY");
        release.setSourceArtifactId("artifact-1");
        release.setProvisioningDetailsJson("{\"runtimeBaseUrl\":\"https://runtime.example\"}");
        release.setCreatedAt(Instant.now());
        release.setAppliedAt(Instant.now());
        release.setUpdatedAt(Instant.now());
        return release;
    }

    private DeploymentTargetProfileEntity targetProfile(String id, String environmentName) {
        DeploymentTargetProfileEntity profile = new DeploymentTargetProfileEntity();
        profile.setId(id);
        profile.setName(id);
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setEnvironmentName(environmentName);
        profile.setActive(true);
        profile.setCreatedAt(Instant.now());
        profile.setUpdatedAt(Instant.now());
        return profile;
    }

    private PlatformConsumerEntity consumer(String consumerId, String customerId, String deploymentId, String releaseId) {
        PlatformConsumerEntity consumer = new PlatformConsumerEntity();
        consumer.setId("con-1");
        consumer.setConsumerId(consumerId);
        consumer.setCustomerId(customerId);
        consumer.setDisplayName("Production Consumer");
        consumer.setStatus("ACTIVE");
        consumer.setBoundDeploymentId(deploymentId);
        consumer.setBoundReleaseId(releaseId);
        consumer.setCreatedAt(Instant.now());
        consumer.setUpdatedAt(Instant.now());
        return consumer;
    }

    private DeploymentProviderResourceHandleEntity handle(String id,
                                                          String deploymentId,
                                                          String releaseId,
                                                          String targetProfileId,
                                                          String status) {
        DeploymentProviderResourceHandleEntity handle = new DeploymentProviderResourceHandleEntity();
        handle.setId(id);
        handle.setDeploymentId(deploymentId);
        handle.setReleaseId(releaseId);
        handle.setTargetProfileId(targetProfileId);
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setResourceKind("RUNTIME");
        handle.setProviderResourceUuid(id + "-uuid");
        handle.setStatus(status);
        handle.setMetadataJson("{}");
        handle.setCreatedAt(Instant.now());
        handle.setUpdatedAt(Instant.now());
        return handle;
    }

    private record TestFixture(
        DeploymentPracticalPromotionService service,
        DeploymentRepository deploymentRepository,
        DeploymentVersionRepository versionRepository,
        DeploymentReleaseRepository releaseRepository,
        DeploymentTargetProfileRepository targetProfileRepository,
        DeploymentProviderResourceHandleRepository resourceHandleRepository,
        PlatformConsumerRepository consumerRepository,
        DeploymentAccessService deploymentAccessService,
        PlatformCustomerConsumerService consumerService,
        AdmissionPromotionRetirementScheduler retirementScheduler
    ) {
    }
}
