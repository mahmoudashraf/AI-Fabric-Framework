package com.ai.fabric.platform.backend.desk;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;

class DeskServiceClientTest {
    @Test void optionalAdministrationReadTreatsOnly404AsMissingAndPreservesSuccessJson() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/desk/admin/v1/", exchange -> {
            int status = Integer.parseInt(exchange.getRequestURI().getPath().substring(exchange.getRequestURI().getPath().lastIndexOf('/') + 1));
            assertThat(exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Service-Key")).isEqualTo("private-key");
            byte[] bytes = "{\"spaceId\":\"desk-example\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        server.start();
        try {
            var client = new DeskServiceClient(new MockEnvironment(), new ObjectMapper());
            var connection = new DeskServiceClient.Connection("http://127.0.0.1:" + server.getAddress().getPort(), "private-key");
            assertThat(client.getOptional(connection, "/api/desk/admin/v1/404")).isEmpty();
            assertThat(client.getOptional(connection, "/api/desk/admin/v1/200").orElseThrow().path("spaceId").asText()).isEqualTo("desk-example");
            for (int code : new int[]{401, 403, 409, 429, 500, 503}) {
                assertThatThrownBy(() -> client.getOptional(connection, "/api/desk/admin/v1/" + code))
                    .isInstanceOf(ResponseStatusException.class).hasMessageContaining("HTTP " + code);
            }
        } finally { server.stop(0); }
    }

    @Test void requiresAnExplicitConfiguredEnvironmentAndPrivateServiceCredential() {
        var client = new DeskServiceClient(new MockEnvironment(), new ObjectMapper());
        assertThatThrownBy(() -> client.connection("production")).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> client.connection("development")).isInstanceOf(ResponseStatusException.class);
    }

    @Test void rejectsUntrustedUrlShapesAndNeverPrintsTheCredential() {
        for (var url : new String[]{"http://desk.example", "https://user:password@desk.example", "https://desk.example/path", "https://desk.example?next=evil"}) {
            var client = new DeskServiceClient(environment(url), new ObjectMapper());
            assertThatThrownBy(() -> client.connection("production")).isInstanceOf(ResponseStatusException.class);
        }
        var client = new DeskServiceClient(environment("https://desk.example/"), new ObjectMapper());
        assertThat(client.connection("production").baseUrl()).isEqualTo("https://desk.example");
        assertThat(client.connection("production").toString()).doesNotContain("credential-that-must-not-be-logged");
    }

    private MockEnvironment environment(String url) {
        return new MockEnvironment().withProperty("LOOMAI_DESK_PRODUCTION_BASE_URL", url)
            .withProperty("LOOMAI_DESK_PRODUCTION_SERVICE_KEY", "credential-that-must-not-be-logged-1234567890");
    }
}
