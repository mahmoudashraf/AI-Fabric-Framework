package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentCuratedModuleRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromptRevisionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.lifecycle.DeploymentTargetMutationService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DeploymentServiceRuntimeAuthorityFreezeTest {

    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentDraftRepository drafts = mock(DeploymentDraftRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentProvisioningService provisioning = mock(DeploymentProvisioningService.class);
    private final DeploymentReleaseExecutionService execution = mock(DeploymentReleaseExecutionService.class);
    private final DeploymentRuntimeAuthorityService authorities = mock(DeploymentRuntimeAuthorityService.class);
    private final CustomerOfferDeploymentRepository offerDeployments = mock(CustomerOfferDeploymentRepository.class);
    private final DeploymentTargetMutationService targetMutations = mock(DeploymentTargetMutationService.class);
    private final DeploymentOperationsService operations = mock(DeploymentOperationsService.class);
    private final com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary offerBoundary =
        mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);
    private final DeploymentAccessService access = mock(DeploymentAccessService.class);
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentTargetProfileEntity target;
    private DeploymentService service;

    @BeforeEach
    void setUp() {
        deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setName("Reviewed deployment");
        deployment.setTemplateId("reviewed-template");
        deployment.setEnvironmentName("staging");
        deployment.setBehaviorType("CONVERSATIONAL");
        deployment.setStatus("DRAFT");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setCreatedAt(Instant.parse("2026-10-14T08:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-10-14T08:00:00Z"));

        version = new DeploymentVersionEntity();
        version.setId("version-1");
        version.setDeploymentId(deployment.getId());
        version.setStatus("PUBLISHED");
        version.setConfigHash("immutable-config");
        version.setBehaviorConfigJson(DeploymentVersionEntity.DEFAULT_BEHAVIOR_CONFIG_JSON);
        version.setMarketplaceDatasetConfigJson(DeploymentVersionEntity.DEFAULT_MARKETPLACE_DATASET_CONFIG_JSON);

        target = new DeploymentTargetProfileEntity();
        target.setId("target-staging");
        target.setProviderType(DeploymentProviderType.COOLIFY);
        target.setEnvironmentName("staging");
        target.setSourceStrategy("IMAGE_SOURCE");
        target.setResourceDefaultsJson("{}");
        target.setActive(true);

        when(deployments.findByIdForUpdate(deployment.getId())).thenReturn(Optional.of(deployment));
        when(versions.findById(version.getId())).thenReturn(Optional.of(version));
        when(provisioning.selectedTargetProfile(target.getId())).thenReturn(target);
        when(releases.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(releases.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service = service();
        ReflectionTestUtils.setField(service, "targetMutations", targetMutations);
        ReflectionTestUtils.setField(service, "deploymentOperations", operations);
        ReflectionTestUtils.setField(service, "offerBoundary", offerBoundary);
    }

    @Test
    void productizedApplyFreezesExactTargetVersionOriginsAndReleaseBeforeExecution() {
        version.setSecurityConfigJson("""
            {"authzMode":"ALLOW_VERIFIED","publicRuntimeBootstrapEnabled":true,
             "corsAllowedOrigins":"https://workspace.example.com, https://admin.example.com"}
            """);
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);
        RuntimeAuthorityBinding.Snapshot candidate = new RuntimeAuthorityBinding.Snapshot(
            "authority-candidate", null, "authority-fingerprint", null
        );
        when(authorities.freezeCandidateForAuthorizedApply(
            deployment.getId(), target.getId(), version.getId(),
            List.of("https://workspace.example.com", "https://admin.example.com")
        )).thenReturn(candidate);
        when(authorities.freezeReleaseForAuthorizedApply(
            eq(deployment.getId()), eq(target.getId()), anyString(),
            eq(candidate.snapshotId()), eq(candidate.fingerprint())
        )).thenAnswer(invocation -> new RuntimeAuthorityBinding.Snapshot(
            "authority-release", invocation.getArgument(2), candidate.fingerprint(), null
        ));

        service.applyVersion(deployment.getId(), version.getId(), null, target.getId(), null);

        var release = org.mockito.ArgumentCaptor.forClass(DeploymentReleaseEntity.class);
        verify(releases).save(release.capture());
        assertThat(release.getValue().getTargetProfileId()).isEqualTo(target.getId());
        assertThat(release.getValue().getDeploymentVersionId()).isEqualTo(version.getId());
        assertThat(release.getValue().getRuntimeAuthoritySnapshotId()).isEqualTo("authority-release");
        assertThat(release.getValue().getRuntimeAuthorityFingerprint()).isEqualTo(candidate.fingerprint());
        verify(authorities).freezeReleaseForAuthorizedApply(
            deployment.getId(), target.getId(), release.getValue().getId(),
            candidate.snapshotId(), candidate.fingerprint()
        );
        verify(operations).authorize(deployment, version, release.getValue(), null);
        verify(access).requireDeploymentOperatorAccess(deployment);
        var order = inOrder(authorities, releases, execution);
        order.verify(authorities).freezeCandidateForAuthorizedApply(anyString(), anyString(), anyString(), any());
        order.verify(releases).saveAndFlush(any(DeploymentReleaseEntity.class));
        order.verify(authorities).freezeReleaseForAuthorizedApply(anyString(), anyString(), anyString(), anyString(), anyString());
        order.verify(releases).save(any(DeploymentReleaseEntity.class));
        order.verify(execution).executeApply(deployment.getId(), version.getId(), release.getValue().getId());
    }

    @Test
    void reviewedCompositionDriftFailsBeforeAuthorityFreezeOrReleasePersistence() {
        var approved = mock(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization.class);
        when(offerBoundary.require(eq(deployment), eq(target.getId()), org.mockito.ArgumentMatchers.isNull(),
            eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY),
            eq("reviewed-apply"), org.mockito.ArgumentMatchers.anyMap(),
            org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull()))
            .thenReturn(approved);
        org.mockito.Mockito.doThrow(
            com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.deny(
                com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.REVIEWED_COMPOSITION_CHANGED))
            .when(offerBoundary).requireVersion(approved, version);

        assertThatThrownBy(() -> service.applyVersion(deployment.getId(), version.getId(), null,
            target.getId(), "reviewed-artifact", "reviewed-apply"))
            .hasMessageContaining("REVIEWED_COMPOSITION_CHANGED");
        verifyNoInteractions(authorities, releases, execution, operations, targetMutations);
    }

    @Test
    void missingCoreAuthorizationServiceCannotDispatchARelease() {
        version.setSecurityConfigJson("{\"authzMode\":\"DENY_ALL\",\"publicRuntimeBootstrapEnabled\":false}");
        ReflectionTestUtils.setField(service, "deploymentOperations", null);
        assertThatThrownBy(() -> service.applyVersion(deployment.getId(), version.getId(), null, target.getId(), null))
            .hasMessageContaining("Core release authorization is required.");
        verifyNoInteractions(execution);
    }

    @Test
    void genericApplyCannotQueueEffectsWhenTheReviewedOfferRejectsTheTarget() {
        var boundary = mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary.class);
        ReflectionTestUtils.setField(service, "offerBoundary", boundary);
        org.mockito.Mockito.doThrow(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException
            .deny(com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException.Code.UNENTITLED_TARGET))
            .when(boundary).require(org.mockito.ArgumentMatchers.eq(deployment), org.mockito.ArgumentMatchers.eq(target.getId()),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq(com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation.APPLY),
                org.mockito.ArgumentMatchers.eq("apply-operation"), org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
        assertThatThrownBy(() -> service.applyVersion(deployment.getId(), version.getId(), null,
            target.getId(), "reviewed-artifact", "apply-operation")).hasMessageContaining("UNENTITLED_TARGET");
        verify(releases, never()).save(any());
        verify(releases, never()).saveAndFlush(any());
        verifyNoInteractions(authorities, execution);
    }

    @Test
    void reviewedOfferDeploymentRejectsGenericDraftMutationAndPublicationRoutes() {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setId("draft-1");
        draft.setDeploymentId(deployment.getId());
        when(drafts.findById(draft.getId())).thenReturn(Optional.of(draft));
        when(drafts.findDeploymentIdById(draft.getId())).thenReturn(Optional.of(deployment.getId()));
        when(drafts.findByIdForUpdate(draft.getId())).thenReturn(Optional.of(draft));
        when(deployments.findById(deployment.getId())).thenReturn(Optional.of(deployment));
        when(access.requireDeploymentEditorAccess(deployment)).thenReturn(deployment);
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.updateDraft(draft.getId(), new UpdateDeploymentDraftRequest(
            null, null, null, null, null, null, null, null, null, null
        ))).isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.publishDraft(draft.getId()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.restorePromptRevision(deployment.getId(), "revision-1"))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.applyCuratedModuleToDraft(
            deployment.getId(), new UpdateDeploymentCuratedModuleRequest("curated-module")))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void productizedApplyFailsClosedWhenPublishedOriginsAreAbsent() {
        version.setSecurityConfigJson("{\"authzMode\":\"ALLOW_VERIFIED\",\"publicRuntimeBootstrapEnabled\":true}");
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);

        assertThatThrownBy(() -> service.applyVersion(deployment.getId(), version.getId(), null, target.getId(), null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                var response = (ResponseStatusException) error;
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(response.getReason()).contains("reviewed exact HTTPS origins");
            });

        verifyNoInteractions(authorities);
        verify(releases, never()).save(any());
        verify(releases, never()).saveAndFlush(any());
        verifyNoInteractions(execution);
    }

    @Test
    void publicRuntimeApplyFailsClosedWhenPublishedOriginsAreAbsent() {
        version.setSecurityConfigJson("{\"authzMode\":\"ALLOW_VERIFIED\",\"publicRuntimeBootstrapEnabled\":true}");
        when(offerDeployments.existsById(deployment.getId())).thenReturn(false);

        assertThatThrownBy(() -> service.applyVersion(deployment.getId(), version.getId(), null, target.getId(), null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> {
                var response = (ResponseStatusException) error;
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(response.getReason()).contains("reviewed exact HTTPS origins");
            });

        verifyNoInteractions(authorities);
        verify(releases, never()).save(any());
        verify(releases, never()).saveAndFlush(any());
        verifyNoInteractions(execution);
    }

    @Test
    void invalidPublishedOriginFailsBeforeReleaseOrAsyncExecution() {
        version.setSecurityConfigJson("""
            {"authzMode":"ALLOW_VERIFIED","publicRuntimeBootstrapEnabled":true,
             "corsAllowedOrigins":"http://workspace.example.com"}
            """);
        when(offerDeployments.existsById(deployment.getId())).thenReturn(true);
        when(authorities.freezeCandidateForAuthorizedApply(
            deployment.getId(), target.getId(), version.getId(), List.of("http://workspace.example.com")
        )).thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target origins must be exact HTTPS origins."));

        assertThatThrownBy(() -> service.applyVersion(deployment.getId(), version.getId(), null, target.getId(), null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exact HTTPS origins");

        verify(releases, never()).save(any());
        verify(releases, never()).saveAndFlush(any());
        verifyNoInteractions(execution);
    }

    @Test
    void legacyReleaseWithoutProductAssociationOrPublicRuntimeHasNoAuthorityEvidence() {
        version.setSecurityConfigJson("{\"authzMode\":\"DENY_ALL\",\"publicRuntimeBootstrapEnabled\":false}");
        when(offerDeployments.existsById(deployment.getId())).thenReturn(false);

        service.applyVersion(deployment.getId(), version.getId(), null, target.getId(), null);

        var release = org.mockito.ArgumentCaptor.forClass(DeploymentReleaseEntity.class);
        verify(releases).save(release.capture());
        assertThat(release.getValue().getRuntimeAuthoritySnapshotId()).isNull();
        assertThat(release.getValue().getRuntimeAuthorityFingerprint()).isNull();
        verifyNoInteractions(authorities);
        verify(releases).saveAndFlush(release.getValue());
        verify(execution).executeApply(deployment.getId(), version.getId(), release.getValue().getId());
    }

    private DeploymentService service() {
        var provisioningProperties = provisioningProperties();
        return new DeploymentService(
            deployments,
            drafts,
            mock(DeploymentPromptRevisionRepository.class),
            versions,
            releases,
            mock(DeploymentVerificationRunRepository.class),
            mock(DeploymentConfigCompiler.class),
            mock(DeploymentDraftValidationService.class),
            provisioning,
            mock(RailwayProvisioningPlanService.class),
            mock(DeploymentReleaseVerificationService.class),
            execution,
            mock(DeploymentServiceConfigModelService.class),
            mock(DeploymentServiceNavigationService.class),
            mock(DeploymentProductionReadinessScorecardService.class),
            mock(DeploymentProviderConnectivityService.class),
            mock(DeploymentSecretUsageService.class),
            mock(DeploymentSecurityGovernanceService.class),
            mock(DeploymentSourceOfTruthService.class),
            new DeploymentSourceResolver(provisioningProperties),
            access,
            mock(DeploymentAssignmentService.class),
            mock(DeploymentOperationApprovalService.class),
            mock(DeploymentDeletionService.class),
            mock(DeploymentCuratedModuleCatalogService.class),
            mock(PlatformCustomerTenantService.class),
            mock(PlatformCustomerConsumerService.class),
            provisioningProperties,
            mock(PlatformAuditService.class),
            new com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService(mapper),
            new DeploymentBehaviorCatalogService(mapper),
            mock(DeploymentSourceArtifactService.class),
            new DeploymentSourceCapabilityManifestService(mapper),
            authorities,
            offerDeployments,
            mapper
        );
    }

    private PlatformProvisioningProperties provisioningProperties() {
        return new PlatformProvisioningProperties(
            "RAILWAY_STUB", null, null, "mahmoudashraf/AI-Fabric-Framework", "main", "dev",
            null, null, null, null, null, null, null, 32, null, null, false, false,
            60_000, Duration.ofSeconds(5), Duration.ofMinutes(10)
        );
    }
}
