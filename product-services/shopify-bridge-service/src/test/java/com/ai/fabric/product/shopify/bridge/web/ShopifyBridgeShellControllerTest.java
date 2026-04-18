package com.ai.fabric.product.shopify.bridge.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.app-name=Companion Shopify Bridge",
    "shopify.bridge.service-ref=shopify-bridge-test",
    "shopify.bridge.environment-scope=staging",
    "shopify.bridge.admin-api-key=test-admin-key"
})
@AutoConfigureMockMvc
class ShopifyBridgeShellControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shellIsPublicAndReturnsMerchantFacingMetadata() throws Exception {
        mockMvc.perform(get("/api/app/shell"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.appName").value("Companion Shopify Bridge"))
            .andExpect(jsonPath("$.serviceRef").value("shopify-bridge-test"))
            .andExpect(jsonPath("$.environmentScope").value("staging"))
            .andExpect(jsonPath("$.status").value("READY_FOR_ONBOARDING"))
            .andExpect(jsonPath("$.onboardingPhases[0]").value("Install and identity"))
            .andExpect(jsonPath("$.launchCapabilities[0]").value("Shopify embedded admin shell"));
    }
}
