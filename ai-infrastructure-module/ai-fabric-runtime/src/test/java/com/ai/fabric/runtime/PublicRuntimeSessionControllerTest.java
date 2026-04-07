package com.ai.fabric.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "ai.config.default-file=classpath:test-runtime-entity-config.yml",
    "AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE=VERIFIED_CONTEXT_REQUIRED",
    "AI_FABRIC_RUNTIME_LEGACY_REQUEST_IDENTITY_ENABLED=false",
    "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY=public-secret",
    "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_ISSUERS=runtime-public-bootstrap",
    "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_ACCEPTED_AUDIENCES=storefront-chat",
    "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_DEFAULT_AUDIENCE=storefront-chat",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED=true",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOWED_ORIGINS=https://shop.example",
    "AI_FABRIC_RUNTIME_DEPLOYMENT_ID=dep-public",
    "AI_FABRIC_RUNTIME_CUSTOMER_ID=cus-public",
    "AI_FABRIC_RUNTIME_TENANT_ID=ten-public"
})
@AutoConfigureMockMvc
class PublicRuntimeSessionControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapIssuesAnonymousTokenAndConversationApisAcceptIt() throws Exception {
        MvcResult bootstrapResult = mockMvc.perform(post("/api/public/chat/session")
                .header("Origin", "https://shop.example")
                .contentType(APPLICATION_JSON)
                .content("""
                    {"sessionId":"anon-public-001"}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.authMode").value("PUBLIC_RUNTIME_ANONYMOUS"))
            .andExpect(jsonPath("$.subjectType").value("ANONYMOUS_SESSION"))
            .andExpect(jsonPath("$.deploymentId").value("dep-public"))
            .andExpect(jsonPath("$.customerId").value("cus-public"))
            .andExpect(jsonPath("$.tenantId").value("ten-public"))
            .andExpect(jsonPath("$.grantedScopes[0]").value("chat:query"))
            .andExpect(jsonPath("$.grantedScopes[1]").value("chat:suggestions"))
            .andExpect(jsonPath("$.grantedScopes[2]").value("chat:conversations"))
            .andExpect(jsonPath("$.audiences[0]").value("storefront-chat"))
            .andReturn();

        JsonNode payload = OBJECT_MAPPER.readTree(bootstrapResult.getResponse().getContentAsString());
        String token = payload.path("token").asText();
        String sessionId = payload.path("sessionId").asText();

        assertThat(token).startsWith("rpt1.");
        assertThat(sessionId).isEqualTo("anon-public-001");

        mockMvc.perform(get("/api/chat/me/conversations")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/chat/me/auth-context")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authMode").value("PUBLIC_RUNTIME_ANONYMOUS"))
            .andExpect(jsonPath("$.subjectType").value("ANONYMOUS_SESSION"))
            .andExpect(jsonPath("$.sessionId").value("anon-public-001"))
            .andExpect(jsonPath("$.deploymentId").value("dep-public"))
            .andExpect(jsonPath("$.customerId").value("cus-public"))
            .andExpect(jsonPath("$.tenantId").value("ten-public"));
    }

    @Test
    void strictConversationApiRejectsRequestsWithoutPublicBearerToken() throws Exception {
        mockMvc.perform(get("/api/chat/me/conversations"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void strictConversationApiRejectsInvalidPublicBearerToken() throws Exception {
        mockMvc.perform(get("/api/chat/me/conversations")
                .header("Authorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void bootstrapRejectsRequestsWithoutAllowedOrigin() throws Exception {
        mockMvc.perform(post("/api/public/chat/session")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void bootstrapRejectsRequestsFromDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/api/public/chat/session")
                .header("Origin", "https://evil.example")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isForbidden());
    }
}

@SpringBootTest(properties = {
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "ai.config.default-file=classpath:test-runtime-entity-config.yml",
    "AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE=VERIFIED_CONTEXT_REQUIRED",
    "AI_FABRIC_RUNTIME_LEGACY_REQUEST_IDENTITY_ENABLED=false",
    "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY=public-secret",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED=false"
})
@AutoConfigureMockMvc
class PublicRuntimeSessionControllerDisabledBootstrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapEndpointReturnsNotFoundWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/public/chat/session").contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isNotFound());
    }
}

@SpringBootTest(properties = {
    "OPENAI_ENABLED=true",
    "OPENAI_API_KEY=test",
    "ACTIONS_CONNECTOR_BASE_URL=http://localhost:18082",
    "ACTIONS_CONNECTOR_API_KEY=test",
    "ai.config.default-file=classpath:test-runtime-entity-config.yml",
    "AI_FABRIC_RUNTIME_AUTH_INGRESS_MODE=VERIFIED_CONTEXT_REQUIRED",
    "AI_FABRIC_RUNTIME_LEGACY_REQUEST_IDENTITY_ENABLED=false",
    "AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY=public-secret",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ENABLED=true",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_ALLOWED_ORIGINS=https://shop.example",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_MAX_REQUESTS_PER_WINDOW=1",
    "AI_FABRIC_RUNTIME_PUBLIC_BOOTSTRAP_RATE_LIMIT_WINDOW_SECONDS=60"
})
@AutoConfigureMockMvc
class PublicRuntimeSessionControllerRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapEnforcesPerOriginRateLimit() throws Exception {
        mockMvc.perform(post("/api/public/chat/session")
                .header("Origin", "https://shop.example")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/public/chat/session")
                .header("Origin", "https://shop.example")
                .contentType(APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isTooManyRequests());
    }
}
