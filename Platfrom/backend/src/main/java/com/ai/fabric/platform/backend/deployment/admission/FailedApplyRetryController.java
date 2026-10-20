package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.ai.fabric.platform.backend.deployment.admission.FailedApplyRetryContracts.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class FailedApplyRetryController {
    private final FailedApplyRetryService service;
    public FailedApplyRetryController(FailedApplyRetryService service) { this.service = service; }
    @PostMapping("/{reservationId}/failed-apply-retry-plan")
    public Plan plan(@PathVariable String offerId, @PathVariable String deploymentId,
                     @PathVariable String reservationId, @RequestBody PlanRequest request) {
        return service.plan(offerId, deploymentId, reservationId, request);
    }
    @PostMapping("/{reservationId}/retry-failed-apply")
    public Summary retry(@PathVariable String offerId, @PathVariable String deploymentId,
                         @PathVariable String reservationId, @RequestBody Command command) {
        return service.retry(offerId, deploymentId, reservationId, command);
    }
}
