package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateSourceSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import com.ai.fabric.product.shopify.bridge.playground.service.ShopifyMerchantPlaygroundService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeMerchantStoreService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class ShopifyMerchantController {

    private final ShopifyBridgeMerchantStoreService merchantStoreService;
    private final ShopifyMerchantPlaygroundService merchantPlaygroundService;

    public ShopifyMerchantController(ShopifyBridgeMerchantStoreService merchantStoreService,
                                     ShopifyMerchantPlaygroundService merchantPlaygroundService) {
        this.merchantStoreService = merchantStoreService;
        this.merchantPlaygroundService = merchantPlaygroundService;
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

    @GetMapping("/store/storefront-preview")
    public ShopifyStorefrontPreviewResponse storefrontPreview(Authentication authentication) {
        return merchantStoreService.storefrontPreview(requireMerchant(authentication));
    }

    @PostMapping("/store/source-settings")
    public ShopifyBridgeStoreSummary updateSourceSettings(Authentication authentication,
                                                          @RequestBody(required = false) ShopifyBridgeUpdateSourceSettingsRequest request) {
        return merchantStoreService.updateSourceSettings(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeUpdateSourceSettingsRequest(null, null, null, null) : request
        );
    }

    @PostMapping("/store/widget-settings")
    public ShopifyBridgeStoreSummary updateWidgetSettings(Authentication authentication,
                                                          @RequestBody(required = false) ShopifyBridgeUpdateWidgetSettingsRequest request) {
        return merchantStoreService.updateWidgetSettings(
            requireMerchant(authentication),
            request == null ? new ShopifyBridgeUpdateWidgetSettingsRequest(null, null) : request
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
