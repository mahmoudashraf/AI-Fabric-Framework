package com.ai.fabric.platform.backend.assistant;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

/** Canonical conversation transport only. No generation, model routing or repair loop. */
@Component
public class ConfiguredAuthoringRuntimeClient {
    private final AuthoringBrainBindingService bindings;
    private final ObjectMapper mapper;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public ConfiguredAuthoringRuntimeClient(AuthoringBrainBindingService bindings, ObjectMapper mapper) {
        this(bindings, mapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    ConfiguredAuthoringRuntimeClient(AuthoringBrainBindingService bindings, ObjectMapper mapper, HttpClient http) {
        this.bindings = bindings; this.mapper = mapper; this.http = http;
    }
    public JsonNode message(AuthoringBrainBindingService.Binding binding, String sessionId, String conversationId,
        String turnId, String task, String query, boolean preview, int corrections, Instant deadline) {
        var request = mapper.createObjectNode().put("query", query);
        request.putObject("configurationWork").put("sessionId", sessionId).put("turnId", turnId).put("task", task)
            .put("profileId", binding.profileId()).put("releaseId", binding.releaseId()).put("bundleHash", binding.bundleHash())
            .put("previewOnly", preview).put("maxCorrections", Math.min(corrections, binding.maxCorrections()));
        return exchange(binding, sessionId, conversationId, "POST", "/messages", request, turnId, deadline);
    }
    public JsonNode conversation(AuthoringBrainBindingService.Binding binding, String sessionId, String conversationId) {
        return exchange(binding, sessionId, conversationId, "GET", "", null, null, Instant.now().plusSeconds(10));
    }
    public void close(AuthoringBrainBindingService.Binding binding, String sessionId, String conversationId) {
        exchange(binding, sessionId, conversationId, "DELETE", "", null, "close-" + conversationId, Instant.now().plusSeconds(10));
    }
    public void interrupt(AuthoringBrainBindingService.Binding binding, String sessionId, String conversationId, String turnId) {
        exchange(binding, sessionId, conversationId, "POST", "/interruptions", mapper.createObjectNode().put("messageKey", turnId),
            "interrupt-" + turnId, Instant.now().plusSeconds(10));
    }
    private JsonNode exchange(AuthoringBrainBindingService.Binding binding, String sessionId, String conversationId,
        String method, String suffix, JsonNode body, String key, Instant deadline) {
        if (!conversationId.matches("[a-z][a-z0-9-]{1,119}")) throw new IllegalArgumentException("AUTHORING_CONVERSATION_ID_INVALID");
        URI base = URI.create(binding.runtimeUrl());
        if (!java.util.List.of("https", "http").contains(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
            || base.getQuery() != null || base.getFragment() != null || !(base.getPath().isEmpty() || "/".equals(base.getPath())))
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_RUNTIME_DESTINATION_INVALID");
        try {
            Duration remaining = Duration.between(Instant.now(), deadline);
            if (remaining.isNegative() || remaining.isZero()) throw new ResponseStatusException(GATEWAY_TIMEOUT, "AUTHORING_TIMEOUT");
            var builder = HttpRequest.newBuilder(base.resolve("/api/chat/me/conversations/" + conversationId + suffix))
                .timeout(remaining).header("Accept", "application/json");
            bindings.headers(binding, sessionId).forEach(builder::header);
            if (key != null) builder.header("Idempotency-Key", key);
            if (body != null) builder.header("Content-Type", "application/json");
            var response = AuthoringHttp.send(http, builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build(), deadline, 1048576);
            {
                byte[] bytes = response.body();
                if (response.statusCode() < 200 || response.statusCode() >= 300)
                    throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_CANONICAL_DELIVERY_INCOMPLETE_" + response.statusCode());
                return bytes.length == 0 ? mapper.createObjectNode() : mapper.readerFor(JsonNode.class)
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readValue(bytes);
            }
        } catch (ResponseStatusException known) { throw known; }
        catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AUTHORING_INTERRUPTED");
        } catch (Exception unavailable) {
            throw new ResponseStatusException(BAD_GATEWAY, "AUTHORING_CANONICAL_DELIVERY_UNCERTAIN");
        }
    }
}
