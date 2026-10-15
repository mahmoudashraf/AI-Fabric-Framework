package com.ai.fabric.platform.backend.deployment.dependency;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/customer-self-service/staff/templates/{templateVersionId}/managed-dependency-spec")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ManagedDependencySpecController {
    private final ManagedDependencySpecPublishingService publishing;

    public ManagedDependencySpecController(ManagedDependencySpecPublishingService publishing) {
        this.publishing = publishing;
    }

    @PostMapping
    public ManagedDependencySpecPublishingService.PublicationSummary publish(
            @PathVariable String templateVersionId,
            @RequestBody ManagedDependencySpecPublishingService.PublicationRequest request) {
        return publishing.publish(templateVersionId, request);
    }
}
