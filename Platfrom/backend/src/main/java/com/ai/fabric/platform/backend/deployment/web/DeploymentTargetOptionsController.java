package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerSelfServiceException;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetProfileSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/** Customer target choices exclude provider credentials, server configuration and resource defaults. */
@RestController
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class DeploymentTargetOptionsController {
    private final DeploymentRepository deployments;
    private final DeploymentTargetProfileRepository profiles;
    private final DeploymentAccessService access;
    private final CustomerSelfServiceOperationBoundary authorization;

    public DeploymentTargetOptionsController(DeploymentRepository deployments, DeploymentTargetProfileRepository profiles,
        DeploymentAccessService access, CustomerSelfServiceOperationBoundary authorization) {
        this.deployments = deployments; this.profiles = profiles;
        this.access = access; this.authorization = authorization;
    }

    @GetMapping("/api/deployments/{deploymentId}/target-profiles")
    public List<DeploymentTargetProfileSummary> list(@PathVariable String deploymentId) {
        var deployment = deployments.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        access.requireDeploymentAccess(deployment);
        return profiles.findAll().stream().filter(profile -> profile.isActive()).filter(profile -> {
            try {
                authorization.require(deployment, profile.getId(), null, Operation.READ, null, Map.of(), null, null, null);
                return true;
            } catch (CustomerSelfServiceException denied) {
                // This list contains only targets authorized for the actual customer deployment.
                return switch (denied.code()) {
                    case UNENTITLED_TARGET, PLACEMENT_DENIED, TRUSTED_PLACEMENT_REQUIRED -> false;
                    default -> throw denied;
                };
            }
        }).sorted(java.util.Comparator.comparing(profile -> profile.getId()))
            .map(profile -> new DeploymentTargetProfileSummary(profile.getId(), profile.getName(), profile.getProviderType(),
                profile.getEnvironmentName(), profile.getRegion(), true, profile.isDefaultForRuntime(), false, false,
                profile.getSourceStrategy(), null, JsonNodeFactory.instance.objectNode(), JsonNodeFactory.instance.objectNode(),
                JsonNodeFactory.instance.objectNode(), profile.getCreatedAt(), profile.getUpdatedAt()))
            .toList();
    }
}
