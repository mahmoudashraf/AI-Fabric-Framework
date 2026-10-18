package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.DeploymentIntegrationOperationsService;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetProfileId}/releases/{releaseId}/integrations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentIntegrationOperationsController {

    private final DeploymentIntegrationOperationsService service;

    public DeploymentIntegrationOperationsController(DeploymentIntegrationOperationsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<JsonNode> overview(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId
    ) {
        return response(service.overview(target(deploymentId, targetProfileId, releaseId)));
    }

    @GetMapping("/sources/{sourceId}")
    public ResponseEntity<JsonNode> source(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId,
        @PathVariable String sourceId
    ) {
        return response(service.source(target(deploymentId, targetProfileId, releaseId), sourceId));
    }

    @PostMapping("/sources/{sourceId}/reconcile")
    public ResponseEntity<JsonNode> reconcile(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId,
        @PathVariable String sourceId,
        @RequestHeader("Idempotency-Key") String operationKey
    ) {
        return response(service.reconcile(target(deploymentId, targetProfileId, releaseId), sourceId, operationKey));
    }

    @GetMapping("/webhooks/{sourceId}/events")
    public ResponseEntity<JsonNode> webhookEvents(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId,
        @PathVariable String sourceId
    ) {
        return response(service.webhookEvents(target(deploymentId, targetProfileId, releaseId), sourceId));
    }

    @PostMapping("/webhooks/{sourceId}/events/{eventId}/replay")
    public ResponseEntity<JsonNode> replayWebhook(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId,
        @PathVariable String sourceId,
        @PathVariable String eventId,
        @RequestHeader("Idempotency-Key") String operationKey
    ) {
        return response(service.replayWebhook(target(deploymentId, targetProfileId, releaseId), sourceId, eventId, operationKey));
    }

    @GetMapping("/conversation-handover")
    public ResponseEntity<JsonNode> conversationHandover(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId
    ) {
        return response(service.conversationHandoverOverview(target(deploymentId, targetProfileId, releaseId)));
    }

    @PostMapping("/conversation-handover/dispatches/{dispatchId}/retry")
    public ResponseEntity<JsonNode> retryConversationHandoverDispatch(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId,
        @PathVariable String dispatchId,
        @RequestHeader("Idempotency-Key") String operationKey
    ) {
        return response(service.retryConversationHandoverDispatch(
            target(deploymentId, targetProfileId, releaseId), dispatchId, operationKey
        ));
    }

    @PostMapping("/conversation-handover/handovers/{handoverId}/expire")
    public ResponseEntity<JsonNode> expireConversationHandover(
        @PathVariable String deploymentId,
        @PathVariable String targetProfileId,
        @PathVariable String releaseId,
        @PathVariable String handoverId,
        @RequestHeader("Idempotency-Key") String operationKey
    ) {
        return response(service.expireConversationHandover(
            target(deploymentId, targetProfileId, releaseId), handoverId, operationKey
        ));
    }

    private DeploymentTargetReference target(String deploymentId, String targetProfileId, String releaseId) {
        return new DeploymentTargetReference(deploymentId, targetProfileId, releaseId);
    }

    private ResponseEntity<JsonNode> response(DeploymentIntegrationOperationsService.RuntimeResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
