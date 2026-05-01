package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.PublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.platform.backend.deployment.model.PublicDeploymentAccessSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.RuntimePublicTokenSigningService;
import com.ai.fabric.platform.backend.tenant.service.PlatformCustomerConsumerService;
import com.ai.fabric.platform.backend.thinker.service.ThinkerResolverService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.ArrayList;
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
    private static final String THINKER_RUNTIME_MODE = "thinker";
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(30);
    private static final Pattern SAFE_SESSION_ID = Pattern.compile("^[A-Za-z0-9._:-]{8,120}$");
    private static final int MAX_CONTEXT_TEXT_LENGTH = 240;
    private static final int MAX_ATTACHMENT_METADATA_ENTRIES = 12;

    private final PlatformCustomerConsumerService platformCustomerConsumerService;
    private final PublicProvisioningApiService publicProvisioningApiService;
    private final PlatformSecretService platformSecretService;
    private final RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService;
    private final RuntimePublicTokenSigningService runtimePublicTokenSigningService;
    private final ThinkerResolverService thinkerResolverService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PublicConsumerBridgeChatService(PlatformCustomerConsumerService platformCustomerConsumerService,
                                           PublicProvisioningApiService publicProvisioningApiService,
                                           PlatformSecretService platformSecretService,
                                           RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService,
                                           RuntimePublicTokenSigningService runtimePublicTokenSigningService,
                                           ThinkerResolverService thinkerResolverService,
                                           ObjectMapper objectMapper) {
        this.platformCustomerConsumerService = platformCustomerConsumerService;
        this.publicProvisioningApiService = publicProvisioningApiService;
        this.platformSecretService = platformSecretService;
        this.runtimePrivateAssertionSigningService = runtimePrivateAssertionSigningService;
        this.runtimePublicTokenSigningService = runtimePublicTokenSigningService;
        this.thinkerResolverService = thinkerResolverService;
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
        boolean thinkerModeRequest = thinkerResolverService != null && thinkerResolverService.isThinkerModeRequest(body);
        ObjectNode thinkerCaptureRequest = thinkerModeRequest ? body.deepCopy() : null;
        if (thinkerModeRequest) {
            if (!thinkerResolverService.isThinkerEnabledForDeployment(resolved.deployment().getId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Thinker is disabled for this deployment.");
            }
            body.put("mode", THINKER_RUNTIME_MODE);
        }
        JsonNode response = sendJson(
            resolved,
            "POST",
            "/api/chat/me/query",
            body,
            shopperSessionId,
            List.of(SCOPE_CHAT_QUERY)
        );
        if (thinkerModeRequest) {
            thinkerResolverService.captureRuntimeResponse(resolved.deployment(), resolved.consumerId(), thinkerCaptureRequest, response, shopperSessionId)
                .ifPresent(summary -> attachThinkerSession(response, summary.id(), summary.status(), summary.recommendation()));
        }
        return response;
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

    private void attachThinkerSession(JsonNode response, String sessionId, String status, String recommendation) {
        if (!(response instanceof ObjectNode objectNode)) {
            return;
        }
        ObjectNode thinker = objectMapper.createObjectNode();
        thinker.put("sessionId", sessionId);
        thinker.put("status", status);
        if (StringUtils.hasText(recommendation)) {
            thinker.put("recommendation", recommendation);
        }
        objectNode.set("thinkerSession", thinker);
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
                "END_USER",
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                "TRUSTED_BACKEND",
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
                "END_USER",
                "PUBLIC_RUNTIME_AUTHENTICATED",
                "PUBLIC_BROWSER",
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
            copyAttachments(request, body);
            appendStorefrontContextAttachment(request, body);
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
            copyAttachments(request, body);
            appendStorefrontContextAttachment(request, body);
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

    private void copyAttachments(JsonNode source, ObjectNode target) {
        if (source == null || !source.isObject()) {
            return;
        }
        JsonNode rawAttachments = source.get("attachments");
        if (rawAttachments == null || !rawAttachments.isArray()) {
            return;
        }
        ArrayNode attachments = objectMapper.createArrayNode();
        for (JsonNode rawAttachment : rawAttachments) {
            ObjectNode sanitized = sanitizeAttachment(rawAttachment);
            if (sanitized != null && !sanitized.isEmpty()) {
                attachments.add(sanitized);
            }
        }
        if (!attachments.isEmpty()) {
            target.set("attachments", attachments);
        }
    }

    private void appendStorefrontContextAttachment(JsonNode source, ObjectNode target) {
        ObjectNode attachment = storefrontContextAttachment(source);
        if (attachment == null || attachment.isEmpty()) {
            return;
        }
        ArrayNode attachments = objectMapper.createArrayNode();
        JsonNode existing = target.get("attachments");
        if (existing != null && existing.isArray()) {
            attachments.addAll((ArrayNode) existing);
        }
        attachments.add(attachment);
        target.set("attachments", attachments);
    }

    private ObjectNode storefrontContextAttachment(JsonNode source) {
        if (source == null || !source.isObject()) {
            return null;
        }
        JsonNode rawContext = source.get("storefrontContext");
        if (rawContext == null || !rawContext.isObject()) {
            return null;
        }
        ObjectNode metadata = objectMapper.createObjectNode();
        copyLimitedTextField(rawContext, metadata, "pageType");
        copyLimitedTextField(rawContext, metadata, "pageTitle");

        JsonNode rawProduct = rawContext.get("product");
        if (rawProduct != null && rawProduct.isObject()) {
            copyLimitedTextField(rawProduct, metadata, "id", "productId");
            copyLimitedTextField(rawProduct, metadata, "handle", "productHandle");
            copyLimitedTextField(rawProduct, metadata, "title", "productTitle");
            copyLimitedTextField(rawProduct, metadata, "vendor", "productVendor");
            copyLimitedTextField(rawProduct, metadata, "type", "productType");
            copyLimitedTextField(rawProduct, metadata, "priceCents", "productPriceCents");
        }

        JsonNode rawCollection = rawContext.get("collection");
        if (rawCollection != null && rawCollection.isObject()) {
            copyLimitedTextField(rawCollection, metadata, "id", "collectionId");
            copyLimitedTextField(rawCollection, metadata, "handle", "collectionHandle");
            copyLimitedTextField(rawCollection, metadata, "title", "collectionTitle");
        }

        String contentText = storefrontContextSummary(metadata);
        if (!StringUtils.hasText(contentText) && metadata.isEmpty()) {
            return null;
        }
        ObjectNode attachment = objectMapper.createObjectNode();
        attachment.put("source", "shopify-storefront-context");
        if (StringUtils.hasText(contentText)) {
            attachment.put("contentText", contentText);
        }
        if (!metadata.isEmpty()) {
            attachment.set("metadata", metadata);
        }
        return attachment;
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String field) {
        copyLimitedTextField(source, target, field, field);
    }

    private void copyLimitedTextField(JsonNode source, ObjectNode target, String sourceField, String targetField) {
        String value = trimToNull(textOrNull(source, sourceField));
        if (value == null) {
            return;
        }
        if (value.length() > MAX_CONTEXT_TEXT_LENGTH) {
            value = value.substring(0, MAX_CONTEXT_TEXT_LENGTH);
        }
        target.put(targetField, value);
    }

    private ObjectNode sanitizeAttachment(JsonNode rawAttachment) {
        if (rawAttachment == null || !rawAttachment.isObject()) {
            return null;
        }
        ObjectNode sanitized = objectMapper.createObjectNode();
        copyLimitedTextField(rawAttachment, sanitized, "id");
        copyLimitedTextField(rawAttachment, sanitized, "vectorSpace");
        copyLimitedTextField(rawAttachment, sanitized, "contentText");
        copyLimitedTextField(rawAttachment, sanitized, "source");
        copyLimitedTextField(rawAttachment, sanitized, "url");
        copyLimitedTextField(rawAttachment, sanitized, "imageUrl");

        JsonNode rawMetadata = rawAttachment.get("metadata");
        if (rawMetadata != null && rawMetadata.isObject()) {
            ObjectNode metadata = objectMapper.createObjectNode();
            int count = 0;
            for (var entry : iterable(rawMetadata.fields())) {
                if (count >= MAX_ATTACHMENT_METADATA_ENTRIES) {
                    break;
                }
                String key = trimToNull(entry.getKey());
                JsonNode valueNode = entry.getValue();
                if (key == null || valueNode == null || valueNode.isNull() || !valueNode.isValueNode()) {
                    continue;
                }
                String value = trimToNull(valueNode.asText(null));
                if (value == null) {
                    continue;
                }
                if (value.length() > MAX_CONTEXT_TEXT_LENGTH) {
                    value = value.substring(0, MAX_CONTEXT_TEXT_LENGTH);
                }
                metadata.put(key, value);
                count++;
            }
            if (!metadata.isEmpty()) {
                sanitized.set("metadata", metadata);
            }
        }

        return sanitized.isEmpty() ? null : sanitized;
    }

    private String storefrontContextSummary(ObjectNode metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addSummaryPart(parts, metadata, "pageType", "Page type");
        addSummaryPart(parts, metadata, "pageTitle", "Page title");
        addSummaryPart(parts, metadata, "productTitle", "Product");
        addSummaryPart(parts, metadata, "productHandle", "Product handle");
        addSummaryPart(parts, metadata, "productVendor", "Product vendor");
        addSummaryPart(parts, metadata, "productType", "Product type");
        addSummaryPart(parts, metadata, "productPriceCents", "Product price cents");
        addSummaryPart(parts, metadata, "collectionTitle", "Collection");
        addSummaryPart(parts, metadata, "collectionHandle", "Collection handle");
        if (parts.isEmpty()) {
            return null;
        }
        String summary = String.join(". ", parts);
        return summary.length() > MAX_CONTEXT_TEXT_LENGTH
            ? summary.substring(0, MAX_CONTEXT_TEXT_LENGTH)
            : summary;
    }

    private void addSummaryPart(List<String> parts, ObjectNode metadata, String field, String label) {
        String value = trimToNull(textOrNull(metadata, field));
        if (value != null) {
            parts.add(label + ": " + value);
        }
    }

    private <T> Iterable<T> iterable(java.util.Iterator<T> iterator) {
        return () -> iterator;
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
