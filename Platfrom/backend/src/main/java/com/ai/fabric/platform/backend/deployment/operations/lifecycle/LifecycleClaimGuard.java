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
}
