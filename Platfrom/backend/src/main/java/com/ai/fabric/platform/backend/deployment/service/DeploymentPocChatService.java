package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.config.PlatformPocProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentPocAuthPath;
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
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.RuntimePrivateAssertionSigningService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.RuntimePublicTokenSigningService;
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
    private static final String SCOPE_CHAT_QUERY = "chat:query";
    private static final String SCOPE_CHAT_SUGGESTIONS = "chat:suggestions";
    private static final String SCOPE_CHAT_CONVERSATIONS = "chat:conversations";
    private static final String SCOPE_CHAT_PROMPT_PREVIEW = "chat:prompt-preview";
    private static final String METADATA_KEY_TIMING = "timing";
    private static final String METADATA_KEY_RUNTIME_REQUEST_DURATION_MS = "runtimeRequestDurationMs";
    private static final String METADATA_KEY_RUNTIME_AUTH_RESOLUTION_MS = "runtimeAuthResolutionMs";
    private static final String METADATA_KEY_RUNTIME_CONTEXT_BUILD_MS = "runtimeContextBuildMs";
    private static final String METADATA_KEY_RUNTIME_ORCHESTRATION_CALL_DURATION_MS = "runtimeOrchestrationCallDurationMs";
    private static final String METADATA_KEY_RUNTIME_NON_PIPELINE_DURATION_MS = "runtimeNonPipelineDurationMs";
    private static final String METADATA_KEY_PIPELINE_TOTAL_DURATION_MS = "pipelineTotalDurationMs";
    private static final String METADATA_KEY_STEP_DURATIONS_MS = "stepDurationsMs";
    private static final String METADATA_KEY_RAG_TOTAL_PROCESSING_TIME_MS = "ragTotalProcessingTimeMs";
    private static final String METADATA_KEY_EMBEDDING_PROCESSING_TIME_MS = "embeddingProcessingTimeMs";
    private static final String METADATA_KEY_EMBEDDING_PROVIDER_PROCESSING_TIME_MS = "embeddingProviderProcessingTimeMs";
    private static final String METADATA_KEY_EMBEDDING_CACHE_HIT = "embeddingCacheHit";
    private static final String METADATA_KEY_EMBEDDING_PROVIDER_NAME = "embeddingProviderName";
    private static final String METADATA_KEY_EMBEDDING_MODEL = "embeddingModel";
    private static final String METADATA_KEY_EXTRACTION_DIAGNOSTICS = "extractionDiagnostics";
    private static final String METADATA_KEY_EXTRACTION_PROCESSING_TIME_MS = "processingTimeMs";
    private static final String METADATA_KEY_EXTRACTION_PROVIDER_PROCESSING_TIME_MS = "providerProcessingTimeMs";
    private static final String METADATA_KEY_EXTRACTION_LLM_CALLS = "llmCalls";
    private static final String METADATA_KEY_EXTRACTION_ATTEMPTS = "extractionAttempts";
    private static final String METADATA_KEY_EXTRACTION_MODEL = "model";
    private static final String METADATA_KEY_EXTRACTION_PATH = "extractionPath";
    private static final String METADATA_KEY_RESPONSE_GENERATION_PROCESSING_TIME_MS = "responseGenerationProcessingTimeMs";
    private static final String METADATA_KEY_RESPONSE_GENERATION_PROVIDER_PROCESSING_TIME_MS = "responseGenerationProviderProcessingTimeMs";
    private static final String METADATA_KEY_RESPONSE_GENERATION_MODEL = "responseGenerationModel";
    private static final String METADATA_KEY_RESPONSE_GENERATION_PATH = "responseGenerationPath";
    private static final String METADATA_KEY_SEARCH_PROCESSING_TIME_MS = "searchProcessingTimeMs";

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentAccessService deploymentAccessService;
    private final DeploymentPocPromptSessionService deploymentPocPromptSessionService;
    private final PlatformAuditService platformAuditService;
    private final PlatformSecretService platformSecretService;
    private final RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService;
    private final RuntimePublicTokenSigningService runtimePublicTokenSigningService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Duration runtimeTimeout;

    public DeploymentPocChatService(DeploymentRepository deploymentRepository,
                                    DeploymentVersionRepository deploymentVersionRepository,
                                    DeploymentAccessService deploymentAccessService,
                                    DeploymentPocPromptSessionService deploymentPocPromptSessionService,
                                    PlatformAuditService platformAuditService,
                                    PlatformSecretService platformSecretService,
                                    RuntimePrivateAssertionSigningService runtimePrivateAssertionSigningService,
                                    RuntimePublicTokenSigningService runtimePublicTokenSigningService,
                                    ObjectMapper objectMapper,
                                    PlatformPocProperties platformPocProperties) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentAccessService = deploymentAccessService;
        this.deploymentPocPromptSessionService = deploymentPocPromptSessionService;
        this.platformAuditService = platformAuditService;
        this.platformSecretService = platformSecretService;
        this.runtimePrivateAssertionSigningService = runtimePrivateAssertionSigningService;
        this.runtimePublicTokenSigningService = runtimePublicTokenSigningService;
        this.objectMapper = objectMapper;
        this.runtimeTimeout = platformPocProperties.runtimeTimeout();
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    public DeploymentPocChatQueryResponse query(String deploymentId, DeploymentPocChatQueryRequest request) {
        if (request == null || !StringUtils.hasText(request.query())) {
            throw new ResponseStatusException(BAD_REQUEST, "query is required.");
        }
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentPocAuthPath authPath = DeploymentPocAuthPath.defaultValue(request.authPath());

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
        QueryPayload queryPayload = prepareQueryPayload(deployment, authPath, body, request.promptPreview());

        JsonNode response = sendJson(
            deployment,
            "POST",
            "/api/chat/me/query",
            queryPayload.body(),
            authPath,
            queryScopes(queryPayload.promptPreview() != null)
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
                "authPath", authPath.name(),
                "promptPreview", queryPayload.promptPreview() != null,
                "promptPreviewKeys", queryPayload.promptPreview() == null ? 0 : queryPayload.promptPreview().size(),
                "promptPreviewSource", queryPayload.promptPreviewSource()
            )
        );

        return summary;
    }

    public JsonNode widgetQuery(String deploymentId,
                                JsonNode request,
                                DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        ObjectNode body = request != null && request.isObject()
            ? (ObjectNode) request.deepCopy()
            : objectMapper.createObjectNode();
        String query = trimToNull(textOrNull(body, "query"));
        if (!StringUtils.hasText(query)) {
            throw new ResponseStatusException(BAD_REQUEST, "query is required.");
        }
        body.put("query", query);
        QueryPayload queryPayload = prepareQueryPayload(deployment, DeploymentPocAuthPath.defaultValue(authPath), body, null);
        return sendJson(
            deployment,
            "POST",
            "/api/chat/me/query",
            queryPayload.body(),
            DeploymentPocAuthPath.defaultValue(authPath),
            queryScopes(queryPayload.promptPreview() != null)
        );
    }

    public DeploymentPocChatSuggestionsResponse suggestions(String deploymentId,
                                                            DeploymentPocChatSuggestionsRequest request) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        DeploymentPocAuthPath authPath = DeploymentPocAuthPath.defaultValue(request == null ? null : request.authPath());
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
            authPath,
            List.of(SCOPE_CHAT_SUGGESTIONS)
        );

        return new DeploymentPocChatSuggestionsResponse(
            response.path("success").asBoolean(false),
            textOrNull(response, "message"),
            toStringList(response.path("suggestions")),
            textOrNull(response, "raw")
        );
    }

    public JsonNode widgetSuggestions(String deploymentId,
                                      JsonNode request,
                                      DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        JsonNode body = request != null && request.isObject()
            ? request.deepCopy()
            : objectMapper.createObjectNode();
        return sendJson(
            deployment,
            "POST",
            "/api/chat/me/suggestions",
            body,
            DeploymentPocAuthPath.defaultValue(authPath),
            List.of(SCOPE_CHAT_SUGGESTIONS)
        );
    }

    public JsonNode listConversations(String deploymentId,
                                      DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        return sendJson(
            deployment,
            "GET",
            "/api/chat/me/conversations",
            null,
            DeploymentPocAuthPath.defaultValue(authPath),
            List.of(SCOPE_CHAT_CONVERSATIONS)
        );
    }

    public JsonNode widgetConversation(String deploymentId,
                                       String conversationId,
                                       DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        if (!StringUtils.hasText(conversationId)) {
            throw new ResponseStatusException(BAD_REQUEST, "conversationId is required.");
        }
        return sendJson(
            deployment,
            "GET",
            "/api/chat/me/conversations/" + encodePathSegment(conversationId.trim()),
            null,
            DeploymentPocAuthPath.defaultValue(authPath),
            List.of(SCOPE_CHAT_CONVERSATIONS)
        );
    }

    public JsonNode widgetRuntimeAuthContext(String deploymentId,
                                             DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        return sendAuthContextRequest(deployment, DeploymentPocAuthPath.defaultValue(authPath));
    }

    public JsonNode widgetShellConfig(String deploymentId,
                                      DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        return sendJson(
            deployment,
            "GET",
            "/api/chat/me/shell-config",
            null,
            DeploymentPocAuthPath.defaultValue(authPath),
            List.of(SCOPE_CHAT_QUERY)
        );
    }

    public DeploymentPocConversationResponse getConversation(String deploymentId,
                                                             String conversationId,
                                                             DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        if (!StringUtils.hasText(conversationId)) {
            throw new ResponseStatusException(BAD_REQUEST, "conversationId is required.");
        }

        JsonNode response = sendJson(
            deployment,
            "GET",
            "/api/chat/me/conversations/" + encodePathSegment(conversationId.trim()),
            null,
            DeploymentPocAuthPath.defaultValue(authPath),
            List.of(SCOPE_CHAT_CONVERSATIONS)
        );
        return toConversationResponse(response);
    }

    public void deleteConversation(String deploymentId,
                                   String conversationId,
                                   DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        if (!StringUtils.hasText(conversationId)) {
            throw new ResponseStatusException(BAD_REQUEST, "conversationId is required.");
        }

        sendJson(
            deployment,
            "DELETE",
            "/api/chat/me/conversations/" + encodePathSegment(conversationId.trim()),
            null,
            DeploymentPocAuthPath.defaultValue(authPath),
            List.of(SCOPE_CHAT_CONVERSATIONS)
        );

        platformAuditService.record(
            "DEPLOYMENT_POC_CHAT_RESET",
            "DEPLOYMENT",
            deployment.getId(),
            Map.of(
                "conversationId", conversationId.trim(),
                "authPath", DeploymentPocAuthPath.defaultValue(authPath).name()
            )
        );
    }

    public DeploymentPocRuntimeAuthContextSummary getRuntimeAuthContext(String deploymentId,
                                                                        DeploymentPocAuthPath authPath) {
        DeploymentEntity deployment = getDeployment(deploymentId);
        JsonNode response = sendAuthContextRequest(deployment, DeploymentPocAuthPath.defaultValue(authPath));
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
                              DeploymentPocAuthPath authPath,
                              List<String> grantedScopes) {
        try {
            Map<String, String> runtimeAuthHeaders = runtimeAuthHeaders(
                deployment,
                DeploymentPocAuthPath.defaultValue(authPath),
                grantedScopes
            );
            if (runtimeAuthHeaders.isEmpty()) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    pocAuthPathUnavailableMessage(deployment, DeploymentPocAuthPath.defaultValue(authPath))
                );
            }
            HttpResponse<String> response = sendRequest(
                runtimeUri(deployment.getRuntimeBaseUrl(), pathWithQuery),
                method,
                body,
                runtimeAuthHeaders
            );
            if (response.statusCode() == 404) {
                if (isConversationItemPath(pathWithQuery) && isRuntimeConversationNotFound(response.body())) {
                    if ("DELETE".equalsIgnoreCase(method)) {
                        return objectMapper.createObjectNode();
                    }
                    throw new ResponseStatusException(
                        NOT_FOUND,
                        runtimeConversationNotFoundMessage(response.body(), pathWithQuery)
                    );
                }
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
                                            DeploymentPocAuthPath authPath) {
        try {
            Map<String, String> runtimeAuthHeaders = runtimeAuthHeaders(
                deployment,
                DeploymentPocAuthPath.defaultValue(authPath),
                List.of()
            );
            if (runtimeAuthHeaders.isEmpty()) {
                throw new ResponseStatusException(
                    BAD_REQUEST,
                    pocAuthPathUnavailableMessage(deployment, DeploymentPocAuthPath.defaultValue(authPath))
                );
            }
            HttpResponse<String> response = sendRequest(
                runtimeUri(deployment.getRuntimeBaseUrl(), "/api/chat/me/auth-context"),
                "GET",
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
                                             Map<String, String> runtimeAuthHeaders) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target)
            .timeout(runtimeTimeout)
            .header("Accept", "application/json");
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

    private QueryPayload prepareQueryPayload(DeploymentEntity deployment,
                                             DeploymentPocAuthPath authPath,
                                             ObjectNode body,
                                             JsonNode requestedPromptPreview) {
        boolean promptPreviewSupported = authPath == DeploymentPocAuthPath.PLATFORM_PRIVATE;
        ObjectNode requestPromptPreview = promptPreviewSupported
            ? DeploymentPocPromptPreviewSupport.sanitizePromptPreview(objectMapper, requestedPromptPreview)
            : null;
        ObjectNode sessionPromptPreview = promptPreviewSupported && requestPromptPreview == null
            ? deploymentPocPromptSessionService.effectivePromptPreview(deployment.getId())
            : null;
        ObjectNode promptPreview = requestPromptPreview != null ? requestPromptPreview : sessionPromptPreview;
        String promptPreviewSource = !promptPreviewSupported
            ? "UNSUPPORTED_AUTH_PATH"
            : requestPromptPreview != null
            ? "REQUEST"
            : sessionPromptPreview != null ? "SESSION" : "NONE";
        if (promptPreview != null) {
            body.set("promptPreview", promptPreview);
        } else {
            body.remove("promptPreview");
        }
        return new QueryPayload(body, promptPreview, promptPreviewSource);
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
        Long runtimeRequestDurationMs = null;
        Long runtimeAuthResolutionMs = null;
        Long runtimeContextBuildMs = null;
        Long runtimeOrchestrationCallDurationMs = null;
        Long runtimeNonPipelineDurationMs = null;
        Long pipelineDurationMs = null;
        Long extractionProcessingTimeMs = null;
        Long extractionProviderProcessingTimeMs = null;
        Integer extractionLlmCalls = null;
        Integer extractionAttempts = null;
        String extractionModel = null;
        String extractionPath = null;
        Long retrievalProcessingTimeMs = null;
        Long embeddingProcessingTimeMs = null;
        Long embeddingProviderProcessingTimeMs = null;
        Boolean embeddingCacheHit = null;
        String embeddingProviderName = null;
        String embeddingModel = null;
        Long responseGenerationProcessingTimeMs = null;
        Long responseGenerationProviderProcessingTimeMs = null;
        String responseGenerationModel = null;
        String responseGenerationPath = null;
        Long searchProcessingTimeMs = null;
        Map<String, Long> stepDurationsMs = new LinkedHashMap<>();

        for (JsonNode node : nodes) {
            JsonNode data = node.path("data");
            JsonNode metadata = node.path("metadata");
            JsonNode timing = metadata.path(METADATA_KEY_TIMING);
            JsonNode extractionDiagnostics = metadata.path(METADATA_KEY_EXTRACTION_DIAGNOSTICS);
            JsonNode ragResponse = data.path("ragResponse");
            JsonNode ragMetadata = ragResponse.path("metadata");

            executedAction = firstNonBlank(executedAction, textOrNull(data, "action"));
            answer = firstNonBlank(answer, textOrNull(data, "answer"));
            actionSummary = firstNonBlank(actionSummary, textOrNull(data, "summary"));
            routingStrategy = firstNonBlank(routingStrategy, textOrNull(data, "routingStrategy"));

            runtimeRequestDurationMs = firstNonNull(runtimeRequestDurationMs, longOrNull(timing, METADATA_KEY_RUNTIME_REQUEST_DURATION_MS));
            runtimeAuthResolutionMs = firstNonNull(runtimeAuthResolutionMs, longOrNull(timing, METADATA_KEY_RUNTIME_AUTH_RESOLUTION_MS));
            runtimeContextBuildMs = firstNonNull(runtimeContextBuildMs, longOrNull(timing, METADATA_KEY_RUNTIME_CONTEXT_BUILD_MS));
            runtimeOrchestrationCallDurationMs = firstNonNull(runtimeOrchestrationCallDurationMs, longOrNull(timing, METADATA_KEY_RUNTIME_ORCHESTRATION_CALL_DURATION_MS));
            runtimeNonPipelineDurationMs = firstNonNull(runtimeNonPipelineDurationMs, longOrNull(timing, METADATA_KEY_RUNTIME_NON_PIPELINE_DURATION_MS));
            pipelineDurationMs = firstNonNull(pipelineDurationMs, longOrNull(timing, METADATA_KEY_PIPELINE_TOTAL_DURATION_MS));
            extractionProcessingTimeMs = firstNonNull(
                extractionProcessingTimeMs,
                longOrNull(extractionDiagnostics, METADATA_KEY_EXTRACTION_PROCESSING_TIME_MS)
            );
            extractionProviderProcessingTimeMs = firstNonNull(
                extractionProviderProcessingTimeMs,
                longOrNull(extractionDiagnostics, METADATA_KEY_EXTRACTION_PROVIDER_PROCESSING_TIME_MS)
            );
            extractionLlmCalls = firstNonNull(
                extractionLlmCalls,
                integerOrNull(extractionDiagnostics, METADATA_KEY_EXTRACTION_LLM_CALLS)
            );
            extractionAttempts = firstNonNull(
                extractionAttempts,
                integerOrNull(extractionDiagnostics, METADATA_KEY_EXTRACTION_ATTEMPTS)
            );
            extractionModel = firstNonBlank(
                extractionModel,
                textOrNull(extractionDiagnostics, METADATA_KEY_EXTRACTION_MODEL)
            );
            extractionPath = firstNonBlank(
                extractionPath,
                textOrNull(extractionDiagnostics, METADATA_KEY_EXTRACTION_PATH)
            );
            if (stepDurationsMs.isEmpty()) {
                stepDurationsMs.putAll(parseLongMap(timing.path(METADATA_KEY_STEP_DURATIONS_MS)));
            }

            retrievalProcessingTimeMs = firstNonNull(
                retrievalProcessingTimeMs,
                firstNonNull(longOrNull(ragMetadata, METADATA_KEY_RAG_TOTAL_PROCESSING_TIME_MS), longOrNull(ragResponse, "processingTimeMs"))
            );
            embeddingProcessingTimeMs = firstNonNull(embeddingProcessingTimeMs, longOrNull(ragMetadata, METADATA_KEY_EMBEDDING_PROCESSING_TIME_MS));
            embeddingProviderProcessingTimeMs = firstNonNull(
                embeddingProviderProcessingTimeMs,
                longOrNull(ragMetadata, METADATA_KEY_EMBEDDING_PROVIDER_PROCESSING_TIME_MS)
            );
            embeddingCacheHit = firstNonNull(embeddingCacheHit, booleanOrNull(ragMetadata, METADATA_KEY_EMBEDDING_CACHE_HIT));
            embeddingProviderName = firstNonBlank(embeddingProviderName, textOrNull(ragMetadata, METADATA_KEY_EMBEDDING_PROVIDER_NAME));
            embeddingModel = firstNonBlank(embeddingModel, textOrNull(ragMetadata, METADATA_KEY_EMBEDDING_MODEL));
            responseGenerationProcessingTimeMs = firstNonNull(
                responseGenerationProcessingTimeMs,
                longOrNull(metadata, METADATA_KEY_RESPONSE_GENERATION_PROCESSING_TIME_MS)
            );
            responseGenerationProviderProcessingTimeMs = firstNonNull(
                responseGenerationProviderProcessingTimeMs,
                longOrNull(metadata, METADATA_KEY_RESPONSE_GENERATION_PROVIDER_PROCESSING_TIME_MS)
            );
            responseGenerationModel = firstNonBlank(
                responseGenerationModel,
                textOrNull(metadata, METADATA_KEY_RESPONSE_GENERATION_MODEL)
            );
            responseGenerationPath = firstNonBlank(
                responseGenerationPath,
                textOrNull(metadata, METADATA_KEY_RESPONSE_GENERATION_PATH)
            );
            searchProcessingTimeMs = firstNonNull(
                searchProcessingTimeMs,
                firstNonNull(longOrNull(ragMetadata, METADATA_KEY_SEARCH_PROCESSING_TIME_MS), longOrNull(ragResponse, "processingTimeMs"))
            );

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
            runtimeRequestDurationMs,
            runtimeAuthResolutionMs,
            runtimeContextBuildMs,
            runtimeOrchestrationCallDurationMs,
            runtimeNonPipelineDurationMs,
            pipelineDurationMs,
            extractionProcessingTimeMs,
            extractionProviderProcessingTimeMs,
            extractionLlmCalls,
            extractionAttempts,
            extractionModel,
            extractionPath,
            retrievalProcessingTimeMs,
            embeddingProcessingTimeMs,
            embeddingProviderProcessingTimeMs,
            embeddingCacheHit,
            embeddingProviderName,
            embeddingModel,
            responseGenerationProcessingTimeMs,
            responseGenerationProviderProcessingTimeMs,
            responseGenerationModel,
            responseGenerationPath,
            searchProcessingTimeMs,
            Map.copyOf(stepDurationsMs),
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

    private Long firstNonNull(Long current, Long candidate) {
        return current != null ? current : candidate;
    }

    private Integer firstNonNull(Integer current, Integer candidate) {
        return current != null ? current : candidate;
    }

    private Boolean firstNonNull(Boolean current, Boolean candidate) {
        return current != null ? current : candidate;
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && StringUtils.hasText(value.asText())) {
            try {
                return Long.parseLong(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer integerOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        if (value.isNumber()) {
            return value.asInt();
        }
        if (value.isTextual() && StringUtils.hasText(value.asText())) {
            try {
                return Integer.parseInt(value.asText().trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Boolean booleanOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(fieldName)) {
            return null;
        }
        JsonNode value = node.path(fieldName);
        return value.isBoolean() ? value.asBoolean() : null;
    }

    private Map<String, Long> parseLongMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return Map.of();
        }
        Map<String, Long> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            if (entry == null || !StringUtils.hasText(entry.getKey())) {
                return;
            }
            Long parsed = longOrNull(node, entry.getKey());
            if (parsed != null) {
                values.put(entry.getKey(), parsed);
            }
        });
        return values;
    }

    private String actorKey(String deploymentId) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String actorSource = principal == null
            ? "anonymous"
            : principal.actorId() + "|" + principal.role().name() + "|" + principal.authenticationMode();
        return "platform-poc-" + deploymentId + "-" + shortSha(actorSource);
    }

    private RuntimeAuthIdentity runtimePrivateProxyIdentity(DeploymentEntity deployment) {
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
        return new RuntimeAuthIdentity(
            subjectId,
            subjectType,
            "PLATFORM_PROXY_SESSION",
            "PLATFORM_PROXY",
            "platform-poc-" + deployment.getId() + "-" + shortSha(sessionSeed),
            issuer
        );
    }

    private RuntimeAuthIdentity publicAuthenticatedIdentity(DeploymentEntity deployment) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String subjectId = principal != null && StringUtils.hasText(principal.actorId())
            ? principal.actorId().trim()
            : actorKey(deployment.getId());
        String authenticationMode = principal == null
            ? "SYSTEM"
            : normalizeIdentityValue(principal.authenticationMode(), "SESSION");
        String sessionSeed = subjectId + "|" + deployment.getId() + "|PUBLIC_AUTHENTICATED|" + authenticationMode;
        return new RuntimeAuthIdentity(
            subjectId,
            "END_USER",
            "PUBLIC_RUNTIME_AUTHENTICATED",
            "PUBLIC_BROWSER",
            "platform-poc-public-auth-" + deployment.getId() + "-" + shortSha(sessionSeed),
            null
        );
    }

    private RuntimeAuthIdentity publicAnonymousIdentity(DeploymentEntity deployment) {
        PlatformPrincipal principal = PlatformSecurityContext.currentPrincipal();
        String actorSource = principal == null
            ? actorKey(deployment.getId())
            : principal.actorId() + "|" + principal.role().name() + "|" + principal.authenticationMode();
        String sessionId = "anon-platform-poc-" + deployment.getId() + "-" + shortSha(actorSource + "|PUBLIC_ANONYMOUS");
        return new RuntimeAuthIdentity(
            sessionId,
            "ANONYMOUS_SESSION",
            "PUBLIC_RUNTIME_ANONYMOUS",
            "PUBLIC_BROWSER",
            sessionId,
            null
        );
    }

    private Map<String, String> runtimeAuthHeaders(DeploymentEntity deployment,
                                                   DeploymentPocAuthPath authPath,
                                                   List<String> grantedScopes) {
        if (DeploymentPocAuthPath.defaultValue(authPath).usesPublicRuntimeBearer()) {
            return runtimePublicAuthHeaders(deployment, DeploymentPocAuthPath.defaultValue(authPath), grantedScopes);
        }
        return runtimePrivateAuthHeaders(deployment, runtimePrivateProxyIdentity(deployment), grantedScopes);
    }

    private Map<String, String> runtimePrivateAuthHeaders(DeploymentEntity deployment,
                                                          RuntimeAuthIdentity runtimeIdentity,
                                                          List<String> grantedScopes) {
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
                normalizeScopes(grantedScopes)
            )
        );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RUNTIME_TRUSTED_BACKEND_API_KEY_HEADER, trustedBackendApiKey);
        headers.put(RUNTIME_PRIVATE_AUTHORIZATION_HEADER, privateAssertion);
        return Map.copyOf(headers);
    }

    private Map<String, String> runtimePublicAuthHeaders(DeploymentEntity deployment,
                                                         DeploymentPocAuthPath authPath,
                                                         List<String> grantedScopes) {
        PublicRuntimeTokenPolicy tokenPolicy = resolvePublicRuntimeTokenPolicy(deployment, authPath);
        RuntimeAuthIdentity runtimeIdentity = authPath == DeploymentPocAuthPath.PUBLIC_ANONYMOUS
            ? publicAnonymousIdentity(deployment)
            : publicAuthenticatedIdentity(deployment);
        String authorization = runtimePublicTokenSigningService.toAuthorizationHeaderValue(
            new RuntimePublicTokenSigningService.RuntimePublicTokenClaims(
                runtimeIdentity.subjectId(),
                runtimeIdentity.subjectType(),
                runtimeIdentity.authMode(),
                runtimeIdentity.callerType(),
                runtimeIdentity.sessionId(),
                deployment.getId(),
                trimToNull(deployment.getCustomerId()),
                trimToNull(deployment.getTenantId()),
                tokenPolicy.issuer(),
                java.time.Instant.now().plus(Duration.ofMinutes(5)),
                List.of(tokenPolicy.audience()),
                normalizeScopes(grantedScopes)
            )
        );
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(RuntimePublicTokenSigningService.AUTHORIZATION_HEADER, authorization);
        return Map.copyOf(headers);
    }

    private PublicRuntimeTokenPolicy resolvePublicRuntimeTokenPolicy(DeploymentEntity deployment,
                                                                     DeploymentPocAuthPath authPath) {
        DeploymentVersionEntity version = activeVersion(deployment);
        JsonNode securityConfig = readJson(version.getSecurityConfigJson());
        if (!ManagedDeploymentProfileCatalog.publicRuntimeRequested(securityConfig)
            || runtimePublicTokenSigningService == null
            || !runtimePublicTokenSigningService.isConfigured()) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                publicRuntimeAuthNotConfiguredMessage(deployment.getId(), authPath)
            );
        }

        boolean anonymousBootstrapEnabled = ManagedDeploymentProfileCatalog.publicRuntimeBootstrapEnabled(securityConfig);
        if (authPath == DeploymentPocAuthPath.PUBLIC_ANONYMOUS && !anonymousBootstrapEnabled) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                publicAnonymousAuthNotConfiguredMessage(deployment.getId())
            );
        }

        Set<String> acceptedIssuers = csvSet(ManagedDeploymentProfileCatalog.publicRuntimeAcceptedIssuers(securityConfig));
        Set<String> acceptedAudiences = csvSet(ManagedDeploymentProfileCatalog.publicRuntimeAcceptedAudiences(securityConfig));
        String issuer = resolvePublicRuntimeIssuer(securityConfig, acceptedIssuers);
        String audience = resolvePublicRuntimeAudience(deployment, securityConfig, acceptedAudiences);

        if (!acceptedIssuers.isEmpty() && !acceptedIssuers.contains(issuer)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment '" + deployment.getId()
                    + "' public runtime issuer policy does not allow platform POC token simulation. "
                    + "Configure security.publicRuntimeTokenIssuer or align publicRuntimeAcceptedIssuers."
            );
        }
        if (!acceptedAudiences.isEmpty() && !acceptedAudiences.contains(audience)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment '" + deployment.getId()
                    + "' public runtime audience policy does not allow platform POC token simulation. "
                    + "Configure security.publicRuntimeDefaultAudience or align publicRuntimeAcceptedAudiences."
            );
        }

        return new PublicRuntimeTokenPolicy(issuer, audience);
    }

    private DeploymentVersionEntity activeVersion(DeploymentEntity deployment) {
        String activeVersionId = trimToNull(deployment == null ? null : deployment.getActiveVersionId());
        if (!StringUtils.hasText(activeVersionId)) {
            throw new ResponseStatusException(
                BAD_REQUEST,
                "Deployment '" + safeDeploymentId(deployment) + "' does not have an active version for public runtime auth simulation."
            );
        }
        return deploymentVersionRepository.findById(activeVersionId)
            .orElseThrow(() -> new ResponseStatusException(
                BAD_REQUEST,
                "Deployment active version is not available: " + activeVersionId
            ));
    }

    private List<String> queryScopes(boolean promptPreview) {
        return promptPreview
            ? List.of(SCOPE_CHAT_QUERY, SCOPE_CHAT_PROMPT_PREVIEW)
            : List.of(SCOPE_CHAT_QUERY);
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

    private String pocAuthPathUnavailableMessage(DeploymentEntity deployment, DeploymentPocAuthPath authPath) {
        return switch (DeploymentPocAuthPath.defaultValue(authPath)) {
            case PLATFORM_PRIVATE -> secureRuntimeAuthRequiredMessage(safeDeploymentId(deployment));
            case PUBLIC_AUTHENTICATED -> publicRuntimeAuthNotConfiguredMessage(safeDeploymentId(deployment), authPath);
            case PUBLIC_ANONYMOUS -> publicAnonymousAuthNotConfiguredMessage(safeDeploymentId(deployment));
        };
    }

    private String secureRuntimeAuthRequiredMessage(String deploymentId) {
        return "Secure POC runtime auth is not configured for deployment '" + deploymentId
            + "'. Configure AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY plus AI_FABRIC_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY and re-apply the deployment onto the verified /api/chat/me/* runtime surface.";
    }

    private String publicRuntimeAuthNotConfiguredMessage(String deploymentId,
                                                         DeploymentPocAuthPath authPath) {
        return "Deployment '" + deploymentId
            + "' is not configured for signed public runtime bearer-token access required by POC auth path '"
            + DeploymentPocAuthPath.defaultValue(authPath).name()
            + "'. Configure AI_FABRIC_RUNTIME_PUBLIC_TOKEN_SIGNING_KEY plus public runtime security posture and re-apply the runtime.";
    }

    private String publicAnonymousAuthNotConfiguredMessage(String deploymentId) {
        return "Deployment '" + deploymentId
            + "' does not enable public anonymous runtime access for POC simulation. Enable publicRuntimeBootstrapEnabled and re-apply the runtime before using PUBLIC_ANONYMOUS.";
    }

    private String verifiedRuntimeRouteRequiredMessage(String deploymentId, String path) {
        return "Deployment '" + deploymentId + "' does not expose the verified runtime route '" + path
            + "'. Re-apply the runtime onto the verified /api/chat/me/* surface.";
    }

    private boolean isConversationItemPath(String pathWithQuery) {
        String path = stripQuery(pathWithQuery);
        return StringUtils.hasText(path)
            && path.startsWith("/api/chat/me/conversations/")
            && !path.endsWith("/api/chat/me/conversations/");
    }

    private boolean isRuntimeConversationNotFound(String responseBody) {
        JsonNode payload = parseJson(responseBody);
        if (payload == null || !payload.isObject()) {
            return false;
        }
        String error = trimToNull(textOrNull(payload, "error"));
        String errorCode = trimToNull(textOrNull(payload, "errorCode"));
        String message = trimToNull(textOrNull(payload, "message"));
        boolean notFoundError = "NOT_FOUND".equals(error) || "NOT_FOUND".equals(errorCode);
        return notFoundError && StringUtils.hasText(message) && message.startsWith("Conversation not found:");
    }

    private String runtimeConversationNotFoundMessage(String responseBody, String pathWithQuery) {
        JsonNode payload = parseJson(responseBody);
        String message = payload == null ? null : trimToNull(textOrNull(payload, "message"));
        return StringUtils.hasText(message)
            ? message
            : "Conversation not found for verified runtime route '" + stripQuery(pathWithQuery) + "'.";
    }

    private JsonNode parseJson(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            return null;
        }
    }

    private JsonNode readJson(String payload) {
        if (!StringUtils.hasText(payload)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String normalizeIdentityValue(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private Set<String> csvSet(String csv) {
        if (!StringUtils.hasText(csv)) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String token : csv.split(",")) {
            String normalized = trimToNull(token);
            if (StringUtils.hasText(normalized)) {
                values.add(normalized);
            }
        }
        return Set.copyOf(values);
    }

    private String resolvePublicRuntimeIssuer(JsonNode securityConfig, Set<String> acceptedIssuers) {
        String configuredIssuer = trimToNull(ManagedDeploymentProfileCatalog.publicRuntimeTokenIssuer(securityConfig));
        if (StringUtils.hasText(configuredIssuer)) {
            return configuredIssuer;
        }
        if (acceptedIssuers.contains("runtime-public-bootstrap")) {
            return "runtime-public-bootstrap";
        }
        if (acceptedIssuers.size() == 1) {
            return acceptedIssuers.iterator().next();
        }
        return "runtime-public-bootstrap";
    }

    private String resolvePublicRuntimeAudience(DeploymentEntity deployment,
                                                JsonNode securityConfig,
                                                Set<String> acceptedAudiences) {
        String configuredAudience = trimToNull(ManagedDeploymentProfileCatalog.publicRuntimeDefaultAudience(securityConfig));
        if (StringUtils.hasText(configuredAudience)) {
            return configuredAudience;
        }
        String deploymentId = safeDeploymentId(deployment);
        if (acceptedAudiences.contains(deploymentId)) {
            return deploymentId;
        }
        if (acceptedAudiences.size() == 1) {
            return acceptedAudiences.iterator().next();
        }
        return deploymentId;
    }

    private String safeDeploymentId(DeploymentEntity deployment) {
        return trimToNull(deployment == null ? null : deployment.getId()) == null
            ? "unknown-deployment"
            : deployment.getId().trim();
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

    private record QueryPayload(ObjectNode body, ObjectNode promptPreview, String promptPreviewSource) {}

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

    private record RuntimeAuthIdentity(
        String subjectId,
        String subjectType,
        String authMode,
        String callerType,
        String sessionId,
        String issuer
    ) {
    }

    private record PublicRuntimeTokenPolicy(
        String issuer,
        String audience
    ) {
    }
}
