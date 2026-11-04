package com.ai.fabric.platform.backend.desk;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVerificationRunRepository;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.FORBIDDEN;

/** Reconciles only local Desk metadata; never provisions or calls the provider. */
@Service
public class DeskBindingReconciliationService {
    private final DeskBindingRepository bindings;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentVerificationRunRepository runs;

    public DeskBindingReconciliationService(DeskBindingRepository bindings, DeploymentRepository deployments,
            DeploymentReleaseRepository releases, DeploymentTargetProfileRepository targets,
            DeploymentVerificationRunRepository runs) {
        this.bindings = bindings; this.deployments = deployments; this.releases = releases;
        this.targets = targets; this.runs = runs;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeskBindingEntity reconcile(String releaseId, String expectedRunId) {
        var release = releases.findByIdForUpdate(releaseId).orElse(null);
        if (release == null || (expectedRunId != null && !expectedRunId.equals(release.getVerificationRunId()))) return null;
        var binding = bindings.findForUpdate(release.getDeploymentId(), release.getTargetProfileId()).orElse(null);
        if (binding == null) return null;
        var deployment = deployments.findById(release.getDeploymentId()).orElse(null);
        if (deployment == null || deployment.getArchivedAt() != null) return null;
        if (!StringUtils.hasText(deployment.getCustomerId()) || !StringUtils.hasText(deployment.getTenantId())
            || !Objects.equals(binding.getCustomerId(), deployment.getCustomerId())
            || !Objects.equals(binding.getTenantId(), deployment.getTenantId())
            || !Objects.equals(binding.getDeploymentId(), deployment.getId())
            || !Objects.equals(binding.getTargetProfileId(), release.getTargetProfileId())) {
            throw new ResponseStatusException(FORBIDDEN, "Desk binding ownership no longer matches the deployment target.");
        }
        if (!releaseId.equals(binding.getReleaseId())
            || !Objects.equals(binding.getVersionId(), release.getDeploymentVersionId())
            || !StringUtils.hasText(binding.getStatus())
            || !Set.of("PREPARING", "APPLYING", "ENABLED", "FAILED").contains(binding.getStatus())) return binding;
        if (targets.findById(release.getTargetProfileId()).filter(target -> target.isActive()).isEmpty()) return binding;
        String next;
        String error;
        if ("FAILED".equals(release.getStatus())) {
            next = "FAILED";
            error = "The Desk configuration could not be applied and verified. Open Versions & updates for details.";
        } else {
            if (!StringUtils.hasText(release.getVerificationRunId())) return binding;
            var run = runs.findById(release.getVerificationRunId()).orElse(null);
            if (run == null || run.getCompletedAt() == null
                || !releaseId.equals(run.getReleaseId())
                || !deployment.getId().equals(run.getDeploymentId())
                || !Objects.equals(release.getDeploymentVersionId(), run.getDeploymentVersionId())
                || !StringUtils.hasText(run.getVerificationType())
                || !Set.of("POST_APPLY", "MANUAL_RERUN", "OPERATIONS_RECOVERY").contains(run.getVerificationType())
                || !Objects.equals(run.getStatus(), release.getVerificationStatus())) return binding;
            if ("APPLIED_VERIFIED".equals(release.getStatus()) && "PASSED".equals(run.getStatus())
                && "ACTIVE".equals(release.getProvisioningStatus())) {
                next = "ENABLED"; error = null;
            } else if ("APPLIED_VERIFICATION_FAILED".equals(release.getStatus()) && "FAILED".equals(run.getStatus())) {
                next = "FAILED";
                error = "The Desk configuration did not pass deployment verification. Open Versions & updates for details.";
            } else return binding;
        }
        if (next.equals(binding.getStatus()) && Objects.equals(error, binding.getErrorMessage())) return binding;
        binding.setStatus(next); binding.setErrorMessage(error); binding.setUpdatedAt(Instant.now());
        return bindings.save(binding);
    }
}
