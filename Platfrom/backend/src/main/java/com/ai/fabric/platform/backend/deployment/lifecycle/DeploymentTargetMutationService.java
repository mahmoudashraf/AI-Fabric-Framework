package com.ai.fabric.platform.backend.deployment.lifecycle;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/** Serializes target changes in core, without a reservation or retirement-policy ledger. */
@Service
public class DeploymentTargetMutationService {
    public static final Set<String> ACTIVE_RELEASE_STATUSES = Set.of(
        "APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING");
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;

    public DeploymentTargetMutationService(DeploymentRepository deployments, DeploymentReleaseRepository releases) {
        this.deployments = deployments;
        this.releases = releases;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForMutation(String deploymentId, String targetProfileId) {
        requireAvailableForReplay(deploymentId, targetProfileId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForReplay(String deploymentId, String targetProfileId) {
        if (targetProfileId == null || targetProfileId.isBlank())
            throw new ResponseStatusException(CONFLICT, "DEPLOYMENT_TARGET_REQUIRED");
        deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, targetProfileId).stream()
            .anyMatch(release -> ACTIVE_RELEASE_STATUSES.contains(release.getStatus())))
            throw new ResponseStatusException(CONFLICT, "RELEASE_APPLY_IN_PROGRESS");
    }
}
