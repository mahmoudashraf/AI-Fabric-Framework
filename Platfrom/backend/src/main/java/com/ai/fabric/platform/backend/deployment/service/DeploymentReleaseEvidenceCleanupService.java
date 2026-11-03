package com.ai.fabric.platform.backend.deployment.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Removes owned control-plane evidence after deployment deletion has been authorized. */
@Service
public class DeploymentReleaseEvidenceCleanupService {
    private final JdbcTemplate jdbc;

    public DeploymentReleaseEvidenceCleanupService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteForDeployment(String deploymentId) {
        jdbc.update("DELETE FROM deployment_lifecycle_effects WHERE command_id IN "
            + "(SELECT c.command_id FROM deployment_lifecycle_commands c JOIN deployment_lifecycle_grants g "
            + "ON g.grant_id=c.grant_id WHERE g.deployment_id=?)", deploymentId);
        jdbc.update("DELETE FROM deployment_lifecycle_commands WHERE grant_id IN "
            + "(SELECT grant_id FROM deployment_lifecycle_grants WHERE deployment_id=?)", deploymentId);
        jdbc.update("DELETE FROM deployment_lifecycle_grants WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM deployment_external_integration_snapshots WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM deployment_external_integration_bindings WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM deployment_external_integration_approvals WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM deployment_unused_vector_handoffs WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM platform_desk_bindings WHERE deployment_id=?", deploymentId);
        // Releases and snapshots reference each other; detach the owned release side first.
        jdbc.update("UPDATE platform_deployment_releases SET runtime_authority_snapshot_id=NULL, "
            + "runtime_authority_fingerprint=NULL WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM platform_runtime_authority_snapshots WHERE deployment_id=?", deploymentId);
        jdbc.update("DELETE FROM platform_runtime_target_authorities WHERE deployment_id=?", deploymentId);
    }
}
