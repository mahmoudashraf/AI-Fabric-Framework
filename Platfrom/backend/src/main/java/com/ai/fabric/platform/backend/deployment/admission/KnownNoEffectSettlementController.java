package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class KnownNoEffectSettlementController {
    private final KnownNoEffectSettlementService service;

    public KnownNoEffectSettlementController(KnownNoEffectSettlementService service) { this.service = service; }

    @PostMapping("/{reservationId}/known-no-effect-plan")
    public Plan plan(@PathVariable String offerId, @PathVariable String deploymentId,
                     @PathVariable String reservationId, @RequestBody PlanRequest request) {
        return service.plan(offerId, deploymentId, reservationId, request);
    }

    @PostMapping("/{reservationId}/settle-known-no-effect")
    public Summary settle(@PathVariable String offerId, @PathVariable String deploymentId,
                          @PathVariable String reservationId, @RequestBody Command command) {
        return service.settle(offerId, deploymentId, reservationId, command);
    }
}
