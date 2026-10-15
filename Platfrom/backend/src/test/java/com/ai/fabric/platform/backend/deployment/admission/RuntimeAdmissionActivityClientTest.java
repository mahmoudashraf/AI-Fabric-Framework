package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class RuntimeAdmissionActivityClientTest {
    @Test
    void deadlineIncludesAResponseBodyThatNeverFinishes() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var release = new java.util.concurrent.CountDownLatch(1);
        server.createContext("/api/private/runtime-admission-activity/readback", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var body = exchange.getResponseBody()) {
                body.write('{'); body.flush();
                try { release.await(10, java.util.concurrent.TimeUnit.SECONDS); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        });
        server.start();
        try {
            var client = new RuntimeAdmissionActivityClient(new ObjectMapper());
            var binding = new ReleaseBinding("release", "allocation", "generation", "apply", "version", "hash",
                "customer", "tenant", "deployment", "staging", "ACTIVE");
            long start = System.nanoTime();
            assertThatThrownBy(() -> client.read("http://127.0.0.1:" + server.getAddress().getPort(), binding,
                Map.of("X-Private-Scope", "server-only"))).hasMessageContaining("READBACK_TIMEOUT");
            assertThat(java.time.Duration.ofNanos(System.nanoTime() - start)).isLessThan(java.time.Duration.ofSeconds(8));
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    @Test
    void privateReadbackCarriesOnlyTheBoundTargetAndRejectsOversizedOrRedirectedResponses() throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var requests = new AtomicInteger();
        var response = new java.util.concurrent.atomic.AtomicReference<>("{\"schemaVersion\":\"loomai-runtime-admission-activity-v1\","
            + "\"deploymentId\":\"deployment\",\"tenantId\":\"tenant\",\"targetProfileId\":\"staging\",\"releaseId\":\"release\","
            + "\"observedAt\":\"2026-10-15T12:00:00Z\",\"signals\":[]}");
        var code = new java.util.concurrent.atomic.AtomicInteger(200);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/private/runtime-admission-activity/readback", exchange -> {
            requests.incrementAndGet();
            assertThat(exchange.getRequestHeaders().getFirst("X-Private-Scope")).isEqualTo("server-only");
            var target = mapper.readTree(exchange.getRequestBody());
            assertThat(target.size()).isEqualTo(3);
            assertThat(target.path("releaseId").asText()).isEqualTo("release");
            byte[] bytes = response.get().getBytes(StandardCharsets.UTF_8);
            if (code.get() == 302) exchange.getResponseHeaders().add("Location", "/must-not-follow");
            exchange.sendResponseHeaders(code.get(), bytes.length);
            try (var body = exchange.getResponseBody()) { body.write(bytes); }
        });
        server.start();
        try {
            var client = new RuntimeAdmissionActivityClient(mapper);
            var binding = new ReleaseBinding("release", "allocation", "generation", "apply", "version", "hash",
                "customer", "tenant", "deployment", "staging", "ACTIVE");
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            var headers = Map.of("X-Private-Scope", "server-only");
            assertThat(client.read(base, binding, headers).releaseId()).isEqualTo("release");
            response.set("x".repeat(9_000));
            assertThatThrownBy(() -> client.read(base, binding, headers)).hasMessageContaining("READBACK_UNAVAILABLE");
            response.set("{}"); code.set(302);
            assertThatThrownBy(() -> client.read(base, binding, headers)).hasMessageContaining("READBACK_UNAVAILABLE");
            assertThatThrownBy(() -> client.read(base, binding, Map.of())).hasMessageContaining("READBACK_UNAVAILABLE");
            assertThat(requests.get()).isEqualTo(3);
        } finally { server.stop(0); }
    }
}
