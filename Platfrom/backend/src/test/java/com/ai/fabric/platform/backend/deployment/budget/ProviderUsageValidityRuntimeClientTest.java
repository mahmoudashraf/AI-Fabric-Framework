package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageValidityExtensionContracts.*;
import static org.assertj.core.api.Assertions.*;

class ProviderUsageValidityRuntimeClientTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final RuntimeCommand command = new RuntimeCommand(1, "extension.one", "approval", UUID.randomUUID(), "a".repeat(64),
        Instant.parse("2026-10-20T12:00:00Z"), Instant.parse("2026-10-21T00:00:00Z"), "Synthetic explicit review");
    final RuntimeReceipt receipt = new RuntimeReceipt(1, command.extensionId(), command.approvalId(),
        new RuntimeScope("customer", "deployment", "target", "staging", "sha256:" + "b".repeat(64), "rate-card"),
        command.storeUuid(), command.runtimeFingerprint(), command.expectedEffectiveExpiresAt(), command.expiresAt(),
        ProviderUsageAllowanceCodec.sha256(command.reason()), Instant.parse("2026-10-20T13:00:00Z"));

    @Test void actualLoopbackPostAndGetUseExactWireSchemaAndNoAutomaticPostRetry() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var posts = new AtomicInteger(); var gets = new AtomicInteger(); var body = new AtomicReference<String>();
        var overviewGets = new AtomicInteger();
        server.createContext("/api/admin/overview", exchange -> {
            assertThat(exchange.getRequestMethod()).isEqualTo("GET");
            assertThat(exchange.getRequestHeaders().getFirst("X-Synthetic")).isEqualTo("not-a-credential");
            overviewGets.incrementAndGet(); byte[] response = "{\"success\":true,\"runtimeCapabilityManifestHash\":\"synthetic\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length); exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/admin/provider-budget/validity-extensions", exchange -> {
            byte[] response;
            if (exchange.getRequestMethod().equals("POST")) {
                posts.incrementAndGet(); body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                response = mapper.writeValueAsBytes(receipt); exchange.sendResponseHeaders(200, response.length);
            } else {
                gets.incrementAndGet(); assertThat(exchange.getRequestURI().getPath()).endsWith("/extension.one");
                response = mapper.writeValueAsBytes(receipt); exchange.sendResponseHeaders(200, response.length);
            }
            exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            var client = new ProviderUsageRuntimeClient(mapper); String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThat(client.extendValidity(url, command, Map.of("X-Synthetic", "not-a-credential"))).isEqualTo(receipt);
            assertThat(body.get()).isEqualTo(mapper.writeValueAsString(command));
            assertThat(mapper.readValue(body.get(), RuntimeCommand.class)).isEqualTo(command);
            assertThat(client.readValidityExtension(url, command.extensionId(), Map.of("X-Synthetic", "not-a-credential"))).isEqualTo(receipt);
            assertThat(posts).hasValue(1); assertThat(gets).hasValue(1);
            assertThat(client.overview(url, Map.of("X-Synthetic", "not-a-credential")).path("success").booleanValue()).isTrue();
            assertThat(overviewGets).hasValue(1);
            assertThatThrownBy(() -> client.readValidityExtension(url, "../other", Map.of("X-Synthetic", "test"))).isInstanceOf(ProviderUsageAllowanceFailure.class);
            assertThat(gets).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void transportFailurePerformsOnlyOnePostAndLeavesReconciliationToCaller() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); var posts = new AtomicInteger();
        server.createContext("/api/admin/provider-budget/validity-extensions", exchange -> {
            posts.incrementAndGet(); exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(409, -1); exchange.close();
        }); server.start();
        try {
            var client = new ProviderUsageRuntimeClient(mapper); String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> client.extendValidity(url, command, Map.of("X-Synthetic", "test"))).hasMessage("PROVIDER_USAGE_RUNTIME_UNVERIFIED");
            assertThat(posts).hasValue(1);
        } finally { server.stop(0); }
    }
}
