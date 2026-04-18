package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeMerchantSessionResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyBridgeMerchantStoreService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class ShopifyMerchantController {

    private final ShopifyBridgeMerchantStoreService merchantStoreService;

    public ShopifyMerchantController(ShopifyBridgeMerchantStoreService merchantStoreService) {
        this.merchantStoreService = merchantStoreService;
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

    private ShopifyMerchantSession requireMerchant(Authentication authentication) {
        Object principal = authentication == null ? null : authentication.getPrincipal();
        if (principal instanceof ShopifyMerchantSession merchantSession) {
            return merchantSession;
        }
        throw new IllegalStateException("Missing Shopify merchant authentication.");
    }
}
