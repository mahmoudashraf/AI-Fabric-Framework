package com.ai.fabric.platform.backend.deployment.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;

@Component
public class RuntimeGenerationTransitionClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;

    private final HttpClient client;
    private final ObjectMapper mapper;

    @Autowired
    public RuntimeGenerationTransitionClient(ObjectMapper mapper) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), mapper);
    }

    RuntimeGenerationTransitionClient(HttpClient client, ObjectMapper mapper) {
        this.client = client;
        this.mapper = mapper;
    }

    public Readback status(String baseUrl, Target target, Map<String, String> headers) {
        return post(baseUrl, "/api/private/runtime-target-retirement/status", target, headers, Readback.class);
    }

    public CommandResult command(String baseUrl, Command command, Map<String, String> headers) {
        return post(baseUrl, "/api/private/runtime-target-retirement/commands", command, headers, CommandResult.class);
    }

    public Snapshot snapshot(String baseUrl, Binding binding, Map<String, String> headers) {
        return post(baseUrl, "/api/private/runtime-target-retirement/snapshots", binding, headers, Snapshot.class);
    }

    public GenerationTransitionResult transition(String baseUrl, GenerationTransition transition,
                                                 Map<String, String> headers) {
        return post(baseUrl, "/api/private/runtime-target-retirement/generation-transitions",
            transition, headers, GenerationTransitionResult.class);
    }

    private <T> T post(String baseUrl, String path, Object body, Map<String, String> headers, Class<T> type) {
        URI uri = endpoint(baseUrl, path);
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (headers == null || headers.isEmpty()) throw new IllegalStateException("RUNTIME_GENERATION_AUTHORITY_REQUIRED");
            headers.forEach((name, value) -> {
                if (name == null || name.isBlank() || value == null || value.isBlank()) {
                    throw new IllegalStateException("RUNTIME_GENERATION_AUTHORITY_INVALID");
                }
                request.header(name, value);
            });
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("RUNTIME_GENERATION_REMOTE_REJECTED:" + response.statusCode());
            }
            String payload = response.body();
            if (payload == null || payload.isBlank() || payload.length() > MAX_RESPONSE_CHARS) {
                throw new IllegalStateException("RUNTIME_GENERATION_REMOTE_RESPONSE_INVALID");
            }
            return mapper.readValue(payload, type);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("RUNTIME_GENERATION_REMOTE_INTERRUPTED", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("RUNTIME_GENERATION_REMOTE_UNAVAILABLE", exception);
        }
    }

    private URI endpoint(String baseUrl, String path) {
        try {
            URI base = URI.create(baseUrl);
            if (!("https".equalsIgnoreCase(base.getScheme()) || "http".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null || base.getRawQuery() != null
                || base.getRawFragment() != null) throw new IllegalArgumentException();
            String value = base.toString();
            while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
            return URI.create(value + path);
        } catch (Exception exception) {
            throw new IllegalStateException("RUNTIME_GENERATION_ENDPOINT_INVALID");
        }
    }
}
