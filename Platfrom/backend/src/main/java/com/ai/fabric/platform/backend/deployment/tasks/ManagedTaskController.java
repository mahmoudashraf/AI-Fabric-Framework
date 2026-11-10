package com.ai.fabric.platform.backend.deployment.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/tasks")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class ManagedTaskController {
    private final ManagedTaskOperationsService tasks;
    private final ManagedTaskResourceService resources;
    public ManagedTaskController(ManagedTaskOperationsService tasks, ManagedTaskResourceService resources) { this.tasks = tasks; this.resources = resources; }
    @GetMapping("/resources")
    public JsonNode resources(@PathVariable String deploymentId) { return resources.resources(deploymentId); }
    @GetMapping("/applied")
    public JsonNode applied(@PathVariable String deploymentId, @RequestParam String targetProfileId) {
        return tasks.applied(deploymentId, targetProfileId);
    }
    @PostMapping("/{taskCode}/execute")
    public ResponseEntity<JsonNode> execute(@PathVariable String deploymentId, @PathVariable String taskCode,
        @RequestParam String targetProfileId, @RequestParam(required = false) String expectedVersion, @RequestBody JsonNode request) {
        var result = tasks.execute(deploymentId, targetProfileId, taskCode, request, expectedVersion);
        return ResponseEntity.status(result.status()).header("Cache-Control", "no-store").body(result.body());
    }
    @PostMapping("/{taskCode}/action-proposals/{receiptId}/decisions")
    public ResponseEntity<JsonNode> decide(@PathVariable String deploymentId, @PathVariable String taskCode,
        @PathVariable String receiptId, @RequestParam String targetProfileId, @RequestParam String expectedVersion, @RequestBody JsonNode request) {
        var result = tasks.decide(deploymentId, targetProfileId, taskCode, receiptId, request, expectedVersion);
        return ResponseEntity.status(result.status()).header("Cache-Control", "no-store").body(result.body());
    }
}
