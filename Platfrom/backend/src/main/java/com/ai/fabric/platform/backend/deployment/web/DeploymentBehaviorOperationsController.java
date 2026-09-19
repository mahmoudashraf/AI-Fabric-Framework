package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentAgenticExecutionRequest;
import com.ai.fabric.platform.backend.deployment.model.SubmitDeploymentSmartBrainTriggerRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentBehaviorOperationsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/behavior-operations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentBehaviorOperationsController {

    private final DeploymentBehaviorOperationsService operationsService;

    public DeploymentBehaviorOperationsController(DeploymentBehaviorOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @PostMapping("/agentic/executions")
    public ResponseEntity<JsonNode> submitAgentic(
        @PathVariable String deploymentId,
        @RequestBody SubmitDeploymentAgenticExecutionRequest request
    ) {
        return response(operationsService.submitAgentic(deploymentId, request));
    }

    @GetMapping("/agentic/executions/{executionId}")
    public ResponseEntity<JsonNode> agenticStatus(
        @PathVariable String deploymentId,
        @PathVariable String executionId
    ) {
        return response(operationsService.agenticStatus(deploymentId, executionId));
    }

    @PostMapping("/agentic/executions/{executionId}/cancel")
    public ResponseEntity<JsonNode> cancelAgentic(
        @PathVariable String deploymentId,
        @PathVariable String executionId
    ) {
        return response(operationsService.cancelAgentic(deploymentId, executionId));
    }

    @PostMapping("/agentic/executions/{executionId}/replay")
    public ResponseEntity<JsonNode> replayAgentic(
        @PathVariable String deploymentId,
        @PathVariable String executionId,
        @RequestBody SubmitDeploymentAgenticExecutionRequest request
    ) {
        return response(operationsService.replayAgentic(deploymentId, executionId, request));
    }

    @PostMapping("/smart-brain/triggers/{triggerCode}")
    public ResponseEntity<JsonNode> triggerSmartBrain(
        @PathVariable String deploymentId,
        @PathVariable String triggerCode,
        @RequestBody SubmitDeploymentSmartBrainTriggerRequest request
    ) {
        return response(operationsService.triggerSmartBrain(deploymentId, triggerCode, request));
    }

    @GetMapping("/smart-brain/operations/{operationId}")
    public ResponseEntity<JsonNode> smartBrainStatus(
        @PathVariable String deploymentId,
        @PathVariable String operationId
    ) {
        return response(operationsService.smartBrainStatus(deploymentId, operationId));
    }

    @PostMapping("/smart-brain/operations/{operationId}/cancel")
    public ResponseEntity<JsonNode> cancelSmartBrain(
        @PathVariable String deploymentId,
        @PathVariable String operationId
    ) {
        return response(operationsService.cancelSmartBrain(deploymentId, operationId));
    }

    @PostMapping("/smart-brain/operations/{operationId}/replay")
    public ResponseEntity<JsonNode> replaySmartBrain(
        @PathVariable String deploymentId,
        @PathVariable String operationId
    ) {
        return response(operationsService.replaySmartBrain(deploymentId, operationId));
    }

    private ResponseEntity<JsonNode> response(DeploymentBehaviorOperationsService.RuntimeOperationResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
