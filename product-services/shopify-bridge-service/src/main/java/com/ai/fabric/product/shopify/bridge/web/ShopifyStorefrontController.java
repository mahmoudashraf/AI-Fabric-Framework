package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontEngagementEventRequest;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontEngagementService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontBootstrapService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/storefront/shops")
public class ShopifyStorefrontController {

    private static final String SHOPPER_SESSION_HEADER = "X-AI-FABRIC-SHOPPER-SESSION-ID";

    private final ShopifyStorefrontBootstrapService storefrontBootstrapService;
    private final ShopifyStorefrontChatService storefrontChatService;
    private final ShopifyStorefrontEngagementService storefrontEngagementService;
    private final ShopifyBridgeUsageService usageService;

    public ShopifyStorefrontController(ShopifyStorefrontBootstrapService storefrontBootstrapService,
                                       ShopifyStorefrontChatService storefrontChatService,
                                       ShopifyStorefrontEngagementService storefrontEngagementService,
                                       ShopifyBridgeUsageService usageService) {
        this.storefrontBootstrapService = storefrontBootstrapService;
        this.storefrontChatService = storefrontChatService;
        this.storefrontEngagementService = storefrontEngagementService;
        this.usageService = usageService;
    }

    @GetMapping("/{shopDomain}/bootstrap")
    public ShopifyStorefrontBootstrapResponse bootstrap(@PathVariable String shopDomain) {
        ShopifyStorefrontBootstrapResponse response = storefrontBootstrapService.bootstrap(shopDomain);
        if (response.available()) {
            usageService.recordEvent(shopDomain, "STOREFRONT_BOOTSTRAP");
        }
        return response;
    }

    @PostMapping("/{shopDomain}/chat/query")
    public JsonNode query(@PathVariable String shopDomain,
                          @RequestBody(required = false) JsonNode request,
                          @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                          String shopperSessionId) {
        JsonNode response = storefrontChatService.query(shopDomain, request, shopperSessionId);
        usageService.recordEvent(shopDomain, "STOREFRONT_QUERY");
        usageService.recordQueryInsight(shopDomain, "STOREFRONT_QUERY", request, "launcher");
        return response;
    }

    @PostMapping("/{shopDomain}/chat/suggestions")
    public JsonNode suggestions(@PathVariable String shopDomain,
                                @RequestBody(required = false) JsonNode request,
                                @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                String shopperSessionId) {
        JsonNode response = storefrontChatService.suggestions(shopDomain, request, shopperSessionId);
        usageService.recordEvent(shopDomain, "STOREFRONT_SUGGESTIONS");
        return response;
    }

    @PostMapping("/{shopDomain}/events")
    public ResponseEntity<Void> event(@PathVariable String shopDomain,
                                      @RequestBody(required = false) ShopifyStorefrontEngagementEventRequest request,
                                      @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                      String shopperSessionId) {
        storefrontEngagementService.record(shopDomain, request, shopperSessionId);
        return ResponseEntity.accepted().build();
    }
}
