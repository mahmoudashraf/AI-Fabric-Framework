package com.ai.fabric.platform.backend.deployment;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTemplateSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentCuratedModuleRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    @Autowired
    private VectorizationPlanRepository vectorizationPlanRepository;

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }

    @Test
    void createDeploymentSeedsPromptBundleFromSelectedCuratedModule() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Commerce Curated Prompt Baseline", "dev", "dev-openai-lucene", "commerce")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());

        assertThat(draft.providerConfig().path("curatedModuleId").asText()).isEqualTo("commerce");
        assertThat(draft.providerConfig().path("curatedPackId").asText()).isEqualTo("commerce");
        assertThat(draft.providerConfig().path("openaiEmbeddingDimensions").asInt()).isEqualTo(1024);
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1024);
        assertThat(draft.promptConfig().path("systemPrompt").asText()).contains("commerce assistant");
        assertThat(draft.promptConfig().path("answerGenerationPrompt").asText()).contains("commerce support assistant");
    }

    @Test
    void createDeploymentSeedsSupportPromptBundleFromSelectedCuratedModule() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Support Curated Prompt Baseline", "dev", "dev-openai-lucene", "support")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());

        assertThat(draft.providerConfig().path("curatedModuleId").asText()).isEqualTo("support");
        assertThat(draft.providerConfig().path("curatedPackId").asText()).isEqualTo("support");
        assertThat(draft.promptConfig().path("systemPrompt").asText()).contains("what you can help with");
        assertThat(draft.promptConfig().path("intentExtractionPrompt").asText()).contains("must not be classified as OUT_OF_SCOPE");
        assertThat(draft.promptConfig().path("answerGenerationPrompt").asText()).contains("capability-overview requests");
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
                "custom-start-from-scratch",
                "dev-openai-lucene",
                "dev-openai-memory",
                "dev-openai-qdrant",
                "dev-openai-pinecone",
                "dev-openai-weaviate",
                "dev-openai-milvus",
                "dev-azure-pinecone",
                "dev-anthropic-lucene",
                "dev-cohere-weaviate",
                "dev-gemini-milvus"
            );
    }

    @Test
    void customStarterTemplateSeedsEditableBaselineDefaults() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Custom Starter", "dev", "custom-start-from-scratch", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("curatedModuleId").asText()).isEqualTo("default");
        assertThat(providerConfig.path("curatedPackId").asText()).isEqualTo("default");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("lucene");
        assertThat(providerConfig.path("vectorProvisioningMode").asText()).isEqualTo("LOCAL_MANAGED");
        assertThat(providerConfig.path("runtimeProfile").asText()).isEqualTo("runtime-managed");
        assertThat(providerConfig.path("connectorProfile").asText()).isEqualTo("connector-hosted");
        assertThat(providerConfig.path("openaiEmbeddingDimensions").asInt()).isEqualTo(1024);
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1024);
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
        assertThat(providerConfig.path("pineconeManagedIndexEnabled").asBoolean()).isTrue();
        assertThat(providerConfig.path("pineconeIndexName").asText()).startsWith("azure-pinecone-defaults-");
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(512);
    }

    @Test
    void createDeploymentSeedsManagedZillizDefaultsForMilvusTemplate() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Zilliz Defaults", "dev", "dev-gemini-milvus", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("gemini");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("gemini");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("milvus");
        assertThat(providerConfig.path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(providerConfig.path("milvusSecure").asBoolean()).isTrue();
        assertThat(providerConfig.path("milvusPort").asInt()).isEqualTo(443);
        assertThat(providerConfig.path("zillizCloudClusterPlan").asText()).isEqualTo("Serverless");
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(768);
    }

    @Test
    void createDeploymentSeedsManagedPineconeDefaultsForOpenAiTemplate() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("OpenAI Pinecone Defaults", "dev", "dev-openai-pinecone", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("pinecone");
        assertThat(providerConfig.path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(providerConfig.path("pineconeManagedIndexEnabled").asBoolean()).isTrue();
        assertThat(providerConfig.path("pineconeDimensions").asInt()).isEqualTo(1536);
        assertThat(providerConfig.path("pineconeIndexName").asText()).startsWith("openai-pinecone-defaults-");
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
    }

    @Test
    void createDeploymentSeedsWeaviateDefaultsForOpenAiTemplate() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("OpenAI Weaviate Defaults", "dev", "dev-openai-weaviate", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("weaviate");
        assertThat(providerConfig.path("vectorProvisioningMode").asText()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(providerConfig.path("weaviateScheme").asText()).isEqualTo("https");
        assertThat(providerConfig.path("weaviatePort").asInt()).isEqualTo(443);
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
    }

    @Test
    void createDeploymentSeedsManagedZillizDefaultsForOpenAiTemplate() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("OpenAI Zilliz Defaults", "dev", "dev-openai-milvus", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("llmProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("embeddingProvider").asText()).isEqualTo("openai");
        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("milvus");
        assertThat(providerConfig.path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(providerConfig.path("milvusSecure").asBoolean()).isTrue();
        assertThat(providerConfig.path("milvusPort").asInt()).isEqualTo(443);
        assertThat(providerConfig.path("zillizCloudClusterPlan").asText()).isEqualTo("Serverless");
        assertThat(draft.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
    }

    @Test
    void createDeploymentEnablesManagedCollectionsForQdrantTemplate() {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Qdrant Cloud Defaults", "dev", "dev-openai-qdrant", "default")
        );

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode providerConfig = draft.providerConfig();

        assertThat(providerConfig.path("vectorStrategy").asText()).isEqualTo("qdrant");
        assertThat(providerConfig.path("qdrantManagedCollectionsEnabled").asBoolean()).isTrue();
        assertThat(providerConfig.path("qdrantHost").asText("")).isEmpty();
        assertThat(vectorizationPlanRepository.findByDeploymentId(deployment.id()))
            .hasValueSatisfying(plan -> {
                assertThat(plan.getRunnerMode()).isEqualTo("PLATFORM_MANAGED_AUTO");
                assertThat(plan.getSyncState()).isEqualTo("BOOTSTRAP_REQUIRED");
                assertThat(plan.getSyncReasonCodesJson()).contains("MANAGED_TEMPLATE_DEFAULT");
            });
    }
}
