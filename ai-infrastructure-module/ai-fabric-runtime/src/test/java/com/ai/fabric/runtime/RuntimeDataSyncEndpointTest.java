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
    "ai.config.default-file=classpath:test-runtime-entity-config.yml"
})
@AutoConfigureMockMvc
class RuntimeDataSyncEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void vectorSpacesEndpointIsRegistered() throws Exception {
        mockMvc.perform(get("/api/ai/data-sync/vector-spaces"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.vectorSpaces[0]").value("product"));
    }
}
