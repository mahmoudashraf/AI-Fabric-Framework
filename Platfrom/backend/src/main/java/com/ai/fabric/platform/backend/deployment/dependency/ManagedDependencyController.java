package com.ai.fabric.platform.backend.deployment.dependency;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/targets/{targetProfileId}/managed-dependencies")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','CUSTOMER_ADMIN')")
public class ManagedDependencyController {
    private final ManagedDependencyLifecycleService lifecycle;
    private final ManagedDependencyOperatorService operators;
    public ManagedDependencyController(ManagedDependencyLifecycleService lifecycle, ManagedDependencyOperatorService operators) {
        this.lifecycle = lifecycle; this.operators = operators;
    }
    @GetMapping
    public ManagedDependencyLifecycleService.Summary status(@PathVariable String deploymentId, @PathVariable String targetProfileId) {
        return lifecycle.status(deploymentId, targetProfileId);
    }
    @PostMapping("/operators")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public ManagedDependencyLifecycleService.Summary enroll(@PathVariable String deploymentId, @PathVariable String targetProfileId,
                                                           @RequestBody ManagedDependencyOperatorService.Enroll request) {
        return operators.enroll(deploymentId, targetProfileId, request);
    }
    @PostMapping("/operators/{operatorId}/rotate")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public ManagedDependencyLifecycleService.Summary rotate(@PathVariable String deploymentId, @PathVariable String targetProfileId,
            @PathVariable String operatorId, @RequestBody ManagedDependencyOperatorService.Mutation request) {
        return operators.rotate(deploymentId, targetProfileId, operatorId, request);
    }
    @PostMapping("/operators/{operatorId}/revoke")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public ManagedDependencyLifecycleService.Summary revoke(@PathVariable String deploymentId, @PathVariable String targetProfileId,
            @PathVariable String operatorId, @RequestBody ManagedDependencyOperatorService.Mutation request) {
        return operators.revoke(deploymentId, targetProfileId, operatorId, request);
    }
    @PostMapping("/operators/{operatorId}/credential-delivery")
    @PreAuthorize("hasRole('CUSTOMER_ADMIN')")
    public ResponseEntity<ManagedDependencyOperatorService.Delivery> deliver(@PathVariable String deploymentId,
            @PathVariable String targetProfileId, @PathVariable String operatorId,
            @RequestBody ManagedDependencyOperatorService.Mutation request, HttpServletRequest transport) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache")
            .body(operators.deliver(deploymentId, targetProfileId, operatorId, request, transport.isSecure()));
    }
}
