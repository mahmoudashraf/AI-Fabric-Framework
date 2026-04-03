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

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentLifecycleOverviewIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void archiveRemovesDeploymentFromDefaultActiveListsAndPreservesOverview() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Customer Lifecycle Smoke", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(get("/api/deployments/overview"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].healthStatus", hasItem("DRAFT")))
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].healthSummary",
                hasItem(startsWith("Draft configuration"))));

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(deployment.id())))
            .andExpect(jsonPath("$.status", is("ARCHIVED")))
            .andExpect(jsonPath("$.healthStatus", is("ARCHIVED")))
            .andExpect(jsonPath("$.archivedAt", notNullValue()));

        mockMvc.perform(get("/api/deployments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')]").isEmpty());

        mockMvc.perform(get("/api/deployments").param("includeArchived", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].status", hasItem("ARCHIVED")));

        mockMvc.perform(get("/api/deployments/overview").param("includeArchived", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].healthStatus", hasItem("ARCHIVED")));

        mockMvc.perform(get("/api/platform/audit-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_ARCHIVED")));
    }

    @Test
    void restoreReturnsDeploymentToActiveListsAndDeleteRemovesItPermanently() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Customer Lifecycle Restore", "stage", "dev-openai-lucene")
        );

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ARCHIVED")));

        mockMvc.perform(post("/api/deployments/{deploymentId}/restore", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id", is(deployment.id())))
            .andExpect(jsonPath("$.archivedAt").doesNotExist())
            .andExpect(jsonPath("$.status", is("DRAFT")));

        mockMvc.perform(get("/api/deployments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')].status", hasItem("DRAFT")));

        mockMvc.perform(post("/api/deployments/{deploymentId}/archive", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("ARCHIVED")));

        mockMvc.perform(delete("/api/deployments/{deploymentId}", deployment.id()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/deployments").param("includeArchived", "true"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + deployment.id() + "')]").isEmpty());

        mockMvc.perform(get("/api/platform/audit-events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_RESTORED")))
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", hasItem("DEPLOYMENT_DELETED")))
            .andExpect(jsonPath("$[?(@.targetId=='" + deployment.id() + "')].action", not(hasItem("DEPLOYMENT_PURGED"))));
    }
}
