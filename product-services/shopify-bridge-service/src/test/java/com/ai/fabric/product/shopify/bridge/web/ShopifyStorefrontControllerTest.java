package com.ai.fabric.product.shopify.bridge.web;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontEngagementEventRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontBootstrapService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontChatService;
import com.ai.fabric.product.shopify.bridge.storefront.service.ShopifyStorefrontEngagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.shopify-api-key=test-shopify-api-key",
    "shopify.bridge.shopify-api-secret=test-shopify-secret",
    "shopify.bridge.public-base-url=https://bridge.example.com"
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

    @MockBean
    private ShopifyStorefrontEngagementService storefrontEngagementService;

    @MockBean
    private ShopifyBridgeUsageService usageService;

    @Test
    void bootstrapIsPublicAndReturnsStorefrontMetadata() throws Exception {
        when(storefrontBootstrapService.bootstrap("alpha.myshopify.com")).thenReturn(new ShopifyStorefrontBootstrapResponse(
            true,
            "alpha.myshopify.com",
            "consumer-alpha",
            "dep-1",
            "ENABLED",
            "READY",
            "FREE",
            "ACTIVE",
            50,
            true,
            false,
            "Need help?",
            "Ask me about products and policies.",
            "SHOPIFY_COMPANION",
            List.of("ai-search"),
            "PRIVATE_RUNTIME_BACKEND_MEDIATED",
            "SIGNED_PRIVATE_RUNTIME",
            "https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/chat/query",
            "https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/chat/suggestions",
            "https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/events",
            "Route storefront traffic through the Shopify Bridge backend.",
            "Storefront bootstrap resolved."
        ));

        mockMvc.perform(get("/api/storefront/shops/alpha.myshopify.com/bootstrap"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.available").value(true))
            .andExpect(jsonPath("$.consumerId").value("consumer-alpha"))
            .andExpect(jsonPath("$.billingTier").value("FREE"))
            .andExpect(jsonPath("$.chatFallbackEnabled").value(false))
            .andExpect(jsonPath("$.shellModeProfile").value("SHOPIFY_COMPANION"))
            .andExpect(jsonPath("$.enabledSurfaces[0]").value("ai-search"))
            .andExpect(jsonPath("$.bridgeQueryUrl").value("https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/chat/query"))
            .andExpect(jsonPath("$.bridgeEventUrl").value("https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/events"));

        verify(usageService).recordEvent("alpha.myshopify.com", "STOREFRONT_BOOTSTRAP");
    }

    @Test
    void storefrontCorsPreflightAllowsThemeOrigin() throws Exception {
        mockMvc.perform(options("/api/storefront/shops/alpha.myshopify.com/chat/query")
                .header("Origin", "https://shopping-companion-test.myshopify.com")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-ai-fabric-shopper-session-id"))
            .andExpect(status().isOk())
            .andExpect(header().string("Access-Control-Allow-Origin", "https://shopping-companion-test.myshopify.com"));
    }

    @Test
    void queryForwardsPublicStorefrontTraffic() throws Exception {
        when(storefrontChatService.query(eq("alpha.myshopify.com"), eq(objectMapper.readTree("""
            {
              "query":"Show me backpacks",
              "storefrontContext":{
                "pageType":"product",
                "product":{"handle":"travel-pack","title":"Travel Pack"}
              }
            }
            """)), eq("shopper-session-1"))).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Here are some backpacks."}}
            """));

        mockMvc.perform(post("/api/storefront/shops/alpha.myshopify.com/chat/query")
                .header("X-AI-FABRIC-SHOPPER-SESSION-ID", "shopper-session-1")
                .contentType("application/json")
                .content("""
                    {
                      "query":"Show me backpacks",
                      "storefrontContext":{
                        "pageType":"product",
                        "product":{"handle":"travel-pack","title":"Travel Pack"}
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.conversationId").value("conv-1"));

        verify(usageService).recordEvent("alpha.myshopify.com", "STOREFRONT_QUERY");
        verify(usageService).recordQueryInsight(
            eq("alpha.myshopify.com"),
            eq("STOREFRONT_QUERY"),
            eq(objectMapper.readTree("""
                {
                  "query":"Show me backpacks",
                  "storefrontContext":{
                    "pageType":"product",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """)),
            eq("launcher")
        );
    }

    @Test
    void eventEndpointRecordsBoundedStorefrontEngagementEvent() throws Exception {
        mockMvc.perform(post("/api/storefront/shops/alpha.myshopify.com/events")
                .header("X-AI-FABRIC-SHOPPER-SESSION-ID", "shopper-session-1")
                .contentType("application/json")
                .content("""
                    {
                      "eventType":"WIDGET_OPENED",
                      "pageType":"product",
                      "pageTitle":"Travel Pack",
                      "productHandle":"travel-pack"
                    }
                    """))
            .andExpect(status().isAccepted());

        verify(storefrontEngagementService).record(
            eq("alpha.myshopify.com"),
            eq(new ShopifyStorefrontEngagementEventRequest(
                "WIDGET_OPENED",
                "product",
                "Travel Pack",
                "travel-pack",
                null
            )),
            eq("shopper-session-1")
        );
    }
}
