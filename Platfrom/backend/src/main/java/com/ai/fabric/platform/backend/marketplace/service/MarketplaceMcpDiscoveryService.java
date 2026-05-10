package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformProductProvisioningProperties;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceMcpDiscoveryRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceMcpDiscoverySummary;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceMcpImportDraftRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplaceMcpImportDraftSummary;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginVersionRepository;
import com.ai.fabric.platform.backend.productservice.entity.PlatformManagedProductServiceEntity;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class MarketplaceMcpDiscoveryService {

    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(45);
    private static final String GATEWAY_API_KEY_HEADER = "X-MCP-GATEWAY-API-KEY";
    private static final String MCP_SECRET_REF_PREFIX = "MCP_SECRET_";
    private static final Pattern SECRET_REF_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]{1,127}");
    private static final Pattern IPV4_LITERAL = Pattern.compile(
        "^(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}$"
    );
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final PlatformProductProvisioningProperties productProvisioningProperties;
    private final PlatformManagedProductServiceService productServiceService;
    private final PlatformSecretService platformSecretService;
    private final MarketplacePluginRepository pluginRepository;
    private final MarketplacePluginVersionRepository versionRepository;
    private final MarketplaceManifestService manifestService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public MarketplaceMcpDiscoveryService(PlatformProductProvisioningProperties productProvisioningProperties,
                                          PlatformManagedProductServiceService productServiceService,
                                          PlatformSecretService platformSecretService,
                                          MarketplacePluginRepository pluginRepository,
                                          MarketplacePluginVersionRepository versionRepository,
                                          MarketplaceManifestService manifestService,
                                          PlatformAuditService platformAuditService,
                                          ObjectMapper objectMapper) {
        this.productProvisioningProperties = productProvisioningProperties;
        this.productServiceService = productServiceService;
        this.platformSecretService = platformSecretService;
        this.pluginRepository = pluginRepository;
        this.versionRepository = versionRepository;
        this.manifestService = manifestService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    }

    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public MarketplaceMcpDiscoverySummary discover(MarketplaceMcpDiscoveryRequest request) {
        GatewayBinding gateway = resolveGateway(request.gatewayServiceRef());
        Map<String, String> resolvedSecretValues = resolveMcpSecretValues(request.server());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverRef", request.serverRef());
        payload.put("server", sanitizedServer(request.server()));
        payload.put("trace", sanitizedTraceWithResolvedSecrets(request.trace(), resolvedSecretValues));
        payload.put("allowedTools", request.allowedTools() == null ? List.of() : request.allowedTools());
        auditResolvedMcpSecretsForwarded(request, gateway, resolvedSecretValues);
        JsonNode response = postGateway(gateway, "/api/internal/mcp/import/discover", payload);
        return toDiscoverySummary(response);
    }

    @Transactional
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public MarketplaceMcpImportDraftSummary importDraft(MarketplaceMcpImportDraftRequest request) {
        MarketplaceMcpDiscoverySummary discovery = discover(new MarketplaceMcpDiscoveryRequest(
            request.serverRef(),
            request.server(),
            request.trace(),
            request.allowedTools(),
            request.gatewayServiceRef()
        ));
        if (!discovery.ready()) {
            throw new ResponseStatusException(CONFLICT, "MCP discovery failed: " + discovery.message());
        }
        ObjectNode manifest = buildManifest(request, discovery);
        String pluginId = normalizeId(request.pluginId(), "pluginId");
        String versionLabel = normalizeVersion(request.version());
        String pluginSlug = normalizeSlug(request.pluginSlug());
        versionRepository.findByPluginIdAndVersion(pluginId, versionLabel).ifPresent(existing -> {
            throw new ResponseStatusException(CONFLICT, "Marketplace plugin version already exists: " + pluginId + "@" + versionLabel);
        });

        MarketplacePluginEntity plugin = pluginRepository.findById(pluginId).orElse(null);
        if (plugin == null) {
            pluginRepository.findBySlugIgnoreCase(pluginSlug).ifPresent(existing -> {
                throw new ResponseStatusException(CONFLICT, "Marketplace plugin slug already exists: " + pluginSlug);
            });
            plugin = new MarketplacePluginEntity();
            plugin.setId(pluginId);
            plugin.setSlug(pluginSlug);
            plugin.setPluginType("ACTION");
            plugin.setPublisherSlug(firstNonBlank(request.publisherSlug(), "mcp-discovery"));
            plugin.setPublisherDisplayName(firstNonBlank(request.publisherDisplayName(), "MCP Discovery"));
            plugin.setCreatedAt(Instant.now());
        } else if (!"ACTION".equalsIgnoreCase(plugin.getPluginType())) {
            throw new ResponseStatusException(CONFLICT, "MCP import can only append ACTION plugin versions.");
        }
        plugin.setDisplayName(firstNonBlank(request.displayName(), pluginId));
        plugin.setShortDescription(firstNonBlank(request.description(), "Discovered MCP action plugin."));
        plugin.setStatus("DRAFT");
        plugin.setUpdatedAt(Instant.now());

        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId("mkv-" + UUID.randomUUID().toString().substring(0, 8));
        version.setPluginId(pluginId);
        version.setVersion(versionLabel);
        version.setReleaseChannel("DRAFT");
        version.setStatus("SUBMITTED");
        version.setManifestJson(writeJson(manifest));
        version.setCreatedAt(Instant.now());
        version.setPublishedAt(Instant.now());
        version.setBundleSha256(sha256(version.getManifestJson()));
        manifestService.parseAndValidate(plugin, version);

        pluginRepository.save(plugin);
        versionRepository.save(version);
        platformAuditService.record(
            "MARKETPLACE_MCP_IMPORT_DRAFT_CREATED",
            "MARKETPLACE_PLUGIN_VERSION",
            version.getId(),
            Map.of("pluginId", pluginId, "version", versionLabel, "serverRef", discovery.serverRef())
        );
        return new MarketplaceMcpImportDraftSummary(
            pluginId,
            version.getId(),
            versionLabel,
            version.getStatus(),
            manifest,
            discovery,
            version.getCreatedAt()
        );
    }

    private ObjectNode buildManifest(MarketplaceMcpImportDraftRequest request, MarketplaceMcpDiscoverySummary discovery) {
        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("schemaVersion", 1);
        manifest.put("pluginId", normalizeId(request.pluginId(), "pluginId"));
        manifest.put("version", normalizeVersion(request.version()));
        manifest.put("pluginType", "ACTION");
        manifest.put("displayName", firstNonBlank(request.displayName(), request.pluginId()));
        manifest.put("description", firstNonBlank(request.description(), "Discovered MCP action plugin."));
        ObjectNode compatibility = manifest.putObject("compatibility");
        ArrayNode capabilities = compatibility.putArray("requiredCapabilities");
        capabilities.add("actions");
        ObjectNode pricing = manifest.putObject("pricing");
        pricing.put("pricingModel", "FREE");
        ObjectNode permissions = manifest.putObject("permissions");
        permissions.put("contributesActions", true);
        ObjectNode contributions = manifest.putObject("contributions");
        ArrayNode mcpServers = contributions.putArray("mcpServers");
        ObjectNode server = mcpServers.addObject();
        server.put("serverRef", discovery.serverRef());
        server.put("transport", "STREAMABLE_HTTP");
        server.put("endpointUrl", discovery.endpointUrl());
        server.set("auth", objectMapper.valueToTree(authFromRequest(request.server())));
        ArrayNode allowedTools = server.putArray("allowedTools");
        discovery.tools().forEach(tool -> allowedTools.add(tool.name()));
        ObjectNode verification = server.putObject("verification");
        verification.put("mode", "INITIALIZE_AND_TOOLS_LIST");
        verification.put("schemaDriftPolicy", "WARN_ONLY");

        ArrayNode actions = contributions.putArray("actions");
        for (MarketplaceMcpDiscoverySummary.McpToolSummary tool : discovery.tools()) {
            ObjectNode action = actions.addObject();
            action.put("actionId", actionIdForTool(tool.name()));
            action.put("displayName", firstNonBlank(tool.title(), tool.name()));
            action.put("adapterType", "mcp-tool");
            action.put("readOnly", true);
            action.put("description", firstNonBlank(tool.description(), "Discovered MCP tool " + tool.name() + "."));
            ObjectNode execution = action.putObject("execution");
            execution.put("adapterType", "mcp-tool");
            ObjectNode mcp = execution.putObject("mcp");
            mcp.put("serverRef", discovery.serverRef());
            mcp.put("toolName", tool.name());
            mcp.put("toolSchemaHash", tool.schemaHash());
            mcp.put("schemaDriftPolicy", "WARN_ONLY");
        }
        return manifest;
    }

    private Map<String, Object> authFromRequest(Map<String, Object> server) {
        if (server == null) {
            return Map.of("mode", "NONE");
        }
        Object auth = server.get("auth");
        if (auth instanceof Map<?, ?> map && map.get("mode") != null) {
            Map<String, Object> out = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null && value != null && !isRawSecretField(key.toString())) {
                    out.put(key.toString(), value);
                }
            });
            return out;
        }
        return Map.of("mode", "NONE");
    }

    private Map<String, Object> sanitizedServer(Map<String, Object> server) {
        if (server == null || server.isEmpty()) {
            return Map.of();
        }
        ObjectNode sanitized = objectMapper.valueToTree(server);
        JsonNode auth = sanitized.path("auth");
        if (auth.isObject()) {
            ObjectNode authObject = (ObjectNode) auth;
            java.util.ArrayList<String> rawFields = new java.util.ArrayList<>();
            authObject.fieldNames().forEachRemaining(fieldName -> {
                if (isRawSecretField(fieldName)) {
                    rawFields.add(fieldName);
                }
            });
            rawFields.forEach(authObject::remove);
        }
        return objectMapper.convertValue(sanitized, MAP_TYPE);
    }

    private boolean isRawSecretField(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return false;
        }
        String normalized = fieldName.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("resolvedsecretvalue")
            || normalized.equals("secretvalue")
            || normalized.equals("tokenvalue")
            || normalized.equals("apikey")
            || normalized.equals("authorization");
    }

    private Map<String, Object> sanitizedTraceWithResolvedSecrets(Map<String, Object> trace,
                                                                  Map<String, String> resolved) {
        ObjectNode out = objectMapper.valueToTree(trace == null ? Map.of() : trace);
        out.remove(List.of("mcpSecretValues", "secretValues", "resolvedSecrets"));
        if (!resolved.isEmpty()) {
            out.set("mcpSecretValues", objectMapper.valueToTree(resolved));
        }
        return objectMapper.convertValue(out, MAP_TYPE);
    }

    private void auditResolvedMcpSecretsForwarded(MarketplaceMcpDiscoveryRequest request,
                                                  GatewayBinding gateway,
                                                  Map<String, String> resolvedSecretValues) {
        if (resolvedSecretValues.isEmpty()) {
            return;
        }
        LinkedHashMap<String, Object> details = new LinkedHashMap<>();
        details.put("serverRef", request.serverRef());
        details.put(
            "gatewayServiceRef",
            firstNonBlank(request.gatewayServiceRef(), productProvisioningProperties.mcpExecutionGatewayServiceRef())
        );
        details.put("gatewayBaseUrl", gateway.baseUrl());
        details.put("secretRefs", List.copyOf(resolvedSecretValues.keySet()));
        details.put("secretCount", resolvedSecretValues.size());
        copyTraceText(details, request.trace(), "tenantId");
        copyTraceText(details, request.trace(), "customerId");
        copyTraceText(details, request.trace(), "deploymentId");
        platformAuditService.record(
            "MARKETPLACE_MCP_SECRET_REFS_FORWARDED",
            "MCP_SERVER",
            firstNonBlank(request.serverRef(), "unknown"),
            details
        );
    }

    private void copyTraceText(Map<String, Object> details, Map<String, Object> trace, String key) {
        if (trace == null || !trace.containsKey(key)) {
            return;
        }
        Object raw = trace.get(key);
        if (raw != null && StringUtils.hasText(raw.toString())) {
            details.put(key, raw.toString().trim());
        }
    }

    private Map<String, String> resolveMcpSecretValues(Map<String, Object> server) {
        if (server == null) {
            return Map.of();
        }
        Object authRaw = server.get("auth");
        if (!(authRaw instanceof Map<?, ?> auth)) {
            return Map.of();
        }
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        resolveSecretRef(auth, "secretRef", out);
        resolveSecretRef(auth, "valueSecretRef", out);
        resolveSecretRef(auth, "tokenSecretRef", out);
        resolveSecretRef(auth, "clientSecretRef", out);
        return Map.copyOf(out);
    }

    private void resolveSecretRef(Map<?, ?> auth, String key, Map<String, String> out) {
        Object raw = auth.get(key);
        if (raw == null) {
            return;
        }
        String secretRef = raw.toString().trim();
        if (!StringUtils.hasText(secretRef)) {
            return;
        }
        if (!SECRET_REF_PATTERN.matcher(secretRef).matches() || !secretRef.startsWith(MCP_SECRET_REF_PREFIX)) {
            throw new ResponseStatusException(CONFLICT, "MCP auth secretRef is invalid: " + key);
        }
        String secretValue = platformSecretService.resolveSecret(secretRef);
        if (!StringUtils.hasText(secretValue)) {
            throw new ResponseStatusException(CONFLICT, "MCP auth secretRef is unavailable: " + key);
        }
        out.put(secretRef, secretValue.trim());
    }

    private GatewayBinding resolveGateway(String requestedServiceRef) {
        String serviceRef = firstNonBlank(requestedServiceRef, productProvisioningProperties.mcpExecutionGatewayServiceRef());
        PlatformManagedProductServiceEntity service = productServiceService.requireService(serviceRef);
        if (!StringUtils.hasText(service.getBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway product service has no baseUrl: " + serviceRef);
        }
        if (!StringUtils.hasText(service.getSecretName())) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway product service has no secretName: " + serviceRef);
        }
        String apiKey = platformSecretService.resolveSecret(service.getSecretName());
        if (!StringUtils.hasText(apiKey)) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway product service secret is missing.");
        }
        URI baseUri = requireGatewayBaseUri(service.getBaseUrl());
        return new GatewayBinding(baseUri.toString(), apiKey.trim());
    }

    private JsonNode postGateway(GatewayBinding gateway, String path, Map<String, Object> payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(joinUrl(gateway.baseUrl(), path)))
                .timeout(HTTP_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header(GATEWAY_API_KEY_HEADER, gateway.apiKey())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(CONFLICT, "MCP gateway request failed with HTTP " + response.statusCode() + ".");
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway request failed: " + ex.getMessage(), ex);
        }
    }

    private URI requireGatewayBaseUri(String rawBaseUrl) {
        URI uri = URI.create(rawBaseUrl.trim());
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost();
        if (!StringUtils.hasText(host) || StringUtils.hasText(uri.getUserInfo())) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway product service baseUrl is invalid.");
        }
        boolean loopback = isLoopbackHost(host);
        if ("http".equals(scheme) && loopback) {
            return uri;
        }
        if (!"https".equals(scheme) || isBlockedGatewayHost(host) || resolvesToBlockedGatewayAddress(host)) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway product service baseUrl is not allowed.");
        }
        return uri;
    }

    private boolean resolvesToBlockedGatewayAddress(String host) {
        String normalized = normalizeHost(host);
        if (IPV4_LITERAL.matcher(normalized).matches() || normalized.contains(":")) {
            return false;
        }
        try {
            for (InetAddress address : InetAddress.getAllByName(normalized)) {
                if (isBlockedGatewayAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException ex) {
            throw new ResponseStatusException(CONFLICT, "MCP gateway product service baseUrl host could not be resolved.");
        }
    }

    private boolean isBlockedGatewayAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            int first = bytes[0] & 0xff;
            int second = bytes[1] & 0xff;
            return first == 0
                || first == 0xfc
                || first == 0xfd
                || (first == 0xfe && (second & 0xc0) == 0x80);
        }
        return false;
    }

    private boolean isBlockedGatewayHost(String host) {
        String normalized = normalizeHost(host);
        if (!StringUtils.hasText(normalized)
            || "metadata.google.internal".equals(normalized)
            || normalized.endsWith(".local")
            || normalized.endsWith(".internal")
            || normalized.endsWith(".localhost")) {
            return true;
        }
        if (IPV4_LITERAL.matcher(normalized).matches()) {
            String[] parts = normalized.split("\\.");
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);
            return first == 0
                || first == 10
                || first == 127
                || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31
                || first == 192 && second == 168
                || first >= 224;
        }
        if (!normalized.contains(":")) {
            return false;
        }
        return "::".equals(normalized)
            || "::1".equals(normalized)
            || normalized.startsWith("0:")
            || normalized.startsWith("fc")
            || normalized.startsWith("fd")
            || normalized.startsWith("fe80:");
    }

    private boolean isLoopbackHost(String host) {
        String normalized = normalizeHost(host);
        return "localhost".equals(normalized)
            || "127.0.0.1".equals(normalized)
            || "::1".equals(normalized);
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private MarketplaceMcpDiscoverySummary toDiscoverySummary(JsonNode node) {
        List<MarketplaceMcpDiscoverySummary.McpToolSummary> tools = new java.util.ArrayList<>();
        for (JsonNode tool : node.path("tools")) {
            tools.add(new MarketplaceMcpDiscoverySummary.McpToolSummary(
                text(tool, "name"),
                text(tool, "title"),
                text(tool, "description"),
                tool.path("inputSchema").deepCopy(),
                tool.path("outputSchema").deepCopy(),
                text(tool, "schemaHash")
            ));
        }
        return new MarketplaceMcpDiscoverySummary(
            node.path("ready").asBoolean(false),
            text(node, "message"),
            text(node, "serverRef"),
            text(node, "endpointUrl"),
            text(node, "protocolVersion"),
            List.copyOf(tools),
            text(node, "errorCode")
        );
    }

    private String actionIdForTool(String toolName) {
        String normalized = normalizeId(toolName, "toolName")
            .toLowerCase(Locale.ROOT)
            .replace('.', '_')
            .replace(':', '_')
            .replace('-', '_');
        return normalized.matches("[a-z][a-z0-9_]{1,127}") ? normalized : "mcp_" + normalized;
    }

    private String normalizeId(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, fieldName + " is required.");
        }
        String normalized = value.trim();
        if (!normalized.matches("[A-Za-z][A-Za-z0-9_.:-]{0,127}")) {
            throw new ResponseStatusException(CONFLICT, fieldName + " is invalid.");
        }
        return normalized;
    }

    private String normalizeSlug(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "pluginSlug is required.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]+", "-").replaceAll("-{2,}", "-").replaceAll("(^-|-$)", "");
        if (!StringUtils.hasText(normalized)) {
            throw new ResponseStatusException(CONFLICT, "pluginSlug is invalid.");
        }
        return normalized;
    }

    private String normalizeVersion(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(CONFLICT, "version is required.");
        }
        return value.trim();
    }

    private String writeJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to write marketplace manifest JSON.", ex);
        }
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash marketplace manifest.", ex);
        }
    }

    private String joinUrl(String baseUrl, String path) {
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String suffix = path.startsWith("/") ? path : "/" + path;
        return base + suffix;
    }

    private String text(JsonNode node, String field) {
        String value = node == null ? null : node.path(field).asText(null);
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String left, String right) {
        return StringUtils.hasText(left) ? left.trim() : StringUtils.hasText(right) ? right.trim() : null;
    }

    private record GatewayBinding(String baseUrl, String apiKey) {
    }
}
