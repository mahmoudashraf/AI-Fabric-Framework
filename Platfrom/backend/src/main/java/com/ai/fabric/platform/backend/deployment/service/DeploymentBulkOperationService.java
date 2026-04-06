package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionItemSummary;
import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionRequest;
import com.ai.fabric.platform.backend.deployment.model.BulkDeploymentActionResponse;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentBulkOperationService {

    private final DeploymentService deploymentService;
    private final DeploymentRepository deploymentRepository;
    private final PlatformAuditService platformAuditService;

    public DeploymentBulkOperationService(DeploymentService deploymentService,
                                          DeploymentRepository deploymentRepository,
                                          PlatformAuditService platformAuditService) {
        this.deploymentService = deploymentService;
        this.deploymentRepository = deploymentRepository;
        this.platformAuditService = platformAuditService;
    }

    public BulkDeploymentActionResponse execute(BulkDeploymentActionRequest request) {
        String action = normalizeAction(request.action());
        List<String> deploymentIds = request.deploymentIds().stream()
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
        if (deploymentIds.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "At least one deployment id is required.");
        }

        List<BulkDeploymentActionItemSummary> results = new ArrayList<>();
        for (String deploymentId : deploymentIds) {
            DeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElse(null);
            String deploymentName = deployment == null ? deploymentId : deployment.getName();
            try {
                switch (action) {
                    case "ARCHIVE" -> deploymentService.archiveDeployment(deploymentId);
                    case "RESTORE" -> deploymentService.restoreDeployment(deploymentId);
                    case "DELETE" -> deploymentService.deleteDeployment(deploymentId);
                    default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported bulk deployment action: " + action);
                }
                results.add(new BulkDeploymentActionItemSummary(
                    deploymentId,
                    deploymentName,
                    action,
                    "SUCCESS",
                    successMessage(action)
                ));
            } catch (ResponseStatusException ex) {
                results.add(new BulkDeploymentActionItemSummary(
                    deploymentId,
                    deploymentName,
                    action,
                    "FAILED",
                    ex.getReason() == null ? ex.getMessage() : ex.getReason()
                ));
            }
        }

        int succeededCount = (int) results.stream().filter(item -> "SUCCESS".equals(item.status())).count();
        int failedCount = results.size() - succeededCount;
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("action", action);
        details.put("requestedCount", deploymentIds.size());
        details.put("succeededCount", succeededCount);
        details.put("failedCount", failedCount);
        details.put("deploymentIds", deploymentIds);
        platformAuditService.record(
            "DEPLOYMENT_BULK_ACTION_EXECUTED",
            "DEPLOYMENT_BULK_OPERATION",
            action,
            details
        );

        return new BulkDeploymentActionResponse(
            action,
            deploymentIds.size(),
            succeededCount,
            failedCount,
            results
        );
    }

    private String normalizeAction(String action) {
        if (!StringUtils.hasText(action)) {
            throw new ResponseStatusException(BAD_REQUEST, "Bulk deployment action is required.");
        }
        return action.trim().toUpperCase(Locale.ROOT);
    }

    private String successMessage(String action) {
        return switch (action) {
            case "ARCHIVE" -> "Deployment archived.";
            case "RESTORE" -> "Deployment restored.";
            case "DELETE" -> "Deletion queued. Deployment is now subject to deletion completion.";
            default -> "Bulk deployment action completed.";
        };
    }
}
