package com.ai.fabric.platform.backend.deployment.tasks;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.HttpStatus.*;

/** Console testing uses the same selected target and frozen private authority as other Brain operations. */
@Service
public class ManagedTaskOperationsService {
    private final DeploymentTargetContextService targets;
    private final DeploymentRuntimeAuthorityService authority;
    private final PlatformAuditService audit;
    private final ObjectMapper mapper;
    private final HttpClient http;

    @Autowired
    public ManagedTaskOperationsService(DeploymentTargetContextService targets,
        DeploymentRuntimeAuthorityService authority, PlatformAuditService audit, ObjectMapper mapper) {
        this(targets, authority, audit, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    ManagedTaskOperationsService(DeploymentTargetContextService targets,
        DeploymentRuntimeAuthorityService authority, PlatformAuditService audit, ObjectMapper mapper, HttpClient http) {
        this.targets = targets; this.authority = authority; this.audit = audit; this.mapper = mapper; this.http = http;
    }

    public JsonNode applied(String deploymentId, String targetProfileId) {
        var context = context(deploymentId, targetProfileId, false);
        JsonNode tasks = taskConfig(context);
        ObjectNode summary = mapper.createObjectNode().put("configurationVersion", context.version().getId())
            .put("taskConfigurationHash", tasks.path("contentHash").asText()).put("runtimeBaseUrl", context.runtimeBaseUrl());
        var catalogue = summary.putArray("tasks");
        for (JsonNode task : tasks.path("compiled").path("tasks")) {
            ObjectNode entry = catalogue.addObject();
            for (String field : List.of("code", "name", "description", "inputSchema", "outputMode", "outputSchema", "deadlineSeconds",
                "writeActions", "confirmationMode")) if (task.has(field)) entry.set(field, task.path(field).deepCopy());
        }
        return summary;
    }

    public Response execute(String deploymentId, String targetProfileId, String taskCode, JsonNode body) {
        return execute(deploymentId, targetProfileId, taskCode, body, null);
    }

    public Response execute(String deploymentId, String targetProfileId, String taskCode, JsonNode body, String expectedVersion) {
        var context = context(deploymentId, targetProfileId, true);
        JsonNode config = taskConfig(context);
        JsonNode task = task(config, taskCode);
        if (expectedVersion != null || !task.path("writeActions").isEmpty()) requireVersion(context, expectedVersion);
        if (body == null || !body.isObject() || !body.path("input").isObject()) throw new ResponseStatusException(BAD_REQUEST, "Task input must be a JSON object.");
        body.fieldNames().forEachRemaining(field -> {
            if (!Set.of("input", "correlationId").contains(field)) throw new ResponseStatusException(BAD_REQUEST, "Unsupported task request field.");
        });
        if (body.has("correlationId") && (!body.path("correlationId").isTextual() || body.path("correlationId").asText().length() > 160))
            throw new ResponseStatusException(BAD_REQUEST, "correlationId must be text of at most 160 characters.");
        return invoke(deploymentId, context, config, task, body, "execute", "execute", "MANAGED_TASK_EXECUTED");
    }

    public Response decide(String deploymentId, String targetProfileId, String taskCode, String receiptId, JsonNode body, String expectedVersion) {
        var context = context(deploymentId, targetProfileId, true);
        requireVersion(context, expectedVersion);
        JsonNode config = taskConfig(context);
        JsonNode task = task(config, taskCode);
        if (receiptId == null || !receiptId.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,119}"))
            throw new ResponseStatusException(BAD_REQUEST, "Select an actual action proposal receipt.");
        if (body == null || !body.isObject() || body.size() != 1 || !body.path("decision").isTextual()
            || !Set.of("CONFIRM", "REJECT").contains(body.path("decision").asText()))
            throw new ResponseStatusException(BAD_REQUEST, "Choose CONFIRM or REJECT for the existing proposal.");
        if ("HUMAN_REVIEW".equals(task.path("confirmationMode").asText()))
            throw new ResponseStatusException(FORBIDDEN, "This task sends confirmation-required actions to the existing human review workflow.");
        return invoke(deploymentId, context, config, task, body, "action-proposals/" + receiptId + "/decisions", "confirm", "MANAGED_TASK_PROPOSAL_DECIDED");
    }

    private void requireVersion(DeploymentTargetContextService.Context context, String expectedVersion) {
        if (!context.version().getId().equals(expectedVersion))
            throw new ResponseStatusException(CONFLICT, "The applied version changed. Refresh the task before sending this request.");
    }

    private JsonNode task(JsonNode config, String code) {
        for (JsonNode candidate : config.path("compiled").path("tasks")) if (candidate.path("code").asText().equals(code)) return candidate;
        throw new ResponseStatusException(NOT_FOUND, "Apply this task to the selected environment before testing it.");
    }

    private Response invoke(String deploymentId, DeploymentTargetContextService.Context context, JsonNode config, JsonNode task,
                            JsonNode body, String suffix, String operation, String auditEvent) {
        String taskCode = task.path("code").asText();
        String json;
        try { json = mapper.writeValueAsString(body); }
        catch (Exception failure) { throw new ResponseStatusException(BAD_REQUEST, "Task input is invalid."); }
        if (json.length() > 30000) throw new ResponseStatusException(BAD_REQUEST, "Task input exceeds 30000 characters.");
        String grant = "task:" + taskCode + ":" + operation;
        var headers = authority.issuePlatformProxyHeaders(deploymentId, context.target().getId(), context.release().getId(),
            context.release().getRuntimeAuthorityFingerprint(), List.of(grant), Duration.ofMinutes(5));
        String runtime = context.runtimeBaseUrl();
        URI uri;
        try {
            URI base = URI.create(runtime);
            if (!Set.of("http", "https").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) throw new IllegalArgumentException();
            uri = URI.create(runtime.replaceAll("/+$", "") + "/api/tasks/v1/tasks/" + taskCode + "/" + suffix);
        } catch (Exception failure) { throw new ResponseStatusException(CONFLICT, "Selected runtime endpoint is invalid."); }
        try {
            var builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(task.path("deadlineSeconds").asInt() + 5L))
                .header("Content-Type", "application/json").header("Accept", "application/json")
                .header("If-Match", "\"" + context.version().getId() + "\"")
                .POST(HttpRequest.BodyPublishers.ofString(json));
            headers.forEach(builder::header);
            var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(131073); }
            if (bytes.length > 131072) throw new ResponseStatusException(BAD_GATEWAY, "Runtime task response exceeds the allowed size.");
            JsonNode result = mapper.readTree(new String(bytes, StandardCharsets.UTF_8));
            if (result == null || !result.isObject()) throw new ResponseStatusException(BAD_GATEWAY, "Runtime returned an invalid task response.");
            if (result.has("taskCode") || response.statusCode() >= 200 && response.statusCode() < 300) {
                if (!taskCode.equals(result.path("taskCode").asText()) || !context.version().getId().equals(result.path("configurationVersion").asText())
                    || !config.path("contentHash").asText().equals(result.path("taskConfigurationHash").asText())) {
                    throw new ResponseStatusException(CONFLICT, "Runtime task configuration does not match the selected applied version. Reapply and verify that environment.");
                }
                audit.record(auditEvent, "DEPLOYMENT", deploymentId, Map.of("taskCode", taskCode,
                    "versionId", context.version().getId(), "targetProfileId", context.target().getId(), "status", result.path("status").asText()));
            }
            return new Response(response.statusCode(), result);
        } catch (ResponseStatusException failure) { throw failure; }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Task request was interrupted. No automatic retry was performed."); }
        catch (Exception failure) { throw new ResponseStatusException(BAD_GATEWAY, "The task runtime could not be reached or returned an invalid response. No automatic retry was performed."); }
    }

    private DeploymentTargetContextService.Context context(String deploymentId, String targetProfileId, boolean mutate) {
        if (PlatformSecurityContext.currentPrincipal() == null) throw new ResponseStatusException(UNAUTHORIZED, "Sign in before using managed Tasks.");
        if (targetProfileId == null || targetProfileId.isBlank() || targetProfileId.length() > 64) throw new ResponseStatusException(BAD_REQUEST, "Select an environment before testing a task.");
        return targets.resolveCurrent(deploymentId, targetProfileId, mutate);
    }

    private JsonNode taskConfig(DeploymentTargetContextService.Context context) {
        try {
            JsonNode behavior = mapper.readTree(context.version().getBehaviorConfigJson());
            if (!ManagedTaskCompiler.enabled(behavior)) throw new ResponseStatusException(CONFLICT, "Tasks are not enabled in this environment's applied version.");
            JsonNode tasks = behavior.path(ManagedTaskCompiler.FIELD);
            new ManagedTaskCompiler(mapper).verifyCompiled(tasks.path("compiled"), tasks.path("contentHash").asText());
            return tasks;
        } catch (ResponseStatusException failure) { throw failure; }
        catch (Exception failure) { throw new ResponseStatusException(CONFLICT, "The applied task configuration is unavailable."); }
    }

    public record Response(int status, JsonNode body) { }
}
