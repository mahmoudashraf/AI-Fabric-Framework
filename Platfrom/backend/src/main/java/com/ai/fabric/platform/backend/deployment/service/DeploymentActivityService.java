package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentActivityService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformAuditService platformAuditService;

    public DeploymentActivityService(DeploymentRepository deploymentRepository,
                                     DeploymentAccessService deploymentAccessService,
                                     PlatformAuditService platformAuditService) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformAuditService = platformAuditService;
    }

    public List<PlatformAuditEventSummary> listRecentActivity(String deploymentId, Integer limit) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentAccess(deployment);
        int normalizedLimit = limit == null ? 100 : limit;
        return platformAuditService.listRecentEventsForDeployment(deploymentId, normalizedLimit);
    }
}
