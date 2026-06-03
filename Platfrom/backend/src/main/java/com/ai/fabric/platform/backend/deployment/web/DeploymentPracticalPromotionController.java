package com.ai.fabric.platform.backend.deployment.web;

import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionActivationRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionRollbackRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPracticalPromotionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderResourceOrphanScanSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentPracticalPromotionService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments/{deploymentId}/practical-promotion")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class DeploymentPracticalPromotionController {

    private final DeploymentPracticalPromotionService promotionService;

    public DeploymentPracticalPromotionController(DeploymentPracticalPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    @PostMapping("/plan")
    public DeploymentPracticalPromotionSummary plan(@PathVariable String deploymentId,
                                                    @Valid @RequestBody(required = false) DeploymentPracticalPromotionRequest request) {
        return promotionService.planPromotion(deploymentId, request);
    }

    @PostMapping("/production-apply")
    public DeploymentPracticalPromotionSummary requestProductionApply(@PathVariable String deploymentId,
                                                                      @Valid @RequestBody(required = false) DeploymentPracticalPromotionRequest request) {
        return promotionService.requestProductionPromotion(deploymentId, request);
    }

    @PostMapping("/activate-production-consumer")
    public DeploymentPracticalPromotionSummary activateProductionConsumer(@PathVariable String deploymentId,
                                                                         @Valid @RequestBody DeploymentPracticalPromotionActivationRequest request) {
        return promotionService.activateProductionConsumer(deploymentId, request);
    }

    @PostMapping("/rollback-production-consumer")
    public DeploymentPracticalPromotionSummary rollbackProductionConsumer(@PathVariable String deploymentId,
                                                                         @Valid @RequestBody DeploymentPracticalPromotionRollbackRequest request) {
        return promotionService.rollbackProductionConsumer(deploymentId, request);
    }

    @GetMapping("/orphan-resources")
    public DeploymentProviderResourceOrphanScanSummary scanOrphanResources(@PathVariable String deploymentId,
                                                                           @RequestParam(defaultValue = "false") boolean mark) {
        return promotionService.scanResourceOrphans(deploymentId, mark);
    }
}
