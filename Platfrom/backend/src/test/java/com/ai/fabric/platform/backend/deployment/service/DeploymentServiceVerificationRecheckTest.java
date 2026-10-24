package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.behavior.DeploymentBehaviorCatalogService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPromptRevisionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.PublicApiDeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.security.service.PlatformCustomerAccessService;
import com.ai.fabric.platform.backend.tenant.entity.PlatformCustomerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformCustomerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeploymentServiceVerificationRecheckTest {
    private final DeploymentRepository deployments = mock(DeploymentRepository.class);
    private final DeploymentVersionRepository versions = mock(DeploymentVersionRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentVerificationRunRepository runs = mock(DeploymentVerificationRunRepository.class);
    private final DeploymentTargetProfileRepository profiles = mock(DeploymentTargetProfileRepository.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final DeploymentReleaseVerificationService verification = mock(DeploymentReleaseVerificationService.class);
    private final DeploymentReleaseExecutionService execution = mock(DeploymentReleaseExecutionService.class);
    private final CustomerSelfServiceOperationBoundary boundary = mock(CustomerSelfServiceOperationBoundary.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private DeploymentService service;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity staging;
    private DeploymentReleaseEntity production;
    private DeploymentTargetProfileEntity profile;
    private DeploymentProviderResourceHandleEntity handle;

    @BeforeEach
    void setUp() {
        var users = mock(PlatformUserRepository.class);
        var customers = mock(PlatformCustomerRepository.class);
        var access = new DeploymentAccessService(mock(DeploymentAssignmentRepository.class),
            mock(PublicApiDeploymentRepository.class), users, new PlatformCustomerAccessService(users, customers));
        var owner = new PlatformUserEntity();
        owner.setCustomerId("customer-1");
        owner.setRole("CUSTOMER_ADMIN");
        owner.setStatus("ACTIVE");
        var customer = new PlatformCustomerEntity();
        customer.setId("customer-1");
        customer.setStatus("ACTIVE");
        when(users.findByEmailIgnoreCase("owner@example.test")).thenReturn(Optional.of(owner));
        when(customers.findById("customer-1")).thenReturn(Optional.of(customer));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("owner@example.test", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION"),
            null, List.of()));

        service = new DeploymentService(deployments, mock(DeploymentDraftRepository.class),
            mock(DeploymentPromptRevisionRepository.class), versions, releases, runs,
            mock(DeploymentConfigCompiler.class), mock(DeploymentDraftValidationService.class),
            mock(DeploymentProvisioningService.class), mock(RailwayProvisioningPlanService.class),
            verification, execution, mock(DeploymentServiceConfigModelService.class),
            mock(DeploymentServiceNavigationService.class), mock(DeploymentProductionReadinessScorecardService.class),
            mock(DeploymentProviderConnectivityService.class), mock(DeploymentSecretUsageService.class),
            mock(DeploymentSecurityGovernanceService.class), mock(DeploymentSourceOfTruthService.class),
            mock(DeploymentSourceResolver.class), access, mock(DeploymentAssignmentService.class),
            mock(DeploymentOperationApprovalService.class), mock(DeploymentDeletionService.class),
            mock(DeploymentCuratedModuleCatalogService.class), mock(PlatformCustomerTenantService.class),
            mock(PlatformCustomerConsumerService.class), mock(PlatformProvisioningProperties.class), audit,
            mock(EntityConfigContractService.class), mock(DeploymentBehaviorCatalogService.class),
            mock(DeploymentSourceArtifactService.class), mock(DeploymentSourceCapabilityManifestService.class),
            mock(DeploymentRuntimeAuthorityService.class), mock(CustomerOfferDeploymentRepository.class), mapper);
        var contexts = new DeploymentTargetContextService(deployments, releases, versions, profiles, handles,
            access, new DeploymentReleaseEndpointService(mapper), mapper, mock(DeploymentDocumentBindingResolver.class));
        ReflectionTestUtils.setField(service, "deploymentTargetContexts", contexts);
        ReflectionTestUtils.setField(service, "offerBoundary", boundary);

        deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setCustomerId("customer-1");
        deployment.setTenantId("tenant-1");
        deployment.setActiveVersionId("ver-production");
        deployment.setStatus("VERIFICATION_FAILED");
        deployment.setRuntimeBaseUrl("https://production.example.test");
        deployment.setConnectorBaseUrl("https://production-connector.example.test");
        deployment.setUpdatedAt(Instant.parse("2026-10-22T21:54:00Z"));
        version = new DeploymentVersionEntity();
        version.setId("ver-staging");
        version.setDeploymentId("dep-1");
        version.setConfigHash("immutable-staging-config");
        staging = new DeploymentReleaseEntity();
        staging.setId("rel-staging");
        staging.setDeploymentId("dep-1");
        staging.setDeploymentVersionId("ver-staging");
        staging.setTargetProfileId("target-staging");
        staging.setProviderType(DeploymentProviderType.COOLIFY);
        staging.setProviderResourceHandleId("handle-staging");
        staging.setSourceArtifactId("artifact-staging");
        staging.setStatus("APPLIED_VERIFIED");
        staging.setProvisioningStatus("ACTIVE");
        staging.setVerificationRunId("vrf-old-staging");
        staging.setProvisioningDetailsJson("{\"runtimeFqdn\":\"https://staging.example.test\","
            + "\"connectorFqdn\":\"https://staging-connector.example.test\",\"runtimeApplicationUuid\":\"app-staging\"}");
        production = new DeploymentReleaseEntity();
        production.setId("rel-production");
        production.setDeploymentId("dep-1");
        production.setDeploymentVersionId("ver-production");
        production.setTargetProfileId("target-production");
        production.setStatus("FAILED");
        production.setVerificationRunId("vrf-production");
        profile = new DeploymentTargetProfileEntity();
        profile.setId("target-staging");
        profile.setActive(true);
        profile.setProviderType(DeploymentProviderType.COOLIFY);
        profile.setProviderConfigJson("{\"serverUuid\":\"server-staging\"}");
        handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("handle-staging");
        handle.setDeploymentId("dep-1");
        handle.setTargetProfileId("target-staging");
        handle.setReleaseId("rel-staging");
        handle.setProviderType(DeploymentProviderType.COOLIFY);
        handle.setStatus("ACTIVE");
        handle.setResourceKind("APPLICATION");
        handle.setProviderServerUuid("server-staging");
        handle.setProviderResourceUuid("app-staging");
        when(deployments.findById("dep-1")).thenReturn(Optional.of(deployment));
        when(releases.findById("rel-staging")).thenReturn(Optional.of(staging));
        when(releases.findTopByDeploymentIdOrderByCreatedAtDesc("dep-1")).thenReturn(Optional.of(production));
        when(versions.findById("ver-staging")).thenReturn(Optional.of(version));
        when(profiles.findById("target-staging")).thenReturn(Optional.of(profile));
        when(handles.findById("handle-staging")).thenReturn(Optional.of(handle));
    }

    @AfterEach
    void clearPrincipal() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void legacyRecoveryIsExplicitlyRetiredForAnAuthorizedOperatorWithoutDispatch() {
        assertThatThrownBy(() -> service.reconcileLatestRelease("dep-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.GONE);
                assertThat(error.getReason()).contains("LEGACY_RELEASE_RECOVERY_RETIRED");
            });
        verifyNoInteractions(releases, versions, verification, execution, runs, boundary, audit);
    }

    @Test
    void legacyRecoveryStillRejectsAForeignCustomerBeforeRevealingRetirement() {
        deployment.setCustomerId("another-customer");
        assertThatThrownBy(() -> service.reconcileLatestRelease("dep-1"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(releases, versions, verification, execution, runs, boundary, audit);
    }

    @ParameterizedTest
    @CsvSource({"FAILED, PASSED", "APPLIED_VERIFIED, PASSED", "FAILED, FAILED", "APPLIED_VERIFIED, FAILED"})
    void rechecksExactStagingReleaseAfterProductionWithoutChangingGlobalProjection(String productionStatus, String result) {
        production.setStatus(productionStatus);
        String provisioningDetails = staging.getProvisioningDetailsJson();
        var run = verificationRun(staging, version, result);
        when(verification.verify(deployment, version, staging, "MANUAL_RERUN")).thenReturn(run);

        var summary = service.rerunVerification("dep-1", "rel-staging");

        assertThat(summary.releaseId()).isEqualTo("rel-staging");
        assertThat(summary.deploymentVersionId()).isEqualTo("ver-staging");
        assertThat(summary.deploymentId()).isEqualTo("dep-1");
        assertThat(summary.verificationType()).isEqualTo("MANUAL_RERUN");
        assertThat(summary.status()).isEqualTo(result);
        assertThat(summary.checks().get(0).path("status").asText()).isEqualTo(result);
        assertThat(staging.getVerificationRunId()).isEqualTo(run.getId());
        assertThat(staging.getVerificationStatus()).isEqualTo(result);
        assertThat(staging.getStatus()).isEqualTo("PASSED".equals(result) ? "APPLIED_VERIFIED" : "APPLIED_VERIFICATION_FAILED");
        assertThat(staging.getSourceArtifactId()).isEqualTo("artifact-staging");
        assertThat(staging.getTargetProfileId()).isEqualTo("target-staging");
        assertThat(staging.getDeploymentVersionId()).isEqualTo("ver-staging");
        assertThat(staging.getProvisioningDetailsJson()).isEqualTo(provisioningDetails);
        assertThat(version.getConfigHash()).isEqualTo("immutable-staging-config");
        assertThat(production.getStatus()).isEqualTo(productionStatus);
        assertThat(production.getVerificationRunId()).isEqualTo("vrf-production");
        assertThat(deployment.getStatus()).isEqualTo("VERIFICATION_FAILED");
        assertThat(deployment.getActiveVersionId()).isEqualTo("ver-production");
        assertThat(deployment.getRuntimeBaseUrl()).isEqualTo("https://production.example.test");
        assertThat(deployment.getConnectorBaseUrl()).isEqualTo("https://production-connector.example.test");
        assertThat(deployment.getUpdatedAt()).isEqualTo(Instant.parse("2026-10-22T21:54:00Z"));
        verify(runs).save(run);
        verify(releases).save(staging);
        verify(releases, never()).save(production);
        verify(deployments, never()).save(any());
        verify(verification).verify(deployment, version, staging, "MANUAL_RERUN");
        verifyNoMoreInteractions(verification);
        verify(versions, never()).findById("ver-production");
        verify(releases, never()).findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(anyString(), anyString());
        verify(boundary).require(eq(deployment), eq("target-staging"), eq("rel-staging"), eq(Operation.VERIFY),
            argThat(key -> key.startsWith("verification-recheck-") && key.length() <= 128), eq(Map.of()),
            isNull(), isNull(), isNull());
        verify(audit).record("VERIFICATION_RERUN_REQUESTED", "DEPLOYMENT_VERIFICATION_RUN", run.getId(),
            Map.of("deploymentId", "dep-1", "releaseId", "rel-staging", "versionId", "ver-staging"));
    }

    @Test
    void olderTargetRecheckCannotChangeGlobalStatusEvenWhenBothTargetsUseTheSameVersion() {
        deployment.setActiveVersionId(version.getId());
        production.setDeploymentVersionId(version.getId());
        when(verification.verify(deployment, version, staging, "MANUAL_RERUN"))
            .thenReturn(verificationRun(staging, version, "PASSED"));

        service.rerunVerification("dep-1", "rel-staging");

        assertThat(deployment.getStatus()).isEqualTo("VERIFICATION_FAILED");
        verify(deployments, never()).save(any());
    }

    @Test
    void explicitReleaseDoesNotRequireAnActiveGlobalVersion() {
        deployment.setActiveVersionId(null);
        when(verification.verify(deployment, version, staging, "MANUAL_RERUN"))
            .thenReturn(verificationRun(staging, version, "PASSED"));

        assertThat(service.rerunVerification("dep-1", "rel-staging").releaseId()).isEqualTo("rel-staging");
        verify(deployments, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PASSED", "FAILED"})
    void explicitCurrentReleaseStillUpdatesItsGlobalProjection(String result) {
        deployment.setActiveVersionId(version.getId());
        when(releases.findTopByDeploymentIdOrderByCreatedAtDesc("dep-1")).thenReturn(Optional.of(staging));
        when(verification.verify(deployment, version, staging, "MANUAL_RERUN"))
            .thenReturn(verificationRun(staging, version, result));

        service.rerunVerification("dep-1", "rel-staging");

        assertThat(deployment.getStatus()).isEqualTo("PASSED".equals(result) ? "ACTIVE" : "VERIFICATION_FAILED");
        verify(deployments).save(deployment);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void omittedReleaseRetainsLatestActiveVersionSelectionWithoutRecovery(boolean useOverload) {
        var activeVersion = new DeploymentVersionEntity();
        activeVersion.setId("ver-production");
        activeVersion.setDeploymentId("dep-1");
        when(versions.findById("ver-production")).thenReturn(Optional.of(activeVersion));
        when(releases.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-1", "ver-production"))
            .thenReturn(Optional.of(production));
        when(verification.verify(deployment, activeVersion, production, "MANUAL_RERUN"))
            .thenReturn(verificationRun(production, activeVersion, "PASSED"));

        var summary = useOverload ? service.rerunVerification("dep-1", null) : service.rerunVerification("dep-1");

        assertThat(summary.releaseId()).isEqualTo("rel-production");
        assertThat(summary.deploymentVersionId()).isEqualTo("ver-production");
        assertThat(deployment.getStatus()).isEqualTo("ACTIVE");
        verify(deployments).save(deployment);
        verifyNoInteractions(boundary, profiles, handles);
        verify(releases, never()).findById(anyString());
    }

    @Test
    void foreignCustomerIsRejectedBeforeReleaseLookupOrRecovery() {
        deployment.setCustomerId("another-customer");

        assertRejected(HttpStatus.NOT_FOUND);

        verifyNoInteractions(releases, versions, profiles, handles, boundary);
    }

    @Test
    void foreignReleaseIsNotRecheckedOrReplacedByLatest() {
        staging.setDeploymentId("another-deployment");

        assertRejected(HttpStatus.NOT_FOUND);

        verifyNoInteractions(versions, profiles, handles, boundary);
        verify(releases, never()).findTopByDeploymentIdOrderByCreatedAtDesc(anyString());
    }

    @Test
    void missingReleaseIsNotReplacedByLatest() {
        when(releases.findById("rel-staging")).thenReturn(Optional.empty());

        assertRejected(HttpStatus.NOT_FOUND);

        verifyNoInteractions(versions, profiles, handles, boundary);
        verify(releases, never()).findTopByDeploymentIdOrderByCreatedAtDesc(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign-version", "wrong-target", "wrong-generation", "wrong-provider", "inactive-target", "not-provisioned"})
    void owningTargetResolverRejectsMismatchedReleaseEvidence(String mismatch) {
        switch (mismatch) {
            case "foreign-version" -> version.setDeploymentId("another-deployment");
            case "wrong-target" -> handle.setTargetProfileId("target-production");
            case "wrong-generation" -> handle.setReleaseId("replacement-release");
            case "wrong-provider" -> staging.setProviderType(null);
            case "inactive-target" -> profile.setActive(false);
            case "not-provisioned" -> staging.setProvisioningStatus("FAILED");
            default -> throw new AssertionError(mismatch);
        }

        assertRejected(HttpStatus.CONFLICT);

        verifyNoInteractions(boundary);
    }

    @Test
    void reviewedOfferDenialPreventsFullVerificationAndPersistence() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Unentitled target"))
            .when(boundary).require(eq(deployment), eq("target-staging"), eq("rel-staging"), eq(Operation.VERIFY),
                anyString(), eq(Map.of()), isNull(), isNull(), isNull());

        assertRejected(HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING"})
    void explicitReleaseCannotBeRecheckedDuringApply(String status) {
        staging.setStatus(status);

        assertRejected(HttpStatus.CONFLICT);
    }

    @Test
    void executingApplyCannotBeRecheckedEvenAfterItsReleaseStatusChanged() {
        when(execution.isApplyExecuting("rel-staging")).thenReturn(true);

        assertRejected(HttpStatus.CONFLICT);
    }

    @Test
    void explicitlyEmptyOrOversizedReleaseNeverFallsBackToLegacySelection() {
        for (String releaseId : List.of("", " ", "r".repeat(65))) {
            assertThatThrownBy(() -> service.rerunVerification("dep-1", releaseId))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                    error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
        verifyNoInteractions(releases, versions, verification, runs, boundary, audit);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ARCHIVED", "QUEUED", "RUNNING"})
    void archivedOrDeletingDeploymentCannotRecheckAnExplicitRelease(String state) {
        if ("ARCHIVED".equals(state)) deployment.setStatus(state);
        else deployment.setDeletionStatus(state);

        assertRejected("ARCHIVED".equals(state) ? HttpStatus.BAD_REQUEST : HttpStatus.CONFLICT);

        verifyNoInteractions(releases, versions, boundary);
    }

    private void assertRejected(HttpStatus status) {
        assertThatThrownBy(() -> service.rerunVerification("dep-1", "rel-staging"))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(status));
        verifyNoInteractions(verification, runs, audit);
        verify(deployments, never()).save(any());
        verify(releases, never()).save(any());
    }

    private DeploymentVerificationRunEntity verificationRun(DeploymentReleaseEntity release,
                                                           DeploymentVersionEntity candidate, String status) {
        var run = new DeploymentVerificationRunEntity();
        run.setId("vrf-recheck");
        run.setDeploymentId(deployment.getId());
        run.setReleaseId(release.getId());
        run.setDeploymentVersionId(candidate.getId());
        run.setVerificationType("MANUAL_RERUN");
        run.setStatus(status);
        run.setChecksJson("[{\"name\":\"server-check\",\"status\":\"" + status + "\"}]");
        run.setCreatedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        return run;
    }
}
