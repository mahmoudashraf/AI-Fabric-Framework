package com.ai.fabric.product.shopify.bridge.playground.service;

import com.ai.fabric.product.shopify.bridge.auth.ShopifyMerchantSession;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ShopifyMerchantPlaygroundServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryUsesMerchantScopedSyntheticShopperSession() throws Exception {
        ShopifyStorefrontChatService storefrontChatService = mock(ShopifyStorefrontChatService.class);
        ShopifyMerchantPlaygroundService service = new ShopifyMerchantPlaygroundService(storefrontChatService);

        service.query(
            new ShopifyMerchantSession(
                "alpha.myshopify.com",
                "https://alpha.myshopify.com",
                "gid://shopify/User/1",
                java.time.Instant.parse("2026-04-18T12:00:00Z")
            ),
            objectMapper.readTree("""
                {"query":"Show me backpacks"}
                """)
        );

        verify(storefrontChatService).query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {"query":"Show me backpacks"}
                """),
            "merchant-playground:alpha.myshopify.com:gid://shopify/User/1"
        );
    }
}
