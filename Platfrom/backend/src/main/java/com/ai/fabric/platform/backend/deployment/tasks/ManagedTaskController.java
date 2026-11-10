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
    public ManagedTaskController(ManagedTaskOperationsService tasks) { this.tasks = tasks; }
    @GetMapping("/applied")
    public JsonNode applied(@PathVariable String deploymentId, @RequestParam String targetProfileId) {
        return tasks.applied(deploymentId, targetProfileId);
    }
    @PostMapping("/{taskCode}/execute")
    public ResponseEntity<JsonNode> execute(@PathVariable String deploymentId, @PathVariable String taskCode,
        @RequestParam String targetProfileId, @RequestBody JsonNode request) {
        var result = tasks.execute(deploymentId, targetProfileId, taskCode, request);
        return ResponseEntity.status(result.status()).header("Cache-Control", "no-store").body(result.body());
    }
}
