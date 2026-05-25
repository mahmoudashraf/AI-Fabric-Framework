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
import java.util.List;
import java.util.Map;

@Component
public class ConnectorDataSyncTargetWriter {

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
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Vectorization target returned HTTP " + response.statusCode() + ".");
        }
        JsonNode bodyJson = StringUtils.hasText(response.body()) ? objectMapper.readTree(response.body()) : objectMapper.createObjectNode();
        if (!bodyJson.path("success").asBoolean(false)) {
            throw new IllegalStateException("Vectorization target rejected batch: " + summarizeFailure(bodyJson) + ".");
        }
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

    private String summarizeFailure(JsonNode bodyJson) {
        StringBuilder summary = new StringBuilder();
        String message = bodyJson.path("message").asText("Unknown error");
        summary.append(message);

        if (bodyJson.path("failedOperations").canConvertToInt()) {
            summary.append(" failedOperations=").append(bodyJson.path("failedOperations").asInt());
        }
        if (StringUtils.hasText(bodyJson.path("errorCode").asText(""))) {
            summary.append(" errorCode=").append(bodyJson.path("errorCode").asText().trim());
        }

        JsonNode firstFailure = firstFailedOperation(bodyJson.path("results"));
        if (firstFailure != null) {
            summary.append(" firstFailure=").append(failureLabel(firstFailure));
            String firstFailureMessage = firstFailure.path("message").asText("");
            if (StringUtils.hasText(firstFailureMessage)) {
                summary.append(": ").append(firstFailureMessage.trim());
            }
            String cause = firstFailure.path("metadata").path("cause").asText("");
            if (StringUtils.hasText(cause)) {
                summary.append(" cause=").append(cause.trim());
            }
        }
        return summary.toString();
    }

    private JsonNode firstFailedOperation(JsonNode results) {
        if (results == null || !results.isArray()) {
            return null;
        }
        for (JsonNode result : results) {
            if (result != null && !result.path("success").asBoolean(true)) {
                return result;
            }
        }
        return null;
    }

    private String failureLabel(JsonNode failure) {
        String vectorSpace = failure.path("vectorSpace").asText("");
        String id = failure.path("id").asText("");
        String errorCode = failure.path("errorCode").asText("");
        StringBuilder label = new StringBuilder();
        if (StringUtils.hasText(vectorSpace) || StringUtils.hasText(id)) {
            label.append(StringUtils.hasText(vectorSpace) ? vectorSpace.trim() : "?");
            label.append("/");
            label.append(StringUtils.hasText(id) ? id.trim() : "?");
        } else {
            label.append("operation");
        }
        if (StringUtils.hasText(errorCode)) {
            label.append("[").append(errorCode.trim()).append("]");
        }
        return label.toString();
    }

    private URI targetUri(TargetConnectionDescriptor target) {
        String baseUrl = target.baseUrl().endsWith("/") ? target.baseUrl().substring(0, target.baseUrl().length() - 1) : target.baseUrl();
        String path = target.batchPath().startsWith("/") ? target.batchPath() : "/" + target.batchPath();
        return URI.create(baseUrl + path);
    }
}
