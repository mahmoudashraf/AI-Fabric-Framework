package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployment-operations")
public class LifecycleOperatorController {
    private final LifecycleGrantService service;
    public LifecycleOperatorController(LifecycleGrantService service) { this.service = service; }
    @PostMapping("/targets/{deploymentId}/{targetProfileId}/jobs")
    public Map<String, Object> create(@PathVariable String deploymentId, @PathVariable String targetProfileId,
        @Valid @RequestBody LifecycleContracts.CreateJob request) { return service.create(deploymentId, targetProfileId, request); }
    @GetMapping("/jobs/{operationId}")
    public JsonNode job(@PathVariable String operationId) { return service.job(operationId, null); }
    @GetMapping("/jobs/{operationId}/events")
    public JsonNode events(@PathVariable String operationId) { return service.job(operationId, "events"); }
    @PostMapping("/jobs/{operationId}/{command:advance|cancel|retry}")
    public JsonNode command(@PathVariable String operationId, @PathVariable String command) { return service.job(operationId, command); }
}
