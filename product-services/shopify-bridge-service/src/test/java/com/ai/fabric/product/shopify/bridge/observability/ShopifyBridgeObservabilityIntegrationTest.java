package com.ai.fabric.product.shopify.bridge.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "shopify.bridge.admin-api-key=test-admin-key",
    "shopify.bridge.service-ref=shopify-bridge-test"
})
@AutoConfigureMockMvc
class ShopifyBridgeObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthEndpointGetsRequestIdHeader() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void prometheusEndpointIsExposed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_threads_live_threads")));
    }
}
