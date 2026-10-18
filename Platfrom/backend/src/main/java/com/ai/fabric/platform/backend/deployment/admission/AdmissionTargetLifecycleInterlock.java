package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Database-backed interlock used while the caller holds the deployment row lock. */
@Service
public class AdmissionTargetLifecycleInterlock {
    private final JdbcTemplate jdbc;

    public AdmissionTargetLifecycleInterlock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireNoActiveReservation(String deploymentId, String targetProfileId) {
        Integer active = jdbc.queryForObject("""
            SELECT COUNT(*) FROM deployment_capacity_reservations
             WHERE deployment_id=? AND target_profile_id=? AND current_slot IS NOT NULL
            """, Integer.class, deploymentId, targetProfileId);
        if (active != null && active > 0) {
            throw new AdmissionFailure(CONFLICT, "ACTIVE_ADMISSION_RESERVATION");
        }
    }

    /**
     * The expiry worker may retire the target whose capacity it still owns. The exact
     * claimed reservation is the only exception; every unrelated live reservation
     * remains a hard blocker.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireOnlyClaimedReservation(String deploymentId, String targetProfileId,
                                              String reservationId, String generation) {
        Integer matching = jdbc.queryForObject("""
            SELECT COUNT(*) FROM deployment_capacity_reservations
             WHERE deployment_id=? AND target_profile_id=? AND current_slot IS NOT NULL
               AND id=? AND generation=? AND expiry_state='CLAIMED'
            """, Integer.class, deploymentId, targetProfileId, reservationId, generation);
        Integer other = jdbc.queryForObject("""
            SELECT COUNT(*) FROM deployment_capacity_reservations
             WHERE deployment_id=? AND target_profile_id=? AND current_slot IS NOT NULL
               AND NOT (id=? AND generation=? AND expiry_state='CLAIMED')
            """, Integer.class, deploymentId, targetProfileId, reservationId, generation);
        if (matching == null || matching != 1 || (other != null && other > 0)) {
            throw new AdmissionFailure(CONFLICT, "ACTIVE_ADMISSION_RESERVATION");
        }
    }
}
