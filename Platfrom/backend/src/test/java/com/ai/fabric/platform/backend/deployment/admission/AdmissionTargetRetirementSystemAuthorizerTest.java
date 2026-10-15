package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AdmissionTargetRetirementSystemAuthorizerTest {
    private final AdmissionStore admissions = mock(AdmissionStore.class);
    private final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
    private final AdmissionTargetRetirementSystemAuthorizer authorizer =
        new AdmissionTargetRetirementSystemAuthorizer(admissions, operations,
            Clock.fixed(AdmissionFixture.NOW, ZoneOffset.UTC));

    @Test
    void exactClaimedAdmissionExpiryAuthorizesTheSystemR3Operation() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.PLANNED, Step.DRAIN);

        assertThat(authorizer.blocker(context(reservation, "deployment", "generation-1"))).isNull();
    }

    @Test
    void changedDeploymentOrGenerationCannotBorrowAdmissionAuthority() {
        Reservation reservation = reservation(ExpiryState.CLAIMED, null);
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.PLANNED, Step.DRAIN);

        assertThat(authorizer.blocker(context(reservation, "foreign-deployment", "generation-1")))
            .isEqualTo("RETIREMENT_ACTOR_REVOKED");
        assertThat(authorizer.blocker(context(reservation, "deployment", "other-generation")))
            .isEqualTo("RETIREMENT_ACTOR_REVOKED");
    }

    @Test
    void destructiveStepsRemainBlockedUntilTheAdmissionRecoveryWindowElapses() {
        Reservation reservation = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.RUNNING, Step.AUTHORITY);

        assertThat(authorizer.blocker(context(reservation, "deployment", "generation-1")))
            .isEqualTo("RECOVERY_WINDOW_NOT_ELAPSED");
    }

    @Test
    void destructiveStepsBecomeAuthorizedAfterTheExactRecoveryDeadline() {
        Reservation reservation = reservation(ExpiryState.HANDED_OFF, "retirement-1");
        when(admissions.require(reservation.id())).thenReturn(reservation);
        operation(TargetRetirementContracts.State.RUNNING, Step.AUTHORITY);
        var elapsed = new AdmissionTargetRetirementSystemAuthorizer(admissions, operations,
            Clock.fixed(AdmissionFixture.NOW.plusSeconds(20), ZoneOffset.UTC));

        assertThat(elapsed.blocker(context(reservation, "deployment", "generation-1"))).isNull();
    }

    private void operation(TargetRetirementContracts.State state, Step step) {
        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement-1");
        operation.setState(state);
        operation.setStep(step);
        when(operations.findById("retirement-1")).thenReturn(java.util.Optional.of(operation));
        FleetPolicy policy = mock(FleetPolicy.class);
        when(policy.restoreEvidenceReference()).thenReturn("restore-proof");
        when(admissions.reviewed("POLICY", "policy1", FleetPolicy.class)).thenReturn(policy);
    }

    private Context context(Reservation reservation, String deploymentId, String generation) {
        Snapshot snapshot = new Snapshot(reservation.customerId(), reservation.tenantId(), deploymentId,
            reservation.targetProfileId(), "target-generation", 1, List.of(), List.of(), List.of());
        return new Context("retirement-1", "admission-expiry:" + reservation.id() + ":" + generation,
            "fingerprint", snapshot, 0, null, "system", "SYSTEM", reservation.drainDeadline(),
            new Retention(RetentionDecision.RECLAIM, null, null, "restore-proof"));
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
