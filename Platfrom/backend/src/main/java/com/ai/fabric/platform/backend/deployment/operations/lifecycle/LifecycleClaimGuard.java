package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Local exclusion for an explicitly accepted native operation, not a remote admission check. */
@Component
public class LifecycleClaimGuard {
    private final JdbcTemplate jdbc;
    public LifecycleClaimGuard(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void requireAvailable(String deploymentId, String targetProfileId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_grants "
            + "WHERE deployment_id=? AND target_profile_id=? AND claimed=TRUE", Integer.class,
            deploymentId, targetProfileId);
        if (count != null && count > 0) throw new ResponseStatusException(CONFLICT, "TARGET_NATIVE_OPERATION_IN_PROGRESS");
    }
    public void requireDeploymentAvailable(String deploymentId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_grants WHERE deployment_id=? AND claimed=TRUE",
            Integer.class, deploymentId);
        if (count != null && count > 0) throw new ResponseStatusException(CONFLICT, "TARGET_NATIVE_OPERATION_IN_PROGRESS");
    }

    /** An expired allowance may be renewed only after this exact recovery restored its native resources. */
    public void requireValidityMaintenanceAvailable(String deploymentId, String targetProfileId, String releaseId) {
        Integer claims = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_grants "
            + "WHERE deployment_id=? AND target_profile_id=? AND claimed=TRUE", Integer.class, deploymentId, targetProfileId);
        if (claims != null && claims == 0) return;
        Integer recovered = jdbc.queryForObject("SELECT COUNT(*) FROM deployment_lifecycle_grants g "
            + "WHERE g.deployment_id=? AND g.target_profile_id=? AND g.release_id=? AND g.claimed=TRUE AND g.state='OPEN' "
            + "AND EXISTS (SELECT 1 FROM deployment_lifecycle_commands c WHERE c.grant_id=g.grant_id "
            + "AND c.phase='RECOVER' AND c.status='SUCCEEDED')", Integer.class, deploymentId, targetProfileId, releaseId);
        if (claims == null || claims != 1 || recovered == null || recovered != 1)
            throw new ResponseStatusException(CONFLICT, "TARGET_NATIVE_OPERATION_IN_PROGRESS");
    }
}
