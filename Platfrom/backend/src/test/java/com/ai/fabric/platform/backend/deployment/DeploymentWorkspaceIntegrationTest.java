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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @Test
    void configDiffCenterShowsDraftPublishedAndTemplateSourceState() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Workspace Diff Center", "dev", "dev-openai-lucene")
        );
        DeploymentDraftResponse firstDraft = deploymentService.getActiveDraftForDeployment(deployment.id());
        deploymentService.publishDraft(firstDraft.id());
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

        mockMvc.perform(get("/api/deployments/{deploymentId}/config-diff-center", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.draft.referenceLabel", is("Draft r2")))
            .andExpect(jsonPath("$.latestPublished.referenceLabel", is("v1")))
            .andExpect(jsonPath("$.live.available", is(false)))
            .andExpect(jsonPath("$.templateSource.templateId", is("dev-openai-lucene")))
            .andExpect(jsonPath("$.templateSource.repository", notNullValue()))
            .andExpect(jsonPath("$.sections[?(@.key=='prompts')].driftState", is(java.util.List.of("DRAFT_AHEAD_UNAPPLIED"))))
            .andExpect(jsonPath("$.sections[?(@.key=='providers')].driftState", is(java.util.List.of("PUBLISHED_NOT_APPLIED"))))
            .andExpect(jsonPath("$.summaryMessage", notNullValue()));
    }

    @Test
    void serviceConfigModelShowsDeploymentServiceSurfaces() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Workspace Service Model", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/service-config-model", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.services.length()", is(5)))
            .andExpect(jsonPath("$.services[?(@.key=='runtime')].status", is(java.util.List.of("BLOCKED"))))
            .andExpect(jsonPath("$.services[?(@.key=='restConnector')].requiredFieldCount", is(java.util.List.of(8))))
            .andExpect(jsonPath("$.services[?(@.key=='uiSurface')].label", is(java.util.List.of("UI and browser surface"))))
            .andExpect(jsonPath("$.services[?(@.key=='upstreamStore')].status", is(java.util.List.of("WARNING"))))
            .andExpect(jsonPath("$.summaryMessage", notNullValue()));
    }

    @Test
    void secretUsageShowsManagedDeploymentSecretReferences() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Workspace Secret Usage", "dev", "dev-openai-lucene")
        );

        mockMvc.perform(get("/api/deployments/{deploymentId}/secret-usage", deployment.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].secretName", is(java.util.List.of("OPENAI_API_KEY"))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='OPENAI_API_KEY')].required", is(java.util.List.of(true))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='ACTIONS_CONNECTOR_API_KEY')].secretName", is(java.util.List.of("ACTIONS_CONNECTOR_API_KEY"))))
            .andExpect(jsonPath("$.secrets[?(@.secretName=='CONNECTOR_API_KEY')].secretName", is(java.util.List.of("CONNECTOR_API_KEY"))))
            .andExpect(jsonPath("$.literalRiskCount", is(0)))
            .andExpect(jsonPath("$.summaryMessage", notNullValue()));
    }
}
