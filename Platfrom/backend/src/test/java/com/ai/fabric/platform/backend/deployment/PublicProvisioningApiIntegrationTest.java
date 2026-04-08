package com.ai.fabric.platform.backend.deployment;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.fail;
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
            .andExpect(jsonPath("$.access.runtimeExposure", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.access.connectorExposure", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.access.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredChatQueryUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredSuggestionsUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredConversationsUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredConversationItemUrlTemplate", nullValue()))
            .andExpect(jsonPath("$.integration.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorHealthUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorActionsOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredAuthContextUrl", nullValue()))
            .andExpect(jsonPath("$.integration.preferredAuthOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.integration.verifiedAuthContextRequired", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackendAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendPlatformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.integration.externalTrustedBackendIntegrationReady", is(false)))
            .andExpect(jsonPath("$.integration.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeTokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.browserDirectRuntimeAccessSupported", is(false)))
            .andExpect(jsonPath("$.integration.browserDirectChatBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.browserDirectCrudBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.backendMediatedRuntimeBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.preferredChatQueryUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredSuggestionsUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredConversationsUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredConversationItemUrlTemplate", nullValue()))
            .andExpect(jsonPath("$.access.preferredAuthContextUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorHealthUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorActionsOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.access.preferredAuthOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.access.verifiedAuthContextRequired", is(false)))
            .andExpect(jsonPath("$.access.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.preferredOperationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendPlatformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.access.externalTrustedBackendIntegrationReady", is(false)));

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

        awaitRuntimeAuthConfigurationRequiredStatus(deploymentId, releaseId, Duration.ofSeconds(3));

        mockMvc.perform(get("/api/public/deployments/{deploymentId}/status", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.latestPublishedVersionLabel", is("v1")))
            .andExpect(jsonPath("$.latestRelease.releaseId", is(releaseId)))
            .andExpect(jsonPath("$.access.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.preferredChatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredSuggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredOperationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorHealthUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorActionsOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredAuthContextUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredAuthOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackendAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendPlatformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.integration.externalTrustedBackendIntegrationReady", is(false)))
            .andExpect(jsonPath("$.integration.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeTokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.browserDirectRuntimeAccessSupported", is(false)))
            .andExpect(jsonPath("$.integration.browserDirectChatBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.browserDirectCrudBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.backendMediatedRuntimeBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.preferredChatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredSuggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.access.preferredAuthContextUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorHealthUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorActionsOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredAuthOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.access.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.preferredOperationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.access.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendPlatformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.access.externalTrustedBackendIntegrationReady", is(false)));

        mockMvc.perform(get("/api/public/deployments/{deploymentId}/credentials", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.access.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.preferredChatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredSuggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredOperationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorHealthUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredConnectorActionsOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredAuthContextUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.preferredAuthOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackendAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackendPlatformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.integration.externalTrustedBackendIntegrationReady", is(false)))
            .andExpect(jsonPath("$.integration.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeTokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntimeAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.browserDirectRuntimeAccessSupported", is(false)))
            .andExpect(jsonPath("$.integration.browserDirectChatBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.browserDirectCrudBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.backendMediatedRuntimeBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.preferredChatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredSuggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.access.preferredAuthContextUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorHealthUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredConnectorActionsOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.preferredAuthOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.access.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.preferredOperationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.access.trustedBackendCallerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAuthorizationHeader", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackendAcceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendAcceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackendPlatformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.access.externalTrustedBackendIntegrationReady", is(false)));

        mockMvc.perform(get("/api/platform/audit-events"))
            .andExpect(status().isUnauthorized());
    }

    private void awaitRuntimeAuthConfigurationRequiredStatus(String deploymentId,
                                                             String releaseId,
                                                             Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            MvcResult result = mockMvc.perform(get("/api/public/deployments/{deploymentId}/status", deploymentId)
                    .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                    .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
                .andExpect(status().isOk())
                .andReturn();
            String body = result.getResponse().getContentAsString();
            if (body.contains("\"releaseId\":\"" + releaseId + "\"")
                && body.contains("\"runtimeAuthMode\":\"AUTH_CONFIGURATION_REQUIRED\"")) {
                return;
            }
            Thread.sleep(50);
        }
        fail("Timed out waiting for public deployment status to reflect runtime auth configuration posture for " + deploymentId);
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
