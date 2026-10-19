package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension.CPU_MILLICORES;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension.DATABASES;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension.MEMORY_BYTES;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CustomerAdmissionDatabaseCapacityVerifierTest {
    private final AdmissionStore store = mock(AdmissionStore.class);
    private final CustomerAdmissionDatabaseCapacityVerifier verifier =
        new CustomerAdmissionDatabaseCapacityVerifier(store);

    @Test
    void acceptsDatabaseCapacityCoveredBySingleDurableReservation() {
        var admitted = reservation(1, 1500, 2L * 1024 * 1024 * 1024);
        when(store.held("dep", "target")).thenReturn(List.of(admitted));

        verifier.requireCovered("dep", "target", 250, 512L * 1024 * 1024);
    }

    @Test
    void rejectsMissingDuplicateOrUndersizedAdmissionEvidence() {
        var undersized = reservation(1, 200, 256L * 1024 * 1024);
        when(store.held("dep", "target")).thenReturn(List.of(undersized));
        assertThatThrownBy(() -> verifier.requireCovered("dep", "target", 250, 512L * 1024 * 1024))
            .hasMessageContaining("CUSTOMER_RUNTIME_DATABASE_CAPACITY_NOT_ADMITTED");

        var first = reservation(1, 1500, 2L * 1024 * 1024 * 1024);
        var second = reservation(1, 1500, 2L * 1024 * 1024 * 1024);
        when(store.held("dep", "target")).thenReturn(List.of(first, second));
        assertThatThrownBy(() -> verifier.requireCovered("dep", "target", 250, 512L * 1024 * 1024))
            .hasMessageContaining("CUSTOMER_RUNTIME_DATABASE_CAPACITY_NOT_ADMITTED");
    }

    private AdmissionContracts.Reservation reservation(long databases, long cpu, long memory) {
        AdmissionContracts.Reservation reservation = mock(AdmissionContracts.Reservation.class);
        when(reservation.deploymentId()).thenReturn("dep");
        when(reservation.targetProfileId()).thenReturn("target");
        when(reservation.occupiesActiveSlot()).thenReturn(true);
        when(reservation.prerequisiteReadback()).thenReturn(mock(AdmissionContracts.PrerequisiteReadback.class));
        when(reservation.demand()).thenReturn(new AdmissionContracts.Vector(Map.of(
            DATABASES, databases,
            CPU_MILLICORES, cpu,
            MEMORY_BYTES, memory
        )));
        return reservation;
    }
}
