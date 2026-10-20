package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.ai.fabric.platform.backend.deployment.admission.PreparedFailureRecoveryContracts.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class PreparedFailureRecoveryController {
    private final PreparedFailureRecoveryService service;
    public PreparedFailureRecoveryController(PreparedFailureRecoveryService service) { this.service = service; }

    @PostMapping("/{reservationId}/prepared-failure-recovery-plan")
    public Plan plan(@PathVariable String offerId, @PathVariable String deploymentId,
                     @PathVariable String reservationId, @RequestBody PlanRequest request) {
        return service.plan(offerId, deploymentId, reservationId, request);
    }

    @PostMapping("/{reservationId}/recover-prepared-failure")
    public Summary recover(@PathVariable String offerId, @PathVariable String deploymentId,
                           @PathVariable String reservationId, @RequestBody Command command) {
        return service.recover(offerId, deploymentId, reservationId, command);
    }
}
