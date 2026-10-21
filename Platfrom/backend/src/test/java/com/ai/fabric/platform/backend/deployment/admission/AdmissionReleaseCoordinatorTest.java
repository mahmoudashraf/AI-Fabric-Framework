package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdmissionReleaseCoordinatorTest extends AdmissionFixture {
    private CustomerOfferDeploymentRepository offers;
    private DeploymentReleaseRepository releases;
    private DeploymentVersionRepository versions;
    private AdmissionResourceReadbackSource readback;
    private AdmissionReleaseCoordinator coordinator;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private DeploymentReleaseEntity release;

    @BeforeEach
    void review() {
        approve(1); store.approveManifest(manifest("manifest", "deployment1"), "operator", NOW);
        offers = mock(CustomerOfferDeploymentRepository.class);
        releases = mock(DeploymentReleaseRepository.class); versions = mock(DeploymentVersionRepository.class);
        readback = mock(AdmissionResourceReadbackSource.class);
        coordinator = new AdmissionReleaseCoordinator(store, offers, releases, versions, c -> evidence(c, NOW), readback,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        deployment = new DeploymentEntity(); deployment.setId("deployment1"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        version = new DeploymentVersionEntity(); version.setId("version"); version.setDeploymentId("deployment1");
        version.setConfigHash("config-hash"); version.setStatus("PUBLISHED");
        release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment1");
        release.setDeploymentVersionId("version"); release.setTargetProfileId(TARGET); release.setStatus("APPLY_REQUESTED");
        when(offers.existsById("deployment1")).thenReturn(true);
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(releases.findByIdForUpdate("release")).thenReturn(Optional.of(release));
        when(versions.findById("version")).thenReturn(Optional.of(version));
        var c = context("manifest");
        var scope = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment1", null, TARGET,
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 1, "placement-proof",
            CustomerOfferContracts.Operation.APPLY, "apply", new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
        store.reserve(c, new Reserve("apply", "manifest", scope), evidence(c, NOW), "customer", NOW);
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> coordinator.bind(deployment, version, release));
    }

    @Test
    void durableReleaseProgressesOnlyAfterRealProviderReadbackAndReplaysAfterRestart() {
        coordinator.beginProvisioning("release");
        var provisioning = store.held().getFirst();
        assertThat(provisioning.state()).isEqualTo(State.PROVISIONING);
        release.setStatus("APPLIED_VERIFIED"); release.setProvisioningStatus("ACTIVE");
        when(readback.read(any())).thenReturn(readback(provisioning, NOW, false, 0L, 0L));
        coordinator.observe("release");
        assertThat(store.require(provisioning.id()).state()).isEqualTo(State.ACTIVE);
        long revision = store.require(provisioning.id()).revision();
        coordinator = new AdmissionReleaseCoordinator(store, offers, releases, versions, c -> evidence(c, NOW), readback,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));
        coordinator.observe("release");
        assertThat(store.require(provisioning.id()).revision()).isEqualTo(revision);
        verify(readback, times(1)).read(any());
    }

    @Test
    void failedOrInterruptedEffectsRetainCapacityUntilLateSuccessIsProved() {
        coordinator.beginProvisioning("release");
        release.setStatus("FAILED");
        coordinator.observe("release");
        var unknown = store.held().getFirst();
        assertThat(unknown.state()).isEqualTo(State.OUTCOME_UNKNOWN);
        assertThat(store.held()).hasSize(1);
        verifyNoInteractions(readback);
        release.setStatus("APPLIED_VERIFIED"); release.setProvisioningStatus("ACTIVE");
        when(readback.read(any())).thenReturn(readback(unknown, NOW, false, 0L, 0L));
        coordinator.observe("release");
        assertThat(store.held().getFirst().state()).isEqualTo(State.ACTIVE);
    }

    @Test void beginProvisioningRefreshesExecutionEvidenceAfterOriginalReadbackExpires() {
        var original = store.held().getFirst();
        var executionTime = NOW.plusSeconds(600);
        var prerequisites = mock(AdmissionEvidenceSource.class);
        when(prerequisites.readForExecution(any())).thenAnswer(call -> evidence(call.getArgument(0), executionTime));
        coordinator = new AdmissionReleaseCoordinator(store, offers, releases, versions, prerequisites, readback,
            java.time.Clock.fixed(executionTime, java.time.ZoneOffset.UTC));

        coordinator.beginProvisioning("release");

        var provisioning = store.require(original.id());
        assertThat(provisioning.state()).isEqualTo(State.PROVISIONING);
        assertThat(provisioning.generation()).isEqualTo(original.generation());
        assertThat(provisioning.chargedDemand()).isEqualTo(original.chargedDemand());
        assertThat(provisioning.prerequisiteReadback().observedAt()).isEqualTo(executionTime);
        assertThat(original.prerequisiteReadback().validUntil()).isBefore(executionTime);
        verify(prerequisites).readForExecution(any());
        verify(prerequisites, never()).read(any());
        verifyNoInteractions(readback);
    }

    @Test
    void reviewedVersionDriftBlocksEffectsAndTerminalReadback() {
        version.setConfigHash("changed");
        assertThatThrownBy(() -> coordinator.beginProvisioning("release")).hasMessageContaining("VERSION_CHANGED");
        assertThatThrownBy(() -> coordinator.observe("release")).hasMessageContaining("VERSION_CHANGED");
        assertThat(store.held().getFirst().state()).isEqualTo(State.RESERVED);
        verifyNoInteractions(readback);
    }

    @Test
    void failedPreflightKeepsItsAllocationReusableWithoutClaimingProviderSuccess() {
        release.setStatus("PRE_APPLY_BLOCKED"); release.setProvisioningStatus("BLOCKED");
        coordinator.observe("release");
        assertThat(store.held().getFirst().state()).isEqualTo(State.RESERVED);
        assertThat(store.releaseBinding("release").state()).isEqualTo("NO_EFFECT");
        assertThat(store.currentReleaseId(store.held().getFirst())).isNull();
        assertThatThrownBy(() -> coordinator.beginProvisioning("release")).hasMessageContaining("RELEASE_ALLOCATION_FENCED");
        verifyNoInteractions(readback);
    }

    @Test
    void reconciliationLogsReasonWithoutProviderPayloadAndKeepsCapacity() {
        coordinator.beginProvisioning("release");
        release.setStatus("APPLIED_VERIFIED"); release.setProvisioningStatus("ACTIVE");
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AdmissionReleaseCoordinator.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            when(readback.read(any())).thenThrow(new AdmissionFailure(
                org.springframework.http.HttpStatus.PRECONDITION_FAILED, "ACTIVE_PROVIDER_READBACK_UNAVAILABLE"));
            coordinator.reconcile();
            doThrow(new IllegalStateException("private-provider-payload")).when(readback).read(any());
            coordinator.reconcile();
            assertThat(appender.list).extracting(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .containsExactly(
                    "Admission release readback remains blocked for release: ACTIVE_PROVIDER_READBACK_UNAVAILABLE",
                    "Admission release readback remains blocked for release: IllegalStateException");
            assertThat(appender.list).allSatisfy(event -> assertThat(event.getThrowableProxy()).isNull());
            assertThat(store.held().getFirst().state()).isEqualTo(State.PROVISIONING);
            assertThat(store.releaseBinding("release").state()).isEqualTo("PROVISIONING");
        } finally {
            logger.detachAppender(appender); appender.stop();
        }
    }

    @Test
    void unsettledReadbackBatchAdvancesPastUnchangedOrUnavailableReleases() {
        var allocation = store.held().getFirst();
        for (int i = 0; i < 33; i++) jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
             (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,
              deployment_id,target_profile_id,state,created_at,updated_at)
            VALUES (?,?,?,?,'version','config-hash','customer','tenant','deployment1',?,'OUTCOME_UNKNOWN',?,?)
            """, "stuck-" + i, allocation.id(), allocation.generation(), "key-" + i, TARGET,
            java.sql.Timestamp.from(NOW.minusSeconds(10)), java.sql.Timestamp.from(NOW.minusSeconds(10)));
        var first = store.unsettledReleases(32);
        assertThat(first).hasSize(32);
        var next = store.unsettledReleases(32);
        assertThat(next).anyMatch(id -> !first.contains(id));
    }
}
