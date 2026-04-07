package com.ai.fabric.platform.backend.deployment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.api-key-enabled=false",
    "platform.auth.session-enabled=true",
    "platform.auth.bootstrap-admin-enabled=false",
    "platform.public-api.enabled=true",
    "platform.public-api.client-id-header-name=X-PLATFORM-CLIENT-ID",
    "platform.public-api.api-key-header-name=X-PLATFORM-PUBLIC-API-KEY",
    "platform.public-api.clients.shopify-dev=shopify-secret",
    "platform.bootstrap.sample-enabled=false",
    "platform.provisioning.mode=RAILWAY_STUB"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicProvisioningApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicClientCanCreateInspectAndIdempotentlyApplyDeployment() throws Exception {
        String body = """
            {
              "externalDeploymentKey": "shop-123",
              "name": "Shopify Merchant Dev",
              "environment": "dev",
              "templateId": "dev-openai-lucene",
              "autoApply": false,
              "callbackMetadata": {
                "shopDomain": "merchant-dev.myshopify.com"
              }
            }
            """;

        var createResult = mockMvc.perform(post("/api/public/deployments")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.clientId", is("shopify-dev")))
            .andExpect(jsonPath("$.externalDeploymentKey", is("shop-123")))
            .andExpect(jsonPath("$.created", is(true)))
            .andExpect(jsonPath("$.deploymentId", notNullValue()))
            .andExpect(jsonPath("$.latestPublishedVersionId", notNullValue()))
            .andExpect(jsonPath("$.latestPublishedVersionLabel", is("v1")))
            .andExpect(jsonPath("$.connectorBaseUrl", nullValue()))
            .andReturn();

        String response = createResult.getResponse().getContentAsString();
        String deploymentId = response.replaceAll(".*\"deploymentId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/public/deployments")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created", is(false)))
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)));

        mockMvc.perform(get("/api/public/deployments/{deploymentId}", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.externalDeploymentKey", is("shop-123")))
            .andExpect(jsonPath("$.connectorBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.runtimeExposure", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.access.connectorExposure", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.access.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeTokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAuthorizationHeader", nullValue()));

        var applyResult = mockMvc.perform(post("/api/public/deployments/{deploymentId}/apply", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionId\":null}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.idempotentReplay", is(false)))
            .andExpect(jsonPath("$.release.id", notNullValue()))
            .andReturn();

        String applyResponse = applyResult.getResponse().getContentAsString();
        String releaseId = applyResponse.replaceAll(".*\"release\":\\{\"id\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/public/deployments/{deploymentId}/apply", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"versionId\":null}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.idempotentReplay", is(true)))
            .andExpect(jsonPath("$.release.id", is(releaseId)));

        mockMvc.perform(get("/api/public/deployments/{deploymentId}/status", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.latestPublishedVersionLabel", is("v1")))
            .andExpect(jsonPath("$.connectorBaseUrl", nullValue()))
            .andExpect(jsonPath("$.latestRelease.releaseId", is(releaseId)))
            .andExpect(jsonPath("$.access.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeTokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAuthorizationHeader", nullValue()));

        mockMvc.perform(get("/api/public/deployments/{deploymentId}/credentials", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.connectorBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeTokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAuthorizationHeader", nullValue()));

        mockMvc.perform(get("/api/platform/audit-events"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publicClientCannotReuseExternalKeyWithDifferentRequest() throws Exception {
        mockMvc.perform(post("/api/public/deployments")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalDeploymentKey": "shop-456",
                      "name": "Original Shop",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene",
                      "autoApply": false
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/public/deployments")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "externalDeploymentKey": "shop-456",
                      "name": "Different Shop",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene",
                      "autoApply": false
                    }
                    """))
            .andExpect(status().isConflict());
    }
}
