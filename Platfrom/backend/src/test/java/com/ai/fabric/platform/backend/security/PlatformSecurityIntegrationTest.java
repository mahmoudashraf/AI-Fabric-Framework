package com.ai.fabric.platform.backend.security;

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

        mockMvc.perform(get(URI.create(signedActionsArtifactUrl)))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("actions:")));

        mockMvc.perform(get(URI.create(signedPromptArtifactUrl)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("systemPrompt")));

        mockMvc.perform(get(URI.create(signedActionsArtifactUrl.replace("sig=", "sig=broken-"))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get(URI.create(signedPromptArtifactUrl.replace("sig=", "sig=broken-"))))
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
}
