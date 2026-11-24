package com.ai.fabric.runtime.workflow;

import ai.fabric.intent.action.ActionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Fixed operator-owned Platform receiver. No user URLs, credentials or automatic HTTP retries. */
@Component
public final class AuthoringPlatformClient {
    public static final Set<String> READS = Set.of("authoring_get_context", "authoring_read_selected_sources", "authoring_check_candidate", "authoring_get_work_status");
    public static final Set<String> WRITES = Set.of("authoring_update_draft", "authoring_undo_request");
    private final boolean enabled;
    private final URI base;
    private final String key;
    private final String releaseId;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public record Reply(int status, JsonNode body) { }

    public AuthoringPlatformClient(ObjectMapper mapper,
        @Value("${loomai.configuration-assistant.enabled:false}") boolean enabled,
        @Value("${loomai.configuration-assistant.platform-base-url:}") String base,
        @Value("${loomai.configuration-assistant.tool-key:${AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY:}}") String key,
        @Value("${loomai.runtime.release-id:${LOOMAI_RUNTIME_RELEASE_ID:}}") String releaseId) {
        this.mapper = mapper; this.enabled = enabled; this.key = key; this.releaseId = releaseId;
        this.base = base == null || base.isBlank() ? null : URI.create(base);
        if (enabled && (this.base == null || !"https".equals(this.base.getScheme()) || this.base.getHost() == null
            || this.base.getUserInfo() != null || this.base.getQuery() != null || this.base.getFragment() != null
            || !(this.base.getPath().isEmpty() || "/".equals(this.base.getPath())) || key == null || key.isBlank() || releaseId.isBlank()))
            throw new IllegalStateException("Configured authoring requires an exact HTTPS Platform receiver, private service credential and applied release.");
    }
    public boolean enabled() { return enabled; }
    public String releaseId() { return releaseId; }

    /** Fixed service authentication renews stored work authority; no old JWT is sent or persisted. */
    public JsonNode renew(com.ai.fabric.runtime.web.dto.ChatQueryRequest.ConfigurationWork work, String workId) {
        if (!enabled || !releaseId.equals(work.releaseId()) || !work.goalMode()) throw new IllegalStateException("AUTHORING_GOAL_UNAVAILABLE");
        String session = id(work.sessionId()); String turn = id(work.turnId());
        try {
            var body = mapper.createObjectNode().put("goalId", work.goalId()).put("requestRevision", work.requestRevision())
                .put("cancellationEpoch", work.cancellationEpoch()).put("segment", work.segment());
            if (workId != null) body.put("workId", workId);
            var request = HttpRequest.newBuilder(base.resolve("/api/internal/configuration-authoring/" + session + "/" + turn + "/authority"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json").header("Accept", "application/json")
                .header("X-LOOMAI-AUTHORING-SERVICE-KEY", key).header("X-LOOMAI-AUTHORING-RELEASE-ID", releaseId)
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            var pending = http.sendAsync(request, ignored -> new WorkflowHttpBody());
            try {
                var response = pending.get(15, java.util.concurrent.TimeUnit.SECONDS);
                if (response.statusCode() != 200) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.valueOf(response.statusCode()), "AUTHORING_GOAL_AUTHORITY_UNAVAILABLE");
                return mapper.readerFor(JsonNode.class).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readValue(response.body());
            } finally { if (!pending.isDone()) pending.cancel(true); }
        } catch (org.springframework.web.server.ResponseStatusException denied) { throw denied; }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("AUTHORING_GOAL_AUTHORITY_INTERRUPTED"); }
        catch (Exception unavailable) { throw new IllegalStateException("AUTHORING_GOAL_AUTHORITY_UNAVAILABLE"); }
    }

    public Reply call(String action, Map<String, Object> parameters, ActionContext context) {
        if (!enabled || !(READS.contains(action) || WRITES.contains(action))) throw new IllegalStateException("AUTHORING_TOOL_UNAVAILABLE");
        var metadata = context.metadata();
        String session = id(metadata.get("authoringSessionId")); String turn = id(metadata.get("authoringTurnId"));
        var auth = context.authContext();
        if (auth == null || !("authoring:" + session).equals(auth.getSubjectId())
            || !"PRIVATE_RUNTIME_BACKEND_MEDIATED".equals(auth.getAuthMode()) || !"TRUSTED_BACKEND".equals(auth.getCallerType())
            || !auth.getGrantedScopes().contains("configuration:author")) throw new IllegalStateException("AUTHORING_TOOL_AUTHORITY_REQUIRED");
        try {
            Instant expires = Instant.parse(auth.getExpiresAt());
            Duration remaining = Duration.between(Instant.now(), expires);
            if (remaining.isNegative() || remaining.isZero()) throw new IllegalStateException("AUTHORING_TOOL_AUTHORITY_EXPIRED");
            if (remaining.compareTo(Duration.ofSeconds(15)) > 0) remaining = Duration.ofSeconds(15);
            var request = HttpRequest.newBuilder(base.resolve("/api/internal/configuration-authoring/" + session + "/" + turn + "/" + action))
                .timeout(remaining).header("Content-Type", "application/json").header("Accept", "application/json")
                .header("X-LOOMAI-AUTHORING-SERVICE-KEY", key).header("X-LOOMAI-AUTHORING-RELEASE-ID", releaseId);
            Map<String, String> headers = new java.util.LinkedHashMap<>();
            headers.put("SUBJECT-ID", auth.getSubjectId()); headers.put("SUBJECT-TYPE", auth.getSubjectType());
            headers.put("MODE", auth.getAuthMode()); headers.put("CALLER-TYPE", auth.getCallerType());
            headers.put("DEPLOYMENT-ID", auth.getDeploymentId()); headers.put("CUSTOMER-ID", auth.getCustomerId());
            headers.put("TENANT-ID", auth.getTenantId()); headers.put("ISSUER", auth.getIssuer());
            headers.put("AUDIENCES", String.join(",", auth.getAudiences())); headers.put("SCOPES", String.join(",", auth.getGrantedScopes()));
            headers.put("EXPIRES-AT", auth.getExpiresAt());
            headers.forEach((name, value) -> { if (value == null || value.isBlank()) throw new IllegalStateException("AUTHORING_TOOL_IDENTITY_INCOMPLETE"); request.header("X-AIFABRIC-AUTH-" + name, value); });
            if (metadata.containsKey("authoringGoalId")) {
                request.header("X-LOOMAI-AUTHORING-GOAL-ID", id(metadata.get("authoringGoalId")))
                    .header("X-LOOMAI-AUTHORING-REQUEST-REVISION", String.valueOf(metadata.get("authoringRequestRevision")))
                    .header("X-LOOMAI-AUTHORING-CANCELLATION-EPOCH", String.valueOf(metadata.get("authoringCancellationEpoch")))
                    .header("X-LOOMAI-AUTHORING-SEGMENT", String.valueOf(metadata.get("authoringSegment")));
            }
            var pending = http.sendAsync(request.POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(parameters))).build(), ignored -> new WorkflowHttpBody());
            try {
                var response = pending.get(remaining.toNanos(), java.util.concurrent.TimeUnit.NANOSECONDS);
                byte[] bytes = response.body();
                if (response.statusCode() >= 500) throw new IllegalStateException("AUTHORING_TOOL_RECEIVER_UNAVAILABLE");
                JsonNode body = bytes.length == 0 ? mapper.createObjectNode() : mapper.readerFor(JsonNode.class)
                    .with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
                return new Reply(response.statusCode(), body);
            } finally { if (!pending.isDone()) pending.cancel(true); }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IllegalStateException("AUTHORING_TOOL_INTERRUPTED");
        } catch (Exception failure) {
            // A write may have committed before transport failed. The governed boundary
            // records an unknown outcome; recovery must read the receiver's receipt.
            throw new IllegalStateException("AUTHORING_TOOL_TRANSPORT_INCOMPLETE");
        }
    }
    private String id(Object value) {
        if (!(value instanceof String text) || !text.matches("[a-z][a-z0-9-]{1,119}")) throw new IllegalArgumentException("AUTHORING_WORK_ID_INVALID");
        return text;
    }
}
