package com.ai.fabric.platform.backend.desk;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/customers/{customerId}/ai-workspace-installations/{installationId}/support-space")
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class DeskSupportSpaceController {
    private final DeskSupportSpaceService service;

    public DeskSupportSpaceController(DeskSupportSpaceService service) { this.service = service; }

    @GetMapping
    public DeskSupportSpaceService.Status status(@PathVariable String customerId, @PathVariable String installationId,
            @RequestParam(required = false) String targetProfileId, @RequestParam(required = false) String releaseId) {
        return service.status(customerId, installationId, targetProfileId, releaseId);
    }

    @PutMapping
    public DeskSupportSpaceService.Status configure(@PathVariable String customerId, @PathVariable String installationId,
            @Valid @RequestBody DeskSupportSpaceService.ConfigureRequest request) {
        return service.configure(customerId, installationId, request);
    }
}
