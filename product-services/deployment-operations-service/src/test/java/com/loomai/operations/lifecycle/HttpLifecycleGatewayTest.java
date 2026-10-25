package com.loomai.operations.lifecycle;

import static com.loomai.operations.lifecycle.LifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpLifecycleGatewayTest {
    private static final String KEY = "core-gateway-test-key-000000000000000000";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private HttpServer server;
    private java.util.concurrent.ExecutorService executor;
    private String base;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor); server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }
    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }

    private LifecycleProperties properties(Duration timeout) {
        return new LifecycleProperties(false, base, KEY, true, timeout, Duration.ofSeconds(30), Duration.ofHours(24), "", "");
    }
    private Command command() { return new Command("1", "command-1", "operation-1", "grant-1", "a".repeat(64), Phase.CLAIM); }

    @Test void corePostAndPollHaveExactIndependentWireRecordsAndDedicatedBearerAuthentication() throws Exception {
        AtomicReference<String> posted = new AtomicReference<>(); AtomicInteger polls = new AtomicInteger();
        Receipt receipt = new Receipt("1", "command-1", "operation-1", "grant-1", Phase.CLAIM, Status.SUCCEEDED,
            "CLAIMED", Instant.now(), List.of());
        server.createContext("/internal/v1/deployment-lifecycle/commands", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + KEY);
            if (exchange.getRequestMethod().equals("POST")) {
                assertThat(exchange.getRequestURI().getPath()).endsWith("/commands");
                posted.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } else {
                assertThat(exchange.getRequestURI().getPath()).endsWith("/commands/command-1"); polls.incrementAndGet();
            }
            byte[] body = mapper.writeValueAsBytes(receipt); exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body); exchange.close();
        });
        try (var gateway = new HttpLifecycleGateway(properties(Duration.ofSeconds(2)), mapper)) {
            assertThat(gateway.submit(command())).isEqualTo(receipt);
            assertThat(gateway.observe(command())).isEqualTo(receipt);
        }
        var fields = new java.util.ArrayList<String>();
        mapper.readTree(posted.get()).fieldNames().forEachRemaining(fields::add);
        assertThat(fields)
            .containsExactly("schemaVersion", "commandId", "operationId", "grantId", "scopeHash", "phase");
        assertThat(polls).hasValue(1);
    }

    @Test void timeoutCoversSlowBodyAndDoesNotRetryThePost() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/internal/v1/deployment-lifecycle/commands", exchange -> {
            calls.incrementAndGet(); exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write('{'); exchange.getResponseBody().flush(); Thread.sleep(2000);
            } catch (Exception ignored) { } finally { exchange.close(); }
        });
        long started = System.nanoTime();
        try (var gateway = new HttpLifecycleGateway(properties(Duration.ofMillis(200)), mapper)) {
            assertThatThrownBy(() -> gateway.submit(command())).isInstanceOf(HttpLifecycleGateway.Unavailable.class)
                .hasMessage("LIFECYCLE_GATEWAY_UNAVAILABLE");
        }
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofMillis(1500));
        assertThat(calls).hasValue(1);
    }

    @Test void oversizedBodyRedirectAndNotFoundAreUnknownRatherThanAdditionalEffects() {
        AtomicInteger redirected = new AtomicInteger();
        server.createContext("/leak", exchange -> { redirected.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close(); });
        server.createContext("/internal/v1/deployment-lifecycle/commands", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path.endsWith("/command-1")) exchange.sendResponseHeaders(404, -1);
            else {
                exchange.getResponseHeaders().add("Location", base + "/leak"); exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        try (var gateway = new HttpLifecycleGateway(properties(Duration.ofSeconds(1)), mapper)) {
            assertThatThrownBy(() -> gateway.submit(command())).isInstanceOf(HttpLifecycleGateway.Unavailable.class);
            assertThatThrownBy(() -> gateway.observe(command())).isInstanceOf(HttpLifecycleGateway.Unavailable.class);
        }
        assertThat(redirected).hasValue(0);
        server.removeContext("/internal/v1/deployment-lifecycle/commands");
        server.createContext("/internal/v1/deployment-lifecycle/commands", exchange -> {
            byte[] body = "x".repeat(65_537).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try { exchange.getResponseBody().write(body); } finally { exchange.close(); }
        });
        try (var gateway = new HttpLifecycleGateway(properties(Duration.ofSeconds(1)), mapper)) {
            assertThatThrownBy(() -> gateway.submit(command())).isInstanceOf(HttpLifecycleGateway.Unavailable.class);
        }
    }

    @Test void webhookUsesOnlyConfiguredDestinationAndStableEventId() {
        AtomicInteger delivered = new AtomicInteger();
        server.createContext("/notice", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer " + KEY);
            assertThat(exchange.getRequestHeaders().getFirst("Idempotency-Key")).isEqualTo("event-stable");
            delivered.incrementAndGet(); exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        var properties = new LifecycleProperties(false, "", "", true, Duration.ofSeconds(1), Duration.ofSeconds(30),
            Duration.ofHours(24), base + "/notice", KEY);
        try (var gateway = new HttpLifecycleGateway(properties, mapper)) {
            gateway.deliver(java.util.Map.of("eventId", "event-stable"), "event-stable");
            gateway.deliver(java.util.Map.of("eventId", "event-stable"), "event-stable");
        }
        assertThat(delivered).hasValue(2);
    }

    @Test void missingGatewayWeakCredentialsAndUnsafeOriginsCannotEnableScheduling() {
        assertThatThrownBy(() -> new HttpLifecycleGateway(new LifecycleProperties(true, "", "", false,
            Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofDays(1), "", ""), mapper)).isInstanceOf(IllegalStateException.class);
        for (String url : List.of(base, "https://user:password@core.example", "https://core.example?secret=1", "file:///tmp/core")) {
            assertThatThrownBy(() -> new HttpLifecycleGateway(new LifecycleProperties(false, url, KEY, false,
                Duration.ofSeconds(1), Duration.ofSeconds(30), Duration.ofDays(1), "", ""), mapper))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining(KEY);
        }
    }
}
