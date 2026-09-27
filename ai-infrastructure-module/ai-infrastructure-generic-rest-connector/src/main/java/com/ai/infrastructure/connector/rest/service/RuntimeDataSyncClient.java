package com.ai.infrastructure.connector.rest.service;

import ai.fabric.datasync.dto.DataSyncBatchRequest;
import ai.fabric.datasync.dto.DataSyncBatchResponse;
import ai.fabric.datasync.dto.DataSyncIdentity;
import ai.fabric.datasync.dto.DataSyncOperation;
import ai.fabric.datasync.dto.DataSyncOperationResponse;
import ai.fabric.datasync.dto.DataSyncOperationType;
import ai.fabric.datasync.dto.DataSyncTrace;
import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.util.UrlBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RuntimeDataSyncClient {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_RUNTIME_RESPONSE_BYTES = 2 * 1024 * 1024;

    private final RestRoutingConfig config;
    private final ObjectMapper objectMapper;
    private final IntegrationStateRepository repository;
    private final HttpClient client;

    public RuntimeDataSyncClient(
        RestRoutingConfig config,
        ObjectMapper objectMapper,
        IntegrationStateRepository repository
    ) {
        this.config = config;
        this.objectMapper = objectMapper;
        this.repository = repository;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public SyncOutcome submit(String sourceId, List<SyncRecord> upserts, List<String> deletes, String vectorSpace) {
        RestRoutingConfig.RuntimeDataSync runtime = requireRuntime();
        List<OperationRef> operations = new ArrayList<>();
        for (SyncRecord record : upserts) {
            DataSyncIdentity identity = new DataSyncIdentity();
            identity.setSourceRecordId(record.id());
            identity.setContentFingerprint(record.fingerprint());
            DataSyncOperation operation = new DataSyncOperation(
                DataSyncOperationType.UPSERT,
                vectorSpace,
                record.id(),
                record.content(),
                record.entity(),
                record.metadata(),
                identity
            );
            operations.add(new OperationRef(record.id(), "UPSERT", operation));
        }
        for (String id : deletes) {
            operations.add(new OperationRef(
                id,
                "DELETE",
                new DataSyncOperation(DataSyncOperationType.DELETE, vectorSpace, id, null, null, null, null)
            ));
        }

        int accepted = 0;
        int completed = 0;
        int failed = 0;
        int completedUpserts = 0;
        int completedDeletes = 0;
        for (int offset = 0; offset < operations.size(); offset += BATCH_SIZE) {
            List<OperationRef> batch = operations.subList(offset, Math.min(offset + BATCH_SIZE, operations.size()));
            DataSyncBatchResponse response = sendBatch(runtime, batch);
            List<DataSyncOperationResponse> results = response.getResults() != null ? response.getResults() : List.of();
            for (int index = 0; index < batch.size(); index++) {
                OperationRef operation = batch.get(index);
                DataSyncOperationResponse result = index < results.size() ? results.get(index) : null;
                if (result == null) {
                    failed++;
                    continue;
                }
                String workId = metadataText(result, "indexingWorkId");
                if (StringUtils.hasText(workId)) {
                    accepted++;
                    repository.recordWork(workId, sourceId, operation.recordId(), operation.operation(),
                        metadataText(result, "indexingStatus", "ACCEPTED"));
                    WorkOutcome work = reconcileWork(runtime, workId);
                    repository.updateWork(workId, work.status(), work.errorCode());
                    if (work.successful()) {
                        completed++;
                        if ("DELETE".equals(operation.operation())) {
                            completedDeletes++;
                        } else {
                            completedUpserts++;
                        }
                    } else {
                        failed++;
                    }
                } else if (Boolean.TRUE.equals(result.getSuccess())) {
                    accepted++;
                    completed++;
                    if ("DELETE".equals(operation.operation())) {
                        completedDeletes++;
                    } else {
                        completedUpserts++;
                    }
                } else {
                    failed++;
                }
            }
        }
        return new SyncOutcome(accepted, completed, failed, completedUpserts, completedDeletes);
    }

    private DataSyncBatchResponse sendBatch(RestRoutingConfig.RuntimeDataSync runtime, List<OperationRef> batch) {
        DataSyncTrace trace = new DataSyncTrace();
        trace.setRequestId("connector-sync-" + UUID.randomUUID());
        trace.setMetadata(Map.of("source", "deployment-integration-connector"));
        DataSyncBatchRequest body = new DataSyncBatchRequest(trace, batch.stream().map(OperationRef::request).toList());

        RuntimeResponse response = send(runtime, "/api/internal/integrations/data-sync/batch", "POST", writeJson(body));
        try {
            DataSyncBatchResponse parsed = objectMapper.readValue(response.body(), DataSyncBatchResponse.class);
            if (response.status() >= 400 && (parsed.getResults() == null || parsed.getResults().isEmpty())) {
                throw new ProviderCallException(
                    response.status() == 401 || response.status() == 403
                        ? ProviderErrorClass.RESOURCE_ACCESS_DENIED : ProviderErrorClass.SERVICE_UNAVAILABLE,
                    response.status(),
                    "Runtime Data Sync rejected the integration batch."
                );
            }
            return parsed;
        } catch (ProviderCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, response.status(), "Runtime Data Sync returned an invalid response.", ex);
        }
    }

    private WorkOutcome reconcileWork(RestRoutingConfig.RuntimeDataSync runtime, String workId) {
        for (int attempt = 0; attempt < runtime.getWorkPollAttempts(); attempt++) {
            RuntimeResponse response = send(
                runtime,
                "/api/internal/integrations/indexing/work/" + encodePath(workId),
                "GET",
                null
            );
            if (response.status() == 404) {
                return new WorkOutcome("NOT_FOUND", false, "INDEXING_WORK_NOT_FOUND");
            }
            if (response.status() < 200 || response.status() >= 300) {
                throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, response.status(), "Runtime indexing-work status is unavailable.");
            }
            try {
                JsonNode root = objectMapper.readTree(response.body());
                String status = root.path("status").asText("");
                boolean terminal = root.path("terminal").asBoolean(false);
                if (terminal) {
                    return new WorkOutcome(
                        status,
                        root.path("successfulTerminal").asBoolean(false),
                        root.path("errorCode").asText(null)
                    );
                }
            } catch (Exception ex) {
                throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, response.status(), "Runtime indexing-work status was invalid.", ex);
            }
            sleep(runtime.getWorkPollIntervalMs());
        }
        return new WorkOutcome("PENDING", false, "INDEXING_WORK_TIMEOUT");
    }

    private RuntimeResponse send(RestRoutingConfig.RuntimeDataSync runtime, String path, String method, String body) {
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(UrlBuilder.join(runtime.getBaseUrl(), path)))
                .timeout(Duration.ofMillis(runtime.getTimeoutMs()))
                .header(runtime.getApiKeyHeader(), runtime.getApiKeyValue());
            if (body != null) {
                request.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
            } else {
                request.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<InputStream> response = client.send(
                request.build(),
                HttpResponse.BodyHandlers.ofInputStream()
            );
            try (InputStream input = response.body()) {
                byte[] bytes = input.readNBytes(MAX_RUNTIME_RESPONSE_BYTES + 1);
                if (bytes.length > MAX_RUNTIME_RESPONSE_BYTES) {
                    throw new ProviderCallException(
                        ProviderErrorClass.MALFORMED_RESPONSE,
                        response.statusCode(),
                        "Runtime indexing response exceeded the connector boundary."
                    );
                }
                return new RuntimeResponse(
                    response.statusCode(),
                    new String(bytes, StandardCharsets.UTF_8)
                );
            }
        } catch (java.net.http.HttpTimeoutException ex) {
            throw new ProviderCallException(ProviderErrorClass.TIMEOUT, 0, "Runtime indexing request timed out.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Runtime indexing request was interrupted.", ex);
        } catch (ProviderCallException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Runtime indexing request failed.", ex);
        }
    }

    private RestRoutingConfig.RuntimeDataSync requireRuntime() {
        RestRoutingConfig.RuntimeDataSync runtime = config.getRuntimeDataSync();
        if (runtime == null || !runtime.isEnabled() || !StringUtils.hasText(runtime.getBaseUrl())
            || !StringUtils.hasText(runtime.getApiKeyHeader()) || !StringUtils.hasText(runtime.getApiKeyValue())) {
            throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Runtime Data Sync service channel is not configured.");
        }
        return runtime;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Failed to serialize runtime Data Sync request.", ex);
        }
    }

    private String metadataText(DataSyncOperationResponse result, String key) {
        return metadataText(result, key, "");
    }

    private String metadataText(DataSyncOperationResponse result, String key, String fallback) {
        Object value = result != null && result.getMetadata() != null
            ? result.getMetadata().get(key)
            : null;
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            return fallback;
        }
        return String.valueOf(value).trim();
    }

    private String encodePath(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(100, millis));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProviderCallException(ProviderErrorClass.SERVICE_UNAVAILABLE, 0, "Indexing-work reconciliation was interrupted.", ex);
        }
    }

    private record OperationRef(String recordId, String operation, DataSyncOperation request) {
    }

    private record WorkOutcome(String status, boolean successful, String errorCode) {
    }

    private record RuntimeResponse(int status, String body) {
    }

    public record SyncRecord(
        String id,
        String content,
        Map<String, Object> entity,
        Map<String, Object> metadata,
        String fingerprint
    ) {
    }

    public record SyncOutcome(
        int accepted,
        int completed,
        int failed,
        int completedUpserts,
        int completedDeletes
    ) {
    }
}
