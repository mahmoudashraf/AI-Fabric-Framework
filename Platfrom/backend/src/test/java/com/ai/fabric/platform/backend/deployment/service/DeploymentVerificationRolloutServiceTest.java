package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentVerificationRolloutServiceTest {

    @Test
    void recreateRolloutsCreatesCanonicalDeploymentsAndSeedsProviderDefaults() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        ObjectMapper objectMapper = new ObjectMapper();

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);

        when(deploymentService.createDeployment(any(CreateDeploymentRequest.class))).thenAnswer(invocation -> {
            CreateDeploymentRequest request = invocation.getArgument(0);
            String deploymentId = switch (request.templateId()) {
                case "dev-openai-lucene" -> "dep-ecommerce";
                case "dev-openai-qdrant" -> "dep-qdrant";
                case "dev-openai-pinecone" -> "dep-pinecone";
                case "dev-openai-milvus" -> "dep-milvus";
                case "dev-openai-weaviate" -> "dep-weaviate";
                default -> throw new IllegalArgumentException("Unexpected template: " + request.templateId());
            };
            return new DeploymentSummary(
                deploymentId,
                request.name(),
                request.environment(),
                request.templateId(),
                new DeploymentSourceSummary("mahmoudashraf/AI-Fabric-Framework", "Platformv-V2", null, null, false),
                "DRAFT",
                null,
                null,
                null,
                false,
                false,
                Instant.now()
            );
        });

        when(deploymentService.getActiveDraftForDeployment(anyString())).thenAnswer(invocation -> {
            String deploymentId = invocation.getArgument(0);
            ObjectNode provider = objectMapper.createObjectNode();
            provider.put("llmProvider", "openai");
            provider.put("embeddingProvider", "openai");
            provider.put("openaiModel", "gpt-4o-mini");
            provider.put("openaiEmbeddingModel", "text-embedding-3-small");
            provider.put("vectorProvisioningMode", "LOCAL_MANAGED");
            provider.put("vectorStrategy", switch (deploymentId) {
                case "dep-qdrant" -> "qdrant";
                case "dep-pinecone" -> "pinecone";
                case "dep-milvus" -> {
                    provider.put("zillizCloudClusterPlan", "Serverless");
                    provider.put("zillizCloudCuType", "");
                    provider.put("zillizCloudCuSize", 0);
                    yield "milvus";
                }
                case "dep-weaviate" -> "weaviate";
                default -> "lucene";
            });
            ObjectNode entity = objectMapper.createObjectNode();
            entity.putObject("ai-config").put("vector-dimensions", 1536);
            entity.putObject("ai-entities");
            return new DeploymentDraftResponse(
                "drf-" + deploymentId,
                deploymentId,
                1,
                "DRAFT",
                objectMapper.createObjectNode().putArray("actions"),
                entity,
                objectMapper.createObjectNode(),
                provider,
                objectMapper.createObjectNode()
                    .put("authzMode", "REMOTE_HTTP")
                    .put("adminApiKeyEnabled", true)
                    .put("connectorApiKeyEnabled", true),
                objectMapper.createObjectNode(),
                Instant.now(),
                Instant.now()
            );
        });

        when(deploymentService.updateDraft(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
            UpdateDeploymentDraftRequest request = invocation.getArgument(1);
            String draftId = invocation.getArgument(0);
            return new DeploymentDraftResponse(
                draftId,
                draftId.replace("drf-", ""),
                1,
                "DRAFT",
                request.actionsConfig(),
                request.entityConfig(),
                request.routingConfig(),
                request.providerConfig(),
                request.securityConfig(),
                request.promptConfig(),
                Instant.now(),
                Instant.now()
            );
        });

        when(deploymentService.validateDraft(anyString())).thenAnswer(invocation -> new DraftValidationResponse(
            invocation.getArgument(0),
            invocation.<String>getArgument(0).replace("drf-", ""),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));

        when(deploymentService.publishDraft(anyString())).thenAnswer(invocation -> {
            String draftId = invocation.getArgument(0);
            return new DeploymentVersionSummary("ver-" + draftId, draftId.replace("drf-", ""), draftId, "v1", "PUBLISHED", "hash", false, Instant.now());
        });

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            platformSecretService,
            objectMapper,
            new DefaultResourceLoader()
        );

        DeploymentVerificationRolloutSummary summary = service.recreateRollouts();

        assertThat(summary.items()).hasSize(5);
        assertThat(summary.items()).allMatch(item -> item.exists() || !item.verificationReady());

        ArgumentCaptor<CreateDeploymentRequest> createCaptor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService, times(5)).createDeployment(createCaptor.capture());
        assertThat(createCaptor.getAllValues())
            .extracting(CreateDeploymentRequest::templateId)
            .containsExactly(
                "dev-openai-lucene",
                "dev-openai-qdrant",
                "dev-openai-pinecone",
                "dev-openai-milvus",
                "dev-openai-weaviate"
            );

        ArgumentCaptor<UpdateDeploymentDraftRequest> updateCaptor = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(deploymentService, times(5)).updateDraft(anyString(), updateCaptor.capture());
        List<UpdateDeploymentDraftRequest> updates = updateCaptor.getAllValues();

        UpdateDeploymentDraftRequest ecommerce = updates.get(0);
        assertThat(ecommerce.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(512);
        assertThat(ecommerce.entityConfig().path("ai-entities").isObject()).isTrue();

        UpdateDeploymentDraftRequest qdrant = updates.get(1);
        assertThat(qdrant.providerConfig().path("qdrantCloudProviderId").asText()).isEqualTo("aws");
        assertThat(qdrant.providerConfig().path("qdrantCloudRegionId").asText()).isEqualTo("eu-west-1");
        assertThat(qdrant.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(qdrant.actionsConfig().path("actions")).isNotEmpty();
        assertThat(qdrant.routingConfig().path("actions")).isNotEmpty();
        assertThat(qdrant.securityConfig().path("authzBaseUrl").asText()).isEqualTo("https://ai-fabric-framework-production-a247.up.railway.app");

        UpdateDeploymentDraftRequest pinecone = updates.get(2);
        assertThat(pinecone.providerConfig().path("pineconeManagedIndexEnabled").asBoolean()).isTrue();
        assertThat(pinecone.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");

        UpdateDeploymentDraftRequest milvus = updates.get(3);
        assertThat(milvus.providerConfig().path("zillizCloudProjectId").asText()).isEqualTo("proj-a58a34b87ccfe2c80d6ec2");
        assertThat(milvus.providerConfig().path("zillizCloudRegionId").asText()).isEqualTo("aws-eu-central-1");
        assertThat(milvus.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(milvus.providerConfig().has("zillizCloudCuSize")).isFalse();
        assertThat(milvus.providerConfig().has("zillizCloudCuType")).isFalse();
        assertThat(milvus.actionsConfig().path("actions")).isNotEmpty();
        assertThat(milvus.routingConfig().path("actions")).isNotEmpty();
        assertThat(milvus.securityConfig().path("authzBaseUrl").asText()).isEqualTo("https://ai-fabric-framework-production-a247.up.railway.app");

        UpdateDeploymentDraftRequest weaviate = updates.get(4);
        assertThat(weaviate.providerConfig().path("weaviateHost").asText()).isEqualTo("l8iep2jcrdodutnyepfvla.c0.europe-west3.gcp.weaviate.cloud");
        assertThat(weaviate.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("EXTERNAL_EXISTING");

        verify(deploymentService, times(5)).applyVersion(anyString(), anyString());
    }
}
