package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentWorkspaceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void workspaceEndpointReturnsUnifiedWorkspaceSummary() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Workspace Shell Smoke", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/workspace", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deployment.id", is(deployment.id())))
            .andExpect(jsonPath("$.deployment.name", is("Workspace Shell Smoke")))
            .andExpect(jsonPath("$.template.id", is("dev-openai-lucene")))
            .andExpect(jsonPath("$.draft.id", notNullValue()))
            .andExpect(jsonPath("$.draft.revisionNumber", is(1)))
            .andExpect(jsonPath("$.draft.status", is("DRAFT")))
            .andExpect(jsonPath("$.latestVersion").doesNotExist())
            .andExpect(jsonPath("$.latestRelease").doesNotExist())
            .andExpect(jsonPath("$.latestVerificationRun").doesNotExist())
            .andExpect(jsonPath("$.versionCount", is(0)))
            .andExpect(jsonPath("$.releaseCount", is(0)))
            .andExpect(jsonPath("$.verificationRunCount", is(0)));
    }
}
