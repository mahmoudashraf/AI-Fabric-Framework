package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentAssignmentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeleteDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVectorizationVerificationSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpsertDeploymentAssignmentRequest;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentAssignmentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.entity.PlatformUserEntity;
import com.ai.fabric.platform.backend.security.repository.PlatformUserRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class DeploymentVerificationRolloutService {

    private static final String ENVIRONMENT = "dev";
    private static final String CURATED_MODULE_ID = "commerce";
    private static final String ECOMMERCE_ACTIONS_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/runtime/config/ai-actions.yml";
    private static final String ECOMMERCE_ENTITIES_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/runtime/config/ai-entity-config.yml";
    private static final String ECOMMERCE_ROUTING_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/rest-connector/actions-routing.yml";
    private static final String ECOMMERCE_UPSTREAM_BASE_URL = "https://ai-fabric-framework-production-a247.up.railway.app";
    private static final int ECOMMERCE_VECTOR_DIMENSIONS = 512;
    private static final int OPENAI_VECTOR_DIMENSIONS = 1536;
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int DEFAULT_BATCH_SIZE = 25;
    private static final String QDRANT_PROVIDER = "aws";
    private static final String QDRANT_REGION = "eu-west-1";
    private static final String WEAVIATE_HOST = "l8iep2jcrdodutnyepfvla.c0.europe-west3.gcp.weaviate.cloud";
    private static final String ZILLIZ_PROJECT_ID = "proj-a58a34b87ccfe2c80d6ec2";
    private static final String ZILLIZ_REGION_ID = "aws-eu-central-1";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentService deploymentService;
    private final DeploymentAssignmentRepository deploymentAssignmentRepository;
    private final DeploymentAssignmentService deploymentAssignmentService;
    private final PlatformUserRepository platformUserRepository;
    private final PlatformSecretService platformSecretService;
    private final DeploymentVectorizationVerificationService deploymentVectorizationVerificationService;
    private final VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository;
    private final VectorizationPlanRepository vectorizationPlanRepository;
    private final VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final ResourceLoader resourceLoader;

    public DeploymentVerificationRolloutService(DeploymentRepository deploymentRepository,
                                                DeploymentReleaseRepository releaseRepository,
                                                DeploymentService deploymentService,
                                                DeploymentAssignmentRepository deploymentAssignmentRepository,
                                                DeploymentAssignmentService deploymentAssignmentService,
                                                PlatformUserRepository platformUserRepository,
                                                PlatformSecretService platformSecretService,
                                                DeploymentVectorizationVerificationService deploymentVectorizationVerificationService,
                                                VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                                VectorizationPlanRepository vectorizationPlanRepository,
                                                VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                                ObjectMapper objectMapper,
                                                ResourceLoader resourceLoader) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.deploymentService = deploymentService;
        this.deploymentAssignmentRepository = deploymentAssignmentRepository;
        this.deploymentAssignmentService = deploymentAssignmentService;
        this.platformUserRepository = platformUserRepository;
        this.platformSecretService = platformSecretService;
        this.deploymentVectorizationVerificationService = deploymentVectorizationVerificationService;
        this.vectorizationSourceConnectionRepository = vectorizationSourceConnectionRepository;
        this.vectorizationPlanRepository = vectorizationPlanRepository;
        this.vectorizationPlanRevisionRepository = vectorizationPlanRevisionRepository;
        this.objectMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceLoader = resourceLoader;
    }

    public DeploymentVerificationRolloutSummary listRollouts() {
        return buildSummary(null);
    }

    public DeploymentVerificationRolloutSummary recreateRollouts() {
        return recreateRollouts(null);
    }

    public DeploymentVerificationRolloutSummary recreateRollouts(List<String> selectedKeys) {
        List<VerificationRolloutDefinition> selected = selectedDefinitions(selectedKeys);
        for (VerificationRolloutDefinition definition : selected) {
            ensureDeployment(definition);
        }
        return buildSummary("Created or reapplied " + selected.size() + " canonical verification rollout deployment(s).");
    }

    public DeploymentVerificationRolloutSummary cleanupRollouts(List<String> selectedKeys) {
        List<VerificationRolloutDefinition> selected = selectedDefinitions(selectedKeys);
        int deleted = 0;
        int missing = 0;
        for (VerificationRolloutDefinition definition : selected) {
            DeploymentEntity existing = resolveExisting(deploymentRepository.findAllByOrderByCreatedAtDesc(), definition);
            if (existing == null) {
                missing++;
                continue;
            }
            if (existing.getArchivedAt() == null) {
                deploymentService.archiveDeployment(existing.getId());
            }
            deploymentService.deleteDeployment(
                existing.getId(),
                new DeleteDeploymentRequest(true, null, "Canonical verification rollout cleanup")
            );
            deleted++;
        }
        return buildSummary(
            "Queued cleanup for " + deleted + " canonical verification rollout deployment(s)."
                + (missing > 0 ? " " + missing + " selected rollout(s) were already absent." : "")
        );
    }

    private DeploymentVerificationRolloutSummary buildSummary(String overrideSummaryMessage) {
        List<DeploymentEntity> deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
        List<DeploymentVerificationRolloutItemSummary> items = definitions().stream()
            .map(definition -> toSummary(definition, resolveExisting(deployments, definition)))
            .toList();
        long ready = items.stream().filter(DeploymentVerificationRolloutItemSummary::verificationReady).count();
        return new DeploymentVerificationRolloutSummary(
            overrideSummaryMessage != null
                ? overrideSummaryMessage
                : ready + " of " + items.size() + " canonical verification deployments are ready to verify.",
            items
        );
    }

    public boolean isCanonicalRolloutDeployment(String deploymentId) {
        return canonicalVerificationProfile(deploymentId) != null;
    }

    public String canonicalVerificationProfile(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return null;
        }
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId).orElse(null);
        if (deployment == null || deployment.getArchivedAt() != null) {
            return null;
        }
        VerificationRolloutDefinition definition = resolveDefinition(deployment);
        return definition == null ? null : definition.verificationProfile();
    }

    private void ensureDeployment(VerificationRolloutDefinition definition) {
        List<DeploymentEntity> deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
        DeploymentEntity existing = resolveExisting(deployments, definition);
        String deploymentId;

        if (existing == null) {
            DeploymentSummary created = deploymentService.createDeployment(new CreateDeploymentRequest(
                definition.deploymentName(),
                ENVIRONMENT,
                definition.templateId(),
                CURATED_MODULE_ID,
                definition.vectorProvisioningMode()
            ));
            deploymentId = created.id();
        } else {
            deploymentId = existing.getId();
            if (existing.getArchivedAt() != null) {
                deploymentService.restoreDeployment(existing.getId());
            }
        }

        ensureCanonicalOwnershipAssignments(deploymentId);
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        UpdateDeploymentDraftRequest request = definition.updateDraft(draft);
        deploymentService.updateDraft(draft.id(), request);
        seedCanonicalVectorization(deploymentId);

        DraftValidationResponse validation = deploymentService.validateDraft(draft.id());
        if (!validation.publishReady()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Canonical verification rollout '" + definition.displayName() + "' is not publish ready: " + summarizeIssues(validation.issues())
            );
        }

        DeploymentVersionSummary version = deploymentService.publishDraft(draft.id());
        deploymentService.applyVersion(deploymentId, version.id());
    }

    private void ensureCanonicalOwnershipAssignments(String deploymentId) {
        List<DeploymentAssignmentEntity> assignments = deploymentAssignmentRepository.findByDeploymentIdOrderByCreatedAtAsc(deploymentId);
        Set<String> existingRoles = assignments.stream()
            .map(DeploymentAssignmentEntity::getAssignmentRole)
            .filter(Objects::nonNull)
            .map(role -> role.trim().toUpperCase(Locale.ROOT))
            .collect(java.util.stream.Collectors.toSet());
        Set<String> assignedUserIds = assignments.stream()
            .map(DeploymentAssignmentEntity::getUserId)
            .filter(Objects::nonNull)
            .collect(java.util.stream.Collectors.toSet());
        if (existingRoles.contains("DEPLOYMENT_ADMIN") && existingRoles.contains("DEPLOYMENT_OPERATOR")) {
            return;
        }

        List<PlatformUserEntity> eligibleUsers = platformUserRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(this::isCanonicalAssignmentCandidate)
            .filter(user -> !assignedUserIds.contains(user.getId()))
            .toList();

        PlatformUserEntity adminCandidate = eligibleUsers.stream()
            .filter(user -> PlatformRole.PLATFORM_ADMIN.name().equalsIgnoreCase(user.getRole()))
            .findFirst()
            .orElse(null);

        PlatformUserEntity operatorCandidate = eligibleUsers.stream()
            .filter(user -> adminCandidate == null || !Objects.equals(user.getId(), adminCandidate.getId()))
            .filter(user -> PlatformRole.PLATFORM_OPERATOR.name().equalsIgnoreCase(user.getRole()))
            .findFirst()
            .orElseGet(() -> eligibleUsers.stream()
                .filter(user -> adminCandidate == null || !Objects.equals(user.getId(), adminCandidate.getId()))
                .findFirst()
                .orElse(null));

        if (!existingRoles.contains("DEPLOYMENT_ADMIN") && adminCandidate != null) {
            deploymentAssignmentService.upsertAssignment(
                deploymentId,
                new UpsertDeploymentAssignmentRequest(adminCandidate.getId(), "DEPLOYMENT_ADMIN")
            );
        }
        if (!existingRoles.contains("DEPLOYMENT_OPERATOR") && operatorCandidate != null) {
            deploymentAssignmentService.upsertAssignment(
                deploymentId,
                new UpsertDeploymentAssignmentRequest(operatorCandidate.getId(), "DEPLOYMENT_OPERATOR")
            );
        }
    }

    private boolean isCanonicalAssignmentCandidate(PlatformUserEntity user) {
        if (user == null || !hasText(user.getId()) || !hasText(user.getRole())) {
            return false;
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            return false;
        }
        return PlatformRole.PLATFORM_ADMIN.name().equalsIgnoreCase(user.getRole())
            || PlatformRole.PLATFORM_OPERATOR.name().equalsIgnoreCase(user.getRole());
    }

    private DeploymentVerificationRolloutItemSummary toSummary(VerificationRolloutDefinition definition,
                                                               DeploymentEntity deployment) {
        List<String> missingPrerequisites = missingPrerequisites(definition);
        DeploymentReleaseEntity latestRelease = deployment == null
            ? null
            : releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        RolloutReadiness readiness = evaluateReadiness(deployment, latestRelease, missingPrerequisites);

        return new DeploymentVerificationRolloutItemSummary(
            definition.key(),
            definition.displayName(),
            definition.description(),
            definition.verificationProfile(),
            definition.writeVerificationSupported(),
            deployment == null ? null : deployment.getId(),
            ENVIRONMENT,
            deployment != null,
            deployment != null && deployment.getArchivedAt() != null,
            readiness.ready(),
            deployment == null ? "MISSING" : deployment.getStatus(),
            deployment == null ? null : deployment.getActiveVersionId(),
            latestRelease == null ? null : latestRelease.getStatus(),
            latestRelease == null ? null : latestRelease.getProvisioningStatus(),
            latestRelease == null ? null : latestRelease.getVerificationStatus(),
            deployment == null ? null : deployment.getRuntimeBaseUrl(),
            deployment == null ? null : deployment.getConnectorBaseUrl(),
            readiness.message(),
            missingPrerequisites
        );
    }

    private RolloutReadiness evaluateReadiness(DeploymentEntity deployment,
                                               DeploymentReleaseEntity latestRelease,
                                               List<String> missingPrerequisites) {
        if (deployment == null) {
            return new RolloutReadiness(false, "This canonical rollout has not been created yet.");
        }
        if (deployment.getArchivedAt() != null) {
            return new RolloutReadiness(false, "This canonical rollout is archived and must be restored before verification.");
        }
        if (!missingPrerequisites.isEmpty()) {
            return new RolloutReadiness(false, "Missing prerequisites: " + String.join(", ", missingPrerequisites));
        }
        if (!hasText(deployment.getActiveVersionId())) {
            return new RolloutReadiness(false, "A published active version is required before hosted verification can run.");
        }
        if (!hasText(deployment.getRuntimeBaseUrl()) || !hasText(deployment.getConnectorBaseUrl())) {
            return new RolloutReadiness(
                false,
                "This rollout exists, but it is not verification-ready yet. Wait for the apply to finish so runtime and connector URLs are attached."
            );
        }

        DeploymentVectorizationVerificationSummary vectorization = deploymentVectorizationVerificationService == null
            ? null
            : deploymentVectorizationVerificationService.build(deployment, objectMapper.createObjectNode());
        if (vectorization != null && vectorization.planPresent()) {
            if (!vectorization.configured()) {
                return new RolloutReadiness(
                    false,
                    "Runtime and connector endpoints are live, but the canonical vectorization plan is not fully linked yet."
                );
            }
            if (vectorization.runnerRequired() && !runnerRegistrationReady(vectorization)) {
                return new RolloutReadiness(
                    false,
                    "Runtime and connector endpoints are live, but the vectorization runner registration is not active yet."
                );
            }
            if (vectorization.platformManagedRunnerExpected() && !runnerServiceProvisioned(latestRelease)) {
                return new RolloutReadiness(
                    false,
                    "Runtime and connector endpoints are live, but the managed vectorization runner service has not been provisioned on the latest release yet."
                );
            }
            return new RolloutReadiness(true, "Runtime, connector, and vectorization runner are ready for hosted verification.");
        }

        return new RolloutReadiness(true, "Runtime and connector endpoints are live, and the rollout is ready for hosted verification.");
    }

    private List<String> missingPrerequisites(VerificationRolloutDefinition definition) {
        List<String> required = new ArrayList<>(List.of(
            "OPENAI_API_KEY",
            "CONNECTOR_API_KEY",
            "ACTIONS_CONNECTOR_API_KEY",
            "APP_ADMIN_API_KEY"
        ));
        required.addAll(definition.requiredSecrets());
        return required.stream()
            .filter(name -> !platformSecretService.isSecretPresent(name))
            .distinct()
            .toList();
    }

    private DeploymentEntity resolveExisting(List<DeploymentEntity> deployments,
                                             VerificationRolloutDefinition definition) {
        DeploymentEntity active = deployments.stream()
            .filter(candidate -> matches(candidate, definition) && candidate.getArchivedAt() == null)
            .findFirst()
            .orElse(null);
        if (active != null) {
            return active;
        }
        return deployments.stream()
            .filter(candidate -> matches(candidate, definition))
            .findFirst()
            .orElse(null);
    }

    private boolean matches(DeploymentEntity candidate, VerificationRolloutDefinition definition) {
        return definition.deploymentName().equalsIgnoreCase(candidate.getName())
            && ENVIRONMENT.equalsIgnoreCase(candidate.getEnvironmentName());
    }

    private VerificationRolloutDefinition resolveDefinition(DeploymentEntity deployment) {
        return definitions().stream()
            .filter(definition -> matches(deployment, definition))
            .findFirst()
            .orElse(null);
    }

    private List<VerificationRolloutDefinition> selectedDefinitions(List<String> selectedKeys) {
        if (selectedKeys == null || selectedKeys.isEmpty()) {
            return definitions();
        }
        Set<String> requested = selectedKeys.stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (requested.isEmpty()) {
            return definitions();
        }
        List<VerificationRolloutDefinition> selected = definitions().stream()
            .filter(definition -> requested.contains(definition.key()))
            .toList();
        if (selected.size() != requested.size()) {
            Set<String> known = definitions().stream().map(VerificationRolloutDefinition::key).collect(java.util.stream.Collectors.toSet());
            List<String> unknown = requested.stream().filter(key -> !known.contains(key)).toList();
            throw new ResponseStatusException(BAD_REQUEST, "Unknown canonical rollout preset(s): " + String.join(", ", unknown));
        }
        return selected;
    }

    private List<VerificationRolloutDefinition> definitions() {
        return List.of(
            new VerificationRolloutDefinition(
                "ecommerce",
                "Ecommerce Verification",
                "Canonical ecommerce verification deployment with the full commerce actions, entities, and upstream routing bootstrap.",
                "dev-openai-lucene",
                "LOCAL_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    return new UpdateDeploymentDraftRequest(
                        readYaml(ECOMMERCE_ACTIONS_RESOURCE),
                        ecommerceEntityConfig(),
                        ecommerceRoutingConfig(),
                        ensureObject(draft.providerConfig()),
                        ecommerceSecurityConfig(draft.securityConfig()),
                        ensureObject(draft.promptConfig())
                    );
                }
            },
            new VerificationRolloutDefinition(
                "qdrant",
                "OpenAI Qdrant Verification",
                "Canonical OpenAI plus platform-managed Qdrant Cloud verification deployment.",
                "dev-openai-qdrant",
                "PLATFORM_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("qdrantManagedCollectionsEnabled", true);
                    provider.put("qdrantCloudProviderId", QDRANT_PROVIDER);
                    provider.put("qdrantCloudRegionId", QDRANT_REGION);
                    return vectorDraftUpdate(draft, provider);
                }
            },
            new VerificationRolloutDefinition(
                "pinecone",
                "OpenAI Pinecone Verification",
                "Canonical OpenAI plus platform-managed Pinecone verification deployment.",
                "dev-openai-pinecone",
                "PLATFORM_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                List<String> requiredSecrets() {
                    return List.of("PINECONE_API_KEY");
                }

                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("pineconeManagedIndexEnabled", true);
                    if (!hasText(provider.path("pineconeRegion").asText(""))) {
                        provider.put("pineconeRegion", "us-east-1");
                    }
                    if (!hasText(provider.path("pineconeCloud").asText(""))) {
                        provider.put("pineconeCloud", "aws");
                    }
                    if (!hasText(provider.path("pineconeMetric").asText(""))) {
                        provider.put("pineconeMetric", "cosine");
                    }
                    return vectorDraftUpdate(draft, provider);
                }
            },
            new VerificationRolloutDefinition(
                "milvus",
                "OpenAI Milvus Verification",
                "Canonical OpenAI plus platform-managed Zilliz Cloud verification deployment.",
                "dev-openai-milvus",
                "PLATFORM_MANAGED",
                "ecommerce",
                true
            ) {
                @Override
                List<String> requiredSecrets() {
                    return List.of("ZILLIZ_CLOUD_API_KEY");
                }

                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "PLATFORM_MANAGED");
                    provider.put("zillizCloudProjectId", ZILLIZ_PROJECT_ID);
                    provider.put("zillizCloudRegionId", ZILLIZ_REGION_ID);
                    provider.put("zillizCloudClusterPlan", "Serverless");
                    provider.remove("zillizCloudCuType");
                    provider.remove("zillizCloudCuSize");
                    provider.put("milvusSecure", true);
                    provider.put("milvusPort", 443);
                    return vectorDraftUpdate(draft, provider);
                }
            },
            new VerificationRolloutDefinition(
                "weaviate",
                "OpenAI Weaviate Verification",
                "Canonical OpenAI plus external-existing Weaviate Cloud verification deployment.",
                "dev-openai-weaviate",
                "EXTERNAL_EXISTING",
                "ecommerce",
                true
            ) {
                @Override
                List<String> requiredSecrets() {
                    return List.of("WEAVIATE_API_KEY");
                }

                @Override
                UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft) {
                    ObjectNode provider = ensureObject(draft.providerConfig());
                    provider.put("vectorProvisioningMode", "EXTERNAL_EXISTING");
                    provider.put("weaviateScheme", "https");
                    provider.put("weaviateHost", WEAVIATE_HOST);
                    provider.put("weaviatePort", 443);
                    return vectorDraftUpdate(draft, provider);
                }
            }
        );
    }

    private UpdateDeploymentDraftRequest vectorDraftUpdate(DeploymentDraftResponse draft, ObjectNode providerConfig) {
        return new UpdateDeploymentDraftRequest(
            ensureObject(readYaml(ECOMMERCE_ACTIONS_RESOURCE)),
            ecommerceEntityConfig(OPENAI_VECTOR_DIMENSIONS),
            ecommerceRoutingConfig(),
            providerConfig,
            ecommerceSecurityConfig(draft.securityConfig()),
            ensureObject(draft.promptConfig())
        );
    }

    private ObjectNode ecommerceEntityConfig() {
        return ecommerceEntityConfig(ECOMMERCE_VECTOR_DIMENSIONS);
    }

    private ObjectNode ecommerceEntityConfig(int vectorDimensions) {
        ObjectNode root = ensureObject(readYaml(ECOMMERCE_ENTITIES_RESOURCE));
        ObjectNode aiConfig = root.with("ai-config");
        aiConfig.put("vector-dimensions", vectorDimensions);
        root.with("ai-entities");
        return root;
    }

    private ObjectNode ecommerceRoutingConfig() {
        ObjectNode root = ensureObject(readYaml(ECOMMERCE_ROUTING_RESOURCE));

        ObjectNode connector = root.with("connector");
        ObjectNode inboundAuth = connector.with("inbound-auth");
        inboundAuth.put("allow-unauthenticated", false);
        ObjectNode apiKey = inboundAuth.with("api-key");
        apiKey.put("enabled", true);
        apiKey.put("header", "X-AIFABRIC-API-KEY");
        apiKey.put("value", "${CONNECTOR_API_KEY}");

        ObjectNode upstream = connector.with("upstream");
        upstream.put("base-url", ECOMMERCE_UPSTREAM_BASE_URL);
        ObjectNode upstreamAuth = upstream.with("auth");
        if (!hasConcreteValue(upstreamAuth.path("type").asText(""))) {
            upstreamAuth.put("type", "NONE");
        }
        if (!hasConcreteValue(upstreamAuth.path("header").asText(""))) {
            upstreamAuth.put("header", "Authorization");
        }
        if (!hasConcreteValue(upstreamAuth.path("value").asText(""))) {
            upstreamAuth.put("value", "");
        }

        ObjectNode authz = root.with("authz");
        authz.put("enabled", true);
        authz.put("path", "/api/authz/check");
        ObjectNode authzUpstream = authz.with("upstream");
        authzUpstream.put("base-url", ECOMMERCE_UPSTREAM_BASE_URL);
        ObjectNode authzUpstreamAuth = authzUpstream.with("auth");
        if (!hasConcreteValue(authzUpstreamAuth.path("type").asText(""))) {
            authzUpstreamAuth.put("type", "NONE");
        }
        if (!hasConcreteValue(authzUpstreamAuth.path("header").asText(""))) {
            authzUpstreamAuth.put("header", "Authorization");
        }
        if (!hasConcreteValue(authzUpstreamAuth.path("value").asText(""))) {
            authzUpstreamAuth.put("value", "");
        }

        root.with("actions");
        return root;
    }

    private void seedCanonicalVectorization(String deploymentId) {
        if (vectorizationSourceConnectionRepository == null
            || vectorizationPlanRepository == null
            || vectorizationPlanRevisionRepository == null) {
            return;
        }
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Deployment not found for canonical vectorization seed: " + deploymentId));
        Instant now = Instant.now();

        VectorizationSourceConnectionEntity connection = vectorizationSourceConnectionRepository.findByDeploymentId(deploymentId)
            .orElseGet(() -> {
                VectorizationSourceConnectionEntity created = new VectorizationSourceConnectionEntity();
                created.setId(generateId("vcn"));
                created.setDeploymentId(deployment.getId());
                created.setCustomerId(deployment.getCustomerId());
                created.setTenantId(deployment.getTenantId());
                created.setCreatedAt(now);
                return created;
            });
        connection.setName(deployment.getName() + " store REST source");
        connection.setAdapterType("REST_API");
        connection.setAuthMode("NONE");
        connection.setStatus("READY");
        connection.setConnectionConfigJson(writeJson(canonicalVectorizationConnectionConfig()));
        connection.setSecretReferencesJson(writeJson(objectMapper.createObjectNode()));
        connection.setDiscoverySummaryJson(writeJson(canonicalDiscoverySummary()));
        connection.setUpdatedAt(now);
        vectorizationSourceConnectionRepository.save(connection);

        VectorizationPlanEntity plan = vectorizationPlanRepository.findByDeploymentId(deploymentId)
            .orElseGet(() -> {
                VectorizationPlanEntity created = new VectorizationPlanEntity();
                created.setId(generateId("vpl"));
                created.setDeploymentId(deployment.getId());
                created.setCustomerId(deployment.getCustomerId());
                created.setTenantId(deployment.getTenantId());
                created.setCreatedAt(now);
                return created;
            });
        plan.setName(deployment.getName() + " vectorization");
        plan.setStatus("ACTIVE");
        plan.setRunnerMode("PLATFORM_MANAGED_AUTO");
        plan.setSyncState("BOOTSTRAP_REQUIRED");
        plan.setSyncReasonCodesJson(writeJson(objectMapper.createArrayNode().add("PLAN_CREATED").add("CANONICAL_ROLLOUT_SEEDED")));
        plan.setSyncReasonDetailsJson(writeJson(objectMapper.createObjectNode()));
        plan.setSourceConnectionId(connection.getId());
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);

        VectorizationPlanRevisionEntity revision = plan.getActiveRevisionId() == null
            ? null
            : vectorizationPlanRevisionRepository.findById(plan.getActiveRevisionId()).orElse(null);
        if (revision == null) {
            revision = vectorizationPlanRevisionRepository.findTopByPlanIdOrderByRevisionNumberDesc(plan.getId())
                .orElseGet(() -> {
                    VectorizationPlanRevisionEntity created = new VectorizationPlanRevisionEntity();
                    created.setId(generateId("vpr"));
                    created.setPlanId(plan.getId());
                    created.setDeploymentId(deployment.getId());
                    created.setRevisionNumber(1);
                    created.setCreatedAt(now);
                    return created;
                });
        }
        revision.setStatus("ACTIVE");
        revision.setSourceConnectionId(connection.getId());
        revision.setEntityScopeJson(writeJson(canonicalEntityScope()));
        revision.setMappingConfigJson(writeJson(canonicalMappingConfig()));
        revision.setExecutionConfigJson(writeJson(canonicalExecutionConfig()));
        revision.setUpdatedAt(now);
        vectorizationPlanRevisionRepository.save(revision);

        plan.setActiveRevisionId(revision.getId());
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);
    }

    private JsonNode canonicalVectorizationConnectionConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("baseUrl", ECOMMERCE_UPSTREAM_BASE_URL);
        ObjectNode datasets = root.putObject("datasets");
        datasets.set("product", canonicalDatasetConfig("/api/products?limit=500"));
        datasets.set("review", canonicalDatasetConfig("/api/reviews?limit=500"));
        datasets.set("policy", canonicalDatasetConfig("/api/policies?limit=500"));
        return root;
    }

    private JsonNode canonicalDiscoverySummary() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode counts = root.putObject("countsByEntityType");
        counts.put("policy", 20);
        counts.put("product", 100);
        counts.put("review", 200);
        ObjectNode methods = root.putObject("countMethodByEntityType");
        methods.put("policy", "EXACT");
        methods.put("product", "EXACT");
        methods.put("review", "EXACT");
        return root;
    }

    private JsonNode canonicalEntityScope() {
        return objectMapper.createArrayNode()
            .add("policy")
            .add("product")
            .add("review");
    }

    private JsonNode canonicalMappingConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode entityMappings = root.putObject("entityMappings");

        ObjectNode product = entityMappings.putObject("product");
        product.put("dataset", "product");
        product.put("recordIdField", "sku");
        product.put("recordVersionField", "updatedAt");
        ObjectNode productFields = product.putObject("entityFieldMappings");
        productFields.put("name", "name");
        productFields.put("description", "description");
        productFields.put("category", "category");
        productFields.put("tags", "tags");
        productFields.put("sku", "sku");
        productFields.put("price", "price");
        productFields.put("currency", "currency");
        productFields.put("inStockQty", "inStockQty");
        ObjectNode productMetadata = product.putObject("metadataFieldMappings");
        productMetadata.put("sku", "sku");
        productMetadata.put("category", "category");
        productMetadata.put("price", "price");
        productMetadata.put("currency", "currency");
        productMetadata.put("inStockQty", "inStockQty");

        ObjectNode review = entityMappings.putObject("review");
        review.put("dataset", "review");
        review.put("recordIdField", "id");
        review.put("recordVersionField", "updatedAt");
        ObjectNode reviewFields = review.putObject("entityFieldMappings");
        reviewFields.put("text", "text");
        reviewFields.put("sku", "sku");
        reviewFields.put("rating", "rating");
        ObjectNode reviewMetadata = review.putObject("metadataFieldMappings");
        reviewMetadata.put("sku", "sku");
        reviewMetadata.put("rating", "rating");

        ObjectNode policy = entityMappings.putObject("policy");
        policy.put("dataset", "policy");
        policy.put("recordIdField", "id");
        policy.put("recordVersionField", "updatedAt");
        ObjectNode policyFields = policy.putObject("entityFieldMappings");
        policyFields.put("title", "title");
        policyFields.put("text", "text");
        policyFields.put("classification", "classification");
        ObjectNode policyMetadata = policy.putObject("metadataFieldMappings");
        policyMetadata.put("title", "title");
        policyMetadata.put("classification", "classification");
        return root;
    }

    private JsonNode canonicalExecutionConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("batchSize", DEFAULT_BATCH_SIZE);
        root.put("pageSize", DEFAULT_PAGE_SIZE);
        return root;
    }

    private ObjectNode canonicalDatasetConfig(String path) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("path", path);
        node.put("paginationMode", "NONE");
        return node;
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize canonical vectorization seed.", ex);
        }
    }

    private ObjectNode ecommerceSecurityConfig(JsonNode source) {
        ObjectNode root = ensureObject(source);
        root.put("authzMode", "REMOTE_HTTP");
        root.put("adminApiKeyEnabled", true);
        root.put("connectorApiKeyEnabled", true);
        root.put("authzBaseUrl", ECOMMERCE_UPSTREAM_BASE_URL);
        return root;
    }

    private JsonNode readYaml(String configuredLocation) {
        try {
            Resource resource = resolveConfiguredResource(configuredLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return yamlMapper.readTree(inputStream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read rollout bootstrap config from " + configuredLocation + ": " + ex.getMessage(), ex);
        }
    }

    private Resource resolveConfiguredResource(String configuredPath) {
        if (configuredPath.startsWith("classpath:") || configuredPath.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(configuredPath);
            if (resource.exists()) {
                return resource;
            }
            throw new IllegalStateException("Rollout config resource not found: " + configuredPath);
        }

        Path raw = Path.of(configuredPath);
        if (raw.isAbsolute() && Files.exists(raw)) {
            return resourceLoader.getResource(raw.normalize().toUri().toString());
        }

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cwdResolved = cwd.resolve(configuredPath).normalize();
        if (Files.exists(cwdResolved)) {
            return resourceLoader.getResource(cwdResolved.toUri().toString());
        }

        Path repoRoot = findRepoRoot(cwd);
        if (repoRoot != null) {
            Path repoResolved = repoRoot.resolve(configuredPath).normalize();
            if (Files.exists(repoResolved)) {
                return resourceLoader.getResource(repoResolved.toUri().toString());
            }
        }

        throw new IllegalStateException("Rollout config resource not found: " + configuredPath);
    }

    private Path findRepoRoot(Path start) {
        Path current = start;
        while (current != null) {
            if (Files.isDirectory(current.resolve(".git"))
                || Files.isDirectory(current.resolve("Real_Apps"))
                || Files.isDirectory(current.resolve("Platfrom"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private String summarizeIssues(List<DraftValidationIssue> issues) {
        return issues.stream()
            .limit(5)
            .map(issue -> issue.code() + ": " + issue.message())
            .reduce((left, right) -> left + "; " + right)
            .orElse("Unknown validation issue.");
    }

    private ObjectNode ensureObject(JsonNode node) {
        return node != null && node.isObject()
            ? (ObjectNode) node.deepCopy()
            : objectMapper.createObjectNode();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasConcreteValue(String value) {
        return hasText(value) && !isPlaceholderExpression(value);
    }

    private boolean runnerRegistrationReady(DeploymentVectorizationVerificationSummary summary) {
        return summary.runner() != null
            && "ACTIVE".equalsIgnoreCase(summary.runner().registrationStatus())
            && (summary.runner().tokenExpiresAt() == null || !summary.runner().tokenExpiresAt().isBefore(Instant.now()));
    }

    private boolean runnerServiceProvisioned(DeploymentReleaseEntity latestRelease) {
        if (latestRelease == null || !hasText(latestRelease.getProvisioningDetailsJson())) {
            return false;
        }
        try {
            JsonNode runnerService = objectMapper.readTree(latestRelease.getProvisioningDetailsJson())
                .path("railway")
                .path("services")
                .path("vectorizationRunner");
            return runnerService.isObject()
                && (hasText(runnerService.path("serviceId").asText("")) || hasText(runnerService.path("serviceName").asText("")))
                && hasText(runnerService.path("deploymentStatus").asText(""));
        } catch (IOException ex) {
            return false;
        }
    }

    private boolean isPlaceholderExpression(String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private abstract static class VerificationRolloutDefinition {
        private final String key;
        private final String displayName;
        private final String description;
        private final String templateId;
        private final String vectorProvisioningMode;
        private final String verificationProfile;
        private final boolean writeVerificationSupported;

        private VerificationRolloutDefinition(String key,
                                             String displayName,
                                             String description,
                                             String templateId,
                                             String vectorProvisioningMode,
                                             String verificationProfile,
                                             boolean writeVerificationSupported) {
            this.key = key;
            this.displayName = displayName;
            this.description = description;
            this.templateId = templateId;
            this.vectorProvisioningMode = vectorProvisioningMode;
            this.verificationProfile = verificationProfile;
            this.writeVerificationSupported = writeVerificationSupported;
        }

        String key() {
            return key;
        }

        String displayName() {
            return displayName;
        }

        String description() {
            return description;
        }

        String templateId() {
            return templateId;
        }

        String vectorProvisioningMode() {
            return vectorProvisioningMode;
        }

        String verificationProfile() {
            return verificationProfile;
        }

        boolean writeVerificationSupported() {
            return writeVerificationSupported;
        }

        String deploymentName() {
            return displayName;
        }

        List<String> requiredSecrets() {
            return switch (key) {
                case "qdrant" -> List.of("QDRANT_CLOUD_MANAGEMENT_API_KEY");
                case "pinecone" -> List.of("PINECONE_API_KEY");
                case "milvus" -> List.of("ZILLIZ_CLOUD_API_KEY");
                case "weaviate" -> List.of("WEAVIATE_API_KEY");
                default -> List.of();
            };
        }

        abstract UpdateDeploymentDraftRequest updateDraft(DeploymentDraftResponse draft);
    }

    private record RolloutReadiness(boolean ready, String message) {
    }
}
