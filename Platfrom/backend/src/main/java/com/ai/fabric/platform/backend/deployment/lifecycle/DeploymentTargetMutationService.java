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
    @org.springframework.beans.factory.annotation.Autowired
    private com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleClaimGuard lifecycleClaims;
    public static final Set<String> ACTIVE_RELEASE_STATUSES = Set.of(
        "APPLY_REQUESTED", "PRE_APPLY_VERIFYING", "PROVISIONING", "VERIFYING");
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles;

    public DeploymentTargetMutationService(DeploymentRepository deployments, DeploymentReleaseRepository releases,
        com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository profiles) {
        this.deployments = deployments;
        this.releases = releases;
        this.profiles = profiles;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForMutation(String deploymentId, String targetProfileId) {
        requireAvailableForReplay(deploymentId, targetProfileId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireAvailableForReplay(String deploymentId, String targetProfileId) {
        if (targetProfileId == null || targetProfileId.isBlank())
            throw new ResponseStatusException(CONFLICT, "DEPLOYMENT_TARGET_REQUIRED");
        var deployment = deployments.findByIdForUpdate(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found."));
        if (deployment.getEnvironmentPolicy() == com.ai.fabric.platform.backend.deployment.model.DeploymentEnvironmentPolicy.STAGING_ONLY)
            com.ai.fabric.platform.backend.deployment.service.DeploymentEnvironmentPolicyGuard.requireAllowed(deployment,
                profiles.findById(targetProfileId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Target profile not found.")));
        if (lifecycleClaims != null) lifecycleClaims.requireAvailable(deploymentId, targetProfileId);
        if (releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, targetProfileId).stream()
            .anyMatch(release -> ACTIVE_RELEASE_STATUSES.contains(release.getStatus())))
            throw new ResponseStatusException(CONFLICT, "RELEASE_APPLY_IN_PROGRESS");
    }
}
