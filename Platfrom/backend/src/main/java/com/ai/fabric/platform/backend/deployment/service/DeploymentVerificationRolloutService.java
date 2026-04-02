package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutItemSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVerificationRolloutSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
    private static final String QDRANT_PROVIDER = "aws";
    private static final String QDRANT_REGION = "eu-west-1";
    private static final String WEAVIATE_HOST = "l8iep2jcrdodutnyepfvla.c0.europe-west3.gcp.weaviate.cloud";
    private static final String ZILLIZ_PROJECT_ID = "proj-a58a34b87ccfe2c80d6ec2";
    private static final String ZILLIZ_REGION_ID = "aws-eu-central-1";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentReleaseRepository releaseRepository;
    private final DeploymentService deploymentService;
    private final PlatformSecretService platformSecretService;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final ResourceLoader resourceLoader;

    public DeploymentVerificationRolloutService(DeploymentRepository deploymentRepository,
                                                DeploymentReleaseRepository releaseRepository,
                                                DeploymentService deploymentService,
                                                PlatformSecretService platformSecretService,
                                                ObjectMapper objectMapper,
                                                ResourceLoader resourceLoader) {
        this.deploymentRepository = deploymentRepository;
        this.releaseRepository = releaseRepository;
        this.deploymentService = deploymentService;
        this.platformSecretService = platformSecretService;
        this.objectMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceLoader = resourceLoader;
    }

    public DeploymentVerificationRolloutSummary listRollouts() {
        List<DeploymentEntity> deployments = deploymentRepository.findAllByOrderByCreatedAtDesc();
        List<DeploymentVerificationRolloutItemSummary> items = definitions().stream()
            .map(definition -> toSummary(definition, resolveExisting(deployments, definition)))
            .toList();
        long ready = items.stream().filter(DeploymentVerificationRolloutItemSummary::verificationReady).count();
        return new DeploymentVerificationRolloutSummary(
            ready + " of " + items.size() + " canonical verification deployments are ready to verify.",
            items
        );
    }

    public DeploymentVerificationRolloutSummary recreateRollouts() {
        for (VerificationRolloutDefinition definition : definitions()) {
            ensureDeployment(definition);
        }
        return listRollouts();
    }

    public boolean isCanonicalRolloutDeployment(String deploymentId) {
        if (deploymentId == null || deploymentId.isBlank()) {
            return false;
        }
        return listRollouts().items().stream()
            .anyMatch(item -> deploymentId.equals(item.deploymentId()) && !item.archived());
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

        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        UpdateDeploymentDraftRequest request = definition.updateDraft(draft);
        deploymentService.updateDraft(draft.id(), request);

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

    private DeploymentVerificationRolloutItemSummary toSummary(VerificationRolloutDefinition definition,
                                                               DeploymentEntity deployment) {
        List<String> missingPrerequisites = missingPrerequisites(definition);
        DeploymentReleaseEntity latestRelease = deployment == null
            ? null
            : releaseRepository.findTopByDeploymentIdOrderByCreatedAtDesc(deployment.getId()).orElse(null);
        boolean verificationReady = deployment != null
            && deployment.getArchivedAt() == null
            && hasText(deployment.getRuntimeBaseUrl())
            && hasText(deployment.getConnectorBaseUrl())
            && hasText(deployment.getActiveVersionId())
            && missingPrerequisites.isEmpty();

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
            verificationReady,
            deployment == null ? "MISSING" : deployment.getStatus(),
            deployment == null ? null : deployment.getActiveVersionId(),
            latestRelease == null ? null : latestRelease.getStatus(),
            latestRelease == null ? null : latestRelease.getProvisioningStatus(),
            latestRelease == null ? null : latestRelease.getVerificationStatus(),
            deployment == null ? null : deployment.getRuntimeBaseUrl(),
            deployment == null ? null : deployment.getConnectorBaseUrl(),
            missingPrerequisites
        );
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
                "vector",
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
                "vector",
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
                "vector",
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
                "vector",
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
            verificationVectorEntityConfig(draft.entityConfig()),
            ecommerceRoutingConfig(),
            providerConfig,
            ecommerceSecurityConfig(draft.securityConfig()),
            ensureObject(draft.promptConfig())
        );
    }

    private ObjectNode ecommerceEntityConfig() {
        ObjectNode root = ensureObject(readYaml(ECOMMERCE_ENTITIES_RESOURCE));
        ObjectNode aiConfig = root.with("ai-config");
        if (aiConfig.path("vector-dimensions").asInt(0) <= 0) {
            aiConfig.put("vector-dimensions", ECOMMERCE_VECTOR_DIMENSIONS);
        }
        root.with("ai-entities");
        return root;
    }

    private ObjectNode verificationVectorEntityConfig(JsonNode existingEntityConfig) {
        ObjectNode root = ensureObject(existingEntityConfig);
        ObjectNode aiConfig = root.with("ai-config");
        if (aiConfig.path("vector-dimensions").asInt(0) <= 0) {
            aiConfig.put("vector-dimensions", 1536);
        }

        ObjectNode aiEntities = objectMapper.createObjectNode();
        ObjectNode product = aiEntities.putObject("product");
        ArrayNode features = product.putArray("features");
        features.add("embedding");
        features.add("search");
        product.put("auto-process", false);
        product.put("enable-search", true);
        product.put("auto-embedding", true);
        product.put("indexable", true);

        ArrayNode searchableFields = product.putArray("searchable-fields");
        searchableFields.addObject().put("name", "name").put("weight", 2.0);
        searchableFields.addObject().put("name", "description").put("weight", 1.8);
        searchableFields.addObject().put("name", "category").put("weight", 1.0);
        searchableFields.addObject().put("name", "sku").put("weight", 0.6);

        ArrayNode metadataFields = product.putArray("metadata-fields");
        metadataFields.addObject().put("name", "sku").put("type", "string");
        metadataFields.addObject().put("name", "category").put("type", "string");
        metadataFields.addObject().put("name", "price").put("type", "number");

        root.set("ai-entities", aiEntities);
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
        if (!hasText(upstreamAuth.path("type").asText(""))) {
            upstreamAuth.put("type", "NONE");
        }
        if (!hasText(upstreamAuth.path("header").asText(""))) {
            upstreamAuth.put("header", "Authorization");
        }
        if (upstreamAuth.path("value").isMissingNode()) {
            upstreamAuth.put("value", "");
        }

        ObjectNode authz = root.with("authz");
        authz.put("enabled", true);
        authz.put("path", "/api/authz/check");
        ObjectNode authzUpstream = authz.with("upstream");
        authzUpstream.put("base-url", ECOMMERCE_UPSTREAM_BASE_URL);
        ObjectNode authzUpstreamAuth = authzUpstream.with("auth");
        if (!hasText(authzUpstreamAuth.path("type").asText(""))) {
            authzUpstreamAuth.put("type", "NONE");
        }
        if (!hasText(authzUpstreamAuth.path("header").asText(""))) {
            authzUpstreamAuth.put("header", "Authorization");
        }
        if (authzUpstreamAuth.path("value").isMissingNode()) {
            authzUpstreamAuth.put("value", "");
        }

        root.with("actions");
        return root;
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
}
