package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontBootstrapService;
import com.fasterxml.jackson.databind.JsonNode;
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

    public ShopifyStorefrontController(ShopifyStorefrontBootstrapService storefrontBootstrapService,
                                       ShopifyStorefrontChatService storefrontChatService) {
        this.storefrontBootstrapService = storefrontBootstrapService;
        this.storefrontChatService = storefrontChatService;
    }

    @GetMapping("/{shopDomain}/bootstrap")
    public ShopifyStorefrontBootstrapResponse bootstrap(@PathVariable String shopDomain) {
        return storefrontBootstrapService.bootstrap(shopDomain);
    }

    @PostMapping("/{shopDomain}/chat/query")
    public JsonNode query(@PathVariable String shopDomain,
                          @RequestBody(required = false) JsonNode request,
                          @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                          String shopperSessionId) {
        return storefrontChatService.query(shopDomain, request, shopperSessionId);
    }

    @PostMapping("/{shopDomain}/chat/suggestions")
    public JsonNode suggestions(@PathVariable String shopDomain,
                                @RequestBody(required = false) JsonNode request,
                                @RequestHeader(value = SHOPPER_SESSION_HEADER, required = false)
                                String shopperSessionId) {
        return storefrontChatService.suggestions(shopDomain, request, shopperSessionId);
    }
}
