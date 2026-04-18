package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import com.ai.fabric.product.shopify.bridge.diagnostics.service.ShopifyBridgeDiagnosticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class ShopifyBridgeAdminController {

    private final ShopifyBridgeDiagnosticsService diagnosticsService;

    public ShopifyBridgeAdminController(ShopifyBridgeDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/overview")
    public ShopifyBridgeOverviewResponse overview() {
        return diagnosticsService.overview();
    }
}
