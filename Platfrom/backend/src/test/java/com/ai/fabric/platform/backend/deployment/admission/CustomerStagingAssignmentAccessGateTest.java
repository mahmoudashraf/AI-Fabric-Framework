package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerStagingAssignmentAccessGateTest {
    private final AdmissionStore store = mock(AdmissionStore.class);
    private final CustomerStagingAssignmentAccessGate gate = new CustomerStagingAssignmentAccessGate(
        store, Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));

    @Test void externalOperationsDoesNotReadLegacyExpiryRecordsForAssignmentResolution() {
        org.springframework.test.util.ReflectionTestUtils.setField(gate, "operationsProperties",
            new com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsProperties());
        gate.requireAvailable("customer", "deployment", AdmissionFixture.TARGET);
        org.mockito.Mockito.verifyNoInteractions(store);
    }

    @Test
    void permitsAStillCurrentCustomerStagingAssignment() {
        when(store.held("deployment", AdmissionFixture.TARGET)).thenReturn(List.of(
            reservation(ExpiryState.CURRENT, AdmissionFixture.NOW.plusSeconds(60))));

        gate.requireAvailable("customer", "deployment", AdmissionFixture.TARGET);
    }

    @Test
    void noticeDeliveryFailureDoesNotFenceBeforeTheLeaseExpires() {
        when(store.held("deployment", AdmissionFixture.TARGET)).thenReturn(List.of(
            reservation(ExpiryState.BLOCKED, AdmissionFixture.NOW.plusSeconds(60))));

        gate.requireAvailable("customer", "deployment", AdmissionFixture.TARGET);
    }

    @Test
    void blocksAtTheExpiryBoundaryBeforeTheLifecycleWorkerRuns() {
        when(store.held("deployment", AdmissionFixture.TARGET)).thenReturn(List.of(
            reservation(ExpiryState.NOTIFIED, AdmissionFixture.NOW)));

        assertThatThrownBy(() -> gate.requireAvailable("customer", "deployment", AdmissionFixture.TARGET))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("CUSTOMER_STAGING_ASSIGNMENT_EXPIRED");
    }

    @Test
    void ignoresAnotherCustomersReservation() {
        Reservation other = reservation(ExpiryState.CLAIMED, AdmissionFixture.NOW.minusSeconds(1));
        other = new Reservation(other.id(), "other", other.tenantId(), other.deploymentId(), other.targetProfileId(),
            other.intent(), other.operationKey(), other.requestFingerprint(), other.generation(), other.revision(), other.state(),
            other.demand(), other.policyId(), other.manifestId(), other.manifestFingerprint(), other.bindingFingerprint(),
            other.createdAt(), other.updatedAt(), other.lastActivityAt(), other.expiresAt(), other.extensions(), other.keepUntil(),
            other.keepEntitlementReference(), other.expiryState(), other.noticeCycle(), other.nextRetryAt(), other.attempts(),
            other.workerToken(), other.drainDeadline(), other.recoveryUntil(), other.retirementOperationId(), other.evidenceReference(),
            other.failureCode(), other.customerContextFingerprint(), other.customerOperation(), other.prerequisiteReadback());
        when(store.held("deployment", AdmissionFixture.TARGET)).thenReturn(List.of(other));

        gate.requireAvailable("customer", "deployment", AdmissionFixture.TARGET);
    }

    private Reservation reservation(ExpiryState state, java.time.Instant expiresAt) {
        return new Reservation("reservation-1", "customer", "tenant", "deployment", AdmissionFixture.TARGET,
            Intent.CUSTOMER_STAGING, "operation", "request", "generation-1", 4, State.ACTIVE,
            AdmissionFixture.vector(1), "policy1", "manifest1", "manifest-fingerprint", "binding-fingerprint",
            AdmissionFixture.NOW.minusSeconds(2000), AdmissionFixture.NOW, AdmissionFixture.NOW.minusSeconds(1000),
            expiresAt, 0, null, null, state, "cycle-1", null, 1,
            "worker-1", AdmissionFixture.NOW.plusSeconds(10), AdmissionFixture.NOW.plusSeconds(20), null,
            "evidence", null, null, null, null);
    }
}
