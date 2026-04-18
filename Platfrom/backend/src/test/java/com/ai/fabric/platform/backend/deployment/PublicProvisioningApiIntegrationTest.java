package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.tenant.model.CreatePlatformConsumerRequest;
import com.ai.fabric.platform.backend.tenant.model.CreatePlatformCustomerRequest;
import com.ai.fabric.platform.backend.tenant.model.UpdatePlatformConsumerBindingRequest;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerTenantService;
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

    @Autowired
    private com.ai.fabric.platform.backend.deployment.service.DeploymentService deploymentService;

    @Autowired
    private PlatformCustomerTenantService platformCustomerTenantService;

    @Autowired
    private PlatformCustomerConsumerService platformCustomerConsumerService;

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
            .andExpect(jsonPath("$.access.posture.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.posture.runtimeAuthMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.integration.runtime.chatQueryUrl", nullValue()))
            .andExpect(jsonPath("$.integration.runtime.suggestionsUrl", nullValue()))
            .andExpect(jsonPath("$.integration.runtime.conversationsUrl", nullValue()))
            .andExpect(jsonPath("$.integration.runtime.conversationItemUrlTemplate", nullValue()))
            .andExpect(jsonPath("$.integration.runtime.operationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.runtime.authContextUrl", nullValue()))
            .andExpect(jsonPath("$.integration.runtime.authOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.integration.posture.verifiedAuthContextRequired", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.authorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackend.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.platformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.externalIntegrationReady", is(false)))
            .andExpect(jsonPath("$.integration.paths.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackend.callerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.tokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.paths.browserDirectRuntimeAccessSupported", is(false)))
            .andExpect(jsonPath("$.integration.paths.browserDirectChatBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.paths.browserDirectCrudBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.paths.backendMediatedRuntimeBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.posture.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.runtime.chatQueryUrl", nullValue()))
            .andExpect(jsonPath("$.access.runtime.suggestionsUrl", nullValue()))
            .andExpect(jsonPath("$.access.runtime.conversationsUrl", nullValue()))
            .andExpect(jsonPath("$.access.runtime.conversationItemUrlTemplate", nullValue()))
            .andExpect(jsonPath("$.access.runtime.authContextUrl", nullValue()))
            .andExpect(jsonPath("$.access.runtime.authOverviewUrl", nullValue()))
            .andExpect(jsonPath("$.access.posture.verifiedAuthContextRequired", is(false)))
            .andExpect(jsonPath("$.access.posture.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.runtime.operationalBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackend.callerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.authorizationHeader", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackend.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.platformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.externalIntegrationReady", is(false)));

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
            .andExpect(jsonPath("$.access.posture.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.posture.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.runtime.chatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.suggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.conversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.conversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.operationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.authContextUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.authOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.posture.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackend.authorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackend.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.platformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.externalIntegrationReady", is(false)))
            .andExpect(jsonPath("$.integration.paths.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackend.callerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.tokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.paths.browserDirectRuntimeAccessSupported", is(false)))
            .andExpect(jsonPath("$.integration.paths.browserDirectChatBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.paths.browserDirectCrudBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.paths.backendMediatedRuntimeBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.posture.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.runtime.chatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.suggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.conversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.conversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.authContextUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.authOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.posture.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.access.posture.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.runtime.operationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.access.trustedBackend.callerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.authorizationHeader", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackend.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.platformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.externalIntegrationReady", is(false)));

        mockMvc.perform(get("/api/public/deployments/{deploymentId}/credentials", deploymentId)
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.access.posture.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.posture.runtimeAuthMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", is("AUTH_CONFIGURATION_REQUIRED")))
            .andExpect(jsonPath("$.integration.runtime.chatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.suggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.conversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.conversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.operationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.authContextUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.runtime.authOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.integration.posture.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackend.authorizationHeader", nullValue()))
            .andExpect(jsonPath("$.integration.trustedBackend.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.platformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.integration.trustedBackend.externalIntegrationReady", is(false)))
            .andExpect(jsonPath("$.integration.paths.connectorInternalOnly", is(true)))
            .andExpect(jsonPath("$.integration.trustedBackend.callerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.tokenValidationConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.anonymousBootstrapSupported", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.publicRuntime.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.integration.paths.browserDirectRuntimeAccessSupported", is(false)))
            .andExpect(jsonPath("$.integration.paths.browserDirectChatBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.paths.browserDirectCrudBaseUrl", nullValue()))
            .andExpect(jsonPath("$.integration.paths.backendMediatedRuntimeBaseUrl", nullValue()))
            .andExpect(jsonPath("$.access.posture.hostBackedRuntimeRequired", is(false)))
            .andExpect(jsonPath("$.access.runtime.chatQueryUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.suggestionsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.conversationsUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.conversationItemUrlTemplate", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.authContextUrl", notNullValue()))
            .andExpect(jsonPath("$.access.runtime.authOverviewUrl", notNullValue()))
            .andExpect(jsonPath("$.access.posture.verifiedAuthContextRequired", is(true)))
            .andExpect(jsonPath("$.access.posture.directConnectorAccessSupported", is(false)))
            .andExpect(jsonPath("$.access.runtime.operationalBaseUrl", notNullValue()))
            .andExpect(jsonPath("$.access.trustedBackend.callerAuthConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.authorizationHeader", nullValue()))
            .andExpect(jsonPath("$.access.trustedBackend.acceptedIssuerPolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.acceptedAudiencePolicyConfigured", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.platformDefaultIssuerPolicy", is(false)))
            .andExpect(jsonPath("$.access.trustedBackend.externalIntegrationReady", is(false)));

        mockMvc.perform(get("/api/platform/audit-events"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void publicClientCannotUsePlatformMediatedConsumerBridgeChatEndpoints() throws Exception {
        mockMvc.perform(post("/api/public/consumers/{consumerId}/bridge/chat/query", "storefront-main")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"query":"hello"}
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void publicConsumerRoutesRequireAuthenticationAndResolveCurrentDeployment() throws Exception {
        var customer = platformCustomerTenantService.createCustomer(
            new CreatePlatformCustomerRequest("Consumer Contract", "Customer for consumer resolution")
        );
        var firstDeployment = createDeploymentForCustomer(customer.id(), "Consumer Frontend A");
        var secondDeployment = createDeploymentForCustomer(customer.id(), "Consumer Frontend B");
        platformCustomerConsumerService.createConsumer(
            customer.id(),
            new CreatePlatformConsumerRequest(
                "storefront-main",
                "Storefront main",
                "Primary external consumer",
                firstDeployment,
                "Initial consumer binding."
            )
        );

        mockMvc.perform(get("/api/public/consumers/{consumerId}/credentials", "storefront-main"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/public/consumers/{consumerId}/credentials", "storefront-main")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumerId", is("storefront-main")))
            .andExpect(jsonPath("$.deploymentId", is(firstDeployment)))
            .andExpect(jsonPath("$.integration.preferredIntegrationMode", notNullValue()));

        platformCustomerConsumerService.updateBinding(
            customer.id(),
            "storefront-main",
            new UpdatePlatformConsumerBindingRequest(
                secondDeployment,
                "Cut over consumer to the replacement deployment."
            )
        );

        mockMvc.perform(get("/api/public/consumers/{consumerId}/status", "storefront-main")
                .header("X-PLATFORM-CLIENT-ID", "shopify-dev")
                .header("X-PLATFORM-PUBLIC-API-KEY", "shopify-secret"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.consumerId", is("storefront-main")))
            .andExpect(jsonPath("$.deploymentId", is(secondDeployment)))
            .andExpect(jsonPath("$.status", notNullValue()));
    }

    private String createDeploymentForCustomer(String customerId, String name) {
        return deploymentService.createDeployment(
            new com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest(
                name,
                "dev",
                "dev-openai-lucene",
                "default",
                null,
                customerId,
                null
            )
        ).id();
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
