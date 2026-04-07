package com.ai.fabric.platform.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=false",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.public-api.enabled=true",
    "platform.public-api.client-id-header-name=X-PLATFORM-CLIENT-ID",
    "platform.public-api.api-key-header-name=X-PLATFORM-PUBLIC-API-KEY",
    "platform.public-api.clients.shopify-dev=shopify-secret",
    "platform.bootstrap.sample-enabled=false",
    "platform.provisioning.mode=RAILWAY_STUB"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformAuthDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void platformApisDoNotGainSyntheticAdminWhenAuthIsDisabled() throws Exception {
        mockMvc.perform(get("/api/platform/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled", is(false)))
            .andExpect(jsonPath("$.authenticated", is(false)))
            .andExpect(jsonPath("$.sessionAuthEnabled", is(false)))
            .andExpect(jsonPath("$.apiKeyAuthEnabled", is(false)))
            .andExpect(jsonPath("$.canManageUsers", is(false)))
            .andExpect(jsonPath("$.canManageSecrets", is(false)))
            .andExpect(jsonPath("$.canOperateDeployments", is(false)));

        mockMvc.perform(post("/api/platform/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "admin@example.com",
                      "password": "ignored"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Platform auth is disabled.")));

        mockMvc.perform(get("/api/deployments"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/deployments")
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/platform/secrets/CONNECTOR_API_KEY")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publicProvisioningApiCanStillAuthenticateSeparatelyWhenPlatformAuthIsDisabled() throws Exception {
        mockMvc.perform(post("/api/public/deployments")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalDeploymentKey": "shop-auth-disabled-123",
                      "name": "Shopify Merchant Dev",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene"
                    }
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/public/deployments")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalDeploymentKey": "shop-auth-disabled-123",
                      "name": "Shopify Merchant Dev",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId", is("shopify-dev")))
            .andExpect(jsonPath("$.deploymentId", notNullValue()))
            .andExpect(jsonPath("$.created", is(true)));
    }
}
