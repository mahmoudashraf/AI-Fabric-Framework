package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentHumanReviewDecisionRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentHumanReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/human-reviews")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class DeploymentHumanReviewController {

    private final DeploymentHumanReviewService reviewService;

    public DeploymentHumanReviewController(DeploymentHumanReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public ResponseEntity<JsonNode> inbox(
        @PathVariable String deploymentId,
        @RequestParam(defaultValue = "50") int limit
    ) {
        return response(reviewService.inbox(deploymentId, limit));
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<JsonNode> detail(
        @PathVariable String deploymentId,
        @PathVariable String taskId
    ) {
        return response(reviewService.detail(deploymentId, taskId));
    }

    @PostMapping("/{taskId}/decisions")
    public ResponseEntity<JsonNode> decide(
        @PathVariable String deploymentId,
        @PathVariable String taskId,
        @RequestBody SubmitDeploymentHumanReviewDecisionRequest request
    ) {
        return response(reviewService.decide(deploymentId, taskId, request));
    }

    private ResponseEntity<JsonNode> response(DeploymentHumanReviewService.RuntimeReviewResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
