package com.ai.fabric.platform.backend.deployment.admission;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ManagedDependencyAdmissionLedgerTest extends AdmissionFixture {
    private static final String SPEC = "a".repeat(64);
    private static final String BINDING = "b".repeat(64);
    private ManagedDependencyAdmissionLedger ledger;
    private ManagedDependencyAdmissionLedger.Requirement requirement;

    @BeforeEach
    void setUpLedger() {
        approve(20_000_000_000L);
        Vector demand = with(with(with(with(with(vector(1), Dimension.CPU_MILLICORES, 1_000),
            Dimension.MEMORY_BYTES, 1024L * 1024L * 1024L), Dimension.DISK_BYTES,
            4L * 1024L * 1024L * 1024L), Dimension.DATABASES, 2), Dimension.DEPENDENCIES, 4);
        Manifest manifest = new Manifest("managed", "deployment1", "version", "config-hash", TARGET,
            BINDING, "reviewed-managed-topology", demand,
            Set.of("managed-dependency-spec:" + SPEC, "immutable-artifacts"));
        store.approveManifest(manifest, "operator", NOW);
        ledger = new ManagedDependencyAdmissionLedger(store, json, Clock.fixed(NOW, ZoneOffset.UTC));
        requirement = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", BINDING, SPEC, 250, 512L * 1024L * 1024L,
            2L * 1024L * 1024L * 1024L, 1, 3);
    }

    @Test
    void bindsExactlyOneReviewedParentReservationAndReplaysWithoutAllocatingCapacity() {
        Reservation reservation = reserve("managed", "apply");

        var first = ledger.require(requirement, null);
        var replay = ledger.require(requirement, reservation.id());

        assertThat(replay).isEqualTo(first);
        assertThat(first.reservationId()).isEqualTo(reservation.id());
        assertThat(first.bindingFingerprint()).isEqualTo(BINDING);
        assertThat(store.held()).hasSize(1);
    }

    @Test
    void ambiguityExpiryFingerprintAndEnvelopeChangesFailClosed() {
        Reservation reservation = reserve("managed", "apply");
        AdmissionStore ambiguous = mock(AdmissionStore.class);
        when(ambiguous.held()).thenReturn(List.of(reservation, reservation));
        var ambiguousLedger = new ManagedDependencyAdmissionLedger(ambiguous, json,
            Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> ambiguousLedger.require(requirement, null))
            .hasMessageContaining("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");

        var expired = new ManagedDependencyAdmissionLedger(store, json,
            Clock.fixed(NOW.plusSeconds(1_001), ZoneOffset.UTC));
        assertThatThrownBy(() -> expired.require(requirement, reservation.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_INVALID");

        var changedBinding = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", "c".repeat(64), SPEC, 250, 1, 1, 1, 1);
        assertThatThrownBy(() -> ledger.require(changedBinding, reservation.id()))
            .hasMessageContaining("DEPENDENCY_ADMISSION_FINGERPRINT_CHANGED");

        var tooLarge = new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1",
            TARGET, "version", BINDING, SPEC, 1_001, 2L * 1024L * 1024L * 1024L,
            5L * 1024L * 1024L * 1024L, 3, 5);
        assertThatThrownBy(() -> ledger.require(tooLarge, reservation.id()))
            .hasMessageContaining("DEPENDENCY_CAPACITY_NOT_RESERVED");
    }

    @Test
    void crossCustomerDeploymentAndTargetCannotSelectTheReservation() {
        Reservation reservation = reserve("managed", "apply");
        for (var foreign : List.of(
            new ManagedDependencyAdmissionLedger.Requirement("foreign", "tenant", "deployment1", TARGET,
                "version", BINDING, SPEC, 1, 1, 1, 1, 1),
            new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "foreign", TARGET,
                "version", BINDING, SPEC, 1, 1, 1, 1, 1),
            new ManagedDependencyAdmissionLedger.Requirement("customer", "tenant", "deployment1", "foreign",
                "version", BINDING, SPEC, 1, 1, 1, 1, 1))) {
            assertThatThrownBy(() -> ledger.require(foreign, reservation.id()))
                .hasMessageContaining("DEPENDENCY_ADMISSION_AMBIGUOUS_OR_MISSING");
        }
    }

    @Test
    void concurrentReadbackReturnsOneStableReservationGeneration() throws Exception {
        Reservation reservation = reserve("managed", "apply");
        CountDownLatch start = new CountDownLatch(1);
        List<ManagedDependencyAdmissionLedger.Evidence> results =
            java.util.Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = java.util.stream.IntStream.range(0, 4).mapToObj(index -> executor.submit(() -> {
                start.await();
                results.add(ledger.require(requirement, reservation.id()));
                return null;
            })).toList();
            start.countDown();
            for (var future : futures) future.get();
        }
        assertThat(results).hasSize(4).allMatch(value -> value.reservationId().equals(reservation.id())
            && value.generation().equals(reservation.generation()));
        assertThat(store.held()).hasSize(1);
    }
}
