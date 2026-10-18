package com.ai.fabric.platform.backend.customerselfservice.service;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;

/** Keeps generic assignment APIs outside reviewed customer-offer lifecycle control. */
@Service
public class CustomerReviewedBindingBoundary {
    private final CustomerOfferDeploymentRepository deployments;

    public CustomerReviewedBindingBoundary(CustomerOfferDeploymentRepository deployments) {
        this.deployments = deployments;
    }

    public void requireGenericMutationAllowed(String currentDeploymentId, String nextDeploymentId) {
        if (reviewed(currentDeploymentId) || reviewed(nextDeploymentId)) {
            throw new ResponseStatusException(CONFLICT,
                "Reviewed customer deployments must be assigned through staging activation, production promotion, rollback, or retirement.");
        }
    }

    public void requireExplicitRuntimeAssignment(String deploymentId, String targetProfileId, DeploymentReleaseEntity release) {
        if (reviewed(deploymentId) && (release == null || !StringUtils.hasText(targetProfileId)
            || !deploymentId.equals(release.getDeploymentId()) || !targetProfileId.equals(release.getTargetProfileId())
            || !"APPLIED_VERIFIED".equals(release.getStatus()) || !"PASSED".equals(release.getVerificationStatus()))) {
            throw new ResponseStatusException(CONFLICT,
                "Reviewed customer assignment requires an explicitly activated verified release and target.");
        }
    }

    private boolean reviewed(String deploymentId) {
        return StringUtils.hasText(deploymentId) && deployments.existsById(deploymentId.trim());
    }
}
