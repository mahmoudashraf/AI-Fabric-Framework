package com.ai.fabric.platform.backend.deployment.authority;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Removes only an already-authorized deleted deployment's authority evidence. */
@Service
public class DeploymentAuthorityCleanupService {
    private final JdbcTemplate jdbc;

    public DeploymentAuthorityCleanupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForDeployment(String deploymentId) {
        // Releases and snapshots reference each other; detach the owned release side first.
        jdbc.update("UPDATE platform_deployment_releases SET runtime_authority_snapshot_id=NULL, "
            + "runtime_authority_fingerprint=NULL WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM platform_runtime_authority_snapshots WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM platform_runtime_target_authorities WHERE deployment_id=?", deploymentId);
    }
}
