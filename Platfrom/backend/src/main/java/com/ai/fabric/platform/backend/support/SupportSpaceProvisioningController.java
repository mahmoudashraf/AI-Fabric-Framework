package com.ai.fabric.platform.backend.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/support")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class SupportSpaceProvisioningController {
    private final SupportSpaceProvisioningService service;
    public SupportSpaceProvisioningController(SupportSpaceProvisioningService service) { this.service = service; }
    @PostMapping("/prepare") public ResponseEntity<JsonNode> prepare() {
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(service.prepare());
    }
}
