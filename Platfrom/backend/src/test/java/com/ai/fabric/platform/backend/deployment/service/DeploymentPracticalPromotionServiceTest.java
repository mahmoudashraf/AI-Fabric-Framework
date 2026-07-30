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
        when(fixture.releaseRepository.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deployment.getId(), "dtp-coolify-prod-staging"))
            .thenReturn(List.of(stagingRelease));
        when(fixture.versionRepository.findById(version.getId())).thenReturn(Optional.of(version));

        var summary = fixture.service.planPromotion(deployment.getId(), new DeploymentPracticalPromotionRequest(null, null, null, null));

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

        assertThatThrownBy(() -> fixture.service.activateProductionConsumer(
            deployment.getId(),
            new DeploymentPracticalPromotionActivationRequest(failedRelease.getId(), "customer-production", false, "activate")
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
        PlatformConsumerEntity consumer = consumer("customer-production", deployment.getCustomerId(), deployment.getId(), "rel-old");
        DeploymentProviderResourceHandleEntity productionHandle = handle("h-prod", deployment.getId(), productionRelease.getId(), "dtp-coolify-production", "ACTIVE");
        DeploymentProviderResourceHandleEntity previousHandle = handle("h-old", deployment.getId(), "rel-old", "dtp-coolify-production", "ACTIVE");
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
            Instant.now()
        );

        when(fixture.deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(fixture.deploymentAccessService.requireDeploymentOperatorAccess(deployment)).thenReturn(deployment);
        when(fixture.releaseRepository.findById(productionRelease.getId())).thenReturn(Optional.of(productionRelease));
        when(fixture.consumerRepository.findByCustomerIdAndConsumerIdIgnoreCase(deployment.getCustomerId(), consumer.getConsumerId()))
            .thenReturn(Optional.of(consumer));
        when(fixture.consumerService.updateBindingForTrustedPromotion(
            eq(deployment.getCustomerId()),
            eq(consumer.getConsumerId()),
            eq(deployment.getId()),
            eq(productionRelease.getId()),
            eq(productionRelease.getTargetProfileId()),
            eq("activate")
        )).thenReturn(updatedConsumer);
        when(fixture.resourceHandleRepository.findByReleaseIdOrderByUpdatedAtDesc(productionRelease.getId())).thenReturn(List.of(productionHandle));
        when(fixture.resourceHandleRepository.findByReleaseIdOrderByUpdatedAtDesc("rel-old")).thenReturn(List.of(previousHandle));
        when(fixture.resourceHandleRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var summary = fixture.service.activateProductionConsumer(
            deployment.getId(),
            new DeploymentPracticalPromotionActivationRequest(productionRelease.getId(), consumer.getConsumerId(), false, "activate")
        );

        assertThat(summary.status()).isEqualTo("PRODUCTION_CONSUMER_ACTIVATED");
        assertThat(summary.consumer().boundReleaseId()).isEqualTo(productionRelease.getId());
        assertThat(summary.resources()).extracting("status").contains("ACTIVE", "ROLLBACK_RESERVED");
        assertThat(productionHandle.getStatus()).isEqualTo("ACTIVE");
        assertThat(previousHandle.getStatus()).isEqualTo("ROLLBACK_RESERVED");
        verify(fixture.consumerService).updateBindingForTrustedPromotion(
            deployment.getCustomerId(),
            consumer.getConsumerId(),
            deployment.getId(),
            productionRelease.getId(),
            productionRelease.getTargetProfileId(),
            "activate"
        );
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
            new ObjectMapper()
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
            consumerService
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
        PlatformCustomerConsumerService consumerService
    ) {
    }
}
