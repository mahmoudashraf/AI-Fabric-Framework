package com.ai.fabric.platform.backend.assistant;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;

class ConfiguredAuthoringRuntimeClientTest {
    HttpServer server; ExecutorService executor; ObjectMapper mapper = new ObjectMapper();
    AuthoringBrainBindingService bindings = mock(AuthoringBrainBindingService.class);
    ConfiguredAuthoringRuntimeClient client; AuthoringBrainBindingService.Binding binding;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); executor = Executors.newCachedThreadPool(); server.setExecutor(executor); server.start();
        binding = new AuthoringBrainBindingService.Binding("brain", "release", "http://127.0.0.1:" + server.getAddress().getPort(),
            "api", "workflow-api@1", "sha256:fixture", 3, 2, List.of("configuration:author"));
        when(bindings.headers(binding, "cas-test")).thenReturn(Map.of("X-PRIVATE-FIXTURE", "verified"));
        client = new ConfiguredAuthoringRuntimeClient(bindings, mapper);
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
    @Test void callsOnlyCanonicalMessageEndpointWithExactFrozenWorkReference() throws Exception {
        server.createContext("/api/chat/me/conversations/conversation-test/messages", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("POST"); assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("cat-test");
            var body = mapper.readTree(exchange.getRequestBody()); assertThat(body.at("/configurationWork/releaseId").asText()).isEqualTo("release");
            assertThat(body.at("/configurationWork/profileId").asText()).isEqualTo("api"); assertThat(body.has("currentConfiguration")).isFalse();
            byte[] response = "{\"success\":true,\"answer\":\"Saved\"}".getBytes(); exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        assertThat(message(Instant.now().plusSeconds(5)).path("answer").asText()).isEqualTo("Saved");
    }
    @Test void unavailableReceiverIsNotAutomaticallyRetried() {
        var calls = new AtomicInteger(); server.createContext("/", exchange -> { calls.incrementAndGet(); exchange.sendResponseHeaders(503, -1); exchange.close(); });
        assertThatThrownBy(() -> message(Instant.now().plusSeconds(5))).hasMessageContaining("INCOMPLETE_503"); assertThat(calls.get()).isEqualTo(1);
    }
    @Test void stalledBodyCannotOutliveTheOverallTurnDeadline() {
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
            try { Thread.sleep(5000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); } finally { exchange.close(); }
        });
        long started = System.nanoTime(); assertThatThrownBy(() -> message(Instant.now().plusMillis(250))).hasMessageContaining("UNCERTAIN");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    }
    @Test void oversizedBodyIsStoppedAndNeverTreatedAsAResult() {
        server.createContext("/", exchange -> {
            byte[] bytes = new byte[1048577]; exchange.sendResponseHeaders(200, bytes.length);
            try { exchange.getResponseBody().write(bytes); } catch (java.io.IOException expectedClose) { } finally { exchange.close(); }
        });
        assertThatThrownBy(() -> message(Instant.now().plusSeconds(5))).hasMessageContaining("UNCERTAIN");
    }
    @Test void malformedConversationIdentifierNeverSendsARequest() {
        assertThatThrownBy(() -> client.message(binding, "cas-test", "../other", "cat-test", "ACTIONS", "Prepare", false, 2, Instant.now().plusSeconds(5)))
            .hasMessageContaining("ID_INVALID"); verifyNoInteractions(bindings);
    }
    @Test void autonomousSubmissionReturnsHandleAndKeepsStableAuthorityAndMessageIdentity() {
        server.createContext("/api/chat/me/conversations/conversation-test/messages", exchange -> {
            var body = mapper.readTree(exchange.getRequestBody());
            assertThat(body.at("/configurationWork/goalMode").asBoolean()).isTrue();
            assertThat(body.at("/configurationWork/authorityReference").asText()).isEqualTo("cag-test");
            assertThat(body.at("/configurationWork/requestRevision").asLong()).isEqualTo(2);
            assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("cat-test");
            byte[] response = "{\"workId\":\"exec-native\",\"goal\":{\"state\":\"QUEUED\"}}".getBytes();
            exchange.sendResponseHeaders(202, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        var authority = mapper.createObjectNode().put("authorityId", "cag-test").put("goalId", "cag-test")
            .put("requestRevision", 2).put("cancellationEpoch", 1).put("segment", 1).put("validUntil", Instant.now().plusSeconds(3600).toString());
        assertThat(client.startGoal(binding, "cas-test", "conversation-test", "cat-test", "ACTIONS", "Prepare", authority).path("workId").asText()).isEqualTo("exec-native");
    }
    private com.fasterxml.jackson.databind.JsonNode message(Instant deadline) {
        return client.message(binding, "cas-test", "conversation-test", "cat-test", "ACTIONS", "Prepare the documented actions", false, 2, deadline);
    }
}
