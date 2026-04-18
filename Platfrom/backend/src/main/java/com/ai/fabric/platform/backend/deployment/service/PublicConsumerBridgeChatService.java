package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentAccessSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.RuntimePublicTokenSigningService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class PublicConsumerBridgeChatService {

    public static final String SHOPPER_SESSION_HEADER = "X-AI-FABRIC-SHOPPER-SESSION-ID";

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    private static final String RUNTIME_PRIVATE_AUTHORIZATION_HEADER = RuntimePrivateAssertionSigningService.AUTHORIZATION_HEADER;
    private static final String SCOPE_CHAT_QUERY = "chat:query";
    private static final String SCOPE_CHAT_SUGGESTIONS = "chat:suggestions";
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,120}$");
    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;

    private final PlatformCustomerConsumerService platformCustomerConsumerService;
    private final PublicProvisioningApiService publicProvisioningApiService;
    private final PlatformSecretService platformSecretService;
    private final RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService;
    private final RuntimePublicTokenSigningService runtimePublicTokenSigningService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PublicConsumerBridgeChatService(PlatformCustomerConsumerService platformCustomerConsumerService,
                                           PublicProvisioningApiService publicProvisioningApiService,
                                           PlatformSecretService platformSecretService,
                                           RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService,
                                           RuntimePublicTokenSigningService runtimePublicTokenSigningService,
                                           ObjectMapper objectMapper) {
        this.platformCustomerConsumerService = platformCustomerConsumerService;
        this.publicProvisioningApiService = publicProvisioningApiService;
        this.platformSecretService = platformSecretService;
        this.runtimePrivateAssertionSigningService = runtimePrivateAssertionSigningService;
        this.runtimePublicTokenSigningService = runtimePublicTokenSigningService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public JsonNode query(String consumerId, JsonNode request, String shopperSessionId) {
        ResolvedConsumerRuntime resolved = resolveConsumerRuntime(consumerId);
        ObjectNode body = normalizeQueryBody(request);
        String query = trimToNull(textOrNull(body, "query"));
        if (!StringUtils.hasText(query)) {
            throw new ResponseStatusException(BAD_REQUEST, "query is required.");
        }
        body.put("query", query);
        return sendJson(
            resolved,
            "POST",
            "/api/chat/me/query",
            body,
            shopperSessionId,
            List.of(SCOPE_CHAT_QUERY)
        );
    }

    public JsonNode suggestions(String consumerId, JsonNode request, String shopperSessionId) {
        ResolvedConsumerRuntime resolved = resolveConsumerRuntime(consumerId);
        ObjectNode body = normalizeSuggestionsBody(request);
        return sendJson(
            resolved,
            "POST",
            "/api/chat/me/suggestions",
            body,
            shopperSessionId,
            List.of(SCOPE_CHAT_SUGGESTIONS)
        );
    }

    private ResolvedConsumerRuntime resolveConsumerRuntime(String consumerId) {
        PlatformCustomerConsumerService.ResolvedPublicConsumer resolved = platformCustomerConsumerService.resolvePublicConsumer(consumerId);
        DeploymentEntity deployment = resolved.deployment();
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment runtime URL is not available for consumer '" + resolved.consumer().getConsumerId()
                    + "'. Apply the deployment before enabling storefront chat."
            );
        }
        PublicConsumerDeploymentCredentialsResponse credentials =
            publicProvisioningApiService.getConsumerDeploymentCredentials(resolved.consumer().getConsumerId());
        return new ResolvedConsumerRuntime(resolved.consumer().getConsumerId(), deployment, credentials);
    }

    private JsonNode sendJson(ResolvedConsumerRuntime resolved,
                              String method,
                              String path,
                              JsonNode body,
                              String shopperSessionId,
                              List<String> grantedScopes) {
        try {
            Map<String, String> runtimeHeaders = runtimeAuthHeaders(resolved, shopperSessionId, grantedScopes);
            if (runtimeHeaders.isEmpty()) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    runtimeAuthUnavailableMessage(resolved.credentials().access())
                );
            }
            HttpResponse<String> response = sendRequest(
                runtimeUri(resolved.deployment().getRuntimeBaseUrl(), path),
                method,
                body,
                runtimeHeaders
            );
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Deployment runtime for consumer '" + resolved.consumerId()
                        + "' is missing verified route " + path + "."
                );
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Runtime bridge request failed with HTTP " + response.statusCode() + "."
                );
            }
            if (!StringUtils.hasText(response.body())) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(response.body());
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_GATEWAY, "Failed to reach deployment runtime: " + ex.getMessage(), ex);
        }
    }

    private HttpResponse<String> sendRequest(URI target,
                                             String method,
                                             JsonNode body,
                                             Map<String, String> runtimeHeaders) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
            .timeout(RUNTIME_TIMEOUT)
            .header("Accept", "application/json");
        runtimeHeaders.forEach((name, value) -> {
            if (StringUtils.hasText(value)) {
                builder.header(name, value.trim());
            }
        });
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(
                    body == null ? "{}" : writeJson(body)
                ));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, String> runtimeAuthHeaders(ResolvedConsumerRuntime resolved,
                                                   String shopperSessionId,
                                                   List<String> grantedScopes) {
        if (canUsePrivateRuntime(resolved.credentials().access())) {
            return privateRuntimeAuthHeaders(resolved, shopperSessionId, grantedScopes);
        }
        if (canUsePublicRuntime(resolved.credentials().access())) {
            return publicRuntimeAuthHeaders(resolved, shopperSessionId, grantedScopes);
        }
        return Map.of();
    }

    private boolean canUsePrivateRuntime(PublicDeploymentAccessSummary access) {
        return access != null
            && access.trustedBackend() != null
            && access.trustedBackend().callerAuthConfigured()
            && access.trustedBackend().assertionValidationConfigured()
            && runtimePrivateAssertionSigningService != null
            && runtimePrivateAssertionSigningService.isConfigured()
            && StringUtils.hasText(platformSecretService.resolveSecret(RUNTIME_TRUSTED_BACKEND_SECRET_NAME));
    }

    private boolean canUsePublicRuntime(PublicDeploymentAccessSummary access) {
        return access != null
            && access.publicRuntime() != null
            && access.publicRuntime().tokenValidationConfigured()
            && runtimePublicTokenSigningService != null
            && runtimePublicTokenSigningService.isConfigured();
    }

    private Map<String, String> privateRuntimeAuthHeaders(ResolvedConsumerRuntime resolved,
                                                          String shopperSessionId,
                                                          List<String> grantedScopes) {
        String trustedBackendApiKey = trimToNull(platformSecretService.resolveSecret(RUNTIME_TRUSTED_BACKEND_SECRET_NAME));
        if (!StringUtils.hasText(trustedBackendApiKey)) {
            return Map.of();
        }
        ShopperIdentity identity = shopperIdentity(resolved.consumerId(), shopperSessionId);
        String authorization = runtimePrivateAssertionSigningService.toAuthorizationHeaderValue(
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                identity.subjectId(),
                "STOREFRONT_SESSION",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "SHOPIFY_BRIDGE_SERVICE",
                identity.sessionId(),
                resolved.deployment().getId(),
                trimToNull(resolved.deployment().getCustomerId()),
                trimToNull(resolved.deployment().getTenantId()),
                "platform-consumer-bridge",
                Instant.now().plus(Duration.ofMinutes(5)),
                List.of(resolved.deployment().getId()),
                normalizeScopes(grantedScopes)
            )
        );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER, trustedBackendApiKey);
        headers.put(RUNTIME_PRIVATE_AUTHORIZATION_HEADER, authorization);
        return Map.copyOf(headers);
    }

    private Map<String, String> publicRuntimeAuthHeaders(ResolvedConsumerRuntime resolved,
                                                         String shopperSessionId,
                                                         List<String> grantedScopes) {
        ShopperIdentity identity = shopperIdentity(resolved.consumerId(), shopperSessionId);
        String issuer = trimToNull(resolved.credentials().access().publicRuntime().tokenIssuerHint());
        String audience = trimToNull(resolved.credentials().access().publicRuntime().defaultAudience());
        String authorization = runtimePublicTokenSigningService.toAuthorizationHeaderValue(
            new RuntimePublicTokenSigningService.RuntimePublicTokenClaims(
                identity.subjectId(),
                "STOREFRONT_SESSION",
                "PUBLIC_RUNTIME_BRIDGE_TOKEN",
                "SHOPIFY_BRIDGE_SERVICE",
                identity.sessionId(),
                resolved.deployment().getId(),
                trimToNull(resolved.deployment().getCustomerId()),
                trimToNull(resolved.deployment().getTenantId()),
                issuer == null ? "platform-consumer-bridge" : issuer,
                Instant.now().plus(Duration.ofMinutes(5)),
                List.of(audience == null ? resolved.deployment().getId() : audience),
                normalizeScopes(grantedScopes)
            )
        );
        return Map.of(RuntimePublicTokenSigningService.AUTHORIZATION_HEADER, authorization);
    }

    private ShopperIdentity shopperIdentity(String consumerId, String shopperSessionId) {
        String normalizedSessionId = normalizeSessionId(consumerId, shopperSessionId);
        String subjectId = "consumer-session-" + shortSha(consumerId + "|" + normalizedSessionId);
        return new ShopperIdentity(subjectId, normalizedSessionId);
    }

    private String normalizeSessionId(String consumerId, String shopperSessionId) {
        String trimmed = trimToNull(shopperSessionId);
        if (trimmed == null) {
            return "storefront-" + shortSha(consumerId);
        }
        if (SAFE_SESSION_ID.matcher(trimmed).matches()) {
            return trimmed;
        }
        return "storefront-" + shortSha(trimmed);
    }

    private ObjectNode normalizeQueryBody(JsonNode request) {
        ObjectNode body = objectMapper.createObjectNode();
        if (request != null && request.isObject()) {
            copyTextField(request, body, "query");
            copyTextField(request, body, "conversationId");
            copyTextField(request, body, "mode");
            copyTextField(request, body, "position");
            copyStorefrontContext(request, body);
        }
        return body;
    }

    private ObjectNode normalizeSuggestionsBody(JsonNode request) {
        ObjectNode body = objectMapper.createObjectNode();
        if (request != null && request.isObject()) {
            copyTextField(request, body, "content");
            JsonNode maxSuggestions = request.get("maxSuggestions");
            if (maxSuggestions != null && maxSuggestions.canConvertToInt()) {
                int value = Math.max(1, Math.min(maxSuggestions.asInt(), 6));
                body.put("maxSuggestions", value);
            }
            copyStorefrontContext(request, body);
        }
        if (!body.has("content")) {
            body.put("content", "");
        }
        if (!body.has("maxSuggestions")) {
            body.put("maxSuggestions", 4);
        }
        return body;
    }

    private void copyTextField(JsonNode source, ObjectNode target, String field) {
        String value = trimToNull(textOrNull(source, field));
        if (value != null) {
            target.put(field, value);
        }
    }

    private void copyStorefrontContext(JsonNode source, ObjectNode target) {
        if (source == null || !source.isObject()) {
            return;
        }
        JsonNode rawContext = source.get("storefrontContext");
        if (rawContext == null || !rawContext.isObject()) {
            return;
        }
        ObjectNode normalized = objectMapper.createObjectNode();
        copyLimitedTextField(rawContext, normalized, "pageType");
        copyLimitedTextField(rawContext, normalized, "pageTitle");

        JsonNode rawProduct = rawContext.get("product");
        if (rawProduct != null && rawProduct.isObject()) {
            ObjectNode product = objectMapper.createObjectNode();
            copyLimitedTextField(rawProduct, product, "id");
            copyLimitedTextField(rawProduct, product, "handle");
            copyLimitedTextField(rawProduct, product, "title");
            copyLimitedTextField(rawProduct, product, "vendor");
            copyLimitedTextField(rawProduct, product, "type");
            copyLimitedTextField(rawProduct, product, "priceCents");
            if (!product.isEmpty()) {
                normalized.set("product", product);
            }
        }

        JsonNode rawCollection = rawContext.get("collection");
        if (rawCollection != null && rawCollection.isObject()) {
            ObjectNode collection = objectMapper.createObjectNode();
            copyLimitedTextField(rawCollection, collection, "id");
            copyLimitedTextField(rawCollection, collection, "handle");
            copyLimitedTextField(rawCollection, collection, "title");
            if (!collection.isEmpty()) {
                normalized.set("collection", collection);
            }
        }

        if (!normalized.isEmpty()) {
            target.set("storefrontContext", normalized);
        }
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String field) {
        String value = trimToNull(textOrNull(source, field));
        if (value == null) {
            return;
        }
        if (value.length() > MAX_CONTEXT_TEXT_LENGTH) {
            value = value.substring(0, MAX_CONTEXT_TEXT_LENGTH);
        }
        target.put(field, value);
    }

    private URI runtimeUri(String runtimeBaseUrl, String path) {
        try {
            URI base = URI.create(runtimeBaseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
                throw new IllegalArgumentException("Runtime base URL must be absolute.");
            }
            String basePath = base.getPath() == null ? "" : base.getPath();
            String normalizedPath = (basePath.endsWith("/") ? basePath.substring(0, basePath.length() - 1) : basePath)
                + (path.startsWith("/") ? path : "/" + path);
            StringBuilder target = new StringBuilder();
            target.append(base.getScheme()).append("://");
            if (StringUtils.hasText(base.getRawAuthority())) {
                target.append(base.getRawAuthority());
            } else {
                target.append(base.getHost());
                if (base.getPort() >= 0) {
                    target.append(':').append(base.getPort());
                }
            }
            target.append(normalizedPath);
            return URI.create(target.toString());
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid runtime base URL: " + runtimeBaseUrl);
        }
    }

    private String runtimeAuthUnavailableMessage(PublicDeploymentAccessSummary access) {
        if (access != null && StringUtils.hasText(access.guidance())) {
            return access.guidance();
        }
        return "No supported runtime auth posture is configured for storefront bridge traffic.";
    }

    private List<String> normalizeScopes(List<String> grantedScopes) {
        if (grantedScopes == null || grantedScopes.isEmpty()) {
            return List.of();
        }
        return grantedScopes.stream()
            .filter(StringUtils::hasText)
            .map(String::trim)
            .distinct()
            .toList();
    }

    private String writeJson(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize runtime request body.", ex);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() || !child.isValueNode() ? null : child.asText(null);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String shortSha(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < hash.length; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (Exception ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private record ResolvedConsumerRuntime(
        String consumerId,
        DeploymentEntity deployment,
        PublicConsumerDeploymentCredentialsResponse credentials
    ) {
    }

    private record ShopperIdentity(
        String subjectId,
        String sessionId
    ) {
    }
}
