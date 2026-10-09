package com.ai.fabric.platform.backend.aiworkspace.service;

import com.ai.fabric.platform.backend.aiworkspace.entity.AIWorkspaceInstallationEntity;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceConnectionMode;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationManifest;
import com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceInstallationStatus;
import com.ai.fabric.platform.backend.aiworkspace.repository.AIWorkspaceInstallationRepository;
import com.ai.fabric.platform.backend.config.PlatformAIWorkspaceProperties;
import com.ai.fabric.platform.backend.config.PlatformDeliveryProperties;
import com.ai.fabric.platform.backend.tenant.entity.PlatformConsumerEntity;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class PublicAIWorkspaceManifestService {

    public static final String SCHEMA_VERSION = "loomai-ai-workspace-installation-v1";

    private final AIWorkspaceInstallationRepository repository;
    private final PlatformConsumerRepository consumerRepository;
    private final PlatformCustomerConsumerService consumerService;
    private final AIWorkspaceAssignmentService assignmentService;
    private final AIWorkspaceReadinessService readinessService;
    private final AIWorkspaceAssetCatalogService assetCatalogService;
    private final AIWorkspaceExperiencePackRegistry experiencePackRegistry;
    private final AIWorkspaceConnectionProfileRegistry connectionProfileRegistry;
    private final PlatformDeliveryProperties deliveryProperties;
    private final PlatformAIWorkspaceProperties properties;
    private final ObjectMapper objectMapper;

    public PublicAIWorkspaceManifestService(AIWorkspaceInstallationRepository repository,
                                            PlatformConsumerRepository consumerRepository,
                                            PlatformCustomerConsumerService consumerService,
                                            AIWorkspaceAssignmentService assignmentService,
                                            AIWorkspaceReadinessService readinessService,
                                            AIWorkspaceAssetCatalogService assetCatalogService,
                                            AIWorkspaceExperiencePackRegistry experiencePackRegistry,
                                            AIWorkspaceConnectionProfileRegistry connectionProfileRegistry,
                                            PlatformDeliveryProperties deliveryProperties,
                                            PlatformAIWorkspaceProperties properties,
                                            ObjectMapper objectMapper) {
        this.repository = repository;
        this.consumerRepository = consumerRepository;
        this.consumerService = consumerService;
        this.assignmentService = assignmentService;
        this.readinessService = readinessService;
        this.assetCatalogService = assetCatalogService;
        this.experiencePackRegistry = experiencePackRegistry;
        this.connectionProfileRegistry = connectionProfileRegistry;
        this.deliveryProperties = deliveryProperties;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ManifestResult resolve(String installationId, String requestOrigin) {
        String origin = normalizeRequestOrigin(requestOrigin);
        AIWorkspaceInstallationEntity installation = activeInstallation(installationId);
        if (!readOrigins(installation.getAllowedOriginsJson()).contains(origin)) unavailable();

        PlatformConsumerEntity consumer = consumerRepository.findById(installation.getConsumerEntityId())
            .orElseThrow(this::unavailableException);
        // Keep public consumer assignment rules canonical without exposing the privileged assignment DTO.
        PlatformCustomerConsumerService.ResolvedPublicConsumer resolved = consumerService
            .resolvePublicConsumer(consumer.getConsumerId());
        var readiness = readinessService.evaluate(installation);
        if (!readiness.ready()) unavailable();
        AIWorkspaceAssignmentService.Assignment assignment = assignmentService.resolveCurrent(resolved.consumer());
        JsonNode connectionConfiguration = readJson(installation.getConnectionConfigurationJson());
        var profile = connectionProfileRegistry.resolve(
            installation.getConnectionProfileCode(), installation.getConnectionProfileVersion(),
            installation.getConnectionMode(), connectionConfiguration);
        var pack = experiencePackRegistry.resolve(
            installation.getExperiencePackCode(), installation.getExperiencePackVersion());
        var workspaceAsset = assetCatalogService.workspace();
        if (pack.asset() == null) unavailable();

        ObjectNode connection = connection(installation.getConnectionMode(), assignment.runtimeBaseUrl(), profile);
        ObjectNode workspace = objectMapper.createObjectNode();
        workspace.set("asset", assetProjection(workspaceAsset));
        ObjectNode packNode = assetProjection(pack.asset());
        packNode.put("code", pack.code());
        workspace.set("experiencePack", packNode);
        workspace.set("configuration", readJson(installation.getConfigurationJson()));

        Instant generatedAt = latest(
            installation.getUpdatedAt(), installation.getCreatedAt(),
            assignment.release().getUpdatedAt(), assignment.release().getAppliedAt());
        String revisionMaterial = installation.getInstallationId() + "\n"
            + installation.getUpdatedAt() + "\n" + installation.getRowVersion() + "\n"
            + assignment.assignmentRevision() + "\n" + profile.code() + "@" + profile.version() + "\n"
            + workspaceAsset.sha256() + "\n" + pack.asset().sha256() + "\n"
            + generatedAt + "\n" + connection + "\n" + workspace;
        String manifestRevision = "sha256:" + sha256(revisionMaterial);
        AIWorkspaceInstallationManifest manifest = new AIWorkspaceInstallationManifest(
            SCHEMA_VERSION, installation.getInstallationId(), manifestRevision, assignment.assignmentRevision(),
            generatedAt, properties.manifestCacheTtl().toSeconds(), connection, workspace
        );
        return new ManifestResult(manifest, origin, '"' + manifestRevision + '"');
    }

    @Transactional(readOnly = true)
    public boolean isOriginAllowed(String installationId, String requestOrigin) {
        try {
            String origin = normalizeRequestOrigin(requestOrigin);
            AIWorkspaceInstallationEntity installation = activeInstallation(installationId);
            return readOrigins(installation.getAllowedOriginsJson()).contains(origin);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private ObjectNode connection(AIWorkspaceConnectionMode mode,
                                  String runtimeBaseUrl,
                                  AIWorkspaceConnectionProfileRegistry.ConnectionProfile profile) {
        ObjectNode connection = objectMapper.createObjectNode();
        connection.put("mode", mode.manifestValue());
        connection.put("profileCode", profile.code());
        connection.put("profileVersion", profile.version());
        connection.put("handler", profile.handler());
        if (mode != AIWorkspaceConnectionMode.BACKEND_MEDIATED_PRIVATE_RUNTIME) {
            connection.put("runtimeBaseUrl", runtimeBaseUrl);
            connection.set("routes", directRuntimeRoutes());
        }
        if (mode == AIWorkspaceConnectionMode.PUBLIC_RUNTIME_ANONYMOUS) {
            ObjectNode bootstrap = objectMapper.createObjectNode();
            bootstrap.put("url", "/api/public/chat/session");
            bootstrap.put("renewUrl", "/api/public/chat/session/renew");
            bootstrap.put("authorizationHeader", "Authorization");
            bootstrap.put("tokenScheme", "Bearer");
            connection.set("anonymousBootstrap", bootstrap);
        } else if (mode == AIWorkspaceConnectionMode.PUBLIC_RUNTIME_AUTHENTICATED) {
            ObjectNode broker = objectMapper.createObjectNode();
            broker.put("url", profile.endpoint());
            broker.put("method", "POST");
            broker.put("credentials", "include");
            broker.put("responseSchemaVersion", "loomai-workspace-runtime-token-v1");
            connection.set("credentialBroker", broker);
        } else {
            ObjectNode adapter = objectMapper.createObjectNode();
            adapter.put("bootstrapUrl", profile.endpoint());
            adapter.put("method", "POST");
            adapter.put("credentials", "include");
            adapter.put("responseSchemaVersion", "loomai-workspace-private-adapter-v1");
            connection.set("adapter", adapter);
        }
        return connection;
    }

    private ObjectNode directRuntimeRoutes() {
        ObjectNode routes = objectMapper.createObjectNode();
        routes.put("queryUrl", "/api/chat/me/query");
        routes.put("suggestionsUrl", "/api/chat/me/suggestions");
        routes.put("authContextUrl", "/api/chat/me/auth-context");
        routes.put("shellConfigUrl", "/api/chat/me/shell-config");
        routes.put("conversationsUrl", "/api/chat/me/conversations");
        routes.put("conversationItemUrlTemplate", "/api/chat/me/conversations/{conversationId}");
        return routes;
    }

    private ObjectNode assetProjection(com.ai.fabric.platform.backend.aiworkspace.model.AIWorkspaceAssetDescriptor asset) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("version", asset.version());
        node.put("url", absolutePublicUrl(asset.requestPath()));
        node.put("integrity", asset.integrity());
        return node;
    }

    private String absolutePublicUrl(String requestPath) {
        return deliveryProperties.publicBaseUrl() + requestPath;
    }

    private AIWorkspaceInstallationEntity activeInstallation(String installationId) {
        if (installationId == null || !installationId.matches("awi_pub_[a-f0-9]{32}")) unavailable();
        AIWorkspaceInstallationEntity installation = repository.findByInstallationId(installationId)
            .orElseThrow(this::unavailableException);
        if (installation.getStatus() != AIWorkspaceInstallationStatus.ACTIVE) unavailable();
        return installation;
    }

    private String normalizeRequestOrigin(String value) {
        try {
            if (value == null || value.isBlank()) unavailable();
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.isBlank() || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))) unavailable();
            int port = uri.getPort();
            return scheme + "://" + host + (port < 0 ? "" : ":" + port);
        } catch (RuntimeException ex) {
            unavailable();
            return null;
        }
    }

    private List<String> readOrigins(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private JsonNode readJson(String raw) {
        try {
            return raw == null || raw.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(raw);
        } catch (Exception ex) {
            unavailable();
            return objectMapper.createObjectNode();
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable.", ex);
        }
    }

    private Instant latest(Instant... values) {
        Instant result = Instant.EPOCH;
        for (Instant value : values) {
            if (value != null && value.isAfter(result)) result = value;
        }
        return result;
    }

    private void unavailable() {
        throw unavailableException();
    }

    private ResponseStatusException unavailableException() {
        return new ResponseStatusException(NOT_FOUND, "AI Workspace installation is unavailable.");
    }

    public record ManifestResult(AIWorkspaceInstallationManifest manifest, String origin, String etag) {
    }
}
