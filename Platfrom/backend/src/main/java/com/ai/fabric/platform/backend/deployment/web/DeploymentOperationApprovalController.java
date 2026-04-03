package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentOperationApprovalRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOperationApprovalSummary;
import com.ai.fabric.platform.backend.deployment.model.ResolveDeploymentOperationApprovalRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentOperationApprovalService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class DeploymentOperationApprovalController {

    private final DeploymentOperationApprovalService deploymentOperationApprovalService;

    public DeploymentOperationApprovalController(DeploymentOperationApprovalService deploymentOperationApprovalService) {
        this.deploymentOperationApprovalService = deploymentOperationApprovalService;
    }

    @GetMapping("/deployments/{deploymentId}/approvals")
    public List<DeploymentOperationApprovalSummary> listApprovals(@PathVariable String deploymentId) {
        return deploymentOperationApprovalService.listApprovals(deploymentId);
    }

    @PostMapping("/deployments/{deploymentId}/approvals")
    @ResponseStatus(HttpStatus.CREATED)
    public DeploymentOperationApprovalSummary createApproval(@PathVariable String deploymentId,
                                                             @Valid @RequestBody CreateDeploymentOperationApprovalRequest request) {
        return deploymentOperationApprovalService.createApproval(deploymentId, request);
    }

    @PostMapping("/deployment-approvals/{approvalId}/approve")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentOperationApprovalSummary approve(@PathVariable String approvalId,
                                                      @RequestBody(required = false) ResolveDeploymentOperationApprovalRequest request) {
        return deploymentOperationApprovalService.approve(
            approvalId,
            request == null ? new ResolveDeploymentOperationApprovalRequest(null) : request
        );
    }

    @PostMapping("/deployment-approvals/{approvalId}/reject")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public DeploymentOperationApprovalSummary reject(@PathVariable String approvalId,
                                                     @RequestBody(required = false) ResolveDeploymentOperationApprovalRequest request) {
        return deploymentOperationApprovalService.reject(
            approvalId,
            request == null ? new ResolveDeploymentOperationApprovalRequest(null) : request
        );
    }
}
