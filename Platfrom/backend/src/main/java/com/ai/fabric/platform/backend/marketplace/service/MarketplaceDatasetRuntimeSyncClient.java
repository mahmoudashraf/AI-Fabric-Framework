package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.security.RuntimePrivateAccessSupport;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class MarketplaceDatasetRuntimeSyncClient {

    private static final String SYSTEM_SUBJECT_ID = "system:platform-marketplace-dataset-sync";
    private static final String SYSTEM_SESSION_ID = "system:marketplace-dataset-sync";
    private static final String SCOPE_DATA_SYNC_UPSERT = "data-sync:upsert";
    private static final String SCOPE_DATA_SYNC_DELETE = "data-sync:delete";
    private static final String SCOPE_VECTORIZATION_VERIFICATION = "vectorization:verification";
    private static final String SYSTEM_ISSUER = "platform-marketplace-dataset-sync";
    private static final Duration RUNTIME_REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_RUNTIME_ATTEMPTS = 5;
    private static final long RETRY_SLEEP_MILLIS = 1500L;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final PlatformSecretService platformSecretService;

    public MarketplaceDatasetRuntimeSyncClient(ObjectMapper objectMapper,
                                               PlatformSecretService platformSecretService) {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = objectMapper;
        this.platformSecretService = platformSecretService;
    }

    public int upsertDocuments(DeploymentEntity deployment,
                               String entityType,
                               String datasetId,
                               String handleRef,
                               String datasetHash,
                               List<MarketplaceDatasetSyncService.DatasetDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }
        int succeeded = 0;
        int batchSize = 50;
        for (int index = 0; index < documents.size(); index += batchSize) {
            List<MarketplaceDatasetSyncService.DatasetDocument> batch = documents.subList(index, Math.min(documents.size(), index + batchSize));
            JsonNode response = postBatch(
                deployment,
                buildBatchBody(deployment, entityType, datasetId, handleRef, datasetHash, batch),
                List.of(SCOPE_DATA_SYNC_UPSERT, SCOPE_VECTORIZATION_VERIFICATION)
            );
            if (!response.path("success").asBoolean(false) || response.path("failedOperations").asInt(0) > 0) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Marketplace dataset sync failed for " + datasetId + ": " + response.path("message").asText("batch failure")
                );
            }
            succeeded += response.path("succeededOperations").asInt(batch.size());
        }
        return succeeded;
    }

    public int deleteDocuments(DeploymentEntity deployment,
                               String entityType,
                               String datasetId,
                               String handleRef,
                               String datasetHash,
                               List<String> documentIds) {
        if (documentIds == null || documentIds.isEmpty()) {
            return 0;
        }
        int succeeded = 0;
        int batchSize = 50;
        for (int index = 0; index < documentIds.size(); index += batchSize) {
            List<String> batch = documentIds.subList(index, Math.min(documentIds.size(), index + batchSize));
            JsonNode response = postBatch(
                deployment,
                buildDeleteBatchBody(deployment, entityType, datasetId, handleRef, datasetHash, batch),
                List.of(SCOPE_DATA_SYNC_DELETE, SCOPE_VECTORIZATION_VERIFICATION)
            );
            if (!response.path("success").asBoolean(false) || response.path("failedOperations").asInt(0) > 0) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Marketplace dataset delete failed for " + datasetId + ": " + response.path("message").asText("batch failure")
                );
            }
            succeeded += response.path("succeededOperations").asInt(batch.size());
        }
        return succeeded;
    }

    private JsonNode postBatch(DeploymentEntity deployment, JsonNode body, List<String> grantedScopes) {
        Map<String, String> runtimeHeaders = RuntimePrivateAccessSupport.issueSystemHeaders(
            platformSecretService,
            objectMapper,
            deployment,
            SYSTEM_SUBJECT_ID,
            SYSTEM_SESSION_ID,
            SYSTEM_ISSUER,
            grantedScopes,
            Duration.ofMinutes(10)
        );
        if (runtimeHeaders.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Secure private-runtime access is not configured for marketplace dataset sync."
            );
        }
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(runtimeUri(deployment, "/api/ai/data-sync/batch"))
            .timeout(RUNTIME_REQUEST_TIMEOUT)
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        runtimeHeaders.forEach(requestBuilder::header);
        HttpRequest request = requestBuilder
            .POST(HttpRequest.BodyPublishers.ofString(write(body), StandardCharsets.UTF_8))
            .build();
        for (int attempt = 1; attempt <= MAX_RUNTIME_ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return StringUtils.hasText(response.body()) ? objectMapper.readTree(response.body()) : objectMapper.createObjectNode();
                }
                if (attempt < MAX_RUNTIME_ATTEMPTS && isRetryableStatus(response.statusCode())) {
                    sleepBeforeRetry();
                    continue;
                }
                throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Marketplace dataset sync runtime batch failed with HTTP " + response.statusCode() + "."
                );
            } catch (ResponseStatusException ex) {
                throw ex;
            } catch (Exception ex) {
                if (attempt < MAX_RUNTIME_ATTEMPTS) {
                    sleepBeforeRetry();
                    continue;
                }
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to reach runtime batch sync endpoint: " + ex.getMessage(), ex);
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Marketplace dataset sync runtime batch exhausted retries.");
    }

    private ObjectNode buildBatchBody(DeploymentEntity deployment,
                                      String entityType,
                                      String datasetId,
                                      String handleRef,
                                      String datasetHash,
                                      List<MarketplaceDatasetSyncService.DatasetDocument> documents) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode trace = root.putObject("trace");
        trace.put("requestId", "marketplace-dataset-sync-" + datasetId + "-" + System.currentTimeMillis());
        trace.set("authContext", buildVerifiedAuthContext(deployment, List.of(
            SCOPE_DATA_SYNC_UPSERT,
            SCOPE_VECTORIZATION_VERIFICATION
        )));
        ObjectNode traceMetadata = trace.putObject("metadata");
        traceMetadata.put("deploymentId", deployment.getId());
        traceMetadata.put("datasetId", datasetId);
        traceMetadata.put("handleRef", handleRef);
        traceMetadata.put("datasetHash", datasetHash);

        ArrayNode operations = root.putArray("operations");
        for (MarketplaceDatasetSyncService.DatasetDocument document : documents) {
            ObjectNode operation = operations.addObject();
            operation.put("type", "UPSERT");
            operation.put("vectorSpace", entityType);
            operation.put("id", document.id());
            operation.put("content", document.content());
            operation.set("metadata", objectMapper.valueToTree(mergeMetadata(document.metadata(), Map.of(
                "knowledgeSourceHandleRef", handleRef,
                "marketplaceDatasetId", datasetId,
                "marketplaceDatasetHash", datasetHash
            ))));
            ObjectNode identity = operation.putObject("identity");
            identity.put("sourceRecordId", document.id());
            identity.put("sourceRecordVersion", datasetHash);
            identity.put("contentFingerprint", document.contentFingerprint());
        }
        return root;
    }

    private ObjectNode buildDeleteBatchBody(DeploymentEntity deployment,
                                            String entityType,
                                            String datasetId,
                                            String handleRef,
                                            String datasetHash,
                                            List<String> documentIds) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode trace = root.putObject("trace");
        trace.put("requestId", "marketplace-dataset-delete-" + datasetId + "-" + System.currentTimeMillis());
        trace.set("authContext", buildVerifiedAuthContext(deployment, List.of(
            SCOPE_DATA_SYNC_DELETE,
            SCOPE_VECTORIZATION_VERIFICATION
        )));
        ObjectNode traceMetadata = trace.putObject("metadata");
        traceMetadata.put("deploymentId", deployment.getId());
        traceMetadata.put("datasetId", datasetId);
        traceMetadata.put("handleRef", handleRef);
        traceMetadata.put("datasetHash", datasetHash);

        ArrayNode operations = root.putArray("operations");
        for (String documentId : documentIds) {
            if (!StringUtils.hasText(documentId)) {
                continue;
            }
            ObjectNode operation = operations.addObject();
            operation.put("type", "DELETE");
            operation.put("vectorSpace", entityType);
            operation.put("id", documentId.trim());
        }
        return root;
    }

    private ObjectNode buildVerifiedAuthContext(DeploymentEntity deployment, List<String> grantedScopes) {
        ObjectNode authContext = objectMapper.createObjectNode();
        authContext.put("subjectId", SYSTEM_SUBJECT_ID);
        authContext.put("subjectType", "SYSTEM_PROCESS");
        authContext.put("authMode", "PRIVATE_RUNTIME_BACKEND_MEDIATED");
        authContext.put("callerType", "SYSTEM_PROCESS");
        authContext.put("sessionId", SYSTEM_SESSION_ID);
        putIfText(authContext, "deploymentId", deployment == null ? null : deployment.getId());
        putIfText(authContext, "customerId", deployment == null ? null : deployment.getCustomerId());
        putIfText(authContext, "tenantId", deployment == null ? null : deployment.getTenantId());
        authContext.put("issuer", "platform-marketplace-dataset-sync");
        ArrayNode scopesNode = authContext.putArray("grantedScopes");
        if (grantedScopes != null) {
            grantedScopes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .forEach(scopesNode::add);
        }
        return authContext;
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> left, Map<String, Object> right) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return merged;
    }

    private URI runtimeUri(DeploymentEntity deployment, String path) {
        String baseUrl = trimToNull(deployment == null ? null : deployment.getRuntimeBaseUrl());
        if (!StringUtils.hasText(baseUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment runtime base URL is not available for marketplace dataset sync.");
        }
        if (baseUrl.contains(".placeholder.local")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Deployment runtime base URL is still a placeholder and is not ready for marketplace dataset sync.");
        }
        try {
            URI base = URI.create(baseUrl);
            return base.resolve(path);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid deployment runtime base URL: " + baseUrl);
        }
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 404
            || statusCode == 502
            || statusCode == 503
            || statusCode == 504;
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(RETRY_SLEEP_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Marketplace dataset sync retry was interrupted.", ex);
        }
    }

    private void putIfText(ObjectNode target, String key, String value) {
        if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
            target.put(key.trim(), value.trim());
        }
    }

    private String write(JsonNode body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize marketplace dataset batch request.", ex);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
