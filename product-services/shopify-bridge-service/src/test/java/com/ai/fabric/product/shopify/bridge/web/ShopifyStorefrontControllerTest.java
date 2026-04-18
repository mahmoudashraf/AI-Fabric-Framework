package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontBootstrapService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret"
})
@AutoConfigureMockMvc
class ShopifyStorefrontControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShopifyStorefrontBootstrapService storefrontBootstrapService;

    @MockBean
    private ShopifyStorefrontChatService storefrontChatService;

    @Test
    void bootstrapIsPublicAndReturnsStorefrontMetadata() throws Exception {
        when(storefrontBootstrapService.bootstrap("alpha.myshopify.com")).thenReturn(new ShopifyStorefrontBootstrapResponse(
            true,
            "alpha.myshopify.com",
            "consumer-alpha",
            "dep-1",
            "ENABLED",
            "READY",
            "PRIVATE_RUNTIME_BACKEND_MEDIATED",
            "SIGNED_PRIVATE_RUNTIME",
            "/api/storefront/shops/alpha.myshopify.com/chat/query",
            "/api/storefront/shops/alpha.myshopify.com/chat/suggestions",
            "Route storefront traffic through the Shopify Bridge backend.",
            "Storefront bootstrap resolved."
        ));

        mockMvc.perform(get("/api/storefront/shops/alpha.myshopify.com/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.consumerId").value("consumer-alpha"))
            .andExpect(jsonPath("$.bridgeQueryUrl").value("/api/storefront/shops/alpha.myshopify.com/chat/query"));
    }

    @Test
    void queryForwardsPublicStorefrontTraffic() throws Exception {
        when(storefrontChatService.query(eq("alpha.myshopify.com"), eq(objectMapper.readTree("""
            {"query":"Show me backpacks"}
            """)), eq("shopper-session-1"))).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Here are some backpacks."}}
            """));

        mockMvc.perform(post("/api/storefront/shops/alpha.myshopify.com/chat/query")
                .header("X-AI-FABRIC-SHOPPER-SESSION-ID", "shopper-session-1")
                .contentType("application/json")
                .content("""
                    {"query":"Show me backpacks"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value("conv-1"));
    }
}
