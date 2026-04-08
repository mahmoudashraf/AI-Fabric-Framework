package com.ai.fabric.vectorization.runner.service;

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

@Component
public class ConnectorDataSyncTargetWriter {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ConnectorDataSyncTargetWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        trace.put("userId", "vectorization-runner");
        trace.put("sessionId", bundle.runId());
        trace.put("requestId", bundle.runId() + "-" + entityType);
        ObjectNode metadata = trace.putObject("metadata");
        metadata.put("deploymentId", bundle.deploymentId());
        metadata.put("runId", bundle.runId());
        metadata.put("entityType", entityType);

        ArrayNode operations = body.putArray("operations");
        for (VectorizationMappedRecord record : records) {
            ObjectNode operation = operations.addObject();
            operation.put("type", "UPSERT");
            operation.put("vectorSpace", entityType);
            operation.put("id", record.logicalEntityId());
            operation.set("entity", objectMapper.valueToTree(record.entity()));
            operation.set("metadata", objectMapper.valueToTree(record.metadata()));
            ObjectNode identity = operation.putObject("identity");
            identity.put("sourceRecordId", record.sourceRecordId());
            if (StringUtils.hasText(record.sourceRecordVersion())) {
                identity.put("sourceRecordVersion", record.sourceRecordVersion());
            }
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(targetUri(target))
            .timeout(Duration.ofSeconds(60))
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
        int succeeded = bodyJson.path("succeededOperations").asInt(records.size());
        int failed = bodyJson.path("failedOperations").asInt(Math.max(records.size() - succeeded, 0));
        return new VectorizationTargetWriteResult(succeeded, failed);
    }

    private URI targetUri(TargetConnectionDescriptor target) {
        String baseUrl = target.baseUrl().endsWith("/") ? target.baseUrl().substring(0, target.baseUrl().length() - 1) : target.baseUrl();
        String path = target.batchPath().startsWith("/") ? target.batchPath() : "/" + target.batchPath();
        return URI.create(baseUrl + path);
    }
}
