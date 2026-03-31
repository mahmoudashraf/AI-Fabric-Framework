package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentCuratedModuleRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentCuratedModuleIntegrationTest {

    @Autowired
    private DeploymentService deploymentService;

    @Test
    void createDeploymentSeedsPromptBundleFromSelectedCuratedModule() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Commerce Curated Prompt Baseline", "dev", "dev-openai-lucene", "commerce")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());

        assertThat(draft.providerConfig().path("curatedModuleId").asText()).isEqualTo("commerce");
        assertThat(draft.providerConfig().path("curatedPackId").asText()).isEqualTo("commerce");
        assertThat(draft.promptConfig().path("systemPrompt").asText()).contains("commerce assistant");
        assertThat(draft.promptConfig().path("answerGenerationPrompt").asText()).contains("commerce support assistant");
    }

    @Test
    void applyingCuratedModuleToDraftRebasesProviderAndPromptConfig() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Curated Prompt Rebase", "dev", "dev-openai-lucene", "default")
        );

        DeploymentDraftResponse updated = deploymentService.applyCuratedModuleToDraft(
            deployment.id(),
            new UpdateDeploymentCuratedModuleRequest("commerce")
        );

        assertThat(updated.providerConfig().path("curatedModuleId").asText()).isEqualTo("commerce");
        assertThat(updated.providerConfig().path("promptPresetId").asText()).isEqualTo("commerce");
        assertThat(updated.providerConfig().path("curatedPackId").asText()).isEqualTo("commerce");
        assertThat(updated.promptConfig().path("systemPrompt").asText()).contains("commerce assistant");
        assertThat(updated.promptConfig().path("retrievalPrompt").asText()).contains("policy, review, and catalog");
    }
}
