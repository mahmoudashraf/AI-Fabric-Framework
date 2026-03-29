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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Service
public class EcommerceDemoBootstrapService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentService deploymentService;
    private final RailwayPreflightService railwayPreflightService;
    private final PlatformBootstrapProperties bootstrapProperties;
    private final ObjectMapper objectMapper;
    private final ObjectMapper yamlMapper;

    public EcommerceDemoBootstrapService(DeploymentRepository deploymentRepository,
                                         DeploymentService deploymentService,
                                         RailwayPreflightService railwayPreflightService,
                                         PlatformBootstrapProperties bootstrapProperties,
                                         ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentService = deploymentService;
        this.railwayPreflightService = railwayPreflightService;
        this.bootstrapProperties = bootstrapProperties;
        this.objectMapper = objectMapper;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }

    public void ensureBootstrapDeployment() {
        PlatformBootstrapProperties.EcommerceDemoProperties properties = bootstrapProperties.ecommerceDemo();
        if (!properties.enabled()) {
            return;
        }

        boolean exists = deploymentRepository
            .findByNameIgnoreCaseAndEnvironmentNameIgnoreCaseAndArchivedAtIsNull(
                properties.name(),
                properties.environment()
            )
            .isPresent();
        if (exists) {
            return;
        }

        JsonNode actionsConfig = readYaml(resolveConfiguredPath(properties.actionsFile()), "actions");
        JsonNode entityConfig = normalizeEntityConfig(
            readYaml(resolveConfiguredPath(properties.entitiesFile()), "entities"),
            properties.vectorDimensions()
        );
        JsonNode routingConfig = normalizeRoutingConfig(
            readYaml(resolveConfiguredPath(properties.routingFile()), "routing"),
            properties
        );

        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest(
                properties.name(),
                properties.environment(),
                properties.templateId()
            )
        );
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deployment.id());
        JsonNode securityConfig = normalizeSecurityConfig(draft.securityConfig(), properties);

        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                actionsConfig,
                entityConfig,
                routingConfig,
                draft.providerConfig(),
                securityConfig
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

    private JsonNode readYaml(Path path, String label) {
        try {
            return yamlMapper.readTree(Files.readString(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read ecommerce demo " + label + " config from " + path + ": " + ex.getMessage(), ex);
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

    private JsonNode normalizeRoutingConfig(JsonNode source,
                                            PlatformBootstrapProperties.EcommerceDemoProperties properties) {
        ObjectNode root = source != null && source.isObject()
            ? source.deepCopy()
            : objectMapper.createObjectNode();

        ObjectNode connector = root.with("connector");
        ObjectNode inboundAuth = connector.with("inbound-auth");
        inboundAuth.put("allow-unauthenticated", properties.connectorAllowUnauthenticated());
        ObjectNode apiKey = inboundAuth.with("api-key");
        apiKey.put("enabled", properties.connectorApiKeyEnabled());
        apiKey.put("header", properties.connectorApiKeyHeader());
        apiKey.put("value", properties.connectorApiKeyValue());

        ObjectNode upstream = connector.with("upstream");
        upstream.put("base-url", properties.upstreamBaseUrl());
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
        authz.put("enabled", properties.authzEnabled());
        authz.put("path", properties.authzPath());
        ObjectNode authzUpstream = authz.with("upstream");
        authzUpstream.put("base-url", properties.authzUpstreamBaseUrl());
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

    private JsonNode normalizeSecurityConfig(JsonNode source,
                                             PlatformBootstrapProperties.EcommerceDemoProperties properties) {
        ObjectNode root = source != null && source.isObject()
            ? source.deepCopy()
            : objectMapper.createObjectNode();
        if (root.path("authzMode").asText("").isBlank()) {
            root.put("authzMode", "REMOTE_HTTP");
        }
        if (!root.path("adminApiKeyEnabled").isBoolean()) {
            root.put("adminApiKeyEnabled", true);
        }
        root.put("connectorApiKeyEnabled", properties.connectorApiKeyEnabled());
        if (properties.authzEnabled()) {
            root.put("authzBaseUrl", properties.authzUpstreamBaseUrl());
        }
        return root;
    }

    private Path resolveConfiguredPath(String configuredPath) {
        Path raw = Path.of(configuredPath);
        if (raw.isAbsolute() && Files.exists(raw)) {
            return raw.normalize();
        }

        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path cwdResolved = cwd.resolve(configuredPath).normalize();
        if (Files.exists(cwdResolved)) {
            return cwdResolved;
        }

        Path repoRoot = findRepoRoot(cwd);
        if (repoRoot != null) {
            Path repoResolved = repoRoot.resolve(configuredPath).normalize();
            if (Files.exists(repoResolved)) {
                return repoResolved;
            }
        }

        throw new IllegalStateException("Bootstrap config file not found: " + configuredPath);
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
