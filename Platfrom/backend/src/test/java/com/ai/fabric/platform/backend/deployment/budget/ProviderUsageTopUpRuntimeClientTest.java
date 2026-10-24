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

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;
import static org.assertj.core.api.Assertions.*;

class ProviderUsageTopUpRuntimeClientTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final RuntimeCommand command = new RuntimeCommand(1, "top-up.one", "approval", UUID.randomUUID(), "a".repeat(64),
        new Limits(50, 1000, 2000), new Limits(25, 500, 1000), "admin", "new-owner-funding", "payer", "Synthetic additive funding");
    final RuntimeReceipt receipt = new RuntimeReceipt(1, command.topUpId(), command.approvalId(),
        new ProviderUsageValidityExtensionContracts.RuntimeScope("customer", "deployment", "target", "staging", "sha256:" + "b".repeat(64), "rate-card"),
        command.storeUuid(), command.runtimeFingerprint(), command.expectedEffectiveLimits(), command.additionalLimits(),
        add(command.expectedEffectiveLimits(), command.additionalLimits()), command.authorizedBy(), command.approvalEvidenceRef(), command.payerRef(),
        ProviderUsageAllowanceCodec.sha256(command.reason()), Instant.parse("2026-10-20T13:00:00Z"));

    @Test void exactTopUpWireSchemaPostAndGetAndExplicitEffectiveLimitsReadback() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var posts = new AtomicInteger(); var gets = new AtomicInteger(); var body = new AtomicReference<String>();
        var readback = new AtomicReference<>("{\"limits\":{\"chatAttempts\":50,\"totalTokens\":1000,\"costNanoUsd\":2000},"
            + "\"effectiveLimits\":{\"chatAttempts\":75,\"totalTokens\":1500,\"costNanoUsd\":3000}}");
        server.createContext("/api/admin/provider-budget/top-ups", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("X-Synthetic")).isEqualTo("not-a-credential");
            if (exchange.getRequestMethod().equals("POST")) {
                posts.incrementAndGet(); body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            } else { gets.incrementAndGet(); assertThat(exchange.getRequestURI().getPath()).endsWith("/top-up.one"); }
            byte[] response = mapper.writeValueAsBytes(receipt); exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        });
        server.createContext("/api/admin/provider-budget", exchange -> {
            byte[] response = readback.get().getBytes(StandardCharsets.UTF_8); exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response); exchange.close();
        }); server.start();
        try {
            var client = new ProviderUsageRuntimeClient(mapper); String url = "http://127.0.0.1:" + server.getAddress().getPort();
            var headers = Map.of("X-Synthetic", "not-a-credential");
            assertThat(client.topUp(url, command, headers)).isEqualTo(receipt);
            assertThat(body.get()).isEqualTo(mapper.writeValueAsString(command));
            assertThat(mapper.readTree(body.get()).size()).isEqualTo(11);
            assertThat(client.readTopUp(url, command.topUpId(), headers)).isEqualTo(receipt);
            assertThat(posts).hasValue(1); assertThat(gets).hasValue(1);
            assertThat(client.read(url, headers).limits()).isEqualTo(command.expectedEffectiveLimits());
            assertThat(client.read(url, headers).effectiveLimits()).isEqualTo(receipt.effectiveLimits());
            readback.set("{\"limits\":{\"chatAttempts\":50,\"totalTokens\":1000,\"costNanoUsd\":2000}}");
            assertThat(client.read(url, headers).effectiveLimits()).isNull();
            assertThatThrownBy(() -> client.readTopUp(url, "../other", headers)).isInstanceOf(ProviderUsageAllowanceFailure.class);
            assertThat(gets).hasValue(1);
        } finally { server.stop(0); }
    }

    @Test void transportFailureIsSinglePostWithoutAutomaticReplay() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); var posts = new AtomicInteger();
        server.createContext("/api/admin/provider-budget/top-ups", exchange -> {
            posts.incrementAndGet(); exchange.getRequestBody().readAllBytes(); exchange.sendResponseHeaders(409, -1); exchange.close();
        }); server.start();
        try {
            var client = new ProviderUsageRuntimeClient(mapper); String url = "http://127.0.0.1:" + server.getAddress().getPort();
            assertThatThrownBy(() -> client.topUp(url, command, Map.of("X-Synthetic", "test"))).hasMessage("PROVIDER_USAGE_RUNTIME_UNVERIFIED");
            assertThat(posts).hasValue(1);
        } finally { server.stop(0); }
    }
}
