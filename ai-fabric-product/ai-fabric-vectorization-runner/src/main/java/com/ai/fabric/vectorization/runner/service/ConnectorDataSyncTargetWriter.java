package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.vectorization.runner.config.VectorizationRunnerProperties;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.ai.fabric.vectorization.model.VectorizationTargetWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class ConnectorDataSyncTargetWriter {

    private static final Set<String> INDEXING_WORK_STATUSES = Set.of(
        "COMMIT_PENDING",
        "PENDING",
        "PROCESSING",
        "COMPLETED",
        "SUPERSEDED",
        "DEAD_LETTER"
    );

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final VectorizationRunnerProperties properties;

    public ConnectorDataSyncTargetWriter(ObjectMapper objectMapper, VectorizationRunnerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public VectorizationTargetWriteResult upsertBatch(VectorizationExecutionBundle bundle,
                                                      String entityType,
                                                      List<VectorizationMappedRecord> records) throws Exception {
        if (records == null || records.isEmpty()) {
            return new VectorizationTargetWriteResult(0, 0);
        }
        TargetConnectionDescriptor target = bundle.targetConnection();
        if (target == null || !StringUtils.hasText(target.baseUrl()) || !StringUtils.hasText(target.batchPath())) {
            throw new IllegalArgumentException("Vectorization target is not configured.");
        }

        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode trace = body.putObject("trace");
        trace.put("requestId", bundle.runId() + "-" + entityType);
        JsonNode verifiedAuthContext = bundle.targetVerifiedAuthContext();
        if (verifiedAuthContext == null || !verifiedAuthContext.isObject() || verifiedAuthContext.isEmpty()) {
            throw new IllegalStateException("Vectorization target verified auth context is not configured.");
        }
        trace.set("authContext", verifiedAuthContext.deepCopy());
        ObjectNode metadata = trace.putObject("metadata");
        metadata.put("deploymentId", bundle.deploymentId());
        metadata.put("runId", bundle.runId());
        metadata.put("entityType", entityType);

        ArrayNode operations = body.putArray("operations");
        for (VectorizationMappedRecord record : records) {
            boolean delete = deleteRecord(record);
            ObjectNode operation = operations.addObject();
            operation.put("type", delete ? "DELETE" : "UPSERT");
            operation.put("vectorSpace", targetVectorSpace(record, entityType));
            operation.put("id", record.logicalEntityId());
            if (!delete) {
                operation.set("entity", objectMapper.valueToTree(record.entity()));
            }
            operation.set("metadata", objectMapper.valueToTree(record.metadata()));
            ObjectNode identity = operation.putObject("identity");
            identity.put("sourceRecordId", record.sourceRecordId());
            if (StringUtils.hasText(record.sourceRecordVersion())) {
                identity.put("sourceRecordVersion", record.sourceRecordVersion());
            }
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri(target))
            .timeout(properties.requestTimeout())
            .header("Accept", "application/json")
            .header("Content-Type", "application/json");
        if (StringUtils.hasText(target.authHeader()) && StringUtils.hasText(target.apiKey())) {
            requestBuilder.header(target.authHeader().trim(), target.apiKey().trim());
        }

        HttpResponse<String> response = httpClient.send(
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8)).build(),
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        ParsedResponse parsed = parseResponse(response);
        if (!parsed.failures().isEmpty()) {
            throw new DataSyncTargetWriteException(
                response.statusCode(),
                count(bodyJson(parsed), "succeededOperations"),
                resolvedFailedCount(bodyJson(parsed), parsed.failures().size()),
                parsed.providerRequestId(),
                parsed.failures()
            );
        }
        JsonNode bodyJson = bodyJson(parsed);
        JsonNode succeededNode = bodyJson.path("succeededOperations");
        JsonNode failedNode = bodyJson.path("failedOperations");
        if (!succeededNode.canConvertToInt() || !failedNode.canConvertToInt()) {
            throw new IllegalStateException("Vectorization target did not return structured batch counts.");
        }
        int succeeded = succeededNode.asInt();
        int failed = failedNode.asInt();
        if (succeeded < 0 || failed < 0 || succeeded + failed > records.size()) {
            throw new IllegalStateException("Vectorization target returned invalid batch counts.");
        }
        return new VectorizationTargetWriteResult(succeeded, failed);
    }

    public DataSyncWorkStatus readWorkStatus(
        VectorizationExecutionBundle bundle,
        String workId
    ) throws Exception {
        TargetConnectionDescriptor target = bundle.targetConnection();
        if (target == null
            || !StringUtils.hasText(target.baseUrl())
            || !StringUtils.hasText(target.workStatusPath())
            || !StringUtils.hasText(target.authHeader())
            || !StringUtils.hasText(target.apiKey())
            || !StringUtils.hasText(target.privateAuthorizationHeader())
            || !StringUtils.hasText(target.privateAuthorization())) {
            throw reconciliationFailure(
                0,
                "INDEXING_WORK_STATUS_NOT_CONFIGURED",
                "Indexing work reconciliation is not configured.",
                workId,
                null,
                null,
                null
            );
        }
        if (!StringUtils.hasText(workId) || !workId.trim().matches("[1-9][0-9]*")) {
            throw reconciliationFailure(
                0,
                "INVALID_INDEXING_WORK_ID",
                "Indexing work ID is invalid.",
                workId,
                null,
                null,
                null
            );
        }

        String pathTemplate = target.workStatusPath().trim();
        if (!pathTemplate.contains("{workId}")) {
            throw reconciliationFailure(
                0,
                "INDEXING_WORK_STATUS_NOT_CONFIGURED",
                "Indexing work status path is invalid.",
                workId,
                null,
                null,
                null
            );
        }
        String path = pathTemplate.replace("{workId}", workId.trim());
        HttpRequest request = HttpRequest.newBuilder(targetUri(target, path))
            .timeout(properties.requestTimeout())
            .header("Accept", "application/json")
            .header(target.authHeader().trim(), target.apiKey().trim())
            .header(
                target.privateAuthorizationHeader().trim(),
                target.privateAuthorization().trim()
            )
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        JsonNode responseBody = readWorkStatusBody(response, workId);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String errorCode = switch (response.statusCode()) {
                case 401, 403 -> "INDEXING_WORK_ACCESS_DENIED";
                case 404 -> "INDEXING_WORK_NOT_FOUND";
                case 503 -> "INDEXING_WORK_STATUS_UNAVAILABLE";
                default -> "INDEXING_WORK_STATUS_HTTP_" + response.statusCode();
            };
            String structuredCode = firstText(responseBody.path("errorCode"));
            throw reconciliationFailure(
                response.statusCode(),
                StringUtils.hasText(structuredCode) ? structuredCode : errorCode,
                "Indexing work status request failed.",
                workId,
                null,
                null,
                null
            );
        }
        if (!responseBody.isObject()
            || !responseBody.path("success").asBoolean(false)) {
            throw reconciliationFailure(
                response.statusCode(),
                "INDEXING_WORK_STATUS_INVALID",
                "Indexing work status response is invalid.",
                workId,
                null,
                null,
                null
            );
        }

        String returnedWorkId = firstText(responseBody.path("workId"));
        String status = firstText(responseBody.path("status"));
        String entityType = firstText(responseBody.path("entityType"));
        String entityId = firstText(responseBody.path("entityId"));
        if (!workId.trim().equals(returnedWorkId)
            || !StringUtils.hasText(status)
            || !INDEXING_WORK_STATUSES.contains(
                status.toUpperCase(Locale.ROOT)
            )
            || !StringUtils.hasText(entityType)
            || !StringUtils.hasText(entityId)) {
            throw reconciliationFailure(
                response.statusCode(),
                "INDEXING_WORK_STATUS_INVALID",
                "Indexing work status response is incomplete.",
                workId,
                status,
                entityType,
                entityId
            );
        }
        return new DataSyncWorkStatus(
            returnedWorkId,
            status.toUpperCase(Locale.ROOT),
            entityType,
            entityId,
            nullableSafeText(firstText(responseBody.path("errorCode"))),
            nonNegativeInt(responseBody.path("retryCount")),
            nonNegativeInt(responseBody.path("maxRetries"))
        );
    }

    private JsonNode readWorkStatusBody(
        HttpResponse<String> response,
        String workId
    ) {
        if (!StringUtils.hasText(response.body())) {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                throw reconciliationFailure(
                    response.statusCode(),
                    "INDEXING_WORK_STATUS_EMPTY",
                    "Indexing work status response is empty.",
                    workId,
                    null,
                    null,
                    null
                );
            }
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(response.body());
        } catch (Exception ignored) {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                throw reconciliationFailure(
                    response.statusCode(),
                    "INDEXING_WORK_STATUS_MALFORMED",
                    "Indexing work status response is malformed.",
                    workId,
                    null,
                    null,
                    null
                );
            }
            return objectMapper.createObjectNode();
        }
    }

    private ParsedResponse parseResponse(HttpResponse<String> response) {
        String providerRequestId = response.headers().firstValue("X-Request-ID")
            .or(() -> response.headers().firstValue("X-Correlation-ID"))
            .map(this::safeText)
            .orElse(null);
        if (!StringUtils.hasText(response.body())) {
            DataSyncTargetFailure failure = syntheticFailure(
                response.statusCode(),
                "EMPTY_RESPONSE",
                "Data Sync target returned an empty response body.",
                providerRequestId
            );
            return new ParsedResponse(null, providerRequestId, List.of(failure));
        }

        JsonNode bodyJson;
        try {
            bodyJson = objectMapper.readTree(response.body());
        } catch (Exception ignored) {
            DataSyncTargetFailure failure = syntheticFailure(
                response.statusCode(),
                "MALFORMED_RESPONSE",
                "Data Sync target returned a malformed response body.",
                providerRequestId
            );
            return new ParsedResponse(null, providerRequestId, List.of(failure));
        }
        providerRequestId = firstText(
            bodyJson.path("providerRequestId"),
            objectMapper.getNodeFactory().textNode(providerRequestId)
        );

        List<DataSyncTargetFailure> failures = new ArrayList<>();
        JsonNode results = bodyJson.path("results");
        if (results.isArray()) {
            for (JsonNode result : results) {
                if (result != null && !result.path("success").asBoolean(true)) {
                    failures.add(operationFailure(
                        response.statusCode(),
                        result,
                        bodyJson,
                        providerRequestId
                    ));
                }
            }
        }
        boolean httpSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
        boolean envelopeSuccess = bodyJson.path("success").asBoolean(false);
        if (failures.isEmpty() && (!httpSuccess || !envelopeSuccess)) {
            failures.add(operationFailure(
                response.statusCode(),
                bodyJson,
                bodyJson,
                providerRequestId
            ));
        }
        return new ParsedResponse(bodyJson, providerRequestId, List.copyOf(failures));
    }

    private DataSyncTargetFailure operationFailure(int httpStatus,
                                                   JsonNode failure,
                                                   JsonNode envelope,
                                                   String providerRequestId) {
        JsonNode metadata = failure.path("metadata");
        String errorCode = firstText(failure.path("errorCode"), envelope.path("errorCode"));
        if (!StringUtils.hasText(errorCode)) {
            errorCode = httpStatus >= 200 && httpStatus < 300
                ? "UNSTRUCTURED_FAILURE"
                : "HTTP_" + httpStatus;
        }
        String indexingWorkId = firstText(metadata.path("indexingWorkId"), failure.path("indexingWorkId"));
        String indexingStatus = firstText(metadata.path("indexingStatus"), failure.path("indexingStatus"));
        boolean durableHandoff = metadata.path("durableHandoffAccepted").asBoolean(false)
            || failure.path("durableHandoffAccepted").asBoolean(false)
            || (StringUtils.hasText(indexingWorkId)
                && ("INDEXING_RETRYABLE".equals(errorCode) || "INDEXING_PERMANENT".equals(errorCode)));
        DataSyncRetryDisposition disposition = retryDisposition(
            errorCode,
            indexingWorkId,
            durableHandoff
        );
        return new DataSyncTargetFailure(
            httpStatus,
            safeText(errorCode),
            safeText(firstNonBlank(
                failure.path("message").asText(null),
                envelope.path("message").asText(null),
                "Data Sync operation failed."
            )),
            nullableSafeText(firstNonBlank(
                failure.path("vectorSpace").asText(null),
                envelope.path("vectorSpace").asText(null)
            )),
            nullableSafeText(firstNonBlank(
                failure.path("id").asText(null),
                envelope.path("id").asText(null)
            )),
            nullableSafeText(indexingWorkId),
            nullableSafeText(indexingStatus),
            disposition,
            durableHandoff,
            nullableSafeText(providerRequestId)
        );
    }

    private DataSyncRetryDisposition retryDisposition(String errorCode,
                                                      String indexingWorkId,
                                                      boolean durableHandoff) {
        return switch (errorCode) {
            case "INVALID_REQUEST", "BATCH_TOO_LARGE", "PROJECTION_REJECTED" ->
                DataSyncRetryDisposition.PERMANENT_INPUT;
            case "ACCESS_DENIED" ->
                DataSyncRetryDisposition.IDENTITY_OR_POLICY_CHANGE_REQUIRED;
            case "VECTOR_SPACE_NOT_FOUND", "VECTOR_SPACE_NOT_INDEXABLE" ->
                DataSyncRetryDisposition.CONTRACT_DRIFT;
            case "INDEXING_SUBMISSION_FAILED" ->
                DataSyncRetryDisposition.SAFE_RESUBMIT;
            case "INDEXING_RETRYABLE" ->
                durableHandoff && StringUtils.hasText(indexingWorkId)
                    ? DataSyncRetryDisposition.RECONCILE_DURABLE_WORK
                    : DataSyncRetryDisposition.OPERATOR_REVIEW;
            case "INDEXING_PERMANENT" ->
                DataSyncRetryDisposition.OPERATOR_REVIEW;
            default -> DataSyncRetryDisposition.UNKNOWN;
        };
    }

    private DataSyncTargetFailure syntheticFailure(int httpStatus,
                                                   String errorCode,
                                                   String message,
                                                   String providerRequestId) {
        return new DataSyncTargetFailure(
            httpStatus,
            errorCode,
            message,
            null,
            null,
            null,
            null,
            DataSyncRetryDisposition.UNKNOWN,
            false,
            nullableSafeText(providerRequestId)
        );
    }

    private JsonNode bodyJson(ParsedResponse parsed) {
        return parsed.bodyJson() == null ? objectMapper.createObjectNode() : parsed.bodyJson();
    }

    private int count(JsonNode bodyJson, String field) {
        JsonNode value = bodyJson.path(field);
        return value.canConvertToInt() ? Math.max(0, value.asInt()) : 0;
    }

    private int resolvedFailedCount(JsonNode bodyJson, int classifiedFailures) {
        JsonNode value = bodyJson.path("failedOperations");
        return value.canConvertToInt()
            ? Math.max(classifiedFailures, Math.max(0, value.asInt()))
            : classifiedFailures;
    }

    private int nonNegativeInt(JsonNode value) {
        return value != null && value.canConvertToInt()
            ? Math.max(0, value.asInt())
            : 0;
    }

    private String firstText(JsonNode... values) {
        if (values == null) {
            return null;
        }
        for (JsonNode value : values) {
            if (value != null && !value.isMissingNode() && !value.isNull()) {
                String text = value.asText(null);
                if (StringUtils.hasText(text)) {
                    return safeText(text);
                }
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String nullableSafeText(String value) {
        return StringUtils.hasText(value) ? safeText(value) : null;
    }

    private String safeText(String value) {
        String safe = value == null
            ? ""
            : value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return safe.length() <= 240 ? safe : safe.substring(0, 240);
    }

    private String targetVectorSpace(VectorizationMappedRecord record, String fallbackEntityType) {
        return StringUtils.hasText(record.entityType()) ? record.entityType().trim() : fallbackEntityType;
    }

    private boolean deleteRecord(VectorizationMappedRecord record) {
        return truthy(record.entity(), "deleted")
            || truthy(record.entity(), "tombstone")
            || truthy(record.metadata(), "deleted")
            || truthy(record.metadata(), "tombstone");
    }

    private boolean truthy(Map<String, Object> values, String key) {
        if (values == null || !values.containsKey(key)) {
            return false;
        }
        Object value = values.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value instanceof String text && "true".equalsIgnoreCase(text.trim());
    }

    private URI targetUri(TargetConnectionDescriptor target) {
        return targetUri(target, target.batchPath());
    }

    private URI targetUri(TargetConnectionDescriptor target, String targetPath) {
        String baseUrl = target.baseUrl().endsWith("/")
            ? target.baseUrl().substring(0, target.baseUrl().length() - 1)
            : target.baseUrl();
        String path = targetPath.startsWith("/")
            ? targetPath
            : "/" + targetPath;
        return URI.create(baseUrl + path);
    }

    private DataSyncWorkReconciliationException reconciliationFailure(
        int httpStatus,
        String errorCode,
        String message,
        String workId,
        String status,
        String entityType,
        String entityId
    ) {
        return new DataSyncWorkReconciliationException(
            httpStatus,
            safeText(errorCode),
            safeText(message),
            nullableSafeText(workId),
            nullableSafeText(status),
            nullableSafeText(entityType),
            nullableSafeText(entityId)
        );
    }

    private record ParsedResponse(
        JsonNode bodyJson,
        String providerRequestId,
        List<DataSyncTargetFailure> failures
    ) {
    }
}
