package com.ai.fabric.platform.backend.deployment.retirement;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetProfileId}/retirements")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR','CUSTOMER_ADMIN')")
public class TargetRetirementController {
    private final TargetRetirementService service;
    public TargetRetirementController(TargetRetirementService service) { this.service = service; }
    @PostMapping("/plan")
    public Summary plan(@PathVariable String deploymentId, @PathVariable String targetProfileId, @RequestBody PlanRequest request) {
        return service.plan(deploymentId, targetProfileId, request);
    }
    @GetMapping
    public List<Summary> list(@PathVariable String deploymentId, @PathVariable String targetProfileId) {
        return service.list(deploymentId, targetProfileId);
    }
    @GetMapping("/{operationId}")
    public Summary status(@PathVariable String deploymentId, @PathVariable String targetProfileId, @PathVariable String operationId) {
        return service.status(deploymentId, targetProfileId, operationId);
    }
    @PostMapping("/{operationId}/execute")
    public Summary execute(@PathVariable String deploymentId, @PathVariable String targetProfileId, @PathVariable String operationId,
                           @RequestBody Command command) { return service.execute(deploymentId, targetProfileId, operationId, command); }
    @PostMapping("/{operationId}/retry")
    public Summary retry(@PathVariable String deploymentId, @PathVariable String targetProfileId, @PathVariable String operationId,
                         @RequestBody Command command) { return service.retry(deploymentId, targetProfileId, operationId, command); }
    @PostMapping("/{operationId}/cancel")
    public Summary cancel(@PathVariable String deploymentId, @PathVariable String targetProfileId, @PathVariable String operationId,
                          @RequestBody Command command) { return service.cancel(deploymentId, targetProfileId, operationId, command); }
}
