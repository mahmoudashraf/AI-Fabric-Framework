package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.diagnostics.model.ShopifyBridgeOverviewResponse;
import com.ai.fabric.product.shopify.bridge.diagnostics.service.ShopifyBridgeDiagnosticsService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourcePageResponse;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeStoreAdminService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.ai.fabric.product.shopify.bridge.webhook.service.ShopifyWebhookSubscriptionDiagnosticsService;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService;

    public ShopifyBridgeAdminController(ShopifyBridgeDiagnosticsService diagnosticsService,
                                        ShopifyBridgeStoreAdminService storeAdminService,
                                        ShopifyWebhookSubscriptionDiagnosticsService webhookSubscriptionDiagnosticsService) {
        this.diagnosticsService = diagnosticsService;
        this.storeAdminService = storeAdminService;
        this.webhookSubscriptionDiagnosticsService = webhookSubscriptionDiagnosticsService;
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

    @GetMapping("/stores/{shopDomain}/billing-summary")
    public ShopifyBridgeBillingSummary billingSummary(@PathVariable String shopDomain) {
        return storeAdminService.billingSummary(shopDomain);
    }

    @GetMapping("/stores/{shopDomain}/vectorization-source/{entityType}")
    public ShopifyBridgeVectorizationSourcePageResponse vectorizationSourcePage(@PathVariable String shopDomain,
                                                                                @PathVariable String entityType,
                                                                                @org.springframework.web.bind.annotation.RequestParam(required = false) String cursor,
                                                                                @org.springframework.web.bind.annotation.RequestParam(required = false) Integer limit) {
        return storeAdminService.vectorizationSourcePage(shopDomain, entityType, cursor, limit);
    }

    @GetMapping("/stores/{shopDomain}/webhook-subscriptions")
    public ShopifyWebhookSubscriptionStatusSummary webhookSubscriptions(@PathVariable String shopDomain) {
        return webhookSubscriptionDiagnosticsService.forShop(shopDomain);
    }

    @PostMapping("/stores/{shopDomain}/bootstrap")
    public ShopifyBridgeStoreBootstrapResponse bootstrap(@PathVariable String shopDomain) {
        return storeAdminService.bootstrap(shopDomain);
    }

    @PostMapping("/stores/{shopDomain}/run-source-preflight")
    public ShopifyBridgeStoreSummary runSourcePreflight(@PathVariable String shopDomain) {
        return storeAdminService.runSourcePreflight(shopDomain);
    }

    @PostMapping("/stores/{shopDomain}/source-preflight")
    public ShopifyBridgeStoreSummary recordSourcePreflight(@PathVariable String shopDomain,
                                                           @RequestBody ShopifyBridgeRecordSourcePreflightRequest request) {
        return storeAdminService.recordSourcePreflight(shopDomain, request);
    }

    @PostMapping("/stores/{shopDomain}/sync-status")
    public ShopifyBridgeStoreSummary recordSyncStatus(@PathVariable String shopDomain,
                                                      @RequestBody ShopifyBridgeRecordSyncStatusRequest request) {
        return storeAdminService.recordSyncStatus(shopDomain, request);
    }

    @PostMapping("/stores/{shopDomain}/widget-status")
    public ShopifyBridgeStoreSummary recordWidgetStatus(@PathVariable String shopDomain,
                                                        @RequestBody ShopifyBridgeRecordWidgetStatusRequest request) {
        return storeAdminService.recordWidgetStatus(shopDomain, request);
    }
}
