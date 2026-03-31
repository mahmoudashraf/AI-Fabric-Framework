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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentPocWorkspaceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void pocWorkspaceReturnsMigrationGuideAndReadinessChecks() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("POC Workspace", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/poc", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dataset.profileId", is("unconfigured")))
            .andExpect(jsonPath("$.migration.suggestedDatasetLabel", is("Operator POC import")))
            .andExpect(jsonPath("$.migration.maxRecordsPerRun", is(100)))
            .andExpect(jsonPath("$.migration.maxContentLength", is(16000)))
            .andExpect(jsonPath("$.migration.defaultVectorSpace", is("default")))
            .andExpect(jsonPath("$.migration.supportedVectorSpaces[0]", is("default")))
            .andExpect(jsonPath("$.migration.supportedSources.length()", is(3)))
            .andExpect(jsonPath("$.migration.supportedSources[0].key", is("TEMPLATE_SAMPLE")))
            .andExpect(jsonPath("$.migration.readinessChecks.length()", is(4)))
            .andExpect(jsonPath("$.migration.readinessChecks[0].status", is("BLOCKED")));
    }
}
