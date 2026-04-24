package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.analytics.model.ShopifyBridgeUsageSummary;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalResponse;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingApprovalRequest;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyBridgeGovernedActionAuditSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationEventSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSelectedEntitiesRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreVectorizationSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateStoreVectorizationPolicyRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSourceSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSupportProfileRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import com.ai.fabric.product.shopify.bridge.playground.service.ShopifyMerchantPlaygroundService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeMerchantStoreService;
import com.ai.fabric.product.shopify.bridge.webhook.model.ShopifyWebhookSubscriptionStatusSummary;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class ShopifyMerchantController {

    private final ShopifyBridgeMerchantStoreService merchantStoreService;
    private final ShopifyMerchantPlaygroundService merchantPlaygroundService;
    private final ShopifyBridgeUsageService usageService;

    public ShopifyMerchantController(ShopifyBridgeMerchantStoreService merchantStoreService,
                                     ShopifyMerchantPlaygroundService merchantPlaygroundService,
                                     ShopifyBridgeUsageService usageService) {
        this.merchantStoreService = merchantStoreService;
        this.merchantPlaygroundService = merchantPlaygroundService;
        this.usageService = usageService;
    }

    @GetMapping("/session")
    public ShopifyBridgeMerchantSessionResponse session(Authentication authentication,
                                                        @RequestHeader(name = "X-Shopify-Embedded-Host", required = false) String embeddedHost) {
        return merchantStoreService.session(requireMerchant(authentication), embeddedHost);
    }

    @PostMapping("/store/connect")
    public ShopifyBridgeStoreSummary connect(Authentication authentication,
                                             @RequestHeader("Authorization") String authorizationHeader) {
        return merchantStoreService.connect(requireMerchant(authentication), authorizationHeader);
    }

    @PostMapping("/store/source-preflight")
    public ShopifyBridgeStoreSummary runSourcePreflight(Authentication authentication,
                                                        @RequestHeader("Authorization") String authorizationHeader) {
        return merchantStoreService.runSourcePreflight(requireMerchant(authentication), authorizationHeader);
    }

    @PostMapping("/store/bootstrap")
    public ShopifyBridgeStoreBootstrapResponse bootstrap(Authentication authentication,
                                                         @RequestHeader("Authorization") String authorizationHeader) {
        return merchantStoreService.bootstrap(requireMerchant(authentication), authorizationHeader);
    }

    @PostMapping("/store/go-live")
    public ShopifyBridgeStoreSummary goLive(Authentication authentication,
                                            @RequestHeader("Authorization") String authorizationHeader) {
        return merchantStoreService.goLive(requireMerchant(authentication), authorizationHeader);
    }

    @PostMapping("/store/sync-now")
    public ShopifyBridgeStoreSummary syncNow(Authentication authentication,
                                             @RequestHeader("Authorization") String authorizationHeader) {
        return merchantStoreService.syncNow(requireMerchant(authentication), authorizationHeader);
    }

    @GetMapping("/store/vectorization")
    public ShopifyBridgeStoreVectorizationSummary vectorization(Authentication authentication) {
        return merchantStoreService.vectorization(requireMerchant(authentication));
    }

    @PostMapping("/store/vectorization/reconcile")
    public ShopifyBridgeStoreVectorizationSummary reconcileVectorization(Authentication authentication) {
        return merchantStoreService.reconcileVectorization(requireMerchant(authentication));
    }

    @PostMapping("/store/vectorization/vectorize-now")
    public ShopifyBridgeStoreVectorizationSummary vectorizeNow(Authentication authentication) {
        return merchantStoreService.vectorizeNow(requireMerchant(authentication));
    }

    @PostMapping("/store/vectorization/index-all")
    public ShopifyBridgeStoreVectorizationSummary indexAllEnabledData(Authentication authentication) {
        return merchantStoreService.indexAllEnabledData(requireMerchant(authentication));
    }

    @PostMapping("/store/vectorization/reindex-all")
    public ShopifyBridgeStoreVectorizationSummary reindexAllEnabledData(Authentication authentication) {
        return merchantStoreService.reindexAllEnabledData(requireMerchant(authentication));
    }

    @PostMapping("/store/vectorization/reindex-selected")
    public ShopifyBridgeStoreVectorizationSummary reindexSelected(Authentication authentication,
                                                                  @RequestBody(required = false) ShopifyBridgeStoreVectorizationSelectedEntitiesRequest request) {
        return merchantStoreService.reindexSelectedEntityTypes(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeStoreVectorizationSelectedEntitiesRequest(null) : request
        );
    }

    @PutMapping("/store/vectorization/policy")
    public ShopifyBridgeStoreVectorizationSummary updateVectorizationPolicy(Authentication authentication,
                                                                            @RequestBody(required = false) ShopifyBridgeUpdateStoreVectorizationPolicyRequest request) {
        return merchantStoreService.updateVectorizationPolicy(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeUpdateStoreVectorizationPolicyRequest(null, null) : request
        );
    }

    @GetMapping("/store/vectorization/events")
    public java.util.List<ShopifyBridgeStoreVectorizationEventSummary> vectorizationEvents(
        Authentication authentication,
        @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return merchantStoreService.vectorizationEvents(requireMerchant(authentication), limit);
    }

    @PostMapping("/store/vectorization/events/{eventId}/replay")
    public ShopifyBridgeStoreVectorizationSummary replayVectorizationEvent(Authentication authentication,
                                                                           @PathVariable String eventId) {
        return merchantStoreService.replayVectorizationEvent(requireMerchant(authentication), eventId);
    }

    @PostMapping("/store/vectorization/retry-last-failed-auto-run")
    public ShopifyBridgeStoreVectorizationSummary retryLastFailedAutoRun(Authentication authentication) {
        return merchantStoreService.retryLastFailedAutoRun(requireMerchant(authentication));
    }

    @GetMapping("/store/storefront-preview")
    public ShopifyStorefrontPreviewResponse storefrontPreview(Authentication authentication) {
        return merchantStoreService.storefrontPreview(requireMerchant(authentication));
    }

    @GetMapping("/store/usage-summary")
    public ShopifyBridgeUsageSummary usageSummary(Authentication authentication) {
        return usageService.summarize(requireMerchant(authentication).shopDomain());
    }

    @GetMapping("/store/billing-summary")
    public ShopifyBridgeBillingSummary billingSummary(Authentication authentication) {
        return merchantStoreService.billingSummary(requireMerchant(authentication));
    }

    @GetMapping("/store/support-readiness")
    public ShopifyBridgeSupportReadinessSummary supportReadiness(Authentication authentication) {
        return merchantStoreService.supportReadiness(requireMerchant(authentication));
    }

    @PostMapping("/store/support-profile")
    public ShopifyBridgeSupportReadinessSummary updateSupportProfile(Authentication authentication,
                                                                    @RequestBody(required = false) ShopifyBridgeUpdateSupportProfileRequest request) {
        return merchantStoreService.updateSupportProfile(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeUpdateSupportProfileRequest(null, null, null, null, null) : request
        );
    }

    @GetMapping("/store/actions/recent")
    public java.util.List<ShopifyBridgeGovernedActionAuditSummary> recentGovernedActions(
        Authentication authentication,
        @RequestParam(name = "limit", defaultValue = "10") int limit
    ) {
        return merchantStoreService.recentGovernedActions(requireMerchant(authentication), limit);
    }

    @PostMapping("/store/billing/approval")
    public ShopifyBridgeBillingApprovalResponse billingApproval(Authentication authentication,
                                                                @RequestHeader("Authorization") String authorizationHeader,
                                                                @RequestBody(required = false) ShopifyBridgeBillingApprovalRequest request) {
        return merchantStoreService.requestBillingApproval(requireMerchant(authentication), authorizationHeader, request);
    }

    @GetMapping("/store/webhook-subscriptions")
    public ShopifyWebhookSubscriptionStatusSummary webhookSubscriptions(Authentication authentication) {
        return merchantStoreService.webhookSubscriptions(requireMerchant(authentication));
    }

    @PostMapping("/store/source-settings")
    public ShopifyBridgeStoreSummary updateSourceSettings(Authentication authentication,
                                                          @RequestBody(required = false) ShopifyBridgeUpdateSourceSettingsRequest request) {
        return merchantStoreService.updateSourceSettings(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeUpdateSourceSettingsRequest(null, null, null, null, null, null) : request
        );
    }

    @PostMapping("/store/widget-settings")
    public ShopifyBridgeStoreSummary updateWidgetSettings(Authentication authentication,
                                                          @RequestBody(required = false) ShopifyBridgeUpdateWidgetSettingsRequest request) {
        return merchantStoreService.updateWidgetSettings(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeUpdateWidgetSettingsRequest(null, null, null, null, null, null, null) : request
        );
    }

    @PostMapping("/store/playground/query")
    public JsonNode queryPlayground(Authentication authentication,
                                    @RequestBody(required = false) JsonNode request) {
        return merchantPlaygroundService.query(requireMerchant(authentication), request);
    }

    @PostMapping("/store/playground/suggestions")
    public JsonNode suggestPlayground(Authentication authentication,
                                      @RequestBody(required = false) JsonNode request) {
        return merchantPlaygroundService.suggestions(requireMerchant(authentication), request);
    }

    private ShopifyMerchantSession requireMerchant(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof ShopifyMerchantSession merchantSession) {
            return merchantSession;
        }
        throw new IllegalStateException("Missing Shopify merchant authentication.");
    }
}
