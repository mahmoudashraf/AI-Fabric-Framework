package com.ai.fabric.platform.backend.desk;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/desk")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class DeskActivationController {
    private final DeskActivationService service;
    public DeskActivationController(DeskActivationService service) { this.service = service; }

    @GetMapping
    public DeskActivationService.Status status(@PathVariable String deploymentId,
        @RequestParam String targetProfileId, @RequestParam String releaseId) {
        return service.status(deploymentId, targetProfileId, releaseId);
    }

    @PostMapping("/enable")
    public DeskActivationService.Status enable(@PathVariable String deploymentId,
        @RequestBody DeskActivationService.EnableRequest request) { return service.enable(deploymentId, request); }
}
