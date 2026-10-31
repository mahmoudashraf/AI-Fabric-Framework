package com.ai.fabric.platform.backend.desk;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class DeskServiceClientPhotoTest {
    private static final String PATH = "/api/desk/admin/v1/bindings/support/support-team/team-" + "a".repeat(64) + "/photo";
    private static final byte[] JPEG = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0, 1};

    @Test void photoTransportRequiresBoundedJpegAndPreservesNotFoundWithoutFollowingRedirects() throws Exception {
        var reply = new AtomicReference<>(new Reply(200, "image/jpeg", JPEG));
        var calls = new ArrayList<Call>();
        var server = server(reply, calls);
        try {
            var client = new DeskServiceClient(new MockEnvironment(), new ObjectMapper());
            var connection = new DeskServiceClient.Connection("http://127.0.0.1:" + server.getAddress().getPort(), "private-service-key");
            assertThat(client.getPhoto(connection, PATH)).containsExactly(JPEG);
            assertThat(calls.getFirst()).isEqualTo(new Call("GET", PATH, "image/jpeg", "private-service-key", ""));
            reply.set(new Reply(404, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
            rejected(() -> client.getPhoto(connection, PATH), 404);
            for (var invalid : List.of(new Reply(403, "application/json", "{}".getBytes(StandardCharsets.UTF_8)),
                new Reply(200, "text/html", JPEG), new Reply(200, "image/jpeg", "not-an-image".getBytes(StandardCharsets.UTF_8)),
                new Reply(200, "image/jpeg", new byte[131073]), new Reply(302, "image/jpeg", JPEG))) {
                reply.set(invalid);
                int before = calls.size();
                rejected(() -> client.getPhoto(connection, PATH), 502);
                assertThat(calls).hasSize(before + 1);
            }
            assertThatThrownBy(() -> client.getPhoto(connection, "/api/desk/v1/agents/private/photo"))
                .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> client.getPhoto(connection, "/api/desk/admin/v1/../private"))
                .isInstanceOf(IllegalArgumentException.class);
        } finally { server.stop(0); }
    }

    @Test void existingJsonReadAndWriteContractsRemainBoundedAndServiceAuthenticated() throws Exception {
        var reply = new AtomicReference<>(new Reply(200, "application/json", "{\"enabled\":true}".getBytes(StandardCharsets.UTF_8)));
        var calls = new ArrayList<Call>(); var server = server(reply, calls);
        try {
            var client = new DeskServiceClient(new MockEnvironment(), new ObjectMapper());
            var connection = new DeskServiceClient.Connection("http://127.0.0.1:" + server.getAddress().getPort(), "private-service-key");
            assertThat(client.get(connection, "/api/desk/admin/v1/bindings/support").path("enabled").asBoolean()).isTrue();
            assertThat(client.put(connection, "/api/desk/admin/v1/workspaces/customer", Map.of("name", "Support"))
                .path("enabled").asBoolean()).isTrue();
            assertThat(calls.get(0).accept()).isEqualTo("application/json");
            assertThat(calls.get(1).method()).isEqualTo("PUT");
            assertThat(calls.get(1).body()).isEqualTo("{\"name\":\"Support\"}");
            reply.set(new Reply(200, "application/json", "not-json".getBytes(StandardCharsets.UTF_8)));
            rejected(() -> client.get(connection, "/api/desk/admin/v1/bindings/support"), 502);
            reply.set(new Reply(200, "application/json", new byte[262145]));
            rejected(() -> client.get(connection, "/api/desk/admin/v1/bindings/support"), 502);
        } finally { server.stop(0); }
    }

    private HttpServer server(AtomicReference<Reply> response, List<Call> calls) throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            calls.add(new Call(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                exchange.getRequestHeaders().getFirst("Accept"), exchange.getRequestHeaders().getFirst("X-LoomAI-Desk-Service-Key"),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            var reply = response.get(); exchange.getResponseHeaders().set("Content-Type", reply.contentType());
            if (reply.status() == 302) exchange.getResponseHeaders().set("Location", "/redirect-target");
            exchange.sendResponseHeaders(reply.status(), reply.data().length);
            try { exchange.getResponseBody().write(reply.data()); } finally { exchange.close(); }
        });
        server.start(); return server;
    }
    private void rejected(org.assertj.core.api.ThrowableAssert.ThrowingCallable action, int status) {
        assertThatThrownBy(action).isInstanceOfSatisfying(ResponseStatusException.class,
            error -> assertThat(error.getStatusCode().value()).isEqualTo(status));
    }
    private record Reply(int status, String contentType, byte[] data) { }
    private record Call(String method, String path, String accept, String key, String body) { }
}
