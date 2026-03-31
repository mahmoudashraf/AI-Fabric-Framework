package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void workspaceEndpointReturnsUnifiedWorkspaceSummary() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Workspace Shell Smoke", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/workspace", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deployment.id", is(deployment.id())))
            .andExpect(jsonPath("$.deployment.name", is("Workspace Shell Smoke")))
            .andExpect(jsonPath("$.deployment.access.assignmentRole", is("DEPLOYMENT_ADMIN")))
            .andExpect(jsonPath("$.deployment.access.canOperate", is(true)))
            .andExpect(jsonPath("$.deployment.access.canEdit", is(true)))
            .andExpect(jsonPath("$.deployment.access.canAdmin", is(true)))
            .andExpect(jsonPath("$.template.id", is("dev-openai-lucene")))
            .andExpect(jsonPath("$.access.assignmentRole", is("DEPLOYMENT_ADMIN")))
            .andExpect(jsonPath("$.access.canOperate", is(true)))
            .andExpect(jsonPath("$.access.canEdit", is(true)))
            .andExpect(jsonPath("$.access.canAdmin", is(true)))
            .andExpect(jsonPath("$.draft.id", notNullValue()))
            .andExpect(jsonPath("$.draft.revisionNumber", is(1)))
            .andExpect(jsonPath("$.draft.status", is("DRAFT")))
            .andExpect(jsonPath("$.lifecycle.savedDraftState", is("NEVER_PUBLISHED")))
            .andExpect(jsonPath("$.lifecycle.liveState", is("NOT_PUBLISHED")))
            .andExpect(jsonPath("$.lifecycle.hasPublishedVersion", is(false)))
            .andExpect(jsonPath("$.lifecycle.hasLiveVersion", is(false)))
            .andExpect(jsonPath("$.latestVersion").doesNotExist())
            .andExpect(jsonPath("$.latestRelease").doesNotExist())
            .andExpect(jsonPath("$.latestVerificationRun").doesNotExist())
            .andExpect(jsonPath("$.versionCount", is(0)))
            .andExpect(jsonPath("$.releaseCount", is(0)))
            .andExpect(jsonPath("$.verificationRunCount", is(0)));
    }

    @Test
    void workspaceLifecycleShowsUnpublishedDraftChangesAfterPublish() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Workspace Lifecycle", "dev", "dev-openai-lucene")
        );
        DeploymentDraftResponse firstDraft = deploymentService.getActiveDraftForDeployment(deployment.id());
        DeploymentVersionSummary publishedVersion = deploymentService.publishDraft(firstDraft.id());
        DeploymentDraftResponse activeDraft = deploymentService.getActiveDraftForDeployment(deployment.id());

        var updatedPrompts = objectMapper.createObjectNode();
        updatedPrompts.put("systemPrompt", "Use grounded answers only.");
        updatedPrompts.put("intentExtractionPrompt", "");
        updatedPrompts.put("actionSelectionPrompt", "");
        updatedPrompts.put("clarificationPrompt", "");
        updatedPrompts.put("answerGenerationPrompt", "");
        updatedPrompts.put("retrievalPrompt", "");
        updatedPrompts.put("assistantUiPrompt", "");

        deploymentService.updateDraft(
            activeDraft.id(),
            new UpdateDeploymentDraftRequest(null, null, null, null, null, updatedPrompts)
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/workspace", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.draft.revisionNumber", is(2)))
            .andExpect(jsonPath("$.latestVersion.id", is(publishedVersion.id())))
            .andExpect(jsonPath("$.lifecycle.savedDraftState", is("UNPUBLISHED_CHANGES")))
            .andExpect(jsonPath("$.lifecycle.liveState", is("LATEST_PUBLISHED_NOT_APPLIED")))
            .andExpect(jsonPath("$.lifecycle.hasPublishedVersion", is(true)))
            .andExpect(jsonPath("$.lifecycle.hasLiveVersion", is(false)))
            .andExpect(jsonPath("$.lifecycle.savedDraftMatchesLatestPublished", is(false)))
            .andExpect(jsonPath("$.lifecycle.liveMatchesLatestPublished", is(false)))
            .andExpect(jsonPath("$.lifecycle.latestPublishedVersionLabel", is(publishedVersion.versionLabel())))
            .andExpect(jsonPath("$.lifecycle.summaryMessage", notNullValue()));
    }
}
