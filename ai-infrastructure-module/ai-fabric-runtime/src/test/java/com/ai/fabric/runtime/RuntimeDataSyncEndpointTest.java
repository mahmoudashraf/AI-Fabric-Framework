package com.ai.fabric.runtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "ai.config.default-file=classpath:test-runtime-entity-config.yml",
    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=runtime-secret"
})
@AutoConfigureMockMvc
class RuntimeDataSyncEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void vectorSpacesEndpointRequiresTrustedBackendAuth() throws Exception {
        mockMvc.perform(get("/api/ai/data-sync/vector-spaces")
                .header("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.vectorSpaces[0]").value("product"));
    }
}

@SpringBootTest(properties = {
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "ai.config.default-file=classpath:test-runtime-entity-config.yml",
    "AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE=VERIFIED_CONTEXT_REQUIRED",
    "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY=runtime-secret"
})
@AutoConfigureMockMvc
class RuntimeDataSyncTrustedBackendEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void vectorSpacesEndpointRejectsRequestsWithoutTrustedBackendApiKeyInVerifiedMode() throws Exception {
        mockMvc.perform(get("/api/ai/data-sync/vector-spaces"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void vectorSpacesEndpointAcceptsTrustedBackendApiKeyInVerifiedMode() throws Exception {
        mockMvc.perform(get("/api/ai/data-sync/vector-spaces")
                .header("X-AIFABRIC-RUNTIME-API-KEY", "runtime-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.vectorSpaces[0]").value("product"));
    }
}
