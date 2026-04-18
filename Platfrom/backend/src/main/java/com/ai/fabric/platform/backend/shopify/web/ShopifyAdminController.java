package com.ai.fabric.platform.backend.shopify.web;

import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSourcePreflightRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSyncStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWebhookEventRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWidgetStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.SyncShopifyStoreDocumentsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreWidgetSettingsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreCredentialsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreBootstrapService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreConnectionService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreCredentialService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreDocumentSyncService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreGoLiveService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourcePreflightService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSyncService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreUninstallService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWebhookService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWidgetService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWidgetSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shopify/stores")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
public class ShopifyAdminController {

    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreBootstrapService shopifyStoreBootstrapService;
    private final ShopifyStoreCredentialService shopifyStoreCredentialService;
    private final ShopifyStoreGoLiveService shopifyStoreGoLiveService;
    private final ShopifyStoreUninstallService shopifyStoreUninstallService;
    private final ShopifyStoreSourcePreflightService shopifyStoreSourcePreflightService;
    private final ShopifyStoreSyncService shopifyStoreSyncService;
    private final ShopifyStoreDocumentSyncService shopifyStoreDocumentSyncService;
    private final ShopifyStoreWebhookService shopifyStoreWebhookService;
    private final ShopifyStoreWidgetService shopifyStoreWidgetService;
    private final ShopifyStoreWidgetSettingsService shopifyStoreWidgetSettingsService;

    public ShopifyAdminController(ShopifyStoreConnectionService shopifyStoreConnectionService,
                                  ShopifyStoreBootstrapService shopifyStoreBootstrapService,
                                  ShopifyStoreCredentialService shopifyStoreCredentialService,
                                  ShopifyStoreGoLiveService shopifyStoreGoLiveService,
                                  ShopifyStoreUninstallService shopifyStoreUninstallService,
                                  ShopifyStoreSourcePreflightService shopifyStoreSourcePreflightService,
                                  ShopifyStoreSyncService shopifyStoreSyncService,
                                  ShopifyStoreDocumentSyncService shopifyStoreDocumentSyncService,
                                  ShopifyStoreWebhookService shopifyStoreWebhookService,
                                  ShopifyStoreWidgetService shopifyStoreWidgetService,
                                  ShopifyStoreWidgetSettingsService shopifyStoreWidgetSettingsService) {
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.shopifyStoreBootstrapService = shopifyStoreBootstrapService;
        this.shopifyStoreCredentialService = shopifyStoreCredentialService;
        this.shopifyStoreGoLiveService = shopifyStoreGoLiveService;
        this.shopifyStoreUninstallService = shopifyStoreUninstallService;
        this.shopifyStoreSourcePreflightService = shopifyStoreSourcePreflightService;
        this.shopifyStoreSyncService = shopifyStoreSyncService;
        this.shopifyStoreDocumentSyncService = shopifyStoreDocumentSyncService;
        this.shopifyStoreWebhookService = shopifyStoreWebhookService;
        this.shopifyStoreWidgetService = shopifyStoreWidgetService;
        this.shopifyStoreWidgetSettingsService = shopifyStoreWidgetSettingsService;
    }

    @GetMapping
    public List<ShopifyStoreConnectionSummary> listStores() {
        return shopifyStoreConnectionService.listConnections();
    }

    @GetMapping("/{shopDomain}")
    public ShopifyStoreConnectionSummary getStore(@PathVariable String shopDomain) {
        return shopifyStoreConnectionService.getConnection(shopDomain);
    }

    @DeleteMapping("/{shopDomain}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public void deleteStore(@PathVariable String shopDomain,
                            @RequestParam(name = "force", defaultValue = "false") boolean force) {
        shopifyStoreConnectionService.deleteConnection(shopDomain, force);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ShopifyStoreConnectionSummary upsertStore(@Valid @RequestBody UpsertShopifyStoreConnectionRequest request) {
        return shopifyStoreConnectionService.upsertConnection(request);
    }

    @PostMapping("/{shopDomain}/credentials")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyStoreConnectionSummary upsertCredentials(@PathVariable String shopDomain,
                                                           @Valid @RequestBody UpsertShopifyStoreCredentialsRequest request) {
        return shopifyStoreCredentialService.upsert(shopDomain, request);
    }

    @DeleteMapping("/{shopDomain}/credentials")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    @ResponseStatus(HttpStatus.OK)
    public ShopifyStoreConnectionSummary clearCredentials(@PathVariable String shopDomain) {
        return shopifyStoreCredentialService.clear(shopDomain);
    }

    @PostMapping("/{shopDomain}/bootstrap")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyStoreBootstrapSummary bootstrapStore(@PathVariable String shopDomain,
                                                       @RequestBody(required = false) BootstrapShopifyStoreRequest request) {
        return shopifyStoreBootstrapService.bootstrap(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/go-live")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyStoreConnectionSummary goLive(@PathVariable String shopDomain) {
        return shopifyStoreGoLiveService.goLive(shopDomain);
    }

    @PostMapping("/{shopDomain}/uninstall")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ShopifyStoreConnectionSummary uninstall(@PathVariable String shopDomain) {
        return shopifyStoreUninstallService.markUninstalled(shopDomain, "Shopify app uninstall cleanup.");
    }

    @PostMapping("/{shopDomain}/source-preflight")
    public ShopifyStoreConnectionSummary recordSourcePreflight(@PathVariable String shopDomain,
                                                               @RequestBody RecordShopifyStoreSourcePreflightRequest request) {
        return shopifyStoreSourcePreflightService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/sync-status")
    public ShopifyStoreConnectionSummary recordSyncStatus(@PathVariable String shopDomain,
                                                          @RequestBody RecordShopifyStoreSyncStatusRequest request) {
        return shopifyStoreSyncService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/documents/sync")
    public ShopifyStoreConnectionSummary syncDocuments(@PathVariable String shopDomain,
                                                       @RequestBody SyncShopifyStoreDocumentsRequest request) {
        return shopifyStoreDocumentSyncService.sync(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/webhook-events")
    public ShopifyStoreConnectionSummary recordWebhookEvent(@PathVariable String shopDomain,
                                                            @RequestBody RecordShopifyStoreWebhookEventRequest request) {
        return shopifyStoreWebhookService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/widget-status")
    public ShopifyStoreConnectionSummary recordWidgetStatus(@PathVariable String shopDomain,
                                                            @RequestBody RecordShopifyStoreWidgetStatusRequest request) {
        return shopifyStoreWidgetService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/widget-settings")
    public ShopifyStoreConnectionSummary updateWidgetSettings(@PathVariable String shopDomain,
                                                              @RequestBody UpdateShopifyStoreWidgetSettingsRequest request) {
        return shopifyStoreWidgetSettingsService.update(shopDomain, request);
    }
}
