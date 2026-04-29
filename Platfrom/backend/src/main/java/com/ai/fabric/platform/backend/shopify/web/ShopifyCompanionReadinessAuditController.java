package com.ai.fabric.platform.backend.shopify.web;

import com.ai.fabric.platform.backend.shopify.model.ShopifyReadinessAuditDefinitionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyReadinessAuditStateSummary;
import com.ai.fabric.platform.backend.shopify.service.ShopifyCompanionReadinessAuditService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/shopify/readiness-audit")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class ShopifyCompanionReadinessAuditController {

    private final ShopifyCompanionReadinessAuditService readinessAuditService;

    public ShopifyCompanionReadinessAuditController(ShopifyCompanionReadinessAuditService readinessAuditService) {
        this.readinessAuditService = readinessAuditService;
    }

    @GetMapping("/definition")
    public ShopifyReadinessAuditDefinitionSummary definition() {
        return readinessAuditService.definition();
    }

    @GetMapping("/latest")
    public ShopifyReadinessAuditStateSummary latest() {
        return readinessAuditService.latest();
    }
}
