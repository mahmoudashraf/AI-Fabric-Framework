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
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.service-ref=shopify-bridge-test",
    "shopify.bridge.platform-base-url=https://platform.example.com"
})
@AutoConfigureMockMvc
class ShopifyBridgeAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void adminOverviewRequiresApiKey() throws Exception {
        mockMvc.perform(get("/api/admin/overview"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void adminOverviewReturnsDiagnosticsWhenApiKeyMatches() throws Exception {
        mockMvc.perform(get("/api/admin/overview").header("X-BRIDGE-API-KEY", "test-admin-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.serviceRef").value("shopify-bridge-test"))
            .andExpect(jsonPath("$.platformBaseUrl").value("https://platform.example.com"))
            .andExpect(jsonPath("$.adminApiKeyConfigured").value(true))
            .andExpect(jsonPath("$.status").value("READY"));
    }
}
