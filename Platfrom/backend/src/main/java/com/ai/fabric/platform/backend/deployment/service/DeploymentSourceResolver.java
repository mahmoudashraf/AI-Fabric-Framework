package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformProvisioningProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentSourceResolver {

    private final PlatformProvisioningProperties provisioningProperties;

    public DeploymentSourceResolver(PlatformProvisioningProperties provisioningProperties) {
        this.provisioningProperties = provisioningProperties;
    }

    public String resolveRepository(DeploymentEntity deployment) {
        String override = normalize(deployment.getSourceRepositoryOverride());
        return override != null ? override : provisioningProperties.repository();
    }

    public String resolveBranch(DeploymentEntity deployment) {
        String override = normalize(deployment.getSourceBranchOverride());
        return override != null ? override : provisioningProperties.branch();
    }

    public DeploymentSourceSummary summarize(DeploymentEntity deployment) {
        String repositoryOverride = normalize(deployment.getSourceRepositoryOverride());
        String branchOverride = normalize(deployment.getSourceBranchOverride());
        return new DeploymentSourceSummary(
            resolveRepository(deployment),
            resolveBranch(deployment),
            repositoryOverride,
            branchOverride,
            repositoryOverride != null || branchOverride != null
        );
    }

    public String normalizeOverride(String value) {
        return normalize(value);
    }

    public void validateEffectiveSource(String repository, String branch) {
        if (repository == null || repository.isBlank() || !repository.contains("/")) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment source repository must be a GitHub slug like 'owner/repo'."
            );
        }
        if (branch == null || branch.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Deployment source branch must not be blank.");
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
