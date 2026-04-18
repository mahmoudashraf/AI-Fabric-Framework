package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class PlatformSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlatformSecretService platformSecretService;

    @Test
    void sessionEndpointIsPublicAndReportsUnauthenticatedStateWithoutKey() throws Exception {
        mockMvc.perform(get("/api/platform/auth/session"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled", is(true)))
            .andExpect(jsonPath("$.authenticated", is(false)))
            .andExpect(jsonPath("$.headerName", is("X-PLATFORM-API-KEY")));

        mockMvc.perform(get("/api/deployments"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void operatorCanUsePlatformApisWhileSecretMutationRequiresAdminAndWritesAuditTrail() throws Exception {
        mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Security Smoke",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Security Smoke")));

        mockMvc.perform(put("/api/platform/secrets/CONNECTOR_API_KEY")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "operator-should-not-write"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/platform/secrets/CONNECTOR_API_KEY")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "value": "admin-secret"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.source", is("DATABASE")))
            .andExpect(jsonPath("$.present", is(true)));

        mockMvc.perform(get("/api/platform/audit-events")
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("DEPLOYMENT_CREATED")))
            .andExpect(jsonPath("$[*].action", hasItem("SECRET_UPDATED")));

        mockMvc.perform(get("/api/platform/secrets/audit-events")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/secrets/audit-events")
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].action", hasItem("SECRET_UPDATED")))
            .andExpect(jsonPath("$[*].targetId", hasItem("CONNECTOR_API_KEY")));
    }

    @Test
    void signedArtifactEndpointsRemainMachineAccessibleWhileBundleSummaryRequiresOperatorAuth() throws Exception {
        var createResult = mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Artifact Access Smoke",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String deploymentId = com.jayway.jsonpath.JsonPath.read(
            createResult.getResponse().getContentAsString(),
            "$.id"
        );
        var draftResult = mockMvc.perform(get("/api/deployments/{deploymentId}/draft", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andReturn();
        String draftId = com.jayway.jsonpath.JsonPath.read(
            draftResult.getResponse().getContentAsString(),
            "$.id"
        );

        var publishResult = mockMvc.perform(post("/api/deployment-drafts/{draftId}/publish", draftId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isCreated())
            .andReturn();

        String versionId = com.jayway.jsonpath.JsonPath.read(
            publishResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/versions/{versionId}/artifacts", deploymentId, versionId))
            .andExpect(status().isUnauthorized());

        var bundleResult = mockMvc.perform(get("/api/deployments/{deploymentId}/versions/{versionId}/artifacts", deploymentId, versionId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.deploymentVersionId", is(versionId)))
            .andReturn();

        String signedActionsArtifactUrl = com.jayway.jsonpath.JsonPath.read(
            bundleResult.getResponse().getContentAsString(),
            "$.actionsArtifactUrl"
        );
        String signedPromptArtifactUrl = com.jayway.jsonpath.JsonPath.read(
            bundleResult.getResponse().getContentAsString(),
            "$.promptArtifactUrl"
        );
        String signedMarketplaceDatasetArtifactUrl = com.jayway.jsonpath.JsonPath.read(
            bundleResult.getResponse().getContentAsString(),
            "$.marketplaceDatasetArtifactUrl"
        );
        mockMvc.perform(get(
                "/api/deployments/{deploymentId}/versions/{versionId}/artifacts/ai-actions.yml",
                deploymentId,
                versionId
            ))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                "/api/deployments/{deploymentId}/versions/{versionId}/artifacts/ai-prompt-config.json",
                deploymentId,
                versionId
            ))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                "/api/deployments/{deploymentId}/versions/{versionId}/artifacts/ai-marketplace-dataset-config.json",
                deploymentId,
                versionId
            ))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(
                URI.create(signedActionsArtifactUrl)
            ))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("actions:")));

        mockMvc.perform(get(
                URI.create(signedPromptArtifactUrl)
            ))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("systemPrompt")));

        mockMvc.perform(get(
                URI.create(signedMarketplaceDatasetArtifactUrl)
            ))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("MARKETPLACE_DATASET_CONFIG_V1")));

        mockMvc.perform(get(URI.create(signedActionsArtifactUrl.replace("sig=", "sig=broken-"))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(URI.create(signedPromptArtifactUrl.replace("sig=", "sig=broken-"))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(URI.create(signedMarketplaceDatasetArtifactUrl.replace("sig=", "sig=broken-"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void hostedVerificationEndpointsRequirePlatformAdmin() throws Exception {
        var createResult = mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Hosted Verification Security",
                      "environment": "dev",
                      "templateId": "custom-start-from-scratch"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String deploymentId = com.jayway.jsonpath.JsonPath.read(
            createResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/hosted-verifications", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments/{deploymentId}/hosted-verification-context", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/{deploymentId}/hosted-verifications", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "profile": "vector"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void deploymentDeletionNotificationsRequirePlatformAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/notifications/deployment-deletions")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/platform/notifications/deployment-deletions/{operationId}/retry", "del-missing")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/notifications/deployment-deletions")
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/platform/notifications/deployment-deletions/{operationId}/retry", "del-missing")
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isNotFound());
    }

    @Test
    void vectorizationVerificationEndpointsRequirePlatformAdmin() throws Exception {
        var createResult = mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Vectorization Verification Security",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String deploymentId = com.jayway.jsonpath.JsonPath.read(
            createResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/vectorization/verifications", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/{deploymentId}/vectorization/verifications", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "verificationType": "CONTROL_PLANE_READINESS",
                      "entityTypes": ["product"]
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments/{deploymentId}/vectorization/verifications", deploymentId)
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk());
    }

    @Test
    void customerAndTenantAdministrationRequirePlatformAdminAndBindingOverrideCatalogIsAdminOnly() throws Exception {
        mockMvc.perform(get("/api/platform/customers")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        var customerResult = mockMvc.perform(post("/api/platform/customers")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Security Customer",
                      "description": "Admin-created customer"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Security Customer")))
            .andReturn();

        String customerId = com.jayway.jsonpath.JsonPath.read(
            customerResult.getResponse().getContentAsString(),
            "$.id"
        );

        var tenantResult = mockMvc.perform(post("/api/platform/customers/{customerId}/tenants", customerId)
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Security Tenant",
                      "description": "Admin-created tenant"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.customerId", is(customerId)))
            .andReturn();

        String tenantId = com.jayway.jsonpath.JsonPath.read(
            tenantResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(get("/api/platform/customers/tenants/{tenantId}/shared-vector-handles", tenantId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/customers/tenants/{tenantId}/shared-vector-handles", tenantId)
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/platform/customers/tenants/{tenantId}/shared-vector-handles/purge", tenantId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "handleIds": ["tsv-12345678"],
                      "providerDeleteConfirmed": true,
                      "confirmationText": "PURGE DETACHED HANDLES",
                      "reason": "Operator should not be able to purge tenant shared handle history."
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Forbidden Bound Deployment",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene",
                      "customerId": "%s"
                    }
                    """.formatted(customerId)))
            .andExpect(status().isForbidden());

        var deploymentResult = mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Admin Bound Deployment",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene",
                      "customerId": "%s",
                      "tenantId": "%s"
                    }
                    """.formatted(customerId, tenantId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.binding.customerId", is(customerId)))
            .andExpect(jsonPath("$.binding.tenantId", is(tenantId)))
            .andReturn();

        String deploymentId = com.jayway.jsonpath.JsonPath.read(
            deploymentResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(put("/api/deployments/{deploymentId}/tenant-binding", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "%s",
                      "tenantId": "%s"
                    }
                    """.formatted(customerId, tenantId)))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/deployments/{deploymentId}/tenant-binding", deploymentId)
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": "%s",
                      "tenantId": "%s"
                    }
                    """.formatted(customerId, tenantId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.binding.customerId", is(customerId)))
            .andExpect(jsonPath("$.binding.tenantId", is(tenantId)));

        mockMvc.perform(put("/api/platform/secrets/deployment-overrides/{name}", "SECURITY_OPENAI_OVERRIDE")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "value": "security-override-value",
                      "deploymentId": "%s",
                      "cleanupPolicy": "DELETE_ON_HARD_DELETE"
                    }
                    """.formatted(deploymentId)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-secret-bindings", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments/{deploymentId}/provider-secret-bindings", deploymentId)
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.availableOverrideSecrets[*].name", hasItem("SECURITY_OPENAI_OVERRIDE")));

        mockMvc.perform(put("/api/deployments/{deploymentId}/provider-secret-bindings", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "secretPurpose": "OPENAI_API_KEY",
                      "bindingMode": "REQUIRE_OVERRIDE",
                      "secretName": "SECURITY_OPENAI_OVERRIDE"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/deployments/{deploymentId}/provider-secret-bindings/{secretPurpose}", deploymentId, "OPENAI_API_KEY")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());
    }

    @Test
    void vectorizationEndpointsKeepOperatorWorkflowButReserveRunnerTokenRotationForDeploymentAdmins() throws Exception {
        var customerResult = mockMvc.perform(post("/api/platform/customers")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Vectorization Customer",
                      "description": "Customer used for vectorization security coverage"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        String customerId = com.jayway.jsonpath.JsonPath.read(
            customerResult.getResponse().getContentAsString(),
            "$.id"
        );

        var tenantResult = mockMvc.perform(post("/api/platform/customers/{customerId}/tenants", customerId)
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Vectorization Tenant",
                      "description": "Tenant used for vectorization security coverage"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();
        String tenantId = com.jayway.jsonpath.JsonPath.read(
            tenantResult.getResponse().getContentAsString(),
            "$.id"
        );

        var deploymentResult = mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Vectorization Security",
                      "environment": "dev",
                      "templateId": "dev-openai-lucene",
                      "customerId": "%s",
                      "tenantId": "%s"
                    }
                    """.formatted(customerId, tenantId)))
            .andExpect(status().isCreated())
            .andReturn();
        String deploymentId = com.jayway.jsonpath.JsonPath.read(
            deploymentResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/vectorization", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)));

        mockMvc.perform(put("/api/deployments/{deploymentId}/vectorization/connection", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Products feed",
                      "adapterType": "REST_API",
                      "authMode": "API_KEY",
                      "connectionConfig": {
                        "baseUrl": "https://source.example",
                        "path": "/products"
                      },
                      "secretReferences": {
                        "apiKeySecretName": "SOURCE_PRODUCTS_API_KEY"
                      },
                      "discoverySummary": {
                        "countsByEntityType": {
                          "product": 12
                        }
                      }
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.adapterType", is("REST_API")))
            .andExpect(jsonPath("$.authMode", is("API_KEY")));

        mockMvc.perform(get("/api/deployments/{deploymentId}/vectorization/preview", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deploymentId)))
            .andExpect(jsonPath("$.reindexOptions.supportsSelectedEntities", is(true)));

        mockMvc.perform(post("/api/deployments/{deploymentId}/vectorization/runner/token", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "runnerMode": "PLATFORM_MANAGED_AUTO",
                      "validityHours": 24
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/{deploymentId}/vectorization/runner/token", deploymentId)
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "runnerMode": "PLATFORM_MANAGED_AUTO",
                      "validityHours": 24
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.registrationId").isNotEmpty())
            .andExpect(jsonPath("$.runnerMode", is("PLATFORM_MANAGED_AUTO")))
            .andExpect(jsonPath("$.registrationToken").isNotEmpty());
    }

    @Test
    void railwayWorkspaceCleanupEndpointsRequirePlatformAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/provisioning/railway/workspace-cleanup")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/platform/provisioning/railway/workspace-cleanup")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "confirm": true,
                      "reason": "cleanup",
                      "projectIds": [],
                      "serviceIds": []
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void platformDiagnosticsEndpointsRequirePlatformAdmin() throws Exception {
        mockMvc.perform(get("/api/platform/diagnostics")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/platform/diagnostics/logs")
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());
    }

    @Test
    void hardDeleteEscalationRequiresPlatformAdmin() throws Exception {
        var createResult = mockMvc.perform(post("/api/deployments")
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Hard Delete Security",
                      "environment": "dev",
                      "templateId": "custom-start-from-scratch"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String deploymentId = com.jayway.jsonpath.JsonPath.read(
            createResult.getResponse().getContentAsString(),
            "$.id"
        );

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deploymentId)
                .header("X-PLATFORM-API-KEY", "admin-test-key"))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/api/deployments/{deploymentId}", deploymentId)
                .header("X-PLATFORM-API-KEY", "operator-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "hardDelete": true,
                      "reason": "remove all provider resources"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void productServiceApiKeyIsScopedToItsOwnShopifyStoresAndCannotUseGeneralPlatformApis() throws Exception {
        mockMvc.perform(post("/api/product-services")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceRef": "shopify-bridge-prod",
                      "displayName": "Shopify Bridge Prod",
                      "productFamily": "SHOPIFY",
                      "serviceKind": "SHOPIFY_BRIDGE_SERVICE",
                      "deploymentMode": "SHARED_PLATFORM_SERVICE",
                      "tenantMode": "MULTI_TENANT_SHARED",
                      "environmentScope": "prod",
                      "desiredReplicas": 1,
                      "minReplicas": 1,
                      "maxReplicas": 3,
                      "baseUrl": "https://shopify-bridge.example.com",
                      "secretName": "MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY"
                    }
                    """))
            .andExpect(status().isCreated());

        platformSecretService.upsertManagedSecret(
            "MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY",
            "bridge-secret-key",
            java.util.Map.of("serviceRef", "shopify-bridge-prod", "purpose", "PRODUCT_SERVICE_SECRET")
        );

        mockMvc.perform(post("/api/product-services")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "serviceRef": "shopify-bridge-other",
                      "displayName": "Shopify Bridge Other",
                      "productFamily": "SHOPIFY",
                      "serviceKind": "SHOPIFY_BRIDGE_SERVICE",
                      "deploymentMode": "SHARED_PLATFORM_SERVICE",
                      "tenantMode": "MULTI_TENANT_SHARED",
                      "environmentScope": "prod",
                      "desiredReplicas": 1,
                      "minReplicas": 1,
                      "maxReplicas": 3,
                      "baseUrl": "https://shopify-bridge-other.example.com",
                      "secretName": "MANAGED_PRODUCT_SHOPIFY_BRIDGE_OTHER_API_KEY"
                    }
                    """))
            .andExpect(status().isCreated());

        platformSecretService.upsertManagedSecret(
            "MANAGED_PRODUCT_SHOPIFY_BRIDGE_OTHER_API_KEY",
            "bridge-other-secret",
            java.util.Map.of("serviceRef", "shopify-bridge-other", "purpose", "PRODUCT_SERVICE_SECRET")
        );

        mockMvc.perform(post("/api/shopify/stores")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shopDomain": "alpha.myshopify.com",
                      "displayName": "Alpha",
                      "productServiceRef": "shopify-bridge-prod",
                      "installStatus": "INSTALLED",
                      "syncStatus": "NOT_SYNCED",
                      "sourceReadinessStatus": "NOT_RUN",
                      "widgetStatus": "NOT_ENABLED",
                      "onboardingStatus": "CONNECTED"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/shopify/stores")
                .header("X-PLATFORM-API-KEY", "admin-test-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "shopDomain": "beta.myshopify.com",
                      "displayName": "Beta",
                      "productServiceRef": "shopify-bridge-other",
                      "installStatus": "INSTALLED",
                      "syncStatus": "NOT_SYNCED",
                      "sourceReadinessStatus": "NOT_RUN",
                      "widgetStatus": "NOT_ENABLED",
                      "onboardingStatus": "CONNECTED"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/shopify/stores")
                .header("X-PLATFORM-API-KEY", "bridge-secret-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].shopDomain", is("alpha.myshopify.com")))
            .andExpect(jsonPath("$.length()", is(1)));

        mockMvc.perform(get("/api/shopify/stores/alpha.myshopify.com")
                .header("X-PLATFORM-API-KEY", "bridge-secret-key"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shopDomain", is("alpha.myshopify.com")));

        mockMvc.perform(get("/api/shopify/stores/beta.myshopify.com")
                .header("X-PLATFORM-API-KEY", "bridge-secret-key"))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/deployments")
                .header("X-PLATFORM-API-KEY", "bridge-secret-key"))
            .andExpect(status().isUnauthorized());
    }
}
