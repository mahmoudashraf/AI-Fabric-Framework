package com.ai.fabric.platform.backend.infrastructure.monitoring;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/infrastructure/monitoring")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class InfrastructureMonitoringController {
    private final InfrastructureMonitoringService monitoring;
    private final CustomerOfferEntitlementService access;

    public InfrastructureMonitoringController(InfrastructureMonitoringService monitoring, CustomerOfferEntitlementService access) {
        this.monitoring = monitoring; this.access = access;
    }

    @GetMapping("/servers")
    public ResponseEntity<JsonNode> fleet() { access.requireStaffAdmin(); return response(monitoring.fleet()); }

    @GetMapping("/servers/{id}")
    public ResponseEntity<JsonNode> server(@PathVariable String id) { access.requireStaffAdmin(); return response(monitoring.server(id)); }

    @GetMapping("/servers/{id}/history")
    public ResponseEntity<JsonNode> history(@PathVariable String id, @RequestParam(defaultValue = "1h") String range,
                                          @RequestParam(required = false) String resourceId) {
        access.requireStaffAdmin(); return response(monitoring.history(id, range, resourceId));
    }

    @ExceptionHandler(MonitoringUnavailableException.class)
    public ResponseEntity<java.util.Map<String, String>> unavailable(MonitoringUnavailableException exception) {
        return ResponseEntity.status(503).cacheControl(CacheControl.noStore())
            .body(java.util.Map.of("errorCode", exception.getMessage(), "message", "Monitoring is unavailable. Deployment operations are unchanged."));
    }

    private ResponseEntity<JsonNode> response(JsonNode value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);
    }
}
