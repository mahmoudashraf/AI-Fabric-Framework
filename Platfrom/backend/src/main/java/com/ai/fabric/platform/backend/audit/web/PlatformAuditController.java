package com.ai.fabric.platform.backend.audit.web;

import com.ai.fabric.platform.backend.audit.model.PlatformAuditEventSummary;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/audit-events")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class PlatformAuditController {

    private final PlatformAuditService platformAuditService;

    public PlatformAuditController(PlatformAuditService platformAuditService) {
        this.platformAuditService = platformAuditService;
    }

    @GetMapping
    public List<PlatformAuditEventSummary> listRecentEvents() {
        return platformAuditService.listRecentEvents();
    }
}
