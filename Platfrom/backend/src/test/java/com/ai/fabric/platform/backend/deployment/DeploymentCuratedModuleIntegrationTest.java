package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentCuratedModuleRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.fasterxml.jackson.databind.JsonNode;
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

    @Test
    void listTemplatesIncludesExpandedProviderPresets() {
        assertThat(deploymentService.listTemplates())
            .extracting(DeploymentTemplateSummary::id)
            .contains(
                "dev-openai-lucene",
                "dev-openai-memory",
                "dev-openai-qdrant",
                "dev-azure-pinecone",
                "dev-anthropic-lucene",
                "dev-cohere-weaviate",
                "dev-gemini-milvus",
                "dev-openai-rest-pinecone"
            );
    }

    @Test
    void createDeploymentSeedsTemplateSpecificProviderAndVectorDefaults() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Azure Pinecone Defaults", "dev", "dev-azure-pinecone", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("azure");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("azure");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("pinecone");
        assertThat(providerConfig.path("runtimeProfile").asText()).isEqualTo("runtime-managed");
        assertThat(providerConfig.path("connectorProfile").asText()).isEqualTo("connector-hosted");
        assertThat(providerConfig.path("azureApiVersion").asText()).isEqualTo("2024-02-15-preview");
        assertThat(providerConfig.path("pineconeDimensions").asInt()).isEqualTo(512);
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(512);
    }

    @Test
    void createDeploymentSeedsExternalEmbeddingDefaultsForRestAndPineconeTemplate() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("REST Pinecone Defaults", "dev", "dev-openai-rest-pinecone", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("rest");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("pinecone");
        assertThat(providerConfig.path("restEmbeddingEndpoint").asText()).isEqualTo("/embed");
        assertThat(providerConfig.path("restEmbeddingBatchEndpoint").asText()).isEqualTo("/embed/batch");
        assertThat(providerConfig.path("restEmbeddingModel").asText()).isEqualTo("all-MiniLM-L6-v2");
        assertThat(providerConfig.path("restEmbeddingTimeoutMs").asInt()).isEqualTo(30000);
        assertThat(providerConfig.path("pineconeDimensions").asInt()).isEqualTo(384);
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(384);
    }
}
