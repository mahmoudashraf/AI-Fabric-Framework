package com.ai.fabric.platform.backend.shopify.web;

import com.ai.fabric.platform.backend.shopify.model.BootstrapShopifyStoreRequest;
import com.ai.fabric.platform.backend.shopify.model.CreateShopifyStoreProvisioningJobRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreBillingStateRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSourcePreflightRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreSyncStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWebhookEventRequest;
import com.ai.fabric.platform.backend.shopify.model.RecordShopifyStoreWidgetStatusRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBindingInspectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBillingStateSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreBootstrapSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreGovernedActionAuditSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreProvisioningJobSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreProvisioningStatusSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreResolvedCredentialsSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSupportProfileSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationEventSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSelectedEntitiesRequest;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreVectorizationSummary;
import com.ai.fabric.platform.backend.shopify.model.SyncShopifyStoreDocumentsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSourceSettingsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreSupportProfileRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreVectorizationPolicyRequest;
import com.ai.fabric.platform.backend.shopify.model.UpdateShopifyStoreWidgetSettingsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreCredentialsRequest;
import com.ai.fabric.platform.backend.shopify.model.UpsertShopifyStoreConnectionRequest;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreBootstrapService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreConnectionService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreCredentialService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreDocumentSyncService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreGovernedActionService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreGoLiveService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreProvisioningService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourcePreflightService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSourceSettingsService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSyncService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreUninstallService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreSupportProfileService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreVectorizationService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWebhookService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWidgetService;
import com.ai.fabric.platform.backend.shopify.service.ShopifyStoreWidgetSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/shopify/stores")
public class ShopifyAdminController {

    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreBootstrapService shopifyStoreBootstrapService;
    private final ShopifyStoreCredentialService shopifyStoreCredentialService;
    private final ShopifyStoreGoLiveService shopifyStoreGoLiveService;
    private final ShopifyStoreUninstallService shopifyStoreUninstallService;
    private final ShopifyStoreGovernedActionService shopifyStoreGovernedActionService;
    private final ShopifyStoreVectorizationService shopifyStoreVectorizationService;
    private final ShopifyStoreSourcePreflightService shopifyStoreSourcePreflightService;
    private final ShopifyStoreSourceSettingsService shopifyStoreSourceSettingsService;
    private final ShopifyStoreSyncService shopifyStoreSyncService;
    private final ShopifyStoreDocumentSyncService shopifyStoreDocumentSyncService;
    private final ShopifyStoreWebhookService shopifyStoreWebhookService;
    private final ShopifyStoreWidgetService shopifyStoreWidgetService;
    private final ShopifyStoreWidgetSettingsService shopifyStoreWidgetSettingsService;
    private final ShopifyStoreSupportProfileService shopifyStoreSupportProfileService;
    private final ShopifyStoreProvisioningService shopifyStoreProvisioningService;

    public ShopifyAdminController(ShopifyStoreConnectionService shopifyStoreConnectionService,
                                  ShopifyStoreBootstrapService shopifyStoreBootstrapService,
                                  ShopifyStoreCredentialService shopifyStoreCredentialService,
                                  ShopifyStoreGoLiveService shopifyStoreGoLiveService,
                                  ShopifyStoreUninstallService shopifyStoreUninstallService,
                                  ShopifyStoreGovernedActionService shopifyStoreGovernedActionService,
                                  ShopifyStoreVectorizationService shopifyStoreVectorizationService,
                                  ShopifyStoreSourcePreflightService shopifyStoreSourcePreflightService,
                                  ShopifyStoreSourceSettingsService shopifyStoreSourceSettingsService,
                                  ShopifyStoreSyncService shopifyStoreSyncService,
                                  ShopifyStoreDocumentSyncService shopifyStoreDocumentSyncService,
                                  ShopifyStoreWebhookService shopifyStoreWebhookService,
                                  ShopifyStoreWidgetService shopifyStoreWidgetService,
                                  ShopifyStoreWidgetSettingsService shopifyStoreWidgetSettingsService,
                                  ShopifyStoreSupportProfileService shopifyStoreSupportProfileService,
                                  ShopifyStoreProvisioningService shopifyStoreProvisioningService) {
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.shopifyStoreBootstrapService = shopifyStoreBootstrapService;
        this.shopifyStoreCredentialService = shopifyStoreCredentialService;
        this.shopifyStoreGoLiveService = shopifyStoreGoLiveService;
        this.shopifyStoreUninstallService = shopifyStoreUninstallService;
        this.shopifyStoreGovernedActionService = shopifyStoreGovernedActionService;
        this.shopifyStoreVectorizationService = shopifyStoreVectorizationService;
        this.shopifyStoreSourcePreflightService = shopifyStoreSourcePreflightService;
        this.shopifyStoreSourceSettingsService = shopifyStoreSourceSettingsService;
        this.shopifyStoreSyncService = shopifyStoreSyncService;
        this.shopifyStoreDocumentSyncService = shopifyStoreDocumentSyncService;
        this.shopifyStoreWebhookService = shopifyStoreWebhookService;
        this.shopifyStoreWidgetService = shopifyStoreWidgetService;
        this.shopifyStoreWidgetSettingsService = shopifyStoreWidgetSettingsService;
        this.shopifyStoreSupportProfileService = shopifyStoreSupportProfileService;
        this.shopifyStoreProvisioningService = shopifyStoreProvisioningService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or hasRole('PLATFORM_PRODUCT_SERVICE')")
    public List<ShopifyStoreConnectionSummary> listStores() {
        return shopifyStoreConnectionService.listConnections();
    }

    @GetMapping("/{shopDomain}")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary getStore(@PathVariable String shopDomain) {
        return shopifyStoreConnectionService.getConnection(shopDomain);
    }

    @GetMapping("/{shopDomain}/binding")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreBindingInspectionSummary inspectBinding(@PathVariable String shopDomain) {
        return shopifyStoreConnectionService.inspectBinding(shopDomain);
    }

    @GetMapping("/{shopDomain}/billing-state")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreBillingStateSummary getBillingState(@PathVariable String shopDomain) {
        return shopifyStoreConnectionService.getBillingState(shopDomain);
    }

    @PostMapping("/{shopDomain}/billing-state")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreBillingStateSummary recordBillingState(@PathVariable String shopDomain,
                                                              @Valid @RequestBody RecordShopifyStoreBillingStateRequest request) {
        return shopifyStoreConnectionService.recordBillingState(shopDomain, request);
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
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canUpsert(authentication, #request)")
    public ShopifyStoreConnectionSummary upsertStore(@Valid @RequestBody UpsertShopifyStoreConnectionRequest request) {
        return shopifyStoreConnectionService.upsertConnection(request);
    }

    @PostMapping("/{shopDomain}/credentials")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary upsertCredentials(@PathVariable String shopDomain,
                                                           @Valid @RequestBody UpsertShopifyStoreCredentialsRequest request) {
        return shopifyStoreCredentialService.upsert(shopDomain, request);
    }

    @DeleteMapping("/{shopDomain}/credentials")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    @ResponseStatus(HttpStatus.OK)
    public ShopifyStoreConnectionSummary clearCredentials(@PathVariable String shopDomain) {
        return shopifyStoreCredentialService.clear(shopDomain);
    }

    @PostMapping("/{shopDomain}/credentials/material")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ResponseEntity<ShopifyStoreResolvedCredentialsSummary> resolveCredentials(@PathVariable String shopDomain) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(shopifyStoreCredentialService.resolveMaterial(shopDomain));
    }

    @PostMapping("/{shopDomain}/bootstrap")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreBootstrapSummary bootstrapStore(@PathVariable String shopDomain,
                                                       @RequestBody(required = false) BootstrapShopifyStoreRequest request) {
        return shopifyStoreBootstrapService.bootstrap(shopDomain, request);
    }

    @GetMapping("/{shopDomain}/provisioning")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreProvisioningStatusSummary provisioningStatus(@PathVariable String shopDomain) {
        return shopifyStoreProvisioningService.getStatus(shopDomain);
    }

    @GetMapping("/{shopDomain}/provisioning-jobs")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public List<ShopifyStoreProvisioningJobSummary> provisioningJobs(@PathVariable String shopDomain) {
        return shopifyStoreProvisioningService.getStatus(shopDomain).recentJobs();
    }

    @PostMapping("/{shopDomain}/provisioning-jobs")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreProvisioningJobSummary enqueueProvisioningJob(@PathVariable String shopDomain,
                                                                     @RequestBody(required = false) CreateShopifyStoreProvisioningJobRequest request) {
        return shopifyStoreProvisioningService.enqueue(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/provisioning-jobs/{jobId}/process")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ShopifyStoreProvisioningJobSummary processProvisioningJob(@PathVariable String shopDomain,
                                                                     @PathVariable String jobId) {
        return shopifyStoreProvisioningService.processJobNow(shopDomain, jobId);
    }

    @PostMapping("/{shopDomain}/provisioning-jobs/{jobId}/run")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ShopifyStoreProvisioningJobSummary runProvisioningJob(@PathVariable String shopDomain,
                                                                 @PathVariable String jobId) {
        return shopifyStoreProvisioningService.processJobNow(shopDomain, jobId);
    }

    @PostMapping("/{shopDomain}/provisioning-jobs/{jobId}/retry")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ShopifyStoreProvisioningJobSummary retryProvisioningJob(@PathVariable String shopDomain,
                                                                   @PathVariable String jobId,
                                                                   @RequestBody(required = false) java.util.Map<String, String> request) {
        return shopifyStoreProvisioningService.retry(shopDomain, jobId, request == null ? null : request.get("reason"));
    }

    @PostMapping("/{shopDomain}/provisioning-jobs/{jobId}/cancel")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR')")
    public ShopifyStoreProvisioningJobSummary cancelProvisioningJob(@PathVariable String shopDomain,
                                                                    @PathVariable String jobId,
                                                                    @RequestBody(required = false) java.util.Map<String, String> request) {
        return shopifyStoreProvisioningService.cancel(shopDomain, jobId, request == null ? null : request.get("reason"));
    }

    @PostMapping("/{shopDomain}/go-live")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary goLive(@PathVariable String shopDomain) {
        return shopifyStoreGoLiveService.goLive(shopDomain);
    }

    @PostMapping("/{shopDomain}/uninstall")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary uninstall(@PathVariable String shopDomain) {
        return shopifyStoreUninstallService.markUninstalled(shopDomain, "Shopify app uninstall cleanup.");
    }

    @GetMapping("/{shopDomain}/actions/recent")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public List<ShopifyStoreGovernedActionAuditSummary> recentGovernedActions(@PathVariable String shopDomain,
                                                                              @RequestParam(defaultValue = "10") int limit) {
        return shopifyStoreGovernedActionService.recentActions(shopDomain, limit);
    }

    @GetMapping("/{shopDomain}/vectorization")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary vectorization(@PathVariable String shopDomain) {
        return shopifyStoreVectorizationService.getSummary(shopDomain);
    }

    @PostMapping("/{shopDomain}/vectorization/reconcile")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary reconcileVectorization(@PathVariable String shopDomain) {
        return shopifyStoreVectorizationService.reconcile(shopDomain);
    }

    @PostMapping("/{shopDomain}/vectorization/vectorize-now")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary vectorizeNow(@PathVariable String shopDomain) {
        return shopifyStoreVectorizationService.vectorizeNow(shopDomain);
    }

    @PostMapping("/{shopDomain}/vectorization/index-all")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary indexAllEnabledData(@PathVariable String shopDomain) {
        return shopifyStoreVectorizationService.indexAllEnabledData(shopDomain);
    }

    @PostMapping("/{shopDomain}/vectorization/reindex-all")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary reindexAllEnabledData(@PathVariable String shopDomain) {
        return shopifyStoreVectorizationService.reindexAllEnabledData(shopDomain);
    }

    @PostMapping("/{shopDomain}/vectorization/reindex-selected")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary reindexSelectedEntityTypes(@PathVariable String shopDomain,
                                                                       @RequestBody ShopifyStoreVectorizationSelectedEntitiesRequest request) {
        return shopifyStoreVectorizationService.reindexSelectedEntityTypes(shopDomain, request);
    }

    @PutMapping("/{shopDomain}/vectorization/policy")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary updateVectorizationPolicy(@PathVariable String shopDomain,
                                                                      @RequestBody UpdateShopifyStoreVectorizationPolicyRequest request) {
        return shopifyStoreVectorizationService.updatePolicy(shopDomain, request);
    }

    @GetMapping("/{shopDomain}/vectorization/events")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public List<ShopifyStoreVectorizationEventSummary> recentVectorizationEvents(@PathVariable String shopDomain,
                                                                                 @RequestParam(defaultValue = "20") int limit) {
        return shopifyStoreVectorizationService.recentEvents(shopDomain, limit);
    }

    @PostMapping("/{shopDomain}/vectorization/events/{eventId}/replay")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary replayVectorizationEvent(@PathVariable String shopDomain,
                                                                     @PathVariable String eventId) {
        return shopifyStoreVectorizationService.replayEvent(shopDomain, eventId);
    }

    @PostMapping("/{shopDomain}/vectorization/retry-last-failed-auto-run")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreVectorizationSummary retryLastFailedAutoRun(@PathVariable String shopDomain) {
        return shopifyStoreVectorizationService.retryLastFailedAutoRun(shopDomain);
    }

    @PostMapping("/{shopDomain}/source-preflight")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary recordSourcePreflight(@PathVariable String shopDomain,
                                                               @RequestBody RecordShopifyStoreSourcePreflightRequest request) {
        return shopifyStoreSourcePreflightService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/source-settings")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary updateSourceSettings(@PathVariable String shopDomain,
                                                              @RequestBody UpdateShopifyStoreSourceSettingsRequest request) {
        return shopifyStoreSourceSettingsService.update(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/sync-status")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary recordSyncStatus(@PathVariable String shopDomain,
                                                          @RequestBody RecordShopifyStoreSyncStatusRequest request) {
        return shopifyStoreSyncService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/documents/sync")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary syncDocuments(@PathVariable String shopDomain,
                                                       @RequestBody SyncShopifyStoreDocumentsRequest request) {
        return shopifyStoreDocumentSyncService.sync(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/webhook-events")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary recordWebhookEvent(@PathVariable String shopDomain,
                                                            @RequestBody RecordShopifyStoreWebhookEventRequest request) {
        return shopifyStoreWebhookService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/widget-status")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary recordWidgetStatus(@PathVariable String shopDomain,
                                                            @RequestBody RecordShopifyStoreWidgetStatusRequest request) {
        return shopifyStoreWidgetService.record(shopDomain, request);
    }

    @PostMapping("/{shopDomain}/widget-settings")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreConnectionSummary updateWidgetSettings(@PathVariable String shopDomain,
                                                              @RequestBody UpdateShopifyStoreWidgetSettingsRequest request) {
        return shopifyStoreWidgetSettingsService.update(shopDomain, request);
    }

    @GetMapping("/{shopDomain}/support-profile")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreSupportProfileSummary getSupportProfile(@PathVariable String shopDomain) {
        return shopifyStoreSupportProfileService.get(shopDomain);
    }

    @PostMapping("/{shopDomain}/support-profile")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_OPERATOR') or @shopifyStorePlatformAccessEvaluator.canAccess(authentication, #shopDomain)")
    public ShopifyStoreSupportProfileSummary updateSupportProfile(@PathVariable String shopDomain,
                                                                  @RequestBody UpdateShopifyStoreSupportProfileRequest request) {
        return shopifyStoreSupportProfileService.update(shopDomain, request);
    }
}
