package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.ExpiryState;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Intent;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Fails public assignment resolution closed as soon as customer staging expires. */
@Service
public class CustomerStagingAssignmentAccessGate {
    private static final Set<ExpiryState> FENCED = Set.of(
        ExpiryState.CLAIMED, ExpiryState.HANDED_OFF, ExpiryState.RELEASED
    );

    private final AdmissionStore store;
    private final Clock clock;

    @Autowired
    public CustomerStagingAssignmentAccessGate(AdmissionStore store) {
        this(store, Clock.systemUTC());
    }

    CustomerStagingAssignmentAccessGate(AdmissionStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public void requireAvailable(String customerId, String deploymentId, String targetProfileId) {
        if (customerId == null || deploymentId == null || targetProfileId == null) return;
        Instant now = clock.instant();
        var scoped = store.held(deploymentId, targetProfileId).stream()
            .filter(reservation -> reservation.intent() == Intent.CUSTOMER_STAGING)
            .filter(reservation -> customerId.equals(reservation.customerId()))
            .toList();
        var occupants = scoped.stream().filter(AdmissionContracts.Reservation::occupiesActiveSlot).toList();
        boolean expired = occupants.isEmpty()
            ? scoped.stream().anyMatch(reservation -> reservation.expiryState() == ExpiryState.SETTLED_RETAINED)
            : occupants.stream().anyMatch(reservation -> FENCED.contains(reservation.expiryState())
                || reservation.expiresAt() != null && !now.isBefore(reservation.expiresAt()));
        if (expired) {
            throw new ResponseStatusException(CONFLICT, "CUSTOMER_STAGING_ASSIGNMENT_EXPIRED");
        }
    }
}
