package com.ai.fabric.platform.backend.observability;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicSessionEndpointGetsRequestIdHeader() throws Exception {
        mockMvc.perform(get("/api/platform/auth/session"))
            .andExpect(status().isOk())
            .andExpect(header().exists("X-Request-Id"));
    }

    @Test
    void prometheusEndpointIsExposedBehindPlatformAuth() throws Exception {
        mockMvc.perform(get("/actuator/prometheus")
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString("jvm_threads_live_threads")));
    }
}
