package com.ai.fabric.runtime.observability;

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
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "spring.datasource.url=jdbc:h2:mem:runtime-observability;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "ai.config.default-file=classpath:test-runtime-entity-config.yml",
    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=runtime-secret",
    "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=runtime-private-signing-secret",
    "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_ISSUERS=platform-poc:SESSION,platform-poc:API_KEY,platform-poc:SYSTEM",
    "AI_FABRIC_RUNTIME_AUTH_ACCEPTED_AUDIENCES=dep-public"
})
@AutoConfigureMockMvc
class RuntimeObservabilityIntegrationTest {

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
