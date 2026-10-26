package com.ai.infrastructure.connector.rest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ai.infrastructure.connector.rest.config.ConversationChannelConnectorProperties;
import com.ai.infrastructure.connector.rest.config.RestConnectorServiceProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class ConversationChannelDispatchWorkerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KEY = "channel-provider-secret-12345678901234567890";

    @ParameterizedTest
    @ValueSource(strings = {"", "404"})
    void environmentBindingKeepsRetryOptInExplicit(String statuses) {
        var source = new MapConfigurationPropertySource(java.util.Map.of(
            "rest-connector.conversation-channel.enabled", "true",
            "rest-connector.conversation-channel.additional-retryable-provider-http-statuses", statuses
        ));
        var properties = new Binder(source).bind("rest-connector.conversation-channel",
            Bindable.of(ConversationChannelConnectorProperties.class)).orElseThrow(() -> new AssertionError("Configuration must bind."));
        assertThat(properties.getAdditionalRetryableProviderHttpStatuses())
            .isEqualTo(statuses.isEmpty() ? Set.of() : Set.of(404));
    }

    @Test
    void optedInProxy404RetriesTheSameDispatchThenAcceptsActualDelivery() throws Exception {
        try (var fixture = new Fixture(404, 200)) {
            var properties = fixture.properties();
            properties.setAdditionalRetryableProviderHttpStatuses(Set.of(404));
            var worker = fixture.worker(properties);
            worker.deliverNext();
            worker.deliverNext();

            assertThat(fixture.outcomes).hasSize(2);
            assertThat(fixture.outcomes.get(0).path("accepted").asBoolean()).isFalse();
            assertThat(fixture.outcomes.get(0).path("retryable").asBoolean()).isTrue();
            assertThat(fixture.outcomes.get(0).path("code").asText()).isEqualTo("PROVIDER_HTTP_404");
            assertThat(fixture.outcomes.get(1).path("accepted").asBoolean()).isTrue();
            assertThat(fixture.receivedDispatches).containsExactly("dispatch-1", "dispatch-1");
            assertThat(fixture.providerKeys).containsExactly(KEY, KEY);
            assertThat(fixture.outcomePaths).containsExactly(
                "/api/private/conversation-channel-dispatches/dispatch-1/outcomes/1",
                "/api/private/conversation-channel-dispatches/dispatch-1/outcomes/2"
            );
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 404, 422})
    void unconfiguredPermanentProviderFailureIsNotRetried(int status) throws Exception {
        try (var fixture = new Fixture(status)) {
            fixture.worker(fixture.properties()).deliverNext();
            assertThat(fixture.outcomes).hasSize(1);
            assertThat(fixture.outcomes.getFirst().path("accepted").asBoolean()).isFalse();
            assertThat(fixture.outcomes.getFirst().path("retryable").asBoolean()).isFalse();
            assertThat(fixture.outcomes.getFirst().path("code").asText()).isEqualTo("PROVIDER_HTTP_" + status);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 503})
    void existingTransientResponsesRemainRetryable(int status) throws Exception {
        try (var fixture = new Fixture(status)) {
            fixture.worker(fixture.properties()).deliverNext();
            assertThat(fixture.outcomes.getFirst().path("accepted").asBoolean()).isFalse();
            assertThat(fixture.outcomes.getFirst().path("retryable").asBoolean()).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {200, 401, 403})
    void retryOptInCannotReclassifySuccessOrCredentialDenials(int status) throws Exception {
        try (var fixture = new Fixture(200)) {
            var properties = fixture.properties();
            properties.setAdditionalRetryableProviderHttpStatuses(Set.of(status));
            assertThatThrownBy(() -> fixture.worker(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("authentication or authorization denials");
            assertThat(fixture.receivedDispatches).isEmpty();
        }
    }

    private static final class Fixture implements AutoCloseable {
        private final HttpServer server;
        private final int[] statuses;
        private final AtomicInteger claims = new AtomicInteger();
        private final AtomicInteger deliveries = new AtomicInteger();
        private final List<JsonNode> outcomes = new ArrayList<>();
        private final List<String> outcomePaths = new ArrayList<>();
        private final List<String> receivedDispatches = new ArrayList<>();
        private final List<String> providerKeys = new ArrayList<>();

        Fixture(int... statuses) throws IOException {
            this.statuses = statuses;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/private/conversation-channel-dispatches/claim", exchange -> {
                int attempt = claims.incrementAndGet();
                respond(exchange, 200, "{\"dispatchId\":\"dispatch-1\",\"attempt\":" + attempt
                    + ",\"dispatch\":{\"dispatchId\":\"dispatch-1\",\"type\":\"USER_MESSAGE\"}}");
            });
            server.createContext("/api/private/conversation-channel-dispatches/dispatch-1/outcomes/", exchange -> {
                outcomes.add(MAPPER.readTree(exchange.getRequestBody()));
                outcomePaths.add(exchange.getRequestURI().getPath());
                respond(exchange, 200, "{}");
            });
            server.createContext("/api/conversation-channel/v1/dispatches", exchange -> {
                receivedDispatches.add(MAPPER.readTree(exchange.getRequestBody()).path("dispatchId").asText());
                providerKeys.add(exchange.getRequestHeaders().getFirst("X-LoomAI-Inbox-Key"));
                int status = this.statuses[Math.min(deliveries.getAndIncrement(), this.statuses.length - 1)];
                respond(exchange, status, status == 200 ? "{\"accepted\":true}" : "{}");
            });
            server.start();
        }

        ConversationChannelConnectorProperties properties() {
            var properties = new ConversationChannelConnectorProperties();
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            properties.setBindingId("binding-1");
            properties.setRuntimeBaseUrl(base);
            properties.setRuntimeApiKey(KEY);
            properties.setRuntimeCallbackSigningSecret(KEY);
            properties.setProviderBaseUrl(base);
            properties.setProviderApiKey(KEY);
            properties.setProviderWebhookSigningSecret(KEY);
            return properties;
        }

        ConversationChannelDispatchWorker worker(ConversationChannelConnectorProperties properties) {
            var service = new RestConnectorServiceProperties();
            service.getPersistence().setEnabled(true);
            return new ConversationChannelDispatchWorker(properties, service, MAPPER);
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
        }

        @Override public void close() { server.stop(0); }
    }
}
