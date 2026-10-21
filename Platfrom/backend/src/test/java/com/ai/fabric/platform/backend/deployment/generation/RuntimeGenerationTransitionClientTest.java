package com.ai.fabric.platform.backend.deployment.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeGenerationTransitionClientTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;

    @AfterEach void stop() { if (server != null) server.stop(0); }

    @Test void sendsTypedStatusWithExactPrivateHeaders() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> key = new AtomicReference<>();
        server = server("/api/private/runtime-target-retirement/status", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            key.set(exchange.getRequestHeaders().getFirst("X-AI-Fabric-Backend-Key"));
            respond(exchange, 200, mapper.writeValueAsString(readback("release-old", State.ACTIVE, 0)));
        });
        var client = client();
        var target = new Target("dep-1", "staging", "release-old");

        Readback result = client.status(baseUrl(), target, Map.of(
            "X-AI-Fabric-Backend-Key", "private-value", "Authorization", "AI-Fabric-Private assertion"));

        assertThat(result.target()).isEqualTo(target);
        assertThat(result.fenceEpoch()).isEqualTo(0);
        assertThat(key.get()).isEqualTo("private-value");
        assertThat(mapper.readTree(body.get()).path("releaseId").asText()).isEqualTo("release-old");
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "null"})
    void absentCounterIsNotSilentlyInterpretedAsTheValidInitialZero(String shape) throws Exception {
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.valueToTree(readback("release-old", State.ACTIVE, 0));
        if ("missing".equals(shape)) payload.remove("fenceEpoch");
        else payload.putNull("fenceEpoch");
        server = server("/api/private/runtime-target-retirement/status", exchange -> respond(exchange, 200, payload.toString()));
        assertThatThrownBy(() -> client().status(baseUrl(), new Target("dep-1", "staging", "release-old"),
            Map.of("Authorization", "private"))).hasMessage("RUNTIME_GENERATION_REMOTE_UNAVAILABLE");
    }

    @Test void postsDrainSnapshotFenceAndTransitionToDedicatedRoutes() throws Exception {
        AtomicReference<String> lastPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                lastPath.set(exchange.getRequestURI().getPath());
                String path = lastPath.get();
                if (path.endsWith("/commands")) respond(exchange, 200, mapper.writeValueAsString(
                    new CommandResult(readback("release-old", State.DRAINING, 2), readback("release-old", State.DRAINING, 2), false)));
                else if (path.endsWith("/snapshots")) respond(exchange, 200, mapper.writeValueAsString(snapshot()));
                else respond(exchange, 200, mapper.writeValueAsString(new GenerationTransitionResult(
                    readback("release-old", State.AUTHORITY_REVOKED, 3), readback("release-new", State.ACTIVE, 4),
                    "coolify-stop:runtime-1", Instant.now(), false)));
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
        server.start();
        var client = client();
        var target = new Target("dep-1", "staging", "release-old");
        var binding = new Binding(target, 2, "replace-release-new", "a".repeat(64));
        var headers = Map.of("Authorization", "AI-Fabric-Private assertion");

        assertThat(client.command(baseUrl(), new Command(binding, 1, CommandType.BEGIN_DRAIN,
            Instant.now().plusSeconds(60), null), headers).current().state()).isEqualTo(State.DRAINING);
        assertThat(lastPath.get()).endsWith("/commands");
        assertThat(client.snapshot(baseUrl(), binding, headers).localDrainComplete()).isTrue();
        assertThat(lastPath.get()).endsWith("/snapshots");
        assertThat(client.transition(baseUrl(), new GenerationTransition(target,
            new Target("dep-1", "staging", "release-new"), "tenant-1", 3,
            "b".repeat(64), "c".repeat(64), "replace-release-new", "a".repeat(64),
            "coolify-stop:runtime-1"), headers).current().target().releaseId()).isEqualTo("release-new");
        assertThat(lastPath.get()).endsWith("/generation-transitions");
    }

    @Test void failsClosedWithoutAuthorityAndDoesNotPersistRemoteBodyInError() throws Exception {
        server = server("/api/private/runtime-target-retirement/status", exchange ->
            respond(exchange, 409, "provider secret: do-not-leak"));
        var target = new Target("dep-1", "staging", "release-old");

        assertThatThrownBy(() -> client().status(baseUrl(), target, Map.of()))
            .hasMessage("RUNTIME_GENERATION_AUTHORITY_REQUIRED");
        assertThatThrownBy(() -> client().status(baseUrl(), target, Map.of("Authorization", "private")))
            .hasMessage("RUNTIME_GENERATION_REMOTE_REJECTED:409")
            .hasMessageNotContaining("do-not-leak");
    }

    @Test void rejectsInvalidEndpointAndOversizedResponse() throws Exception {
        var target = new Target("dep-1", "staging", "release-old");
        assertThatThrownBy(() -> client().status("file:///tmp/runtime", target, Map.of("Authorization", "private")))
            .hasMessage("RUNTIME_GENERATION_ENDPOINT_INVALID");
        server = server("/api/private/runtime-target-retirement/status", exchange ->
            respond(exchange, 200, "x".repeat(256 * 1024 + 1)));
        assertThatThrownBy(() -> client().status(baseUrl(), target, Map.of("Authorization", "private")))
            .hasMessage("RUNTIME_GENERATION_REMOTE_RESPONSE_INVALID");
    }

    private RuntimeGenerationTransitionClient client() {
        return new RuntimeGenerationTransitionClient(HttpClient.newHttpClient(), mapper);
    }

    private HttpServer server(String path, Handler handler) throws Exception {
        HttpServer result = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        result.createContext(path, exchange -> {
            try { handler.handle(exchange); } catch (Exception exception) { throw new RuntimeException(exception); }
        });
        result.start();
        return result;
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }

    private Readback readback(String release, State state, long fence) {
        return new Readback(new Target("dep-1", "staging", release), "tenant-1", state, fence,
            state == State.ACTIVE ? null : "replace-release-new", state == State.ACTIVE ? null : "a".repeat(64),
            state == State.ACTIVE ? null : Instant.now().plusSeconds(60), state == State.AUTHORITY_REVOKED,
            false, Instant.now());
    }

    private Snapshot snapshot() {
        Metric zero = new Metric(0L, true, List.of());
        return new Snapshot("snapshot-1", "runtime-target-drain:snapshot-1",
            readback("release-old", State.DRAINING, 2), Instant.now(), zero, zero, zero, zero,
            0, true, true, true, true, false, "b".repeat(64), List.of(), List.of());
    }

    private void respond(HttpExchange exchange, int status, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface interface Handler { void handle(HttpExchange exchange) throws Exception; }
}
