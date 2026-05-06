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
import org.mockito.InOrder;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doNothing;

class DeploymentVerificationRolloutServiceTest {

    @Test
    void listRolloutsRecoversStaleInProgressReleasesBeforeSummarizing() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentReleaseRecoveryService deploymentReleaseRecoveryService = mock(DeploymentReleaseRecoveryService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);

        DeploymentEntity existing = new DeploymentEntity();
        existing.setId("dep-813fa5c9");
        existing.setName("OpenAI Weaviate Verification");
        existing.setEnvironmentName("dev");
        existing.setStatus("VERSION_PUBLISHED");
        existing.setActiveVersionId("ver-1");
        existing.setRuntimeBaseUrl("https://runtime.example.test");
        existing.setConnectorBaseUrl("https://connector.example.test");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-stale");
        release.setDeploymentId(existing.getId());
        release.setStatus("PRE_APPLY_VERIFYING");
        release.setVerificationStatus("RUNNING");
        release.setProvisioningTarget("RAILWAY_API");
        release.setCurrentStepKey("preflight_verification");
        release.setCreatedAt(Instant.now());
        release.setUpdatedAt(Instant.now());

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(existing), List.of(existing));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(existing.getId())).thenReturn(Optional.of(release));
        when(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(anyString())).thenReturn(List.of());
        when(platformUserRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(platformUser("usr-admin", "admin@example.com", "PLATFORM_ADMIN")));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(sourceConnectionRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(planRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(anyString())).thenReturn(Optional.empty());
        when(deploymentReleaseRecoveryService.reconcileLatestInProgressRelease(existing.getId())).thenReturn(true);

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentReleaseRecoveryService,
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

        assertThat(summary.items()).isNotEmpty();
        verify(deploymentReleaseRecoveryService).reconcileLatestInProgressRelease(existing.getId());
        verify(deploymentRepository, times(2)).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void recreateRolloutsRecoversExistingDeploymentBeforeApply() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentReleaseRecoveryService deploymentReleaseRecoveryService = mock(DeploymentReleaseRecoveryService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);

        DeploymentEntity existing = new DeploymentEntity();
        existing.setId("dep-813fa5c9");
        existing.setName("OpenAI Weaviate Verification");
        existing.setEnvironmentName("dev");
        existing.setStatus("VERSION_PUBLISHED");
        existing.setActiveVersionId("ver-old");
        existing.setRuntimeBaseUrl("https://runtime.example.test");
        existing.setConnectorBaseUrl("https://connector.example.test");
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(existing), List.of(existing));
        when(deploymentRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(existing.getId())).thenReturn(Optional.empty());
        when(deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(existing.getId())).thenReturn(List.of());
        when(platformUserRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(platformUser("usr-admin", "admin@example.com", "PLATFORM_ADMIN")));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(sourceConnectionRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(planRepository.findByDeploymentId(anyString())).thenReturn(Optional.empty());
        when(revisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(anyString())).thenReturn(Optional.empty());
        when(sourceConnectionRepository.save(any(VectorizationSourceConnectionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(planRepository.save(any(VectorizationPlanEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(revisionRepository.save(any(VectorizationPlanRevisionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(deploymentReleaseRecoveryService.reconcileLatestInProgressRelease(existing.getId(), true)).thenReturn(true);
        ObjectMapper objectMapper = new ObjectMapper();
        when(deploymentService.getActiveDraftForDeploymentInternal(existing.getId())).thenReturn(new DeploymentDraftResponse(
            "drf-weaviate",
            existing.getId(),
            1,
            "DRAFT",
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            objectMapper.createObjectNode(),
            Instant.now(),
            Instant.now()
        ));
        when(deploymentService.validateDraftInternal("drf-weaviate")).thenReturn(new DraftValidationResponse(
            "drf-weaviate",
            existing.getId(),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));
        when(deploymentService.publishDraftInternal("drf-weaviate", true)).thenReturn(new DeploymentVersionSummary(
            "ver-new",
            existing.getId(),
            "drf-weaviate",
            "v1",
            "PUBLISHED",
            "hash",
            false,
            Instant.now()
        ));

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentReleaseRecoveryService,
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

        service.recreateRollouts(List.of("weaviate"));

        InOrder order = inOrder(deploymentReleaseRecoveryService, deploymentService);
        order.verify(deploymentReleaseRecoveryService).reconcileLatestInProgressRelease(existing.getId(), true);
        order.verify(deploymentService).getActiveDraftForDeploymentInternal(existing.getId());
        order.verify(deploymentService).applyVersionInternal(existing.getId(), "ver-new", null, true);
    }

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
        Map<String, DeploymentEntity> deploymentsById = new ConcurrentHashMap<>();

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
            String deploymentId = rolloutDeploymentId(request);
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

        when(deploymentService.getActiveDraftForDeploymentInternal(anyString())).thenAnswer(invocation -> {
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

        when(deploymentService.updateDraftInternal(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
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

        when(deploymentService.validateDraftInternal(anyString())).thenAnswer(invocation -> new DraftValidationResponse(
            invocation.getArgument(0),
            invocation.<String>getArgument(0).replace("drf-", ""),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));

        when(deploymentService.publishDraftInternal(anyString(), eq(true))).thenAnswer(invocation -> {
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

        assertThat(summary.items()).hasSize(6);
        assertThat(summary.items()).extracting(item -> item.verificationProfile())
            .contains("ecommerce", "marketplace-runtime");
        assertThat(summary.items()).allMatch(item -> item.exists() || !item.verificationReady());

        ArgumentCaptor<CreateDeploymentRequest> createCaptor = ArgumentCaptor.forClass(CreateDeploymentRequest.class);
        verify(deploymentService, times(6)).createDeployment(createCaptor.capture());
        assertThat(createCaptor.getAllValues())
            .extracting(CreateDeploymentRequest::templateId)
            .containsExactly(
                "dev-openai-lucene",
                "dev-openai-qdrant",
                "dev-openai-qdrant",
                "dev-openai-pinecone",
                "dev-openai-milvus",
                "dev-openai-weaviate"
            );

        ArgumentCaptor<UpdateDeploymentDraftRequest> updateCaptor = ArgumentCaptor.forClass(UpdateDeploymentDraftRequest.class);
        verify(deploymentService, times(6)).updateDraftInternal(anyString(), updateCaptor.capture());
        List<UpdateDeploymentDraftRequest> updates = updateCaptor.getAllValues();
        assertThat(updates)
            .allSatisfy(update -> {
                assertThat(update.promptConfig().path("ragSimilarityThreshold").asDouble(-1.0d)).isEqualTo(0.1d);
                assertThat(update.promptConfig().path("smartSuggestionsEnabled").asBoolean(true)).isFalse();
                JsonNode interceptors = update.actionsConfig().path("confirmationInterceptors");
                assertThat(interceptors.isArray()).isTrue();
                assertThat(java.util.stream.StreamSupport.stream(interceptors.spliterator(), false)
                    .map(node -> node.path("name").asText())
                    .toList())
                    .containsExactly(
                        "cancel_to_retention_offer",
                        "accept_retention_offer",
                        "reject_retention_offer"
                    );
            });

        UpdateDeploymentDraftRequest ecommerce = updates.get(0);
        assertThat(ecommerce.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(512);
        assertThat(ecommerce.providerConfig().path("openaiEmbeddingDimensions").asInt()).isEqualTo(512);
        assertThat(ecommerce.providerConfig().path("orchestrationLlmProvider").asText()).isEqualTo("openai");
        assertThat(ecommerce.providerConfig().path("orchestrationModel").asText()).isEqualTo("gpt-5.4-nano");
        assertThat(ecommerce.providerConfig().path("generationLlmProvider").asText()).isEqualTo("openai");
        assertThat(ecommerce.providerConfig().path("generationModel").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(ecommerce.providerConfig().path("generationMaxTokens").asInt()).isEqualTo(800);
        assertThat(ecommerce.entityConfig().path("ai-entities").isObject()).isTrue();
        assertThat(ecommerce.routingConfig().path("connector").path("upstream").path("auth").path("type").asText()).isEqualTo("NONE");
        assertThat(ecommerce.routingConfig().path("connector").path("upstream").path("auth").path("header").asText()).isEqualTo("Authorization");
        assertThat(ecommerce.routingConfig().path("connector").path("upstream").path("auth").path("value").asText()).isEmpty();
        assertThat(ecommerce.routingConfig().path("authz").path("upstream").path("auth").path("type").asText()).isEqualTo("NONE");
        assertThat(ecommerce.routingConfig().path("authz").path("upstream").path("auth").path("header").asText()).isEqualTo("Authorization");
        assertThat(ecommerce.routingConfig().path("authz").path("upstream").path("auth").path("value").asText()).isEmpty();
        assertThat(ecommerce.routingConfig().path("actions").path("list_products").path("authz").path("enabled").asBoolean(false)).isTrue();
        assertThat(ecommerce.routingConfig().path("actions").path("list_products").path("authz").path("resourceId").asText())
            .isEqualTo("action:list_products");
        assertThat(ecommerce.routingConfig().path("actions").path("create_purchase_order").path("authz").path("enabled").asBoolean(false)).isTrue();
        assertThat(ecommerce.routingConfig().path("actions").path("create_purchase_order").path("authz").path("resourceId").asText())
            .isEqualTo("action:create_purchase_order");
        assertThat(findAction(ecommerce.actionsConfig(), "search_products").path("anonymousAllowed").asBoolean(false)).isTrue();
        assertThat(findAction(ecommerce.actionsConfig(), "view_cart").path("anonymousAllowed").asBoolean(false)).isTrue();
        assertThat(findAction(ecommerce.actionsConfig(), "create_purchase_order").path("anonymousAllowed").asBoolean(false)).isFalse();
        assertThat(ecommerce.securityConfig().path("publicRuntimeBootstrapEnabled").asBoolean(false)).isTrue();
        assertThat(ecommerce.securityConfig().path("publicRuntimeTokenIssuer").asText()).isEqualTo("ecommerce-demo");
        assertThat(ecommerce.securityConfig().path("publicRuntimeAcceptedIssuers").asText())
            .isEqualTo("ecommerce-demo,runtime-public-bootstrap");
        assertThat(ecommerce.securityConfig().path("publicRuntimeAcceptedAudiences").asText()).isEqualTo("ecommerce-demo-chat");
        assertThat(ecommerce.securityConfig().path("publicRuntimeDefaultAudience").asText()).isEqualTo("ecommerce-demo-chat");

        UpdateDeploymentDraftRequest marketplace = updates.get(1);
        assertThat(marketplace.knowledgeSourceConfig().path("contractVersion").asText()).isEqualTo("KNOWLEDGE_SOURCE_CONFIG_V1");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").isArray()).isTrue();
        assertThat(marketplace.knowledgeSourceConfig().path("sources")).hasSize(2);
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(0).path("id").asText()).isEqualTo("deployment-marketplace-knowledge");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(0).path("adapterType").asText()).isEqualTo("deployment-private-vector");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(1).path("id").asText()).isEqualTo("shared-marketplace-refund-policy");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(1).path("adapterType").asText()).isEqualTo("shared-index");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(1).path("datasetRef").asText())
            .isEqualTo("shared-marketplace-refund-policy-seed");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(1).path("handleRef").asText()).isEqualTo("commerce-catalog/refund-policy");
        assertThat(marketplace.knowledgeSourceConfig().path("sources").get(1).path("filters").path("classification").asText()).isEqualTo("refund");
        assertThat(marketplace.marketplaceDatasetConfig().path("contractVersion").asText()).isEqualTo("MARKETPLACE_DATASET_CONFIG_V1");
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets")).hasSize(1);
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("systemManaged").asBoolean()).isTrue();
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("marketplacePluginId").asText())
            .isEqualTo("platform-marketplace-runtime-rollout");
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("marketplacePluginVersionId").asText())
            .isEqualTo("platform-marketplace-runtime-rollout-v1");
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("datasetId").asText())
            .isEqualTo("shared-marketplace-refund-policy-seed");
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("ingestionMode").asText())
            .isEqualTo("PACKAGED_SEED");
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("handleRef").asText())
            .isEqualTo("commerce-catalog/refund-policy");
        assertThat(marketplace.marketplaceDatasetConfig().path("datasets").get(0).path("seedDatasetRef").asText())
            .isEqualTo("classpath:marketplace/datasets/verification/refund-policy.jsonl");
        assertThat(marketplace.providerConfig().path("vectorStrategy").asText()).isEqualTo("qdrant");
        assertThat(marketplace.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(marketplace.providerConfig().path("vectorStoragePosture").asText()).isEqualTo("SHARED");
        assertThat(marketplace.providerConfig().path("qdrantManagedCollectionsEnabled").asBoolean()).isTrue();
        assertThat(marketplace.providerConfig().path("qdrantCloudProviderId").asText()).isEqualTo("aws");
        assertThat(marketplace.providerConfig().path("qdrantCloudRegionId").asText()).isEqualTo("eu-west-1");
        assertThat(marketplace.providerConfig().path("llmProvider").asText()).isEqualTo("openai");
        assertThat(marketplace.providerConfig().path("orchestrationLlmProvider").asText()).isEqualTo("openai");
        assertThat(marketplace.providerConfig().path("orchestrationEndpointProfile").asText()).isEmpty();
        assertThat(marketplace.providerConfig().path("orchestrationModel").asText()).isEqualTo("gpt-5.4-nano");
        assertThat(marketplace.providerConfig().path("generationLlmProvider").asText()).isEqualTo("openai");
        assertThat(marketplace.providerConfig().path("generationEndpointProfile").asText()).isEmpty();
        assertThat(marketplace.providerConfig().path("generationModel").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(marketplace.providerConfig().path("embeddingProvider").asText()).isEqualTo("openai");
        assertThat(marketplace.providerConfig().path("embeddingEndpointProfile").asText()).isEmpty();
        assertThat(marketplace.providerConfig().path("openaiEmbeddingModel").asText()).isEqualTo("text-embedding-3-small");
        assertThat(marketplace.providerConfig().path("openaiEmbeddingDimensions").asInt()).isEqualTo(1536);
        assertThat(marketplace.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(marketplace.securityConfig().path("authzMode").asText()).isEqualTo("ALLOW_VERIFIED");
        assertThat(marketplace.securityConfig().has("authzBaseUrl")).isFalse();
        assertThat(marketplace.shellConfig().path("contractVersion").asText()).isEqualTo("SHELL_CONFIG_V1");
        assertThat(marketplace.shellConfig().path("modules").isArray()).isTrue();
        assertThat(marketplace.shellConfig().path("cards").isArray()).isTrue();
        assertThat(marketplace.shellConfig().path("starterPrompts")).hasSize(2);
        assertThat(marketplace.shellConfig().path("modules")).extracting(node -> node.path("id").asText())
            .containsExactly("product-catalog", "policies", "orders");
        assertThat(marketplace.shellConfig().path("cards")).extracting(node -> node.path("id").asText())
            .containsExactly("product-list", "policy-summary", "order-status");

        UpdateDeploymentDraftRequest qdrant = updates.get(2);
        assertThat(qdrant.providerConfig().path("qdrantCloudProviderId").asText()).isEqualTo("aws");
        assertThat(qdrant.providerConfig().path("qdrantCloudRegionId").asText()).isEqualTo("eu-west-1");
        assertThat(qdrant.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(qdrant.providerConfig().path("openaiEmbeddingDimensions").asInt()).isEqualTo(1536);
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
        assertThat(qdrant.securityConfig().path("publicRuntimeBootstrapEnabled").asBoolean(false)).isTrue();
        assertThat(qdrant.securityConfig().path("publicRuntimeTokenIssuer").asText()).isEqualTo("ecommerce-demo");
        assertThat(qdrant.securityConfig().path("publicRuntimeAcceptedAudiences").asText()).isEqualTo("ecommerce-demo-chat");

        UpdateDeploymentDraftRequest pinecone = updates.get(3);
        assertThat(pinecone.providerConfig().path("pineconeManagedIndexEnabled").asBoolean()).isTrue();
        assertThat(pinecone.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("PLATFORM_MANAGED");
        assertThat(pinecone.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(pinecone.entityConfig().path("ai-entities").has("product")).isTrue();
        assertThat(pinecone.entityConfig().path("ai-entities").has("policy")).isTrue();
        assertThat(pinecone.entityConfig().path("ai-entities").has("review")).isTrue();

        UpdateDeploymentDraftRequest milvus = updates.get(4);
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

        UpdateDeploymentDraftRequest weaviate = updates.get(5);
        assertThat(weaviate.providerConfig().path("weaviateHost").asText()).isEqualTo("weaviate.example.test");
        assertThat(weaviate.providerConfig().path("vectorProvisioningMode").asText()).isEqualTo("EXTERNAL_EXISTING");
        assertThat(weaviate.entityConfig().path("ai-config").path("vector-dimensions").asInt()).isEqualTo(1536);
        assertThat(weaviate.entityConfig().path("ai-entities").has("product")).isTrue();
        assertThat(weaviate.entityConfig().path("ai-entities").has("policy")).isTrue();
        assertThat(weaviate.entityConfig().path("ai-entities").has("review")).isTrue();

        ArgumentCaptor<VectorizationSourceConnectionEntity> connectionCaptor = ArgumentCaptor.forClass(VectorizationSourceConnectionEntity.class);
        verify(sourceConnectionRepository, times(6)).save(connectionCaptor.capture());
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
        verify(planRepository, times(12)).save(planCaptor.capture());
        assertThat(planCaptor.getAllValues())
            .extracting(VectorizationPlanEntity::getRunnerMode)
            .containsOnly("PLATFORM_MANAGED_AUTO");

        ArgumentCaptor<VectorizationPlanRevisionEntity> revisionCaptor = ArgumentCaptor.forClass(VectorizationPlanRevisionEntity.class);
        verify(revisionRepository, times(6)).save(revisionCaptor.capture());
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

        verify(deploymentAssignmentService, times(6)).upsertAssignmentInternal(anyString(), argThat(request ->
            request.userId().equals("usr-admin") && request.assignmentRole().equals("DEPLOYMENT_ADMIN")
        ));
        verify(deploymentAssignmentService, times(6)).upsertAssignmentInternal(anyString(), argThat(request ->
            request.userId().equals("usr-operator") && request.assignmentRole().equals("DEPLOYMENT_OPERATOR")
        ));
        verify(deploymentService, times(6)).applyVersionInternal(anyString(), anyString(), eq(null), eq(true));
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

        DeploymentEntity marketplace = new DeploymentEntity();
        marketplace.setId("dep-marketplace");
        marketplace.setName("Marketplace Runtime Verification");
        marketplace.setEnvironmentName("dev");

        when(deploymentRepository.findById(eq("dep-pinecone"))).thenReturn(java.util.Optional.of(deployment));
        when(deploymentRepository.findById(eq("dep-marketplace"))).thenReturn(java.util.Optional.of(marketplace));

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
        assertThat(service.canonicalVerificationProfile("dep-marketplace")).isEqualTo("marketplace-runtime");
        assertThat(service.isCanonicalRolloutDeployment("dep-pinecone")).isTrue();
        assertThat(service.isCanonicalRolloutDeployment("dep-marketplace")).isTrue();
    }

    @Test
    void listRolloutsMarksLatestFailedReleaseAsNotVerificationReady() {
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
        deployment.setId("dep-713bb33e");
        deployment.setName("OpenAI Weaviate Verification");
        deployment.setEnvironmentName("dev");
        deployment.setActiveVersionId("ver-772ad0da");
        deployment.setRuntimeBaseUrl("https://runtime-dep-713bb33e-dev.up.railway.app");
        deployment.setConnectorBaseUrl("https://rest-connector-dep-713bb33e-dev.up.railway.app");

        DeploymentReleaseEntity failedRelease = new DeploymentReleaseEntity();
        failedRelease.setId("rel-a36e4d65");
        failedRelease.setDeploymentId("dep-713bb33e");
        failedRelease.setDeploymentVersionId("ver-772ad0da");
        failedRelease.setStatus("APPLIED_VERIFICATION_FAILED");
        failedRelease.setVerificationStatus("FAILED");

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(deployment));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-713bb33e"))
            .thenReturn(Optional.of(failedRelease));
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(argThat(id -> !"dep-713bb33e".equals(id))))
            .thenReturn(Optional.empty());
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);

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

        assertThat(summary.items())
            .filteredOn(item -> "weaviate".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.exists()).isTrue();
                assertThat(item.verificationReady()).isFalse();
                assertThat(item.latestReleaseStatus()).isEqualTo("APPLIED_VERIFICATION_FAILED");
                assertThat(item.latestVerificationStatus()).isEqualTo("FAILED");
                assertThat(item.readinessMessage()).contains("not in a verified ready state");
            });
    }

    @Test
    void recreateRolloutsCanTargetSelectedPresetsOnly() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentReleaseRecoveryService deploymentReleaseRecoveryService = mock(DeploymentReleaseRecoveryService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, DeploymentEntity> deploymentsById = new ConcurrentHashMap<>();

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
            String deploymentId = rolloutDeploymentId(request);
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
        when(deploymentService.getActiveDraftForDeploymentInternal(anyString())).thenAnswer(invocation -> draftResponse(invocation.getArgument(0), objectMapper));
        when(deploymentService.updateDraftInternal(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
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
        when(deploymentService.validateDraftInternal(anyString())).thenAnswer(invocation -> new DraftValidationResponse(
            invocation.getArgument(0),
            invocation.<String>getArgument(0).replace("drf-", ""),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));
        when(deploymentService.publishDraftInternal(anyString(), eq(true))).thenAnswer(invocation -> {
            String draftId = invocation.getArgument(0);
            return new DeploymentVersionSummary("ver-" + draftId, draftId.replace("drf-", ""), draftId, "v1", "PUBLISHED", "hash", false, Instant.now());
        });

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentReleaseRecoveryService,
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
        verify(deploymentService, times(2)).applyVersionInternal(anyString(), anyString(), eq(null), eq(true));
        verify(deploymentReleaseRecoveryService, times(4)).reconcileLatestInProgressRelease(anyString(), eq(true));
    }

    @Test
    void recreateRolloutsExecutesSelectedPresetsSequentially() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentService deploymentService = mock(DeploymentService.class);
        DeploymentReleaseRecoveryService deploymentReleaseRecoveryService = mock(DeploymentReleaseRecoveryService.class);
        DeploymentAssignmentRepository deploymentAssignmentRepository = mock(DeploymentAssignmentRepository.class);
        DeploymentAssignmentService deploymentAssignmentService = mock(DeploymentAssignmentService.class);
        PlatformUserRepository platformUserRepository = mock(PlatformUserRepository.class);
        PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
        DeploymentVectorizationVerificationService deploymentVectorizationVerificationService = mock(DeploymentVectorizationVerificationService.class);
        VectorizationSourceConnectionRepository sourceConnectionRepository = mock(VectorizationSourceConnectionRepository.class);
        VectorizationPlanRepository planRepository = mock(VectorizationPlanRepository.class);
        VectorizationPlanRevisionRepository revisionRepository = mock(VectorizationPlanRevisionRepository.class);
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, DeploymentEntity> deploymentsById = new ConcurrentHashMap<>();
        AtomicInteger activeCreates = new AtomicInteger();
        AtomicInteger maxConcurrentCreates = new AtomicInteger();

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
            int active = activeCreates.incrementAndGet();
            maxConcurrentCreates.accumulateAndGet(active, Math::max);
            activeCreates.decrementAndGet();

            String deploymentId = rolloutDeploymentId(request);
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
        when(deploymentService.getActiveDraftForDeploymentInternal(anyString())).thenAnswer(invocation -> draftResponse(invocation.getArgument(0), objectMapper));
        when(deploymentService.updateDraftInternal(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
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
        when(deploymentService.validateDraftInternal(anyString())).thenAnswer(invocation -> new DraftValidationResponse(
            invocation.getArgument(0),
            invocation.<String>getArgument(0).replace("drf-", ""),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));
        when(deploymentService.publishDraftInternal(anyString(), eq(true))).thenAnswer(invocation -> {
            String draftId = invocation.getArgument(0);
            return new DeploymentVersionSummary("ver-" + draftId, draftId.replace("drf-", ""), draftId, "v1", "PUBLISHED", "hash", false, Instant.now());
        });

        DeploymentVerificationRolloutService service = new DeploymentVerificationRolloutService(
            deploymentRepository,
            releaseRepository,
            deploymentService,
            deploymentReleaseRecoveryService,
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
            Runnable::run
        );

        DeploymentVerificationRolloutSummary summary = service.recreateRollouts(List.of("pinecone", "weaviate"));

        assertThat(summary.summaryMessage()).contains("sequentially");
        assertThat(maxConcurrentCreates.get()).isEqualTo(1);
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
        release.setStatus("APPLIED_VERIFIED");
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
        assertThat(summary.items()).hasSize(6);
        assertThat(summary.items())
            .filteredOn(item -> "weaviate".equals(item.key()))
            .singleElement()
            .satisfies(item -> {
                assertThat(item.verificationReady()).isFalse();
                assertThat(item.readinessMessage()).contains("vectorization runner registration is not active yet");
            });
    }

    @Test
    void listRolloutsTreatsActiveRunnerSessionAsVerificationReadyWhenTokenExpired() {
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
        deployment.setId("dep-marketplace");
        deployment.setName("Marketplace Runtime Verification");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("ACTIVE");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.example");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningStatus("ACTIVE");
        release.setVerificationStatus("PASSED");
        release.setProvisioningDetailsJson("""
            {"railway":{"services":{"runtime":{"serviceId":"svc-runtime"},"restConnector":{"serviceId":"svc-connector"},"vectorizationRunner":{"serviceId":"svc-runner","deploymentStatus":"SUCCESS"}}}}
            """);

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(deployment));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-marketplace")).thenReturn(Optional.of(release));
        when(deploymentVectorizationVerificationService.build(eq(deployment), any())).thenReturn(
            new DeploymentVectorizationVerificationSummary(
                "dep-marketplace",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of("policy", "product", "review"),
                List.of("policy", "product", "review"),
                null,
                null,
                new VectorizationRunnerSummary(
                    "vrr-123",
                    "PLATFORM_MANAGED_AUTO",
                    "ACTIVE",
                    "CURRENT",
                    "hint-1234",
                    Instant.parse("2026-04-20T09:51:41Z"),
                    "vectorization-runner-dep-marketplace",
                    "2026.04.track-b",
                    "1",
                    Instant.parse("2026-04-21T11:30:00Z"),
                    Instant.parse("2026-04-21T11:45:00Z"),
                    Instant.parse("2099-04-21T17:45:00Z")
                )
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
        assertThat(summary.items()).filteredOn(item -> "marketplace".equals(item.key())).singleElement().satisfies(item -> {
            assertThat(item.key()).isEqualTo("marketplace");
            assertThat(item.verificationReady()).isTrue();
            assertThat(item.readinessMessage()).contains("vectorization runner");
        });
    }

    @Test
    void listRolloutsAcceptsCoolifyManagedVectorizationRunnerServiceEvidence() {
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
        deployment.setId("dep-marketplace");
        deployment.setName("Marketplace Runtime Verification");
        deployment.setEnvironmentName("dev");
        deployment.setStatus("ACTIVE");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl("https://runtime.example");
        deployment.setConnectorBaseUrl("https://connector.example");

        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setStatus("APPLIED_VERIFIED");
        release.setProvisioningStatus("ACTIVE");
        release.setVerificationStatus("PASSED");
        release.setProvisioningDetailsJson("""
            {"coolify":{"services":{"vectorizationRunner":{"serviceId":"runner-app","serviceName":"vectorization-runner-dep-marketplace","deploymentStatus":"SUCCESS","deploymentId":"deploy-runner"}}}}
            """);

        when(deploymentRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(deployment));
        when(platformSecretService.isSecretPresent(anyString())).thenReturn(true);
        when(releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc("dep-marketplace")).thenReturn(Optional.of(release));
        when(deploymentVectorizationVerificationService.build(eq(deployment), any())).thenReturn(
            new DeploymentVectorizationVerificationSummary(
                "dep-marketplace",
                true,
                true,
                true,
                true,
                true,
                true,
                true,
                List.of("policy", "product", "review"),
                List.of("policy", "product", "review"),
                null,
                null,
                new VectorizationRunnerSummary(
                    "vrr-123",
                    "PLATFORM_MANAGED_AUTO",
                    "ACTIVE",
                    "CURRENT",
                    "hint-1234",
                    Instant.parse("2026-04-20T09:51:41Z"),
                    "vectorization-runner-dep-marketplace",
                    "2026.04.track-b",
                    "1",
                    Instant.parse("2026-04-21T11:30:00Z"),
                    Instant.parse("2026-04-21T11:45:00Z"),
                    Instant.parse("2099-04-21T17:45:00Z")
                )
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

        assertThat(summary.items()).filteredOn(item -> "marketplace".equals(item.key())).singleElement().satisfies(item -> {
            assertThat(item.verificationReady()).isTrue();
            assertThat(item.readinessMessage()).contains("vectorization runner");
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
            String deploymentId = rolloutDeploymentId(request);
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
        when(deploymentService.getActiveDraftForDeploymentInternal(anyString())).thenAnswer(invocation -> draftResponse(invocation.getArgument(0), objectMapper));
        when(deploymentService.updateDraftInternal(anyString(), any(UpdateDeploymentDraftRequest.class))).thenAnswer(invocation -> {
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
        when(deploymentService.validateDraftInternal(anyString())).thenAnswer(invocation -> new DraftValidationResponse(
            invocation.getArgument(0),
            invocation.<String>getArgument(0).replace("drf-", ""),
            true,
            0,
            0,
            Instant.now(),
            List.of()
        ));
        when(deploymentService.publishDraftInternal(anyString(), eq(true))).thenAnswer(invocation -> {
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

        verify(deploymentAssignmentService, never()).upsertAssignmentInternal(anyString(), argThat(request ->
            request.userId().equals("usr-admin") && request.assignmentRole().equals("DEPLOYMENT_OPERATOR")
        ));
        verify(deploymentAssignmentService, times(6)).upsertAssignmentInternal(anyString(), argThat(request ->
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
    void hardResetRolloutsQueuesHardDeleteWhileBackgroundCleanupContinues() {
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
        pinecone.setArchivedAt(Instant.now());
        pinecone.setDeletionStatus("RUNNING");
        deploymentsById.put(pinecone.getId(), pinecone);

        DeploymentEntity weaviate = new DeploymentEntity();
        weaviate.setId("dep-weaviate");
        weaviate.setName("OpenAI Weaviate Verification");
        weaviate.setEnvironmentName("dev");
        weaviate.setArchivedAt(Instant.now());
        weaviate.setDeletionStatus(null);
        deploymentsById.put(weaviate.getId(), weaviate);

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

        DeploymentVerificationRolloutSummary summary = service.hardResetRollouts(List.of("pinecone", "weaviate"));

        assertThat(summary.summaryMessage()).contains("Force hard cleanup queued hard delete for 1 canonical verification rollout deployment(s)");
        assertThat(summary.summaryMessage()).contains("Background cleanup continues for OpenAI Pinecone Verification (RUNNING).");
        verify(deploymentService, never()).archiveDeployment("dep-pinecone");
        verify(deploymentService, never()).deleteDeployment(eq("dep-pinecone"), any(DeleteDeploymentRequest.class));
        verify(deploymentService).deleteDeployment(
            eq("dep-weaviate"),
            argThat((DeleteDeploymentRequest request) -> request != null
                && Boolean.TRUE.equals(request.hardDelete())
                && "Canonical verification rollout hard reset".equals(request.reason()))
        );
        verify(deploymentService, never()).createDeployment(any(CreateDeploymentRequest.class));
        verify(deploymentService, never()).applyVersionInternal(anyString(), anyString(), any(), anyBoolean());
    }

    @Test
    void hardResetRolloutsReportsBlockedArchiveWithoutRecreating() {
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
        Map<String, DeploymentEntity> deploymentsById = new HashMap<>();

        DeploymentEntity pinecone = new DeploymentEntity();
        pinecone.setId("dep-pinecone");
        pinecone.setName("OpenAI Pinecone Verification");
        pinecone.setEnvironmentName("dev");
        pinecone.setArchivedAt(null);
        pinecone.setDeletionStatus(null);
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

        doThrow(new ResponseStatusException(BAD_REQUEST, "Deployment cannot be archived while apply is in progress: rel-stuck"))
            .when(deploymentService).archiveDeployment("dep-pinecone");

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

        DeploymentVerificationRolloutSummary summary = service.hardResetRollouts(List.of("pinecone"));

        assertThat(summary.summaryMessage()).contains("Force hard cleanup queued hard delete for 0 canonical verification rollout deployment(s)");
        assertThat(summary.summaryMessage()).contains("Blocked: OpenAI Pinecone Verification (archive blocked: Deployment cannot be archived while apply is in progress: rel-stuck)");
        verify(deploymentService).archiveDeployment("dep-pinecone");
        verify(deploymentService, never()).deleteDeployment(eq("dep-pinecone"), any(DeleteDeploymentRequest.class));
        verify(deploymentService, never()).createDeployment(any(CreateDeploymentRequest.class));
        verify(deploymentService, never()).applyVersionInternal(anyString(), anyString(), any(), anyBoolean());
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

    private JsonNode findAction(JsonNode actionsConfig, String actionName) {
        JsonNode actions = actionsConfig.path("actions");
        if (!actions.isArray()) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
        for (JsonNode action : actions) {
            if (actionName.equals(action.path("name").asText())) {
                return action;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
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

    private String rolloutDeploymentId(CreateDeploymentRequest request) {
        if ("Marketplace Runtime Verification".equals(request.name())) {
            return "dep-marketplace";
        }
        return switch (request.templateId()) {
            case "dev-openai-lucene" -> "dep-ecommerce";
            case "dev-openai-qdrant" -> "dep-qdrant";
            case "dev-openai-pinecone" -> "dep-pinecone";
            case "dev-openai-milvus" -> "dep-milvus";
            case "dev-openai-weaviate" -> "dep-weaviate";
            default -> "dep-" + request.templateId();
        };
    }

    private DeploymentDraftResponse draftResponse(String deploymentId, ObjectMapper objectMapper) {
        ObjectNode provider = objectMapper.createObjectNode();
        provider.put("llmProvider", "openai");
        provider.put("embeddingProvider", "openai");
        provider.put("openaiModel", "gpt-4o-mini");
        provider.put("openaiEmbeddingModel", "text-embedding-3-small");
        provider.put("openaiEmbeddingDimensions", 1536);
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
