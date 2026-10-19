package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.retirement.ExternalIndexRetentionContracts.Authorization;
import com.ai.fabric.platform.backend.deployment.retirement.ExternalIndexRetentionContracts.Policy;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/platform/admission/reviews/external-index-retentions")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class ExternalIndexRetentionReviewController {
    private final ExternalIndexRetentionService retention;

    public ExternalIndexRetentionReviewController(ExternalIndexRetentionService retention) { this.retention = retention; }

    @PostMapping
    public Authorization approve(@RequestBody Policy policy) { return retention.approve(policy); }
}
