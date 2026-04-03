package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentPocImportRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocDatasetSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocImportRunSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocMigrationCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocMigrationGuideSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocMigrationSourceSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocResetCapabilities;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeIndexingSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeResetResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocWorkspaceSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentPocImportRunRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentDraftRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentPocWorkspaceService {

    private final DeploymentRepository deploymentRepository;
    private final DeploymentDraftRepository deploymentDraftRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentPocImportRunRepository deploymentPocImportRunRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final PlatformSecretService platformSecretService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeploymentPocWorkspaceService(DeploymentRepository deploymentRepository,
                                         DeploymentDraftRepository deploymentDraftRepository,
                                         DeploymentVersionRepository deploymentVersionRepository,
                                         DeploymentPocImportRunRepository deploymentPocImportRunRepository,
                                         DeploymentAccessService deploymentAccessService,
                                         PlatformSecretService platformSecretService,
                                         PlatformAuditService platformAuditService,
                                         ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentDraftRepository = deploymentDraftRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentPocImportRunRepository = deploymentPocImportRunRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.platformSecretService = platformSecretService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public DeploymentPocWorkspaceSummary getWorkspace(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        ConfigSnapshot snapshot = loadConfigSnapshot(deployment);
        List<String> warnings = new ArrayList<>(snapshot.warnings());
        DeploymentPocRuntimeIndexingSummary indexing = fetchRuntimeIndexingSummary(deployment, warnings);
        boolean runtimeAdminConfigured = hasText(platformSecretService.resolveSecret("APP_ADMIN_API_KEY"));
        DeploymentPocMigrationGuideSummary migration = buildMigrationGuide(deployment, snapshot);
        return new DeploymentPocWorkspaceSummary(
            buildDatasetSummary(snapshot),
            indexing,
            migration,
            new DeploymentPocResetCapabilities(
                runtimeAdminConfigured && hasText(deployment.getRuntimeBaseUrl()),
                hasText(deployment.getRuntimeBaseUrl())
            ),
            recentImports(deployment.getId()),
            List.copyOf(warnings)
        );
    }

    public DeploymentPocRuntimeResetResponse clearRuntimeVectors(String deploymentId,
                                                                 DeploymentPocRuntimeResetRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new ResponseStatusException(BAD_REQUEST, "confirm=true is required.");
        }

        DeploymentEntity deployment = getDeployment(deploymentId);
        if (!hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment runtime URL is not available. Apply the deployment before clearing vectors."
            );
        }

        String adminApiKey = platformSecretService.resolveSecret("APP_ADMIN_API_KEY");
        if (!hasText(adminApiKey)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "APP_ADMIN_API_KEY is not configured for platform-managed runtime administration."
            );
        }

        String reason = hasText(request.reason()) ? request.reason().trim() : "platform-poc-reset";
        JsonNode response = sendRuntimeJson(
            deployment.getRuntimeBaseUrl(),
            "POST",
            "/api/admin/migration/clear?confirm=true",
            adminApiKey.trim(),
            body -> {
                body.put("confirm", true);
                body.put("clearVectors", true);
                body.put("reason", reason);
            }
        );

        long removedVectors = response.path("result").path("removedVectors").asLong(0L);
        boolean clearedVectors = response.path("result").path("clearedVectors").asBoolean(false);
        String message = textOrNull(response, "message");
        platformAuditService.record(
            "DEPLOYMENT_POC_RUNTIME_VECTORS_CLEARED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "removedVectors", removedVectors,
                "reason", reason
            )
        );
        return new DeploymentPocRuntimeResetResponse(
            response.path("success").asBoolean(clearedVectors),
            clearedVectors,
            removedVectors,
            message,
            List.of()
        );
    }

    private DeploymentPocDatasetSummary buildDatasetSummary(ConfigSnapshot snapshot) {
        String profileId = "generic-poc";
        String profileLabel = "Deployment dataset profile";
        String profileDescription = "Current entity and upstream profile derived from the selected deployment configuration.";
        List<String> normalizedEntityTypes = snapshot.entityTypes().stream()
            .map(value -> value.toLowerCase(Locale.ROOT))
            .toList();
        if (normalizedEntityTypes.containsAll(List.of("product", "review", "policy"))) {
            profileId = "ecommerce-demo";
            profileLabel = "Ecommerce demo dataset";
            profileDescription = "Platform-packaged commerce proof-of-concept profile with products, reviews, and policy knowledge.";
        } else if (snapshot.entityTypes().isEmpty()) {
            profileId = "unconfigured";
            profileLabel = "Dataset not configured yet";
            profileDescription = "No entity types are configured yet for this deployment.";
        }

        return new DeploymentPocDatasetSummary(
            snapshot.configSource(),
            profileId,
            profileLabel,
            profileDescription,
            snapshot.upstreamBaseUrl(),
            snapshot.entityTypes()
        );
    }

    private DeploymentPocMigrationGuideSummary buildMigrationGuide(DeploymentEntity deployment, ConfigSnapshot snapshot) {
        boolean connectorUrlReady = hasText(deployment.getConnectorBaseUrl());
        boolean connectorApiKeyReady = hasText(platformSecretService.resolveSecret("CONNECTOR_API_KEY"));
        boolean runtimeUrlReady = hasText(deployment.getRuntimeBaseUrl());
        boolean runtimeAdminReady = hasText(platformSecretService.resolveSecret("APP_ADMIN_API_KEY"));

        List<String> supportedVectorSpaces = snapshot.entityTypes().isEmpty()
            ? List.of("default")
            : snapshot.entityTypes().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        String defaultVectorSpace = supportedVectorSpaces.get(0);

        List<DeploymentPocMigrationSourceSummary> supportedSources = List.of(
            new DeploymentPocMigrationSourceSummary(
                "TEMPLATE_SAMPLE",
                "Template sample",
                "Load a small starter payload shaped for the selected deployment profile."
            ),
            new DeploymentPocMigrationSourceSummary(
                "JSON_FILE",
                "Upload JSON",
                "Upload a sanitized JSON file for a bounded proof-of-concept import run."
            ),
            new DeploymentPocMigrationSourceSummary(
                "JSON_PASTE",
                "Paste JSON",
                "Paste a small JSON array directly into the platform for rapid workshop iteration."
            )
        );

        List<DeploymentPocMigrationCheckSummary> readinessChecks = List.of(
            readinessCheck(
                "CONNECTOR_URL",
                "Connector endpoint",
                connectorUrlReady,
                "Connector URL is available for POC imports.",
                "Apply the deployment first so the connector URL exists."
            ),
            readinessCheck(
                "CONNECTOR_API_KEY",
                "Connector import key",
                connectorApiKeyReady,
                "CONNECTOR_API_KEY is configured for platform-managed imports.",
                "CONNECTOR_API_KEY must be configured in platform secrets before imports can run."
            ),
            warningCheck(
                "RUNTIME_URL",
                "Runtime endpoint",
                runtimeUrlReady,
                "Runtime URL is available for post-import validation and POC chat testing.",
                "Runtime URL is not available yet. Imports can still be prepared, but post-import validation will be limited."
            ),
            warningCheck(
                "RUNTIME_ADMIN",
                "Runtime admin visibility",
                runtimeUrlReady && runtimeAdminReady,
                "APP_ADMIN_API_KEY is configured for indexing visibility and reset controls.",
                "APP_ADMIN_API_KEY is missing or runtime is not applied yet, so indexing verification will stay limited."
            )
        );

        List<String> warnings = new ArrayList<>();
        if (!connectorUrlReady || !connectorApiKeyReady) {
            warnings.add("Imports stay blocked until the connector URL and CONNECTOR_API_KEY are both available.");
        }
        if (snapshot.entityTypes().isEmpty()) {
            warnings.add("No entity types are configured yet. The wizard falls back to a generic default vector space.");
        }
        if (!runtimeUrlReady) {
            warnings.add("Runtime validation is not available yet, so use import runs as preparation only until the deployment is applied.");
        }

        return new DeploymentPocMigrationGuideSummary(
            "Operator POC import",
            DeploymentPocImportService.MAX_RECORDS_PER_RUN,
            DeploymentPocImportService.MAX_CONTENT_LENGTH,
            defaultVectorSpace,
            supportedVectorSpaces,
            supportedSources,
            readinessChecks,
            List.copyOf(warnings)
        );
    }

    private List<DeploymentPocImportRunSummary> recentImports(String deploymentId) {
        return deploymentPocImportRunRepository.findTop10ByDeploymentIdOrderByCreatedAtDesc(deploymentId).stream()
            .map(this::toImportRunSummary)
            .toList();
    }

    private DeploymentPocImportRunSummary toImportRunSummary(DeploymentPocImportRunEntity entity) {
        return new DeploymentPocImportRunSummary(
            entity.getId(),
            entity.getDeploymentId(),
            entity.getDatasetLabel(),
            entity.getSourceType(),
            entity.getVectorSpace(),
            entity.getStatus(),
            entity.getRecordCount(),
            entity.getImportedCount(),
            entity.getFailedCount(),
            entity.getErrorMessage(),
            entity.getCreatedByActorId(),
            entity.getCreatedByDisplayName(),
            entity.getCreatedAt() == null ? null : entity.getCreatedAt().toString()
        );
    }

    private DeploymentPocRuntimeIndexingSummary fetchRuntimeIndexingSummary(DeploymentEntity deployment,
                                                                            List<String> warnings) {
        if (!hasText(deployment.getRuntimeBaseUrl())) {
            warnings.add("Runtime URL is not available yet. Apply the deployment to inspect indexing state.");
            return new DeploymentPocRuntimeIndexingSummary(false, null, Map.of(), 0L, false);
        }

        String adminApiKey = platformSecretService.resolveSecret("APP_ADMIN_API_KEY");
        if (!hasText(adminApiKey)) {
            warnings.add("APP_ADMIN_API_KEY is not configured, so runtime indexing cannot be inspected from the platform.");
            return new DeploymentPocRuntimeIndexingSummary(false, null, Map.of(), 0L, false);
        }

        try {
            JsonNode response = sendRuntimeJson(
                deployment.getRuntimeBaseUrl(),
                "GET",
                "/api/admin/indexing/overview",
                adminApiKey.trim(),
                null
            );
            return new DeploymentPocRuntimeIndexingSummary(
                response.path("success").asBoolean(true),
                textOrNull(response, "vectorDb"),
                readCounts(response.path("countsByEntityType")),
                response.path("totalVectors").asLong(0L),
                response.path("supportsVectorScan").asBoolean(false)
            );
        } catch (ResponseStatusException ex) {
            warnings.add(ex.getReason() == null ? ex.getMessage() : ex.getReason());
            return new DeploymentPocRuntimeIndexingSummary(false, null, Map.of(), 0L, false);
        }
    }

    private ConfigSnapshot loadConfigSnapshot(DeploymentEntity deployment) {
        String configSource = "none";
        JsonNode entityConfig = objectMapper.createObjectNode();
        JsonNode routingConfig = objectMapper.createObjectNode();
        List<String> warnings = new ArrayList<>();

        if (hasText(deployment.getActiveVersionId())) {
            DeploymentVersionEntity version = deploymentVersionRepository.findById(deployment.getActiveVersionId())
                .orElse(null);
            if (version != null) {
                configSource = "active-version";
                entityConfig = readJson(version.getEntityConfigJson());
                routingConfig = readJson(version.getRoutingConfigJson());
            } else {
                warnings.add("Active version could not be loaded from the platform database.");
            }
        }

        if ("none".equals(configSource) && hasText(deployment.getActiveDraftId())) {
            DeploymentDraftEntity draft = deploymentDraftRepository.findById(deployment.getActiveDraftId())
                .orElse(null);
            if (draft != null) {
                configSource = "active-draft";
                entityConfig = readJson(draft.getEntityConfigJson());
                routingConfig = readJson(draft.getRoutingConfigJson());
            } else {
                warnings.add("Active draft could not be loaded from the platform database.");
            }
        }

        List<String> entityTypes = new ArrayList<>();
        JsonNode aiEntities = entityConfig.path("ai-entities");
        if (aiEntities.isObject()) {
            Iterator<String> fieldNames = aiEntities.fieldNames();
            while (fieldNames.hasNext()) {
                entityTypes.add(fieldNames.next());
            }
        }

        String upstreamBaseUrl = textOrNull(routingConfig.path("connector").path("upstream"), "base-url");
        return new ConfigSnapshot(configSource, List.copyOf(entityTypes), upstreamBaseUrl, List.copyOf(warnings));
    }

    private JsonNode sendRuntimeJson(String runtimeBaseUrl,
                                     String method,
                                     String pathWithQuery,
                                     String adminApiKey,
                                     java.util.function.Consumer<ObjectNode> bodyCustomizer) {
        URI target = runtimeUri(runtimeBaseUrl, pathWithQuery);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("X-ADMIN-API-KEY", adminApiKey);
            if ("GET".equalsIgnoreCase(method)) {
                builder.GET();
            } else {
                ObjectNode body = objectMapper.createObjectNode();
                if (bodyCustomizer != null) {
                    bodyCustomizer.accept(body);
                }
                builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Runtime POC admin request failed with HTTP " + response.statusCode() + "."
                );
            }
            if (!hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach deployment runtime: " + ex.getMessage(), ex);
        }
    }

    private URI runtimeUri(String runtimeBaseUrl, String pathWithQuery) {
        try {
            URI base = URI.create(runtimeBaseUrl.trim());
            if (!hasText(base.getScheme()) || !hasText(base.getHost())) {
                throw new IllegalArgumentException("Runtime base URL must be absolute.");
            }
            String basePath = base.getPath() == null ? "" : base.getPath();
            String suffix = pathWithQuery.startsWith("/") ? pathWithQuery : "/" + pathWithQuery;
            String query = null;
            String path = suffix;
            int queryIndex = suffix.indexOf('?');
            if (queryIndex >= 0) {
                path = suffix.substring(0, queryIndex);
                query = suffix.substring(queryIndex + 1);
            }
            String normalizedPath = (basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath) + path;
            return new URI(base.getScheme(), base.getUserInfo(), base.getHost(), base.getPort(), normalizedPath, query, null);
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid runtime base URL: " + runtimeBaseUrl);
        }
    }

    private Map<String, Long> readCounts(JsonNode countsNode) {
        if (!countsNode.isObject()) {
            return Map.of();
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = countsNode.fields();
        while (iterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = iterator.next();
            counts.put(entry.getKey(), entry.getValue().asLong(0L));
        }
        return counts;
    }

    private JsonNode readJson(String value) {
        if (!hasText(value)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        return deploymentAccessService.requireDeploymentOperatorAccess(deployment);
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isTextual() && hasText(value.asText()) ? value.asText().trim() : null;
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    private DeploymentPocMigrationCheckSummary readinessCheck(String key,
                                                              String label,
                                                              boolean ready,
                                                              String readyMessage,
                                                              String blockedMessage) {
        return new DeploymentPocMigrationCheckSummary(
            key,
            label,
            ready ? "READY" : "BLOCKED",
            ready ? readyMessage : blockedMessage
        );
    }

    private DeploymentPocMigrationCheckSummary warningCheck(String key,
                                                            String label,
                                                            boolean ready,
                                                            String readyMessage,
                                                            String warningMessage) {
        return new DeploymentPocMigrationCheckSummary(
            key,
            label,
            ready ? "READY" : "WARNING",
            ready ? readyMessage : warningMessage
        );
    }

    private record ConfigSnapshot(
        String configSource,
        List<String> entityTypes,
        String upstreamBaseUrl,
        List<String> warnings
    ) {
    }
}
