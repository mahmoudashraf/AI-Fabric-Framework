package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension.CPU_MILLICORES;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension.DATABASES;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.Dimension.MEMORY_BYTES;

/** Proves that a customer database allocation remains inside its durable admission envelope. */
@Service
public class CustomerAdmissionDatabaseCapacityVerifier {
    private final AdmissionStore store;

    CustomerAdmissionDatabaseCapacityVerifier(AdmissionStore store) {
        this.store = store;
    }

    public void requireCovered(String deploymentId, String targetProfileId,
                               long cpuMillicores, long memoryBytes) {
        List<AdmissionContracts.Reservation> reservations = store.held(deploymentId, targetProfileId).stream()
            .filter(AdmissionContracts.Reservation::occupiesActiveSlot).toList();
        if (reservations.size() != 1) fail();
        AdmissionContracts.Reservation reservation = reservations.getFirst();
        if (!reservation.occupiesActiveSlot() || reservation.prerequisiteReadback() == null
            || amount(reservation, DATABASES) < 1
            || amount(reservation, CPU_MILLICORES) < cpuMillicores
            || amount(reservation, MEMORY_BYTES) < memoryBytes) {
            fail();
        }
    }

    private long amount(AdmissionContracts.Reservation reservation, AdmissionContracts.Dimension dimension) {
        if (reservation.demand() == null || reservation.demand().amounts() == null) return -1;
        Long amount = reservation.demand().amounts().get(dimension);
        return amount == null ? -1 : amount;
    }

    private void fail() {
        throw new ResponseStatusException(
            HttpStatus.PRECONDITION_FAILED,
            "CUSTOMER_RUNTIME_DATABASE_CAPACITY_NOT_ADMITTED"
        );
    }
}
