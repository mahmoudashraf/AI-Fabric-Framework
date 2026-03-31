package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformBootstrapProperties;
import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentVersionSummary;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationIssue;
import com.ai.fabric.platform.backend.deployment.model.DraftValidationResponse;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
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
import java.util.List;

@Service
public class EcommerceDemoBootstrapService {

    private static final String DEFAULT_DEPLOYMENT_NAME = "Ecommerce Demo Restored";
    private static final String DEFAULT_ENVIRONMENT = "dev";
    private static final String DEFAULT_TEMPLATE_ID = "dev-openai-lucene";
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

    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final RailwayPreflightService railwayPreflightService;
    private final PlatformBootstrapProperties bootstrapProperties;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;
    private final ResourceLoader resourceLoader;

    public EcommerceDemoBootstrapService(DeploymentRepository deploymentRepository,
                                         DeploymentService deploymentService,
                                         RailwayPreflightService railwayPreflightService,
                                         PlatformBootstrapProperties bootstrapProperties,
                                         ObjectMapper objectMapper,
                                         ResourceLoader resourceLoader) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentService = deploymentService;
        this.railwayPreflightService = railwayPreflightService;
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
                DEFAULT_TEMPLATE_ID
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
