package com.ai.fabric.platform.backend.deployment.budget;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('CUSTOMER_ADMIN','PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class RerankingUsageStatusController {
    private final RerankingUsageStatusService service;
    public RerankingUsageStatusController(RerankingUsageStatusService service) { this.service = service; }

    @GetMapping("/api/deployments/{deploymentId}/reranking-usage")
    public RerankingUsageStatusService.Status read(@PathVariable String deploymentId, @RequestParam String targetProfileId) {
        return service.read(deploymentId, targetProfileId);
    }
}
