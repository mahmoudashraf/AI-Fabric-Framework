package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.integration.connection.ConnectionDescriptor;
import com.ai.fabric.integration.credential.ResolvedSourceAuthMaterial;
import com.ai.fabric.vectorization.model.TargetConnectionDescriptor;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationRunReason;
import com.ai.fabric.vectorization.model.VectorizationRunnerMode;
import com.ai.fabric.vectorization.runner.config.VectorizationRunnerProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class VectorizationRunnerPlatformClient {

    private final ObjectMapper objectMapper;
    private final VectorizationRunnerProperties properties;
    private final HttpClient httpClient;

    public VectorizationRunnerPlatformClient(ObjectMapper objectMapper, VectorizationRunnerProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    public boolean isConfigured() {
        return StringUtils.hasText(properties.platformBaseUrl()) && StringUtils.hasText(properties.registrationToken());
    }

    public RunnerSession createSession() throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("registrationToken", properties.registrationToken());
        request.put("runnerInstanceId", effectiveRunnerInstanceId());
        request.put("productVersion", properties.productVersion());
        request.put("compatibilityVersion", properties.compatibilityVersion());
        JsonNode response = post("/api/vectorization/runner/session", request);
        return new RunnerSession(
            requiredText(response, "sessionToken"),
            requiredText(response, "deploymentId"),
            text(response, "runnerMode"),
            text(response, "compatibilityStatus")
        );
    }

    public ClaimedRun claimNextRun(String sessionToken) throws Exception {
        JsonNode response = post("/api/vectorization/runner/claim?sessionToken=" + encode(sessionToken), null);
        JsonNode run = response.path("run");
        if (run.isMissingNode() || run.isNull() || !StringUtils.hasText(run.path("id").asText(null))) {
            return null;
        }
        return new ClaimedRun(requiredText(run, "id"), text(run, "reason"), text(run, "status"), text(run, "requestedStatus"));
    }

    public VectorizationExecutionBundle fetchExecutionBundle(String sessionToken, String runId) throws Exception {
        JsonNode response = get("/api/vectorization/runner/runs/" + encode(runId) + "/bundle?sessionToken=" + encode(sessionToken));
        ConnectionDescriptor connectionDescriptor = new ConnectionDescriptor(
            text(response.path("connectionDescriptor"), "connectionId"),
            text(response.path("connectionDescriptor"), "name"),
            text(response.path("connectionDescriptor"), "adapterType"),
            text(response.path("connectionDescriptor"), "authMode"),
            response.path("connectionDescriptor").path("config"),
            response.path("connectionDescriptor").path("discoverySummary")
        );
        ResolvedSourceAuthMaterial sourceAuthMaterial = new ResolvedSourceAuthMaterial(
            toStringMap(response.path("sourceAuth")),
            toStringMap(response.path("localSecretAliases"))
        );
        TargetConnectionDescriptor targetConnection = new TargetConnectionDescriptor(
            text(response.path("targetDescriptor"), "targetType"),
            text(response.path("targetDescriptor"), "baseUrl"),
            text(response.path("targetDescriptor"), "batchPath"),
            text(response.path("targetDescriptor"), "vectorSpacesPath"),
            text(response.path("targetDescriptor"), "authHeader"),
            text(response.path("targetAuth"), "apiKey")
        );
        return new VectorizationExecutionBundle(
            requiredText(response, "deploymentId"),
            requiredText(response, "runId"),
            VectorizationRunReason.valueOf(requiredText(response, "reason")),
            requiredText(response, "planRevisionId"),
            VectorizationRunnerMode.valueOf(requiredText(response, "runnerMode")),
            connectionDescriptor.connectionId(),
            readEntityScope(response.path("entityScope")),
            response.path("mappingConfig"),
            response.path("executionConfig"),
            connectionDescriptor,
            sourceAuthMaterial,
            targetConnection
        );
    }

    public HeartbeatDecision heartbeat(String sessionToken, String runId) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("sessionToken", sessionToken);
        request.put("runId", runId);
        JsonNode response = post("/api/vectorization/runner/heartbeat", request);
        return new HeartbeatDecision(text(response, "requestedStatus"));
    }

    public void reportCheckpoint(String sessionToken,
                                 String runId,
                                 String entityType,
                                 String checkpointType,
                                 String checkpointValue,
                                 JsonNode progress,
                                 JsonNode details) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("sessionToken", sessionToken);
        request.put("runId", runId);
        if (StringUtils.hasText(entityType)) {
            request.put("entityType", entityType);
        }
        request.put("checkpointType", checkpointType);
        if (StringUtils.hasText(checkpointValue)) {
            request.put("checkpointValue", checkpointValue);
        }
        request.set("progress", progress == null ? objectMapper.createObjectNode() : progress);
        request.set("details", details == null ? objectMapper.createObjectNode() : details);
        post("/api/vectorization/runner/checkpoint", request);
    }

    public void reportDiscovery(String sessionToken, String connectionId, VectorizationDiscoveryResult result) throws Exception {
        if (!StringUtils.hasText(connectionId) || result == null) {
            return;
        }
        ObjectNode summary = objectMapper.createObjectNode();
        ObjectNode counts = summary.putObject("countsByEntityType");
        result.countsByEntityType().forEach(counts::put);
        ObjectNode methods = summary.putObject("countMethodByEntityType");
        result.countMethodByEntityType().forEach((key, value) -> methods.put(key, value.name()));

        ObjectNode request = objectMapper.createObjectNode();
        request.put("sessionToken", sessionToken);
        request.put("connectionId", connectionId);
        request.set("discoverySummary", summary);
        post("/api/vectorization/runner/discovery", request);
    }

    public void completeRun(String sessionToken, String runId, String finalStatus, JsonNode progressSummary, JsonNode errorSummary) throws Exception {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("sessionToken", sessionToken);
        request.put("runId", runId);
        request.put("finalStatus", finalStatus);
        request.set("progressSummary", progressSummary == null ? objectMapper.createObjectNode() : progressSummary);
        request.set("errorSummary", errorSummary == null ? objectMapper.createObjectNode() : errorSummary);
        post("/api/vectorization/runner/complete", request);
    }

    private JsonNode get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri(path))
            .timeout(properties.requestTimeout())
            .GET()
            .header("Accept", "application/json")
            .build();
        return send(request);
    }

    private JsonNode post(String path, JsonNode body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri(path))
            .timeout(properties.requestTimeout())
            .header("Accept", "application/json");
        if (body == null) {
            request.POST(HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json");
            request.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)));
        }
        return send(request.build());
    }

    private JsonNode send(HttpRequest request) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw new RunnerAuthException("Runner platform session is unauthorized.");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Runner platform request failed with HTTP " + response.statusCode() + ".");
        }
        return StringUtils.hasText(response.body()) ? objectMapper.readTree(response.body()) : objectMapper.createObjectNode();
    }

    private URI uri(String path) {
        String baseUrl = properties.platformBaseUrl().trim();
        String normalized = path.startsWith("/") ? path : "/" + path;
        return URI.create(baseUrl.replaceAll("/$", "") + normalized);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Runner platform response is missing required field '" + fieldName + "'.");
        }
        return value;
    }

    private String text(JsonNode node, String fieldName) {
        return text(node.path(fieldName));
    }

    private String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() || !StringUtils.hasText(node.asText(null))
            ? null
            : node.asText().trim();
    }

    private List<String> readEntityScope(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return objectMapper.convertValue(node, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
    }

    private Map<String, String> toStringMap(JsonNode node) {
        Map<String, String> values = new LinkedHashMap<>();
        if (node == null || !node.isObject()) {
            return values;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (StringUtils.hasText(entry.getValue().asText(null))) {
                values.put(entry.getKey(), entry.getValue().asText().trim());
            }
        }
        return values;
    }

    private String effectiveRunnerInstanceId() {
        if (StringUtils.hasText(properties.runnerInstanceId())) {
            return properties.runnerInstanceId();
        }
        String hostname = System.getenv("HOSTNAME");
        if (StringUtils.hasText(hostname)) {
            return hostname.trim();
        }
        return "vectorization-runner-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record RunnerSession(String sessionToken, String deploymentId, String runnerMode, String compatibilityStatus) {
    }

    public record ClaimedRun(String runId, String reason, String status, String requestedStatus) {
    }

    public record HeartbeatDecision(String requestedStatus) {
        public boolean shouldPause() {
            return "PAUSE_REQUESTED".equalsIgnoreCase(requestedStatus);
        }

        public boolean shouldCancel() {
            return "CANCEL_REQUESTED".equalsIgnoreCase(requestedStatus);
        }
    }

    public static class RunnerAuthException extends RuntimeException {
        public RunnerAuthException(String message) {
            super(message);
        }
    }
}
