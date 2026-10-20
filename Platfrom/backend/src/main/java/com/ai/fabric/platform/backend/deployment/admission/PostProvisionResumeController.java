package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import static com.ai.fabric.platform.backend.deployment.admission.PostProvisionResumeContracts.*;

@RestController
@RequestMapping("/api/customer-self-service/catalogue/{offerId}/deployments/{deploymentId}/admission-reservations")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class PostProvisionResumeController {
    private final PostProvisionResumeService service;
    public PostProvisionResumeController(PostProvisionResumeService service) { this.service = service; }
    @PostMapping("/{reservationId}/post-provision-resume-plan")
    public Plan plan(@PathVariable String offerId, @PathVariable String deploymentId,
        @PathVariable String reservationId, @RequestBody PlanRequest request) {
        return service.plan(offerId, deploymentId, reservationId, request);
    }
    @PostMapping("/{reservationId}/resume-post-provisioning")
    public Summary resume(@PathVariable String offerId, @PathVariable String deploymentId,
        @PathVariable String reservationId, @RequestBody Command command) {
        return service.resume(offerId, deploymentId, reservationId, command);
    }
}
