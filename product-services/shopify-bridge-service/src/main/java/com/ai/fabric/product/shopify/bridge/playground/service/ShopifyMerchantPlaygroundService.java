package com.ai.fabric.product.shopify.bridge.playground.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class ShopifyMerchantPlaygroundService {

    private final ShopifyStorefrontChatService storefrontChatService;
    private final ShopifyBridgeUsageService usageService;

    public ShopifyMerchantPlaygroundService(ShopifyStorefrontChatService storefrontChatService,
                                            ShopifyBridgeUsageService usageService) {
        this.storefrontChatService = storefrontChatService;
        this.usageService = usageService;
    }

    public JsonNode query(ShopifyMerchantSession merchantSession, JsonNode request) {
        JsonNode response = storefrontChatService.query(
            merchantSession.shopDomain(),
            request,
            shopperSessionId(merchantSession)
        );
        usageService.recordQueryInsight(merchantSession.shopDomain(), "MERCHANT_PLAYGROUND_QUERY", request, "merchant-playground");
        return response;
    }

    public JsonNode suggestions(ShopifyMerchantSession merchantSession, JsonNode request) {
        JsonNode response = storefrontChatService.suggestions(
            merchantSession.shopDomain(),
            request,
            shopperSessionId(merchantSession)
        );
        usageService.recordEvent(merchantSession.shopDomain(), "MERCHANT_PLAYGROUND_SUGGESTIONS");
        return response;
    }

    private String shopperSessionId(ShopifyMerchantSession merchantSession) {
        String shop = merchantSession.shopDomain() == null ? "shop" : merchantSession.shopDomain().trim().toLowerCase(Locale.ROOT);
        String user = merchantSession.userId() == null ? "merchant" : merchantSession.userId().trim();
        return "merchant-playground:" + shop + ":" + user;
    }
}
