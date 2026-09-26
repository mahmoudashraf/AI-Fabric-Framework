package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentKnowledgeOperationsService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/document-knowledge")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class DeploymentDocumentKnowledgeController {

    private final DeploymentDocumentKnowledgeOperationsService service;

    public DeploymentDocumentKnowledgeController(DeploymentDocumentKnowledgeOperationsService service) {
        this.service = service;
    }

    @GetMapping("/connector")
    public ResponseEntity<JsonNode> connector(@PathVariable String deploymentId) {
        return response(service.connectorStatus(deploymentId));
    }

    @PostMapping("/discover")
    public ResponseEntity<JsonNode> discover(@PathVariable String deploymentId, @RequestBody JsonNode body) {
        return response(service.discover(deploymentId, body));
    }

    @GetMapping("/sources")
    public ResponseEntity<JsonNode> list(@PathVariable String deploymentId) {
        return response(service.list(deploymentId));
    }

    @PostMapping("/sources")
    public ResponseEntity<JsonNode> register(
        @PathVariable String deploymentId,
        @RequestBody JsonNode body,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return response(service.register(deploymentId, body, idempotencyKey));
    }

    @GetMapping("/sources/{sourceId}")
    public ResponseEntity<JsonNode> detail(@PathVariable String deploymentId, @PathVariable String sourceId) {
        return response(service.detail(deploymentId, sourceId));
    }

    @GetMapping("/sources/{sourceId}/preview")
    public ResponseEntity<JsonNode> preview(@PathVariable String deploymentId, @PathVariable String sourceId) {
        return response(service.preview(deploymentId, sourceId));
    }

    @PostMapping("/sources/{sourceId}/refresh")
    public ResponseEntity<JsonNode> refresh(
        @PathVariable String deploymentId,
        @PathVariable String sourceId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return response(service.refresh(deploymentId, sourceId, idempotencyKey));
    }

    @PostMapping("/sources/{sourceId}/index")
    public ResponseEntity<JsonNode> index(
        @PathVariable String deploymentId,
        @PathVariable String sourceId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return response(service.index(deploymentId, sourceId, idempotencyKey));
    }

    @PostMapping("/sources/{sourceId}/reconcile")
    public ResponseEntity<JsonNode> reconcile(
        @PathVariable String deploymentId,
        @PathVariable String sourceId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return response(service.reconcile(deploymentId, sourceId, idempotencyKey));
    }

    @DeleteMapping("/sources/{sourceId}")
    public ResponseEntity<JsonNode> removeIndex(
        @PathVariable String deploymentId,
        @PathVariable String sourceId,
        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return response(service.removeIndex(deploymentId, sourceId, idempotencyKey));
    }

    @PostMapping("/retrieval-proof")
    public ResponseEntity<JsonNode> retrievalProof(@PathVariable String deploymentId, @RequestBody JsonNode body) {
        return response(service.retrievalProof(deploymentId, body));
    }

    @GetMapping("/retention")
    public ResponseEntity<JsonNode> retentionStatus(@PathVariable String deploymentId) {
        return response(service.retentionStatus(deploymentId));
    }

    @PostMapping("/retention/cleanup")
    public ResponseEntity<JsonNode> cleanupRetention(@PathVariable String deploymentId) {
        return response(service.cleanupRetention(deploymentId));
    }

    private ResponseEntity<JsonNode> response(DeploymentDocumentKnowledgeOperationsService.RuntimeResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }
}
