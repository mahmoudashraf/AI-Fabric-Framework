package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class AdmissionLifecycleWorkerTest {
    private final AdmissionStore store = mock(AdmissionStore.class);
    private final ProductionAdmissionNoticeDeliverySource delivery = mock(ProductionAdmissionNoticeDeliverySource.class);
    private final AdmissionNoticeDeliverySource deliveryReadback = mock(AdmissionNoticeDeliverySource.class);
    private final AdmissionResourceReadbackSource resourceReadback = mock(AdmissionResourceReadbackSource.class);
    private final AdmissionRetirementHandoffService retirement = mock(AdmissionRetirementHandoffService.class);
    private final AdmissionLifecycleWorker worker = new AdmissionLifecycleWorker(store, delivery, deliveryReadback,
        resourceReadback, retirement, new AdmissionLifecycleProperties(true, Duration.ofSeconds(1),
        Duration.ofSeconds(1), 8, "approved-mail", true, true, "notices@loomai.test", true),
        Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));

    @Test
    void schedulerUsesThePersistedRoundRobinBatchInsteadOfTheGloballyFirstReservations() {
        Reservation claimed = reservation(ExpiryState.CLAIMED, null);
        when(store.lifecycleBatch(8)).thenReturn(java.util.List.of(claimed));
        when(store.require(claimed.id())).thenReturn(claimed);
        when(retirement.handoff(claimed)).thenReturn(claimed);

        worker.reconcile();

        verify(store).lifecycleBatch(8);
        verify(store, never()).held();
        verify(retirement).handoff(claimed);
    }

    @Test
    void restartResumesThePersistedClaimWithoutIssuingAnotherNoticeOrClaim() {
        Reservation claimed = reservation(ExpiryState.CLAIMED, null);
        when(store.require(claimed.id())).thenReturn(claimed);
        when(retirement.handoff(claimed)).thenReturn(claimed);

        worker.reconcileOne(claimed);

        verify(retirement).handoff(claimed);
        verifyNoInteractions(delivery, deliveryReadback, resourceReadback);
    }

    @Test
    void uncertainRemoteDeliveryBlocksUntilExplicitResolutionInsteadOfBlindRetry() {
        Reservation pending = reservation(ExpiryState.NOTICE_PENDING, null);
        FleetPolicy policy = mock(FleetPolicy.class);
        LifecyclePolicy lifecycle = mock(LifecyclePolicy.class);
        NoticeWork email = new NoticeWork(pending.fence(), pending.noticeCycle(), NoticeChannel.OWNER_EMAIL,
            pending.id() + ":" + pending.noticeCycle() + ":OWNER_EMAIL", pending.expiresAt(), "approved-mail");
        when(store.require(pending.id())).thenReturn(pending);
        when(store.reviewed("POLICY", pending.policyId(), FleetPolicy.class)).thenReturn(policy);
        when(policy.lifecycle()).thenReturn(lifecycle);
        when(lifecycle.noticeLeadSeconds()).thenReturn(100L);
        when(store.noticeWork(pending.fence(), "system:admission-lifecycle", AdmissionFixture.NOW))
            .thenReturn(java.util.List.of(email));
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.PRECONDITION_FAILED,
            "NOTICE_DELIVERY_OUTCOME_UNKNOWN")).when(delivery).deliver(pending, email);

        worker.reconcileSafely(pending);

        verify(store).noticeOutcomeUnknown(pending.fence(), "system:admission-lifecycle", AdmissionFixture.NOW);
        verify(store, never()).noticeFailed(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void completedR3UsesAuthoritativeReadbackBeforeCapacityRelease() {
        Reservation handedOff = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        FleetPolicy policy = mock(FleetPolicy.class);
        ResourceReadback evidence = mock(ResourceReadback.class);
        TargetRetirementContracts.Summary summary = mock(TargetRetirementContracts.Summary.class);
        when(summary.state()).thenReturn(TargetRetirementContracts.State.COMPLETE);
        when(store.require(handedOff.id())).thenReturn(handedOff);
        when(store.reviewed("POLICY", handedOff.policyId(), FleetPolicy.class)).thenReturn(policy);
        when(retirement.reconcile(handedOff)).thenReturn(summary);
        when(resourceReadback.read(new ReadbackContext(handedOff, policy, handedOff.workerToken()))).thenReturn(evidence);

        worker.reconcileOne(handedOff);

        verify(store).reconcile(handedOff.fence(), evidence, true, "system:admission-lifecycle", AdmissionFixture.NOW);
    }

    @Test
    void retainedR3SettlesResidualCapacityInsteadOfReleasingTheWholeReservation() {
        Reservation handedOff = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        FleetPolicy policy = mock(FleetPolicy.class);
        ResourceReadback evidence = mock(ResourceReadback.class);
        TargetRetirementContracts.Summary summary = mock(TargetRetirementContracts.Summary.class);
        when(summary.state()).thenReturn(TargetRetirementContracts.State.RETIRED_WITH_RETAINED_DATA);
        when(store.require(handedOff.id())).thenReturn(handedOff);
        when(store.reviewed("POLICY", handedOff.policyId(), FleetPolicy.class)).thenReturn(policy);
        when(retirement.reconcile(handedOff)).thenReturn(summary);
        when(resourceReadback.read(new ReadbackContext(handedOff, policy, handedOff.workerToken()))).thenReturn(evidence);
        worker.reconcileOne(handedOff);
        verify(store).settleRetained(handedOff.fence(), evidence, "system:admission-lifecycle", AdmissionFixture.NOW);
        verify(store, never()).reconcile(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void settledRetainedLiabilityDoesNotRestartExpiryWork() {
        Reservation retained = mock(Reservation.class);
        when(retained.id()).thenReturn("retained-reservation");
        when(retained.consumesCapacity()).thenReturn(true);
        when(retained.occupiesActiveSlot()).thenReturn(false);
        when(store.require(retained.id())).thenReturn(retained);
        worker.reconcileOne(retained);
        verifyNoInteractions(retirement, resourceReadback, delivery, deliveryReadback);
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
