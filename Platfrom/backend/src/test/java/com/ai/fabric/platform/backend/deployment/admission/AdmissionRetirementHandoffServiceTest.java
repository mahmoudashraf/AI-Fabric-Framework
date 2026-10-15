package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentProviderResourceHandleEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementParticipation;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementStore;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdmissionRetirementHandoffServiceTest {
    private final AdmissionStore admissions = mock(AdmissionStore.class);
    private final DeploymentProviderResourceHandleRepository handles = mock(DeploymentProviderResourceHandleRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final PlatformConsumerRepository consumers = mock(PlatformConsumerRepository.class);
    private final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
    private final TargetRetirementStore retirements = mock(TargetRetirementStore.class);
    private final TargetRetirementParticipation participation = mock(TargetRetirementParticipation.class);
    private final AdmissionLifecycleProperties properties = new AdmissionLifecycleProperties(true,
        Duration.ofSeconds(1), Duration.ofSeconds(1), 8, "approved-mail", true, true,
        "notices@loomai.test", true);
    private final AdmissionRetirementHandoffService service = new AdmissionRetirementHandoffService(
        admissions, handles, releases, consumers, operations, retirements, participation, properties,
        Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));

    @Test
    void activeRuntimePreDrainEvidenceQueuesTheOperationWithoutSuppressingOtherBlockers() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        var operation = operation(reservation, TargetRetirementContracts.State.PLANNED);
        var queued = operation(reservation, TargetRetirementContracts.State.QUEUED);
        var work = expiryWork(reservation);
        var context = mock(TargetRetirementContracts.Context.class);
        Reservation handedOff = reservation(ExpiryState.HANDED_OFF, operation.getId());
        var summary = mock(TargetRetirementContracts.Summary.class);
        var runtime = new TargetRetirementContracts.Evidence("fingerprint", 0, AdmissionFixture.NOW,
            "runtime-target-retirement:drain-not-started", false, false, false, true,
            null, null, null, null, false, List.of("DRAIN_NOT_STARTED"));
        when(summary.id()).thenReturn(operation.getId());
        when(admissions.expiryWork(reservation)).thenReturn(work);
        when(operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
            reservation.deploymentId(), reservation.targetProfileId(), operation.getOperationKey()))
            .thenReturn(Optional.of(operation));
        when(retirements.context(operation)).thenReturn(context);
        when(participation.inspect(context)).thenReturn(new TargetRetirementParticipation.Inspection(
            Map.of(TargetRetirementContracts.ParticipantKind.RUNTIME_DRAIN, runtime),
            mock(TargetRetirementContracts.Evidence.class), List.of("DRAIN_NOT_STARTED", "DRAIN_WORK_UNKNOWN",
            "PARTICIPANT_INVENTORY_UNPROVEN_RUNTIME_DRAIN")));
        when(admissions.handoff(org.mockito.ArgumentMatchers.eq(work), org.mockito.ArgumentMatchers.eq(operation.getId()),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(handedOff);
        when(retirements.execute(operation.getId(), new TargetRetirementContracts.Command(operation.getRequestFingerprint())))
            .thenReturn(summary);
        when(retirements.require(operation.getId())).thenReturn(queued);

        assertThat(service.handoff(reservation)).isSameAs(handedOff);
        verify(retirements).preview(operation.getId(), List.of());
        verify(retirements).execute(operation.getId(), new TargetRetirementContracts.Command(operation.getRequestFingerprint()));
    }

    @Test
    void plannedRetirementWithMissingParticipantsRemainsClaimedAndNeverExecutes() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        var operation = operation(reservation, TargetRetirementContracts.State.PLANNED);
        var work = expiryWork(reservation);
        var context = mock(TargetRetirementContracts.Context.class);
        when(admissions.expiryWork(reservation)).thenReturn(work);
        when(operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
            reservation.deploymentId(), reservation.targetProfileId(), operation.getOperationKey()))
            .thenReturn(Optional.of(operation));
        when(retirements.context(operation)).thenReturn(context);
        when(participation.inspect(context)).thenReturn(new TargetRetirementParticipation.Inspection(
            Map.of(), null, List.of("RETIREMENT_INTERLOCK_MISSING")));

        assertThat(service.handoff(reservation)).isSameAs(reservation);

        verify(retirements).preview(operation.getId(), List.of("RETIREMENT_INTERLOCK_MISSING"));
        verify(retirements, never()).execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(admissions, never()).handoff(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void restartAfterR3AcceptedPersistsTheOriginalOperationAsTheOnlyHandoff() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        var operation = operation(reservation, TargetRetirementContracts.State.QUEUED);
        var work = expiryWork(reservation);
        Reservation handedOff = reservation(ExpiryState.HANDED_OFF, operation.getId());
        when(admissions.expiryWork(reservation)).thenReturn(work);
        when(operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
            reservation.deploymentId(), reservation.targetProfileId(), operation.getOperationKey()))
            .thenReturn(Optional.of(operation));
        when(admissions.handoff(org.mockito.ArgumentMatchers.eq(work), org.mockito.ArgumentMatchers.eq(operation.getId()),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(handedOff);

        assertThat(service.handoff(reservation)).isSameAs(handedOff);
        verify(retirements, never()).plan(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any());
        verify(retirements, never()).execute(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void acceptedPlanPersistsAdmissionHandoffBeforeR3CanRunAsynchronously() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        var operation = operation(reservation, TargetRetirementContracts.State.PLANNED);
        var queued = operation(reservation, TargetRetirementContracts.State.QUEUED);
        var work = expiryWork(reservation);
        var context = mock(TargetRetirementContracts.Context.class);
        Reservation handedOff = reservation(ExpiryState.HANDED_OFF, operation.getId());
        var summary = mock(TargetRetirementContracts.Summary.class);
        when(summary.id()).thenReturn(operation.getId());
        when(admissions.expiryWork(reservation)).thenReturn(work);
        when(operations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
            reservation.deploymentId(), reservation.targetProfileId(), operation.getOperationKey()))
            .thenReturn(Optional.of(operation));
        when(retirements.context(operation)).thenReturn(context);
        when(participation.inspect(context)).thenReturn(new TargetRetirementParticipation.Inspection(
            Map.of(), mock(TargetRetirementContracts.Evidence.class), List.of()));
        when(admissions.handoff(org.mockito.ArgumentMatchers.eq(work), org.mockito.ArgumentMatchers.eq(operation.getId()),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(handedOff);
        when(retirements.execute(operation.getId(), new TargetRetirementContracts.Command(operation.getRequestFingerprint())))
            .thenReturn(summary);
        when(retirements.require(operation.getId())).thenReturn(queued);

        assertThat(service.handoff(reservation)).isSameAs(handedOff);

        var order = inOrder(admissions, retirements);
        order.verify(admissions).handoff(org.mockito.ArgumentMatchers.eq(work),
            org.mockito.ArgumentMatchers.eq(operation.getId()), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        order.verify(retirements).execute(operation.getId(),
            new TargetRetirementContracts.Command(operation.getRequestFingerprint()));
    }

    @Test
    void restartAfterDurableHandoffResumesAStillPlannedR3Operation() {
        Reservation reservation = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        var operation = operation(reservation, TargetRetirementContracts.State.PLANNED);
        var context = mock(TargetRetirementContracts.Context.class);
        var planned = mock(TargetRetirementContracts.Summary.class);
        var queued = mock(TargetRetirementContracts.Summary.class);
        when(planned.state()).thenReturn(TargetRetirementContracts.State.PLANNED);
        when(queued.state()).thenReturn(TargetRetirementContracts.State.QUEUED);
        when(operations.findById(operation.getId())).thenReturn(Optional.of(operation));
        when(retirements.summary(operation)).thenReturn(planned);
        when(retirements.context(operation)).thenReturn(context);
        when(participation.inspect(context)).thenReturn(new TargetRetirementParticipation.Inspection(
            Map.of(), mock(TargetRetirementContracts.Evidence.class), List.of()));
        when(retirements.execute(operation.getId(), new TargetRetirementContracts.Command(operation.getRequestFingerprint())))
            .thenReturn(queued);

        assertThat(service.reconcile(reservation)).isSameAs(queued);

        verify(retirements).execute(operation.getId(),
            new TargetRetirementContracts.Command(operation.getRequestFingerprint()));
    }

    @Test
    void recoveryWindowBlockDoesNotConsumeRetirementRetriesBeforeItsDeadline() {
        Reservation reservation = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        var operation = operation(reservation, TargetRetirementContracts.State.BLOCKED);
        var blocked = mock(TargetRetirementContracts.Summary.class);
        when(blocked.state()).thenReturn(TargetRetirementContracts.State.BLOCKED);
        when(blocked.errorCode()).thenReturn("RECOVERY_WINDOW_NOT_ELAPSED");
        when(blocked.inFlightEffect()).thenReturn(null);
        when(operations.findById(operation.getId())).thenReturn(Optional.of(operation));
        when(retirements.summary(operation)).thenReturn(blocked);

        assertThat(service.reconcile(reservation)).isSameAs(blocked);

        verify(retirements, never()).retry(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void postActivationProductionBindingIsCarriedAsRetirementRevisionEvidence() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        var work = expiryWork(reservation);
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("runtime-handle");
        handle.setDeploymentId(reservation.deploymentId());
        handle.setTargetProfileId(reservation.targetProfileId());
        handle.setReleaseId("staging-release");
        handle.setResourceKind("APPLICATION");
        var release = new DeploymentReleaseEntity();
        release.setId("staging-release");
        release.setDeploymentId(reservation.deploymentId());
        release.setTargetProfileId(reservation.targetProfileId());
        var productionConsumer = new PlatformConsumerEntity();
        productionConsumer.setId("consumer-entity");
        productionConsumer.setBoundDeploymentId(reservation.deploymentId());
        productionConsumer.setBoundTargetProfileId("production-target");
        productionConsumer.setBoundReleaseId("production-release");
        productionConsumer.setBindingRevision(12L);
        var operation = operation(reservation, TargetRetirementContracts.State.QUEUED);
        var planned = mock(TargetRetirementContracts.Summary.class);
        when(planned.id()).thenReturn(operation.getId());
        when(admissions.expiryWork(reservation)).thenReturn(work);
        when(handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
            reservation.deploymentId(), reservation.targetProfileId())).thenReturn(List.of(handle));
        when(releases.findById("staging-release")).thenReturn(Optional.of(release));
        when(consumers.findAll()).thenReturn(List.of(productionConsumer));
        when(admissions.reviewed("POLICY", reservation.policyId(), FleetPolicy.class)).thenReturn(
            new FleetPolicy("policy1", null, Map.of(), null, "restore-proof", null, null));
        when(retirements.plan(org.mockito.ArgumentMatchers.eq(reservation.deploymentId()),
            org.mockito.ArgumentMatchers.eq(reservation.targetProfileId()), org.mockito.ArgumentMatchers.any()))
            .thenReturn(planned);
        when(retirements.require(operation.getId())).thenReturn(operation);
        when(admissions.handoff(org.mockito.ArgumentMatchers.eq(work), org.mockito.ArgumentMatchers.eq(operation.getId()),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any()))
            .thenReturn(reservation(ExpiryState.HANDED_OFF, operation.getId()));

        service.handoff(reservation);

        ArgumentCaptor<TargetRetirementContracts.PlanRequest> request =
            ArgumentCaptor.forClass(TargetRetirementContracts.PlanRequest.class);
        verify(retirements).plan(org.mockito.ArgumentMatchers.eq(reservation.deploymentId()),
            org.mockito.ArgumentMatchers.eq(reservation.targetProfileId()), request.capture());
        assertThat(request.getValue().consumers()).containsExactly(
            new TargetRetirementContracts.ConsumerExpectation("consumer-entity", 12L));
    }

    private TargetRetirementOperationEntity operation(Reservation reservation, TargetRetirementContracts.State state) {
        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement-1");
        operation.setCustomerId(reservation.customerId());
        operation.setDeploymentId(reservation.deploymentId());
        operation.setTargetProfileId(reservation.targetProfileId());
        operation.setOperationKey("admission-expiry:" + reservation.id() + ":" + reservation.generation());
        operation.setRequestFingerprint("f".repeat(64));
        operation.setState(state);
        return operation;
    }

    private ExpiryWork expiryWork(Reservation reservation) {
        return new ExpiryWork(reservation.fence(), reservation.workerToken(), reservation.noticeCycle(),
            reservation.expiresAt(), reservation.drainDeadline(), reservation.recoveryUntil(), "STAGING_INACTIVITY_EXPIRED");
    }

    private Reservation reservation(ExpiryState state, String retirementId) {
        return new Reservation("reservation-1", "customer", "tenant", "deployment", AdmissionFixture.TARGET,
            Intent.CUSTOMER_STAGING, "operation", "request", "generation-1", 4, AdmissionContracts.State.ACTIVE,
            AdmissionFixture.vector(1), "policy1", "manifest1", "manifest-fingerprint", "binding-fingerprint",
            AdmissionFixture.NOW.minusSeconds(2000), AdmissionFixture.NOW, AdmissionFixture.NOW.minusSeconds(1000),
            AdmissionFixture.NOW.minusSeconds(1), 0, null, null, state, "cycle-1", null, 1,
            "worker-1", AdmissionFixture.NOW.plusSeconds(10), AdmissionFixture.NOW.plusSeconds(20), retirementId,
            "evidence", null, null, null, null);
    }
}
