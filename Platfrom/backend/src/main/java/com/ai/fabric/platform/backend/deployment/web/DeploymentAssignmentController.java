package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentAssignmentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentAssignmentRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/assignments")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class DeploymentAssignmentController {

    private final DeploymentAssignmentService deploymentAssignmentService;

    public DeploymentAssignmentController(DeploymentAssignmentService deploymentAssignmentService) {
        this.deploymentAssignmentService = deploymentAssignmentService;
    }

    @GetMapping
    public List<DeploymentAssignmentSummary> listAssignments(@PathVariable String deploymentId) {
        return deploymentAssignmentService.listAssignments(deploymentId);
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentAssignmentSummary upsertAssignment(@PathVariable String deploymentId,
                                                        @Valid @RequestBody UpsertDeploymentAssignmentRequest request) {
        return deploymentAssignmentService.upsertAssignment(deploymentId, request);
    }

    @DeleteMapping("/{assignmentId}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAssignment(@PathVariable String deploymentId,
                                 @PathVariable String assignmentId) {
        deploymentAssignmentService.deleteAssignment(deploymentId, assignmentId);
    }
}
