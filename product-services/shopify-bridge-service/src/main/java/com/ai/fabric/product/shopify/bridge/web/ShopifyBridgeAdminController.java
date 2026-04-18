package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import com.ai.fabric.product.shopify.bridge.diagnostics.service.ShopifyBridgeDiagnosticsService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreAdminService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class ShopifyBridgeAdminController {

    private final ShopifyBridgeDiagnosticsService diagnosticsService;
    private final ShopifyBridgeStoreAdminService storeAdminService;

    public ShopifyBridgeAdminController(ShopifyBridgeDiagnosticsService diagnosticsService,
                                        ShopifyBridgeStoreAdminService storeAdminService) {
        this.diagnosticsService = diagnosticsService;
        this.storeAdminService = storeAdminService;
    }

    @GetMapping("/overview")
    public ShopifyBridgeOverviewResponse overview() {
        return diagnosticsService.overview();
    }

    @GetMapping("/stores")
    public List<ShopifyBridgeStoreSummary> listStores() {
        return storeAdminService.listStores();
    }

    @GetMapping("/stores/{shopDomain}")
    public ShopifyBridgeStoreSummary getStore(@PathVariable String shopDomain) {
        return storeAdminService.getStore(shopDomain);
    }

    @PostMapping("/stores/{shopDomain}/bootstrap")
    public ShopifyBridgeStoreBootstrapResponse bootstrap(@PathVariable String shopDomain) {
        return storeAdminService.bootstrap(shopDomain);
    }
}
