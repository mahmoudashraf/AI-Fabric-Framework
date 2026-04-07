package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatQueryResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatSuggestionsResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocChatTurnSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocConversationResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocRuntimeAuthContextSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocTraceDocumentSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocTraceSummary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DeploymentPocChatService {

    private static final String RUNTIME_TRUSTED_BACKEND_SECRET_NAME = "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY";
    private static final String RUNTIME_PRIVATE_ASSERTION_SIGNING_SECRET_NAME = "AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY";
    private static final String RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER = "X-AIFABRIC-RUNTIME-API-KEY";
    private static final String RUNTIME_PRIVATE_AUTHORIZATION_HEADER = RuntimePrivateAssertionSigningService.AUTHORIZATION_HEADER;

    private final DeploymentRepository deploymentRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentPocPromptSessionService deploymentPocPromptSessionService;
    private final PlatformAuditService platformAuditService;
    private final PlatformSecretService platformSecretService;
    private final RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DeploymentPocChatService(DeploymentRepository deploymentRepository,
                                    DeploymentAccessService deploymentAccessService,
                                    DeploymentPocPromptSessionService deploymentPocPromptSessionService,
                                    PlatformAuditService platformAuditService,
                                    PlatformSecretService platformSecretService,
                                    RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService,
                                    ObjectMapper objectMapper) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentPocPromptSessionService = deploymentPocPromptSessionService;
        this.platformAuditService = platformAuditService;
        this.platformSecretService = platformSecretService;
        this.runtimePrivateAssertionSigningService = runtimePrivateAssertionSigningService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public DeploymentPocChatQueryResponse query(String deploymentId, DeploymentPocChatQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new ResponseStatusException(BAD_REQUEST, "query is required.");
        }
        DeploymentEntity deployment = getDeployment(deploymentId);
        RuntimeProxyIdentity runtimeIdentity = runtimeProxyIdentity(deployment);

        ObjectNode body = objectMapper.createObjectNode();
        body.put("query", request.query().trim());
        if (StringUtils.hasText(request.conversationId())) {
            body.put("conversationId", request.conversationId().trim());
        }
        if (StringUtils.hasText(request.mode())) {
            body.put("mode", request.mode().trim());
        }
        if (StringUtils.hasText(request.position())) {
            body.put("position", request.position().trim());
        }
        ObjectNode requestPromptPreview = DeploymentPocPromptPreviewSupport.sanitizePromptPreview(objectMapper, request.promptPreview());
        ObjectNode sessionPromptPreview = requestPromptPreview == null
            ? deploymentPocPromptSessionService.effectivePromptPreview(deployment.getId())
            : null;
        ObjectNode promptPreview = requestPromptPreview != null ? requestPromptPreview : sessionPromptPreview;
        String promptPreviewSource = requestPromptPreview != null
            ? "REQUEST"
            : sessionPromptPreview != null ? "SESSION" : "NONE";
        String promptPreviewAdminApiKey = null;
        if (promptPreview != null) {
            promptPreviewAdminApiKey = platformSecretService.resolveSecret("APP_ADMIN_API_KEY");
            if (!StringUtils.hasText(promptPreviewAdminApiKey)) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Prompt preview requires APP_ADMIN_API_KEY to be configured."
                );
            }
            body.set("promptPreview", promptPreview);
        }

        JsonNode response = sendJson(
            deployment,
            "POST",
            "/api/chat/me/query",
            objectMapper.valueToTree(body),
            promptPreviewAdminApiKey,
            runtimeIdentity
        );
        DeploymentPocChatQueryResponse summary = new DeploymentPocChatQueryResponse(
            response.path("success").asBoolean(false),
            textOrNull(response, "message"),
            textOrNull(response, "conversationId"),
            textOrNull(response, "sessionId"),
            response.path("result").isMissingNode() ? objectMapper.nullNode() : response.path("result"),
            summarizeTrace(response.path("result"))
        );

        platformAuditService.record(
            "DEPLOYMENT_POC_CHAT_QUERIED",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "conversationId", summary.conversationId() == null ? "" : summary.conversationId(),
                "queryLength", request.query().trim().length(),
                "promptPreview", promptPreview != null,
                "promptPreviewKeys", promptPreview == null ? 0 : promptPreview.size(),
                "promptPreviewSource", promptPreviewSource
            )
        );

        return summary;
    }

    public DeploymentPocChatSuggestionsResponse suggestions(String deploymentId,
                                                            DeploymentPocChatSuggestionsRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        RuntimeProxyIdentity runtimeIdentity = runtimeProxyIdentity(deployment);
        ObjectNode body = objectMapper.createObjectNode();
        body.put("content", request != null && StringUtils.hasText(request.content()) ? request.content().trim() : "");
        if (request != null && request.maxSuggestions() != null) {
            body.put("maxSuggestions", request.maxSuggestions());
        }

        JsonNode response = sendJson(
            deployment,
            "POST",
            "/api/chat/me/suggestions",
            objectMapper.valueToTree(body),
            null,
            runtimeIdentity
        );

        return new DeploymentPocChatSuggestionsResponse(
            response.path("success").asBoolean(false),
            textOrNull(response, "message"),
            toStringList(response.path("suggestions")),
            textOrNull(response, "raw")
        );
    }

    public DeploymentPocConversationResponse getConversation(String deploymentId, String conversationId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        RuntimeProxyIdentity runtimeIdentity = runtimeProxyIdentity(deployment);
        if (!StringUtils.hasText(conversationId)) {
            throw new ResponseStatusException(BAD_REQUEST, "conversationId is required.");
        }

        JsonNode response = sendJson(
            deployment,
            "GET",
            "/api/chat/me/conversations/" + encodePathSegment(conversationId.trim()),
            null,
            null,
            runtimeIdentity
        );
        return toConversationResponse(response);
    }

    public void deleteConversation(String deploymentId, String conversationId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        RuntimeProxyIdentity runtimeIdentity = runtimeProxyIdentity(deployment);
        if (!StringUtils.hasText(conversationId)) {
            throw new ResponseStatusException(BAD_REQUEST, "conversationId is required.");
        }

        sendJson(
            deployment,
            "DELETE",
            "/api/chat/me/conversations/" + encodePathSegment(conversationId.trim()),
            null,
            null,
            runtimeIdentity
        );

        platformAuditService.record(
            "DEPLOYMENT_POC_CHAT_RESET",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of("conversationId", conversationId.trim())
        );
    }

    public DeploymentPocRuntimeAuthContextSummary getRuntimeAuthContext(String deploymentId) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        RuntimeProxyIdentity runtimeIdentity = runtimeProxyIdentity(deployment);
        JsonNode response = sendAuthContextRequest(deployment, runtimeIdentity);
        return new DeploymentPocRuntimeAuthContextSummary(
            textOrNull(response, "subjectId"),
            textOrNull(response, "subjectType"),
            textOrNull(response, "authMode"),
            textOrNull(response, "callerType"),
            textOrNull(response, "sessionId"),
            textOrNull(response, "deploymentId"),
            textOrNull(response, "customerId"),
            textOrNull(response, "tenantId"),
            textOrNull(response, "issuer"),
            textOrNull(response, "expiresAt"),
            toStringList(response.path("grantedScopes")),
            toStringList(response.path("warnings"))
        );
    }

    private DeploymentEntity getDeployment(String deploymentId) {
        DeploymentEntity deployment = deploymentRepository.findById(deploymentId)
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deployment not found: " + deploymentId));
        deploymentAccessService.requireDeploymentOperatorAccess(deployment);
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment runtime URL is not available. Apply the deployment before using the POC chat console."
            );
        }
        return deployment;
    }

    private JsonNode sendJson(DeploymentEntity deployment,
                              String method,
                              String pathWithQuery,
                              JsonNode body,
                              String adminApiKey,
                              RuntimeProxyIdentity runtimeIdentity) {
        try {
            Map<String, String> runtimeAuthHeaders = runtimeAuthHeaders(deployment, runtimeIdentity);
            if (runtimeAuthHeaders.isEmpty()) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    secureRuntimeAuthRequiredMessage(deployment.getId())
                );
            }
            HttpResponse<String> response = sendRequest(
                runtimeUri(deployment.getRuntimeBaseUrl(), pathWithQuery),
                method,
                body,
                adminApiKey,
                runtimeAuthHeaders
            );
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    verifiedRuntimeRouteRequiredMessage(deployment.getId(), stripQuery(pathWithQuery))
                );
            }

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Runtime POC chat request failed with HTTP " + response.statusCode() + "."
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

    private JsonNode sendAuthContextRequest(DeploymentEntity deployment,
                                            RuntimeProxyIdentity runtimeIdentity) {
        try {
            Map<String, String> runtimeAuthHeaders = runtimeAuthHeaders(deployment, runtimeIdentity);
            if (runtimeAuthHeaders.isEmpty()) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    secureRuntimeAuthRequiredMessage(deployment.getId())
                );
            }
            HttpResponse<String> response = sendRequest(
                runtimeUri(deployment.getRuntimeBaseUrl(), "/api/chat/me/auth-context"),
                "GET",
                null,
                null,
                runtimeAuthHeaders
            );
            if (response.statusCode() == 404) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    verifiedRuntimeRouteRequiredMessage(deployment.getId(), "/api/chat/me/auth-context")
                );
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(
                    BAD_GATEWAY,
                    "Runtime POC auth context request failed with HTTP " + response.statusCode() + "."
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
                                             String adminApiKey,
                                             Map<String, String> runtimeAuthHeaders) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json");
        if (StringUtils.hasText(adminApiKey)) {
            builder.header("X-ADMIN-API-KEY", adminApiKey.trim());
        }
        for (Map.Entry<String, String> entry : runtimeAuthHeaders.entrySet()) {
            if (StringUtils.hasText(entry.getValue())) {
                builder.header(entry.getKey(), entry.getValue().trim());
            }
        }
        if ("GET".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(
                    body == null ? "{}" : objectMapper.writeValueAsString(body)
                ));
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI runtimeUri(String runtimeBaseUrl, String pathWithQuery) {
        try {
            URI base = URI.create(runtimeBaseUrl.trim());
            if (!StringUtils.hasText(base.getScheme()) || !StringUtils.hasText(base.getHost())) {
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
            if (StringUtils.hasText(query)) {
                target.append('?').append(query);
            }
            return URI.create(target.toString());
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid runtime base URL: " + runtimeBaseUrl);
        }
    }

    private DeploymentPocConversationResponse toConversationResponse(JsonNode response) {
        List<DeploymentPocChatTurnSummary> turns = response.path("turns").isArray()
            ? java.util.stream.StreamSupport.stream(response.path("turns").spliterator(), false)
                .map(turn -> new DeploymentPocChatTurnSummary(
                    textOrNull(turn, "timestamp"),
                    textOrNull(turn, "userQuery"),
                    textOrNull(turn, "aiResponse")
                ))
                .toList()
            : List.of();
        return new DeploymentPocConversationResponse(
            textOrNull(response, "id"),
            textOrNull(response.path("authContext"), "subjectId"),
            textOrNull(response, "status"),
            textOrNull(response, "createdAt"),
            textOrNull(response, "lastInteractionAt"),
            turns
        );
    }

    private DeploymentPocTraceSummary summarizeTrace(JsonNode result) {
        if (result == null || result.isMissingNode() || result.isNull() || !result.isObject()) {
            return null;
        }

        List<JsonNode> nodes = flattenResultTree(result);
        List<DeploymentPocTraceDocumentSummary> documents = new ArrayList<>();
        Set<String> documentKeys = new LinkedHashSet<>();
        Set<String> vectorSpaces = new LinkedHashSet<>();
        Set<String> candidateVectorSpaces = new LinkedHashSet<>();

        String executedAction = null;
        String answer = null;
        String actionSummary = null;
        String routingStrategy = null;
        JsonNode actionValidation = null;

        for (JsonNode node : nodes) {
            JsonNode data = node.path("data");
            JsonNode metadata = node.path("metadata");

            executedAction = firstNonBlank(executedAction, textOrNull(data, "action"));
            answer = firstNonBlank(answer, textOrNull(data, "answer"));
            actionSummary = firstNonBlank(actionSummary, textOrNull(data, "summary"));
            routingStrategy = firstNonBlank(routingStrategy, textOrNull(data, "routingStrategy"));

            if ((actionValidation == null || actionValidation.isMissingNode() || actionValidation.isNull())
                && metadata.path("actionParamValidation").isObject()) {
                actionValidation = metadata.path("actionParamValidation").deepCopy();
            }

            collectTextValues(data.path("candidateVectorSpaces"), candidateVectorSpaces);
            collectTextValues(data.path("vectorSpace"), vectorSpaces);
            collectDocuments(data.path("documents"), documents, documentKeys, vectorSpaces);
        }

        List<String> childResultTypes = result.path("children").isArray()
            ? java.util.stream.StreamSupport.stream(result.path("children").spliterator(), false)
                .map(child -> textOrNull(child, "type"))
                .filter(StringUtils::hasText)
                .toList()
            : List.of();

        return new DeploymentPocTraceSummary(
            textOrNull(result, "type"),
            result.path("success").asBoolean(false),
            textOrNull(result, "message"),
            textOrNull(result, "errorCode"),
            executedAction,
            answer,
            actionSummary,
            routingStrategy,
            List.copyOf(vectorSpaces),
            List.copyOf(candidateVectorSpaces),
            childResultTypes,
            documents.size(),
            List.copyOf(documents),
            actionValidation
        );
    }

    private List<JsonNode> flattenResultTree(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        ArrayDeque<JsonNode> queue = new ArrayDeque<>();
        queue.add(root);
        while (!queue.isEmpty()) {
            JsonNode node = queue.removeFirst();
            nodes.add(node);
            JsonNode children = node.path("children");
            if (children.isArray()) {
                for (JsonNode child : children) {
                    if (child != null && child.isObject()) {
                        queue.addLast(child);
                    }
                }
            }
        }
        return List.copyOf(nodes);
    }

    private void collectDocuments(JsonNode documentsNode,
                                  List<DeploymentPocTraceDocumentSummary> documents,
                                  Set<String> documentKeys,
                                  Set<String> vectorSpaces) {
        if (!documentsNode.isArray()) {
            return;
        }
        for (JsonNode document : documentsNode) {
            if (!document.isObject()) {
                continue;
            }
            String id = textOrNull(document, "id");
            String title = textOrNull(document, "title");
            String source = textOrNull(document, "source");
            String url = textOrNull(document, "url");
            JsonNode metadata = document.path("metadata");
            String vectorSpace = textOrNull(metadata, "vectorSpace");
            if (StringUtils.hasText(vectorSpace)) {
                vectorSpaces.add(vectorSpace);
            }
            Double score = document.path("score").isNumber()
                ? document.path("score").asDouble()
                : (document.path("similarity").isNumber() ? document.path("similarity").asDouble() : null);

            String key = String.join("|",
                nullSafe(id),
                nullSafe(title),
                nullSafe(vectorSpace),
                nullSafe(source),
                nullSafe(url)
            );
            if (!documentKeys.add(key)) {
                continue;
            }
            documents.add(new DeploymentPocTraceDocumentSummary(id, title, vectorSpace, score, source, url));
        }
    }

    private void collectTextValues(JsonNode node, Set<String> out) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            if (StringUtils.hasText(node.asText())) {
                out.add(node.asText().trim());
            }
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTextValues(child, out);
            }
        }
    }

    private String firstNonBlank(String current, String candidate) {
        return StringUtils.hasText(current) ? current : candidate;
    }

    private String nullSafe(String value) {
        return Objects.toString(value, "");
    }

    private String actorKey(String deploymentId) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String actorSource = principal == null
            ? "anonymous"
            : principal.actorId() + "|" + principal.role().name() + "|" + principal.authenticationMode();
        return "platform-poc-" + deploymentId + "-" + shortSha(actorSource);
    }

    private RuntimeProxyIdentity runtimeProxyIdentity(DeploymentEntity deployment) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String subjectId = principal != null && StringUtils.hasText(principal.actorId())
            ? principal.actorId().trim()
            : actorKey(deployment.getId());
        String subjectType = principal == null ? "SYSTEM_PROCESS" : "INTERNAL_PLATFORM_USER";
        String issuer = principal == null
            ? "platform-poc:SYSTEM"
            : "platform-poc:" + normalizeIdentityValue(principal.authenticationMode(), "SESSION");
        String sessionSeed = subjectId
            + "|" + deployment.getId()
            + "|" + (principal == null ? "SYSTEM" : normalizeIdentityValue(principal.authenticationMode(), "SESSION"));
        return new RuntimeProxyIdentity(
            subjectId,
            subjectType,
            "PLATFORM_PROXY_SESSION",
            "PLATFORM_PROXY",
            "platform-poc-" + deployment.getId() + "-" + shortSha(sessionSeed),
            issuer
        );
    }

    private Map<String, String> runtimeAuthHeaders(DeploymentEntity deployment, RuntimeProxyIdentity runtimeIdentity) {
        String trustedBackendApiKey = trimToNull(platformSecretService.resolveSecret(RUNTIME_TRUSTED_BACKEND_SECRET_NAME));
        String privateAssertionSigningKey = trimToNull(platformSecretService.resolveSecret(RUNTIME_PRIVATE_ASSERTION_SIGNING_SECRET_NAME));
        if (!StringUtils.hasText(trustedBackendApiKey)
            || !StringUtils.hasText(privateAssertionSigningKey)
            || runtimePrivateAssertionSigningService == null
            || !runtimePrivateAssertionSigningService.isConfigured()) {
            return Map.of();
        }
        String privateAssertion = runtimePrivateAssertionSigningService.toAuthorizationHeaderValue(
            new RuntimePrivateAssertionSigningService.RuntimePrivateAssertionClaims(
                runtimeIdentity.subjectId(),
                runtimeIdentity.subjectType(),
                runtimeIdentity.authMode(),
                runtimeIdentity.callerType(),
                runtimeIdentity.sessionId(),
                deployment.getId(),
                trimToNull(deployment.getCustomerId()),
                trimToNull(deployment.getTenantId()),
                runtimeIdentity.issuer(),
                java.time.Instant.now().plus(Duration.ofMinutes(5)),
                List.of(deployment.getId()),
                List.of("poc:chat", "poc:conversation")
            )
        );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER, trustedBackendApiKey);
        headers.put(RUNTIME_PRIVATE_AUTHORIZATION_HEADER, privateAssertion);
        return Map.copyOf(headers);
    }

    private String secureRuntimeAuthRequiredMessage(String deploymentId) {
        return "Secure POC runtime auth is not configured for deployment '" + deploymentId
            + "'. Configure AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY plus AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY and re-apply the deployment onto the verified /api/chat/me/* runtime surface.";
    }

    private String verifiedRuntimeRouteRequiredMessage(String deploymentId, String path) {
        return "Deployment '" + deploymentId + "' does not expose the verified runtime route '" + path
            + "'. Re-apply the runtime onto the verified /api/chat/me/* surface.";
    }

    private String normalizeIdentityValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private List<String> toStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
            .filter(JsonNode::isTextual)
            .map(JsonNode::asText)
            .toList();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
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

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String encodeQueryValue(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String stripQuery(String pathWithQuery) {
        if (!StringUtils.hasText(pathWithQuery)) {
            return "";
        }
        int queryIndex = pathWithQuery.indexOf('?');
        return queryIndex >= 0 ? pathWithQuery.substring(0, queryIndex) : pathWithQuery;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private record RuntimeProxyIdentity(
        String subjectId,
        String subjectType,
        String authMode,
        String callerType,
        String sessionId,
        String issuer
    ) {
    }
}
