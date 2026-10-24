package com.ai.fabric.platform.backend.deployment.operations;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/deployment-operations")
public class DeploymentOperationsController {
    private final DeploymentOperationsService operations;
    private final CoreReleaseAuthorizationStore authorizations;
    private final DeploymentReleaseRepository releases;
    private final DeploymentRepository deployments;
    private final DeploymentAccessService access;

    public DeploymentOperationsController(DeploymentOperationsService operations, CoreReleaseAuthorizationStore authorizations,
        DeploymentReleaseRepository releases, DeploymentRepository deployments, DeploymentAccessService access) {
        this.operations = operations; this.authorizations = authorizations; this.releases = releases;
        this.deployments = deployments; this.access = access;
    }

    @GetMapping("/status")
    public DeploymentOperationsContracts.Status status() {
        if (PlatformSecurityContext.currentPrincipal() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return operations.status();
    }

    @GetMapping("/releases/{releaseId}")
    public Map<String, Object> release(@PathVariable String releaseId) {
        var release = releases.findById(releaseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var deployment = deployments.findById(release.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        access.requireDeploymentAccess(deployment);
        var authority = authorizations.find(releaseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return Map.of("releaseId", releaseId, "providerUsageMode", authority.providerUsageMode(), "operations", authority.operations());
    }
}
