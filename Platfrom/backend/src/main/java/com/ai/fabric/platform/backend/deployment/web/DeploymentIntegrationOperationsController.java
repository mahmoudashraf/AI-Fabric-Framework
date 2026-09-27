package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.DeploymentIntegrationOperationsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/integrations")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentIntegrationOperationsController {

    private final DeploymentIntegrationOperationsService service;

    public DeploymentIntegrationOperationsController(DeploymentIntegrationOperationsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<JsonNode> overview(@PathVariable String deploymentId) {
        return response(service.overview(deploymentId));
    }

    @GetMapping("/sources/{sourceId}")
    public ResponseEntity<JsonNode> source(@PathVariable String deploymentId, @PathVariable String sourceId) {
        return response(service.source(deploymentId, sourceId));
    }

    @PostMapping("/sources/{sourceId}/reconcile")
    public ResponseEntity<JsonNode> reconcile(@PathVariable String deploymentId, @PathVariable String sourceId) {
        return response(service.reconcile(deploymentId, sourceId));
    }

    @GetMapping("/webhooks/{sourceId}/events")
    public ResponseEntity<JsonNode> webhookEvents(@PathVariable String deploymentId, @PathVariable String sourceId) {
        return response(service.webhookEvents(deploymentId, sourceId));
    }

    @PostMapping("/webhooks/{sourceId}/events/{eventId}/replay")
    public ResponseEntity<JsonNode> replayWebhook(
        @PathVariable String deploymentId,
        @PathVariable String sourceId,
        @PathVariable String eventId
    ) {
        return response(service.replayWebhook(deploymentId, sourceId, eventId));
    }

    private ResponseEntity<JsonNode> response(DeploymentIntegrationOperationsService.RuntimeResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
