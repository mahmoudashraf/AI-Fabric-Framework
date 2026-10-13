package com.loomai.demo.dealership.handover;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loomai.demo.dealership.config.DealershipDemoProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class HumanInboxClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void readsOnlyTheBoundedQueueAndKeepsOperatorCredentialServerSide() throws Exception {
        AtomicReference<String> requestTarget = new AtomicReference<>();
        AtomicReference<String> operatorKey = new AtomicReference<>();
        server = server(exchange -> {
            requestTarget.set(exchange.getRequestURI().toString());
            operatorKey.set(exchange.getRequestHeaders().getFirst("X-LoomAI-Operator-Key"));
            respond(exchange, 200, "[{\"handoverId\":\"handover-1\",\"status\":\"WAITING\"}]");
        });
        HumanInboxClient client = clientFor(server);

        assertThat(client.queue("waiting", 500).get(0).path("handoverId").asText())
            .isEqualTo("handover-1");
        assertThat(requestTarget.get())
            .isEqualTo("/api/operator/conversations?limit=100&status=WAITING");
        assertThat(operatorKey.get()).isEqualTo("server-only-operator-key");
    }

    @Test
    void forwardsTypedReplyWithAnExplicitIdempotencyKey() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        server = server(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"accepted\":true,\"outcome\":\"APPLIED\"}");
        });
        HumanInboxClient client = clientFor(server);

        assertThat(client.reply("handover-1", "I can help with that.", "reply-1").path("accepted").asBoolean())
            .isTrue();
        assertThat(requestBody.get())
            .contains("\"text\":\"I can help with that.\"")
            .contains("\"idempotencyKey\":\"reply-1\"");
    }

    @Test
    void failsClosedWhenTheHumanInboxBindingIsDisabled() {
        DealershipDemoProperties properties = new DealershipDemoProperties();
        HumanInboxClient client = new HumanInboxClient(properties, RestClient.builder());

        assertThatThrownBy(() -> client.queue(null, 20))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(503));
    }

    private HumanInboxClient clientFor(HttpServer testServer) {
        DealershipDemoProperties properties = new DealershipDemoProperties();
        properties.getHumanInbox().setEnabled(true);
        properties.getHumanInbox().setBaseUrl(
            "http://127.0.0.1:" + testServer.getAddress().getPort()
        );
        properties.getHumanInbox().setOperatorApiKey("server-only-operator-key");
        properties.getHumanInbox().setRequestTimeout(Duration.ofSeconds(2));
        return new HumanInboxClient(properties, RestClient.builder());
    }

    private HttpServer server(ExchangeHandler handler) throws IOException {
        HttpServer testServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        testServer.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        testServer.start();
        return testServer;
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
