package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.ai.fabric.platform.backend.deployment.admission.AttendedStagingStopContracts.*;

@RestController
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@RequestMapping("/api/platform/admission/reservations/{reservationId}/attended-stop")
public class AttendedStagingStopController {
    private final AttendedStagingStopService service;
    public AttendedStagingStopController(AttendedStagingStopService service) { this.service = service; }
    @PostMapping("/plans")
    public Summary plan(@PathVariable String reservationId, @RequestBody Request request) {
        return service.plan(reservationId, request);
    }
    @PostMapping("/plans/{planId}/claim")
    public Summary claim(@PathVariable String reservationId, @PathVariable String planId, @RequestBody Command command) {
        return service.claim(reservationId, planId, command);
    }
    @PostMapping("/plans/{planId}/verify-stopped")
    public Summary settle(@PathVariable String reservationId, @PathVariable String planId, @RequestBody Command command) {
        return service.settle(reservationId, planId, command);
    }
}
