package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentOverviewSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationPlanRevisionEntity;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationSourceConnectionEntity;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationPlanRevisionRepository;
import com.ai.fabric.platform.backend.vectorization.repository.VectorizationSourceConnectionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Service;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class EcommerceDemoBootstrapService {

    private static final String DEFAULT_DEPLOYMENT_NAME = "Ecommerce Demo Restored";
    private static final String DEFAULT_ENVIRONMENT = "dev";
    private static final String DEFAULT_TEMPLATE_ID = "dev-openai-lucene";
    private static final String DEFAULT_CURATED_MODULE_ID = "commerce";
    private static final String DEFAULT_ACTIONS_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/runtime/config/ai-actions.yml";
    private static final String DEFAULT_ENTITIES_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/runtime/config/ai-entity-config.yml";
    private static final String DEFAULT_ROUTING_RESOURCE =
        "classpath:bootstrap/ecommerce-demo/rest-connector/actions-routing.yml";
    private static final int DEFAULT_VECTOR_DIMENSIONS = 512;
    private static final String DEFAULT_UPSTREAM_BASE_URL = "https://ai-fabric-framework-production-a247.up.railway.app";
    private static final String DEFAULT_AUTHZ_PATH = "/api/authz/check";
    private static final boolean DEFAULT_AUTHZ_ENABLED = true;
    private static final boolean DEFAULT_CONNECTOR_ALLOW_UNAUTHENTICATED = false;
    private static final boolean DEFAULT_CONNECTOR_API_KEY_ENABLED = true;
    private static final String DEFAULT_CONNECTOR_API_KEY_HEADER = "X-AIFABRIC-API-KEY";
    private static final String DEFAULT_CONNECTOR_API_KEY_VALUE = "${CONNECTOR_API_KEY}";
    private static final String DEFAULT_PUBLIC_RUNTIME_TOKEN_ISSUER = "ecommerce-demo";
    private static final String DEFAULT_PUBLIC_RUNTIME_ACCEPTED_ISSUERS =
        DEFAULT_PUBLIC_RUNTIME_TOKEN_ISSUER + ",runtime-public-bootstrap";
    private static final String DEFAULT_PUBLIC_RUNTIME_ACCEPTED_AUDIENCES = "ecommerce-demo-chat";
    private static final String DEFAULT_PUBLIC_RUNTIME_DEFAULT_AUDIENCE = "ecommerce-demo-chat";
    private static final int DEFAULT_PAGE_SIZE = 500;
    private static final int DEFAULT_BATCH_SIZE = 25;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final RailwayPreflightService railwayPreflightService;
    private final VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository;
    private final VectorizationPlanRepository vectorizationPlanRepository;
    private final VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository;
    private final PlatformBootstrapProperties bootstrapProperties;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final ResourceLoader resourceLoader;

    public EcommerceDemoBootstrapService(DeploymentRepository deploymentRepository,
                                         DeploymentService deploymentService,
                                         RailwayPreflightService railwayPreflightService,
                                         VectorizationSourceConnectionRepository vectorizationSourceConnectionRepository,
                                         VectorizationPlanRepository vectorizationPlanRepository,
                                         VectorizationPlanRevisionRepository vectorizationPlanRevisionRepository,
                                         PlatformBootstrapProperties bootstrapProperties,
                                         ObjectMapper objectMapper,
                                         ResourceLoader resourceLoader) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentService = deploymentService;
        this.railwayPreflightService = railwayPreflightService;
        this.vectorizationSourceConnectionRepository = vectorizationSourceConnectionRepository;
        this.vectorizationPlanRepository = vectorizationPlanRepository;
        this.vectorizationPlanRevisionRepository = vectorizationPlanRevisionRepository;
        this.bootstrapProperties = bootstrapProperties;
        this.objectMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.resourceLoader = resourceLoader;
    }

    public void ensureBootstrapDeployment() {
        PlatformBootstrapProperties.EcommerceDemoProperties properties = bootstrapProperties.ecommerceDemo();
        if (!properties.enabled()) {
            return;
        }

        boolean exists = deploymentRepository
            .findByNameIgnoreCaseAndEnvironmentNameIgnoreCaseAndArchivedAtIsNull(
                DEFAULT_DEPLOYMENT_NAME,
                DEFAULT_ENVIRONMENT
            )
            .isPresent();
        if (exists) {
            return;
        }

        JsonNode actionsConfig = readYaml(DEFAULT_ACTIONS_RESOURCE, "actions");
        JsonNode entityConfig = normalizeEntityConfig(
            readYaml(DEFAULT_ENTITIES_RESOURCE, "entities"),
            DEFAULT_VECTOR_DIMENSIONS
        );
        JsonNode routingConfig = normalizeRoutingConfig(
            readYaml(DEFAULT_ROUTING_RESOURCE, "routing")
        );

        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest(
                DEFAULT_DEPLOYMENT_NAME,
                DEFAULT_ENVIRONMENT,
                DEFAULT_TEMPLATE_ID,
                DEFAULT_CURATED_MODULE_ID
            )
        );
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode securityConfig = normalizeSecurityConfig(draft.securityConfig());

        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                actionsConfig,
                entityConfig,
                routingConfig,
                draft.providerConfig(),
                securityConfig,
                draft.promptConfig()
            )
        );
        seedBootstrapVectorization(deployment.id());

        DraftValidationResponse validation = deploymentService.validateDraft(draft.id());
        if (!validation.publishReady()) {
            throw new IllegalStateException("Ecommerce demo bootstrap validation failed: " + summarizeIssues(validation.issues()));
        }

        DeploymentVersionSummary version = deploymentService.publishDraft(draft.id());
        if (properties.autoApply()) {
            RailwayPreflightSummary preflight = railwayPreflightService.run();
            ensureApplyReady(preflight);
            deploymentService.applyVersion(deployment.id(), version.id());
        }
    }

    public DeploymentOverviewSummary rolloutBootstrapDeployment() {
        DeploymentEntityRef deployment = resolveOrCreateBootstrapDeployment();
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode securityConfig = normalizeSecurityConfig(draft.securityConfig());

        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                readYaml(DEFAULT_ACTIONS_RESOURCE, "actions"),
                normalizeEntityConfig(readYaml(DEFAULT_ENTITIES_RESOURCE, "entities"), DEFAULT_VECTOR_DIMENSIONS),
                normalizeRoutingConfig(readYaml(DEFAULT_ROUTING_RESOURCE, "routing")),
                draft.providerConfig(),
                securityConfig,
                draft.promptConfig()
            )
        );
        seedBootstrapVectorization(deployment.id());

        DraftValidationResponse validation = deploymentService.validateDraft(draft.id());
        if (!validation.publishReady()) {
            throw new IllegalStateException("Ecommerce demo rollout validation failed: " + summarizeIssues(validation.issues()));
        }

        DeploymentVersionSummary version = deploymentService.publishDraft(draft.id());
        RailwayPreflightSummary preflight = railwayPreflightService.run();
        ensureApplyReady(preflight);
        deploymentService.applyVersion(deployment.id(), version.id());
        return deploymentService.getDeploymentOverview(deployment.id());
    }

    private DeploymentEntityRef resolveOrCreateBootstrapDeployment() {
        Optional<com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity> existing = deploymentRepository.findAllByOrderByCreatedAtDesc().stream()
            .filter(deployment -> DEFAULT_DEPLOYMENT_NAME.equalsIgnoreCase(deployment.getName()))
            .filter(deployment -> DEFAULT_ENVIRONMENT.equalsIgnoreCase(deployment.getEnvironmentName()))
            .findFirst();
        if (existing.isPresent()) {
            com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity deployment = existing.get();
            if (deployment.getArchivedAt() != null) {
                deploymentService.restoreDeployment(deployment.getId());
            }
            return new DeploymentEntityRef(deployment.getId());
        }
        DeploymentSummary created = deploymentService.createDeployment(
            new CreateDeploymentRequest(
                DEFAULT_DEPLOYMENT_NAME,
                DEFAULT_ENVIRONMENT,
                DEFAULT_TEMPLATE_ID,
                DEFAULT_CURATED_MODULE_ID
            )
        );
        return new DeploymentEntityRef(created.id());
    }

    private record DeploymentEntityRef(String id) {
    }

    private void seedBootstrapVectorization(String deploymentId) {
        com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new IllegalStateException("Deployment not found for ecommerce demo vectorization seed: " + deploymentId));
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
        connection.setConnectionConfigJson(writeJson(bootstrapVectorizationConnectionConfig()));
        connection.setSecretReferencesJson(writeJson(objectMapper.createObjectNode()));
        connection.setDiscoverySummaryJson(writeJson(bootstrapDiscoverySummary()));
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
        plan.setSyncReasonCodesJson(writeJson(objectMapper.createArrayNode().add("PLAN_CREATED").add("ECOMMERCE_DEMO_ROLLOUT_SEEDED")));
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
        revision.setEntityScopeJson(writeJson(bootstrapEntityScope()));
        revision.setMappingConfigJson(writeJson(bootstrapMappingConfig()));
        revision.setExecutionConfigJson(writeJson(bootstrapExecutionConfig()));
        revision.setUpdatedAt(now);
        vectorizationPlanRevisionRepository.save(revision);

        plan.setActiveRevisionId(revision.getId());
        plan.setUpdatedAt(now);
        vectorizationPlanRepository.save(plan);
    }

    private JsonNode bootstrapVectorizationConnectionConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("baseUrl", DEFAULT_UPSTREAM_BASE_URL);
        ObjectNode datasets = root.putObject("datasets");
        datasets.set("product", bootstrapDatasetConfig("/api/products?limit=500"));
        datasets.set("review", bootstrapDatasetConfig("/api/reviews?limit=500"));
        datasets.set("policy", bootstrapDatasetConfig("/api/policies?limit=500"));
        return root;
    }

    private JsonNode bootstrapDiscoverySummary() {
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

    private JsonNode bootstrapEntityScope() {
        return objectMapper.createArrayNode()
            .add("policy")
            .add("product")
            .add("review");
    }

    private JsonNode bootstrapMappingConfig() {
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

    private JsonNode bootstrapExecutionConfig() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("batchSize", DEFAULT_BATCH_SIZE);
        root.put("pageSize", DEFAULT_PAGE_SIZE);
        return root;
    }

    private ObjectNode bootstrapDatasetConfig(String path) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("path", path);
        node.put("paginationMode", "NONE");
        return node;
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to serialize ecommerce demo vectorization seed.", ex);
        }
    }

    private String generateId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private JsonNode readYaml(String configuredLocation, String label) {
        try {
            Resource resource = resolveConfiguredResource(configuredLocation);
            try (InputStream inputStream = resource.getInputStream()) {
                return yamlMapper.readTree(inputStream);
            }
        } catch (IOException ex) {
            throw new IllegalStateException(
                "Failed to read ecommerce demo " + label + " config from " + configuredLocation + ": " + ex.getMessage(),
                ex
            );
        }
    }

    private JsonNode normalizeEntityConfig(JsonNode source, int vectorDimensions) {
        ObjectNode root = source != null && source.isObject()
            ? source.deepCopy()
            : objectMapper.createObjectNode();
        ObjectNode aiConfig = root.with("ai-config");
        if (aiConfig.path("vector-dimensions").asInt(0) <= 0) {
            aiConfig.put("vector-dimensions", vectorDimensions);
        }
        root.with("ai-entities");
        return root;
    }

    private JsonNode normalizeRoutingConfig(JsonNode source) {
        ObjectNode root = source != null && source.isObject()
            ? source.deepCopy()
            : objectMapper.createObjectNode();

        ObjectNode connector = root.with("connector");
        ObjectNode inboundAuth = connector.with("inbound-auth");
        inboundAuth.put("allow-unauthenticated", DEFAULT_CONNECTOR_ALLOW_UNAUTHENTICATED);
        ObjectNode apiKey = inboundAuth.with("api-key");
        apiKey.put("enabled", DEFAULT_CONNECTOR_API_KEY_ENABLED);
        apiKey.put("header", DEFAULT_CONNECTOR_API_KEY_HEADER);
        apiKey.put("value", DEFAULT_CONNECTOR_API_KEY_VALUE);

        ObjectNode upstream = connector.with("upstream");
        upstream.put("base-url", DEFAULT_UPSTREAM_BASE_URL);
        ObjectNode upstreamAuth = upstream.with("auth");
        if (upstreamAuth.path("type").asText("").isBlank()) {
            upstreamAuth.put("type", "NONE");
        }
        if (upstreamAuth.path("header").asText("").isBlank()) {
            upstreamAuth.put("header", "Authorization");
        }
        if (upstreamAuth.path("value").isMissingNode()) {
            upstreamAuth.put("value", "");
        }

        ObjectNode authz = root.with("authz");
        authz.put("enabled", DEFAULT_AUTHZ_ENABLED);
        authz.put("path", DEFAULT_AUTHZ_PATH);
        ObjectNode authzUpstream = authz.with("upstream");
        authzUpstream.put("base-url", DEFAULT_UPSTREAM_BASE_URL);
        ObjectNode authzUpstreamAuth = authzUpstream.with("auth");
        if (authzUpstreamAuth.path("type").asText("").isBlank()) {
            authzUpstreamAuth.put("type", "NONE");
        }
        if (authzUpstreamAuth.path("header").asText("").isBlank()) {
            authzUpstreamAuth.put("header", "Authorization");
        }
        if (authzUpstreamAuth.path("value").isMissingNode()) {
            authzUpstreamAuth.put("value", "");
        }

        root.with("actions");
        return root;
    }

    private JsonNode normalizeSecurityConfig(JsonNode source) {
        ObjectNode root = source != null && source.isObject()
            ? source.deepCopy()
            : objectMapper.createObjectNode();
        if (root.path("authzMode").asText("").isBlank()) {
            root.put("authzMode", "REMOTE_HTTP");
        }
        if (!root.path("adminApiKeyEnabled").isBoolean()) {
            root.put("adminApiKeyEnabled", true);
        }
        root.put("connectorApiKeyEnabled", DEFAULT_CONNECTOR_API_KEY_ENABLED);
        if (DEFAULT_AUTHZ_ENABLED) {
            root.put("authzBaseUrl", DEFAULT_UPSTREAM_BASE_URL);
        }
        root.put("publicRuntimeBootstrapEnabled", true);
        root.put("publicRuntimeTokenIssuer", DEFAULT_PUBLIC_RUNTIME_TOKEN_ISSUER);
        root.put("publicRuntimeAcceptedIssuers", DEFAULT_PUBLIC_RUNTIME_ACCEPTED_ISSUERS);
        root.put("publicRuntimeAcceptedAudiences", DEFAULT_PUBLIC_RUNTIME_ACCEPTED_AUDIENCES);
        root.put("publicRuntimeDefaultAudience", DEFAULT_PUBLIC_RUNTIME_DEFAULT_AUDIENCE);
        return root;
    }

    private Resource resolveConfiguredResource(String configuredPath) {
        if (configuredPath.startsWith("classpath:") || configuredPath.startsWith("file:")) {
            Resource resource = resourceLoader.getResource(configuredPath);
            if (resource.exists()) {
                return resource;
            }
            throw new IllegalStateException("Bootstrap config resource not found: " + configuredPath);
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

        throw new IllegalStateException("Bootstrap config resource not found: " + configuredPath);
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
            .filter(issue -> "ERROR".equals(issue.severity()))
            .map(issue -> issue.code() + " (" + issue.path() + "): " + issue.message())
            .limit(3)
            .reduce((left, right) -> left + " | " + right)
            .orElse("Unknown validation failure.");
    }

    private void ensureApplyReady(RailwayPreflightSummary preflight) {
        List<RailwayPreflightCheckSummary> failedChecks = preflight.checks().stream()
            .filter(check -> "FAILED".equals(check.status()))
            .toList();
        if (!failedChecks.isEmpty()) {
            String failureMessage = failedChecks.stream()
                .map(check -> check.key() + ": " + check.message())
                .reduce((left, right) -> left + " | " + right)
                .orElse("Unknown preflight failure.");
            throw new IllegalStateException("Ecommerce demo bootstrap auto-apply blocked: " + failureMessage);
        }

        boolean artifactProbePassed = preflight.checks().stream()
            .anyMatch(check -> "public_base_url_probe".equals(check.key()) && "PASSED".equals(check.status()));
        if (!artifactProbePassed) {
            throw new IllegalStateException(
                "Ecommerce demo bootstrap auto-apply blocked: PLATFORM_PUBLIC_BASE_URL is not proven reachable by Railway."
            );
        }
    }
}
