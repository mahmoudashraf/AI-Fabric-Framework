package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDeletionOperationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentAssignmentRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationRunnerSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.DefaultResourceLoader;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

class DeploymentVerificationRolloutServiceTest {

    @Test
    void recreateRolloutsCreatesCanonicalDeploymentsAndSeedsProviderDefaults() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, DeploymentEntity> deploymentsById = new HashMap<>();

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(deploymentRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(deploymentsById.get(invocation.getArgument(0))));
        when(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(platformUserRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
            platformUser("usr-admin", "admin@example.com", "PLATFORM_ADMIN"),
            platformUser("usr-operator", "operator@example.com", "PLATFORM_OPERATOR")
        ));
        when(sourceConnectionRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(planRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(anyString())).thenReturn(Optional.empty());
        when(sourceConnectionRepository.save(any(VectorizationSourceConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any(VectorizationPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.save(any(VectorizationPlanRevisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId(deploymentId);
            entity.setName(request.name());
            entity.setEnvironmentName(request.environment());
            entity.setCustomerId("cust-internal");
            entity.setTenantId("ten-" + deploymentId);
            deploymentsById.put(deploymentId, entity);
            return new DeploymentSummary(
                deploymentId,
                request.name(),
                request.environment(),
                request.templateId(),
                null,
                new DeploymentSourceSummary("mahmoudashraf/AI-Fabric-Framework", "Platformv-V2", null, null, false),
                "DRAFT",
                null,
                null,
                false,
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
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            objectMapper,
            new DefaultResourceLoader()
        );

        DeploymentVerificationRolloutSummary summary = service.recreateRollouts();

        assertThat(summary.items()).hasSize(5);
        assertThat(summary.items()).extracting(item -> item.verificationProfile()).containsOnly("ecommerce");
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
        assertThat(ecommerce.routingConfig().path("connector").path("upstream").path("auth").path("type").asText()).isEqualTo("NONE");
        assertThat(ecommerce.routingConfig().path("connector").path("upstream").path("auth").path("header").asText()).isEqualTo("Authorization");
        assertThat(ecommerce.routingConfig().path("connector").path("upstream").path("auth").path("value").asText()).isEmpty();
        assertThat(ecommerce.routingConfig().path("authz").path("upstream").path("auth").path("type").asText()).isEqualTo("NONE");
        assertThat(ecommerce.routingConfig().path("authz").path("upstream").path("auth").path("header").asText()).isEqualTo("Authorization");
        assertThat(ecommerce.routingConfig().path("authz").path("upstream").path("auth").path("value").asText()).isEmpty();

        UpdateDeploymentDraftRequest qdrant = updates.get(1);
        assertThat(qdrant.providerConfig().path("qdrantCloudProviderId").asText()).isEqualTo("aws");
        assertThat(qdrant.providerConfig().path("qdrantCloudRegionId").asText()).isEqualTo("eu-west-1");
        assertThat(qdrant.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(qdrant.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(qdrant.entityConfig().path("ai-entities").has("product")).isTrue();
        assertThat(qdrant.entityConfig().path("ai-entities").has("policy")).isTrue();
        assertThat(qdrant.entityConfig().path("ai-entities").has("review")).isTrue();
        assertThat(qdrant.actionsConfig().path("actions")).isNotEmpty();
        assertThat(qdrant.routingConfig().path("actions")).isNotEmpty();
        assertThat(qdrant.routingConfig().path("connector").path("upstream").path("auth").path("type").asText()).isEqualTo("NONE");
        assertThat(qdrant.routingConfig().path("connector").path("upstream").path("auth").path("value").asText()).isEmpty();
        assertThat(qdrant.routingConfig().path("authz").path("upstream").path("auth").path("type").asText()).isEqualTo("NONE");
        assertThat(qdrant.routingConfig().path("authz").path("upstream").path("auth").path("value").asText()).isEmpty();
        assertThat(qdrant.securityConfig().path("authzBaseUrl").asText()).isEqualTo("https://ai-fabric-framework-production-a247.up.railway.app");

        UpdateDeploymentDraftRequest pinecone = updates.get(2);
        assertThat(pinecone.providerConfig().path("pineconeManagedIndexEnabled").asBoolean()).isTrue();
        assertThat(pinecone.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(pinecone.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(pinecone.entityConfig().path("ai-entities").has("product")).isTrue();
        assertThat(pinecone.entityConfig().path("ai-entities").has("policy")).isTrue();
        assertThat(pinecone.entityConfig().path("ai-entities").has("review")).isTrue();

        UpdateDeploymentDraftRequest milvus = updates.get(3);
        assertThat(milvus.providerConfig().path("zillizCloudProjectId").asText()).isEqualTo("proj-a58a34b87ccfe2c80d6ec2");
        assertThat(milvus.providerConfig().path("zillizCloudRegionId").asText()).isEqualTo("aws-eu-central-1");
        assertThat(milvus.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(milvus.providerConfig().has("zillizCloudCuSize")).isFalse();
        assertThat(milvus.providerConfig().has("zillizCloudCuType")).isFalse();
        assertThat(milvus.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(milvus.entityConfig().path("ai-entities").has("product")).isTrue();
        assertThat(milvus.entityConfig().path("ai-entities").has("policy")).isTrue();
        assertThat(milvus.entityConfig().path("ai-entities").has("review")).isTrue();
        assertThat(milvus.actionsConfig().path("actions")).isNotEmpty();
        assertThat(milvus.routingConfig().path("actions")).isNotEmpty();
        assertThat(milvus.securityConfig().path("authzBaseUrl").asText()).isEqualTo("https://ai-fabric-framework-production-a247.up.railway.app");

        UpdateDeploymentDraftRequest weaviate = updates.get(4);
        assertThat(weaviate.providerConfig().path("weaviateHost").asText()).isEqualTo("l8iep2jcrdodutnyepfvla.c0.europe-west3.gcp.weaviate.cloud");
        assertThat(weaviate.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(weaviate.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(weaviate.entityConfig().path("ai-entities").has("product")).isTrue();
        assertThat(weaviate.entityConfig().path("ai-entities").has("policy")).isTrue();
        assertThat(weaviate.entityConfig().path("ai-entities").has("review")).isTrue();

        ArgumentCaptor<VectorizationSourceConnectionEntity> connectionCaptor = ArgumentCaptor.forClass(VectorizationSourceConnectionEntity.class);
        verify(sourceConnectionRepository, times(5)).save(connectionCaptor.capture());
        assertThat(connectionCaptor.getAllValues())
            .extracting(VectorizationSourceConnectionEntity::getAdapterType)
            .containsOnly("REST_API");
        assertThat(connectionCaptor.getAllValues())
            .extracting(VectorizationSourceConnectionEntity::getAuthMode)
            .containsOnly("NONE");
        assertThat(connectionCaptor.getAllValues())
            .extracting(VectorizationSourceConnectionEntity::getStatus)
            .containsOnly("READY");
        assertThat(connectionCaptor.getAllValues())
            .allSatisfy(connection -> {
                JsonNode config = objectMapper.readTree(connection.getConnectionConfigJson());
                assertThat(config.path("baseUrl").asText()).isEqualTo("https://ai-fabric-framework-production-a247.up.railway.app");
                assertThat(config.path("datasets").path("product").path("path").asText()).isEqualTo("/api/products?limit=500");
                assertThat(config.path("datasets").path("review").path("path").asText()).isEqualTo("/api/reviews?limit=500");
                assertThat(config.path("datasets").path("policy").path("path").asText()).isEqualTo("/api/policies?limit=500");
            });

        ArgumentCaptor<VectorizationPlanEntity> planCaptor = ArgumentCaptor.forClass(VectorizationPlanEntity.class);
        verify(planRepository, times(10)).save(planCaptor.capture());
        assertThat(planCaptor.getAllValues())
            .extracting(VectorizationPlanEntity::getRunnerMode)
            .containsOnly("PLATFORM_MANAGED_AUTO");

        ArgumentCaptor<VectorizationPlanRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(VectorizationPlanRevisionEntity.class);
        verify(revisionRepository, times(5)).save(revisionCaptor.capture());
        assertThat(revisionCaptor.getAllValues())
            .allSatisfy(revision -> {
                JsonNode entityScope = objectMapper.readTree(revision.getEntityScopeJson());
                assertThat(entityScope).extracting(JsonNode::asText).containsExactly("policy", "product", "review");
                JsonNode executionConfig = objectMapper.readTree(revision.getExecutionConfigJson());
                assertThat(executionConfig.path("batchSize").asInt()).isEqualTo(25);
                assertThat(executionConfig.path("pageSize").asInt()).isEqualTo(500);
                JsonNode mappingConfig = objectMapper.readTree(revision.getMappingConfigJson());
                assertThat(mappingConfig.path("entityMappings").path("product").path("dataset").asText()).isEqualTo("product");
                assertThat(mappingConfig.path("entityMappings").path("review").path("dataset").asText()).isEqualTo("review");
                assertThat(mappingConfig.path("entityMappings").path("policy").path("dataset").asText()).isEqualTo("policy");
            });

        verify(deploymentAssignmentService, times(5)).upsertAssignment(anyString(), argThat(request ->
            request.userId().equals("usr-admin") && request.assignmentRole().equals("DEPLOYMENT_ADMIN")
        ));
        verify(deploymentAssignmentService, times(5)).upsertAssignment(anyString(), argThat(request ->
            request.userId().equals("usr-operator") && request.assignmentRole().equals("DEPLOYMENT_OPERATOR")
        ));
        verify(deploymentService, times(5)).applyVersion(anyString(), anyString());
    }

    @Test
    void canonicalVerificationProfileReturnsEcommerceForCanonicalVectorDeployment() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-pinecone");
        deployment.setName("OpenAI Pinecone Verification");
        deployment.setEnvironmentName("dev");

        when(deploymentRepository.findById(eq("dep-pinecone"))).thenReturn(java.util.Optional.of(deployment));

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            new ObjectMapper(),
            new DefaultResourceLoader()
        );

        assertThat(service.canonicalVerificationProfile("dep-pinecone")).isEqualTo("ecommerce");
        assertThat(service.isCanonicalRolloutDeployment("dep-pinecone")).isTrue();
    }

    @Test
    void recreateRolloutsCanTargetSelectedPresetsOnly() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, DeploymentEntity> deploymentsById = new HashMap<>();

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(deploymentRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(deploymentsById.get(invocation.getArgument(0))));
        when(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(platformUserRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
            platformUser("usr-admin", "admin@example.com", "PLATFORM_ADMIN"),
            platformUser("usr-operator", "operator@example.com", "PLATFORM_OPERATOR")
        ));
        when(sourceConnectionRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(planRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(anyString())).thenReturn(Optional.empty());
        when(sourceConnectionRepository.save(any(VectorizationSourceConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any(VectorizationPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.save(any(VectorizationPlanRevisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(deploymentService.createDeployment(any(CreateDeploymentRequest.class))).thenAnswer(invocation -> {
            CreateDeploymentRequest request = invocation.getArgument(0);
            String deploymentId = "dep-" + request.templateId();
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId(deploymentId);
            entity.setName(request.name());
            entity.setEnvironmentName(request.environment());
            entity.setCustomerId("cust-internal");
            entity.setTenantId("ten-" + deploymentId);
            deploymentsById.put(deploymentId, entity);
            return new DeploymentSummary(
                deploymentId,
                request.name(),
                request.environment(),
                request.templateId(),
                null,
                new DeploymentSourceSummary("repo", "branch", null, null, false),
                "DRAFT",
                null,
                null,
                false,
                false,
                false,
                Instant.now()
            );
        });
        when(deploymentService.getActiveDraftForDeployment(anyString())).thenAnswer(invocation -> draftResponse(invocation.getArgument(0), objectMapper));
        when(deploymentService.updateDraft(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
            String draftId = invocation.getArgument(0);
            UpdateDeploymentDraftRequest request = invocation.getArgument(1);
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
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            objectMapper,
            new DefaultResourceLoader()
        );

        DeploymentVerificationRolloutSummary summary = service.recreateRollouts(List.of("pinecone", "weaviate"));

        assertThat(summary.summaryMessage()).contains("2 canonical verification rollout deployment(s)");
        verify(deploymentService, times(2)).createDeployment(any(CreateDeploymentRequest.class));
        verify(deploymentService, times(2)).applyVersion(anyString(), anyString());
    }

    @Test
    void listRolloutsSurfacesManagedRunnerReadinessInMessage() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-weaviate");
        deployment.setName("OpenAI Weaviate Verification");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("ACTIVE");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.example");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setStatus("APPLIED");
        release.setProvisioningStatus("ACTIVE");
        release.setVerificationStatus("PASSED");
        release.setProvisioningDetailsJson("""
            {"railway":{"services":{"runtime":{"serviceId":"svc-runtime"},"restConnector":{"serviceId":"svc-connector"}}}}
            """);

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(deployment));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-weaviate")).thenReturn(Optional.of(release));
        when(deploymentVectorizationVerificationService.build(eq(deployment), any())).thenReturn(
            new DeploymentVectorizationVerificationSummary(
                "dep-weaviate",
                true,
                true,
                true,
                true,
                false,
                true,
                true,
                List.of("policy", "product", "review"),
                List.of("policy", "product", "review"),
                null,
                null,
                null
            )
        );

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            new ObjectMapper(),
            new DefaultResourceLoader()
        );

        DeploymentVerificationRolloutSummary summary = service.listRollouts();
        assertThat(summary.items()).hasSize(5);
        assertThat(summary.items())
            .filteredOn(item -> "weaviate".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.verificationReady()).isFalse();
                assertThat(item.readinessMessage()).contains("vectorization runner registration is not active yet");
            });
    }

    @Test
    void recreateRolloutsDoesNotReassignExistingOwnerToDifferentCanonicalRole() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, DeploymentEntity> deploymentsById = new HashMap<>();

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of());
        when(deploymentRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(deploymentsById.get(invocation.getArgument(0))));
        when(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of(existingAssignment(
            "asg-admin",
            "dep-any",
            "usr-admin",
            "DEPLOYMENT_ADMIN"
        )));
        when(platformUserRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
            platformUser("usr-admin", "admin@example.com", "PLATFORM_ADMIN"),
            platformUser("usr-operator", "operator@example.com", "PLATFORM_OPERATOR")
        ));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(sourceConnectionRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(planRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(anyString())).thenReturn(Optional.empty());
        when(sourceConnectionRepository.save(any(VectorizationSourceConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any(VectorizationPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.save(any(VectorizationPlanRevisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(deploymentService.createDeployment(any(CreateDeploymentRequest.class))).thenAnswer(invocation -> {
            CreateDeploymentRequest request = invocation.getArgument(0);
            String deploymentId = "dep-" + request.templateId();
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId(deploymentId);
            entity.setName(request.name());
            entity.setEnvironmentName(request.environment());
            deploymentsById.put(deploymentId, entity);
            return new DeploymentSummary(
                deploymentId,
                request.name(),
                request.environment(),
                request.templateId(),
                null,
                new DeploymentSourceSummary("repo", "branch", null, null, false),
                "DRAFT",
                null,
                null,
                false,
                false,
                false,
                Instant.now()
            );
        });
        when(deploymentService.getActiveDraftForDeployment(anyString())).thenAnswer(invocation -> draftResponse(invocation.getArgument(0), objectMapper));
        when(deploymentService.updateDraft(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
            String draftId = invocation.getArgument(0);
            UpdateDeploymentDraftRequest request = invocation.getArgument(1);
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
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            objectMapper,
            new DefaultResourceLoader()
        );

        service.recreateRollouts();

        verify(deploymentAssignmentService, never()).upsertAssignment(anyString(), argThat(request ->
            request.userId().equals("usr-admin") && request.assignmentRole().equals("DEPLOYMENT_OPERATOR")
        ));
        verify(deploymentAssignmentService, times(5)).upsertAssignment(anyString(), argThat(request ->
            request.userId().equals("usr-operator") && request.assignmentRole().equals("DEPLOYMENT_OPERATOR")
        ));
    }

    @Test
    void cleanupRolloutsArchivesAndHardDeletesSelectedPresets() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);

        DeploymentEntity pinecone = new DeploymentEntity();
        pinecone.setId("dep-pinecone");
        pinecone.setName("OpenAI Pinecone Verification");
        pinecone.setEnvironmentName("dev");

        DeploymentEntity weaviate = new DeploymentEntity();
        weaviate.setId("dep-weaviate");
        weaviate.setName("OpenAI Weaviate Verification");
        weaviate.setEnvironmentName("dev");
        weaviate.setArchivedAt(Instant.now());

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(pinecone, weaviate));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(deploymentService.deleteDeployment(anyString(), any(DeleteDeploymentRequest.class))).thenReturn(
            new DeploymentDeletionOperationSummary(
                "del-test",
                "dep-pinecone",
                "OpenAI Pinecone Verification",
                "dev",
                null,
                null,
                "QUEUED",
                "Subject to deletion completion. Cleanup is queued.",
                true,
                null,
                "Canonical verification rollout cleanup",
                "system",
                "SYSTEM",
                new ObjectMapper().createObjectNode(),
                new ObjectMapper().createObjectNode(),
                null,
                Instant.now(),
                null,
                null,
                Instant.now()
            )
        );

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            new ObjectMapper(),
            new DefaultResourceLoader()
        );

        DeploymentVerificationRolloutSummary summary = service.cleanupRollouts(List.of("pinecone", "weaviate"));

        assertThat(summary.summaryMessage()).contains("Queued cleanup for 2 canonical verification rollout deployment(s)");
        verify(deploymentService).archiveDeployment("dep-pinecone");
        verify(deploymentService, never()).archiveDeployment("dep-weaviate");
        verify(deploymentService).deleteDeployment(
            eq("dep-pinecone"),
            argThat((DeleteDeploymentRequest request) -> request != null && Boolean.TRUE.equals(request.hardDelete()))
        );
        verify(deploymentService).deleteDeployment(
            eq("dep-weaviate"),
            argThat((DeleteDeploymentRequest request) -> request != null && Boolean.TRUE.equals(request.hardDelete()))
        );
    }

    @Test
    void hardResetRolloutsWaitsForDeletionCompletionThenRecreatesSelectedPresets() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, DeploymentEntity> deploymentsById = new HashMap<>();

        DeploymentEntity pinecone = new DeploymentEntity();
        pinecone.setId("dep-pinecone");
        pinecone.setName("OpenAI Pinecone Verification");
        pinecone.setEnvironmentName("dev");
        deploymentsById.put(pinecone.getId(), pinecone);

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenAnswer(invocation -> List.copyOf(deploymentsById.values()));
        when(deploymentRepository.findById(anyString())).thenAnswer(invocation -> Optional.ofNullable(deploymentsById.get(invocation.getArgument(0))));
        when(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(platformUserRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(
            platformUser("usr-admin", "admin@example.com", "PLATFORM_ADMIN"),
            platformUser("usr-operator", "operator@example.com", "PLATFORM_OPERATOR")
        ));
        when(sourceConnectionRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(planRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(anyString())).thenReturn(Optional.empty());
        when(sourceConnectionRepository.save(any(VectorizationSourceConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any(VectorizationPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.save(any(VectorizationPlanRevisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(deploymentService.deleteDeployment(eq("dep-pinecone"), any(DeleteDeploymentRequest.class))).thenAnswer(invocation -> {
            pinecone.setArchivedAt(Instant.now());
            pinecone.setDeletionStatus("RUNNING");
            return new DeploymentDeletionOperationSummary(
                "del-pinecone",
                pinecone.getId(),
                pinecone.getName(),
                "dev",
                null,
                null,
                "QUEUED",
                "Subject to deletion completion. Cleanup is queued.",
                true,
                null,
                "Canonical verification rollout hard reset",
                "system",
                "SYSTEM",
                objectMapper.createObjectNode(),
                objectMapper.createObjectNode(),
                null,
                Instant.now(),
                null,
                null,
                Instant.now()
            );
        });
        when(deploymentService.createDeployment(any(CreateDeploymentRequest.class))).thenAnswer(invocation -> {
            CreateDeploymentRequest request = invocation.getArgument(0);
            DeploymentEntity entity = new DeploymentEntity();
            entity.setId("dep-pinecone-reset");
            entity.setName(request.name());
            entity.setEnvironmentName(request.environment());
            entity.setCustomerId("cust-internal");
            entity.setTenantId("ten-reset");
            deploymentsById.put(entity.getId(), entity);
            return new DeploymentSummary(
                entity.getId(),
                request.name(),
                request.environment(),
                request.templateId(),
                null,
                new DeploymentSourceSummary("repo", "branch", null, null, false),
                "DRAFT",
                null,
                null,
                false,
                false,
                false,
                Instant.now()
            );
        });
        when(deploymentService.getActiveDraftForDeployment(anyString())).thenAnswer(invocation -> draftResponse(invocation.getArgument(0), objectMapper));
        when(deploymentService.updateDraft(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
            String draftId = invocation.getArgument(0);
            UpdateDeploymentDraftRequest request = invocation.getArgument(1);
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
                request.shellConfig(),
                request.knowledgeSourceConfig(),
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
            deploymentAssignmentRepository,
            deploymentAssignmentService,
            platformUserRepository,
            platformSecretService,
            deploymentVectorizationVerificationService,
            sourceConnectionRepository,
            planRepository,
            revisionRepository,
            objectMapper,
            new DefaultResourceLoader(),
            2,
            1,
            millis -> deploymentsById.remove("dep-pinecone")
        );

        DeploymentVerificationRolloutSummary summary = service.hardResetRollouts(List.of("pinecone"));

        assertThat(summary.summaryMessage()).contains("Hard reset recreated 1 canonical verification rollout deployment(s)");
        verify(deploymentService).archiveDeployment("dep-pinecone");
        verify(deploymentService).deleteDeployment(
            eq("dep-pinecone"),
            argThat((DeleteDeploymentRequest request) -> request != null
                && Boolean.TRUE.equals(request.hardDelete())
                && "Canonical verification rollout hard reset".equals(request.reason()))
        );
        verify(deploymentService).createDeployment(any(CreateDeploymentRequest.class));
        verify(deploymentService).applyVersion("dep-pinecone-reset", "ver-drf-dep-pinecone-reset");
    }

    private PlatformUserEntity platformUser(String id, String email, String role) {
        PlatformUserEntity user = new PlatformUserEntity();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(email);
        user.setRole(role);
        user.setStatus("ACTIVE");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private DeploymentAssignmentEntity existingAssignment(String id, String deploymentId, String userId, String role) {
        DeploymentAssignmentEntity assignment = new DeploymentAssignmentEntity();
        assignment.setId(id);
        assignment.setDeploymentId(deploymentId);
        assignment.setUserId(userId);
        assignment.setAssignmentRole(role);
        assignment.setCreatedAt(Instant.now());
        assignment.setUpdatedAt(Instant.now());
        return assignment;
    }

    private DeploymentDraftResponse draftResponse(String deploymentId, ObjectMapper objectMapper) {
        ObjectNode provider = objectMapper.createObjectNode();
        provider.put("llmProvider", "openai");
        provider.put("embeddingProvider", "openai");
        provider.put("openaiModel", "gpt-4o-mini");
        provider.put("openaiEmbeddingModel", "text-embedding-3-small");
        provider.put("vectorProvisioningMode", "LOCAL_MANAGED");
        provider.put("vectorStrategy", "lucene");
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
    }
}
