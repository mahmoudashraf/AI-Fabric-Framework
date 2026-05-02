package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CoolifyApiClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updatePublicApplicationSendsOnlyPatchAcceptedFields() throws Exception {
        AtomicReference<String> observedBody = new AtomicReference<>();
        HttpServer server = patchServer(observedBody);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            client.updatePublicApplication(
                connection(server),
                "app-uuid",
                new CoolifyCreatePublicApplicationRequest(
                    "project-uuid",
                    "server-uuid",
                    "staging",
                    "environment-uuid",
                    "https://github.com/example/repo.git",
                    "Platform-V8",
                    "dockerfile",
                    "/",
                    "/runtime/Dockerfile",
                    "8080",
                    "destination-uuid",
                    "runtime-dep-123",
                    "Managed by AI Fabric deployment dep-123",
                    "http://dep-123.example.test",
                    true,
                    "/actuator/health",
                    "8080",
                    false,
                    false,
                    false,
                    false
                )
            );

            JsonNode body = objectMapper.readTree(observedBody.get());
            assertThat(body.has("project_uuid")).isFalse();
            assertThat(body.has("server_uuid")).isFalse();
            assertThat(body.has("environment_name")).isFalse();
            assertThat(body.has("environment_uuid")).isFalse();
            assertThat(body.has("destination_uuid")).isFalse();
            assertThat(body.has("autogenerate_domain")).isFalse();
            assertThat(body.path("git_repository").asText()).isEqualTo("https://github.com/example/repo.git");
            assertThat(body.path("dockerfile_location").asText()).isEqualTo("/runtime/Dockerfile");
            assertThat(body.path("domains").asText()).isEqualTo("http://dep-123.example.test");
            assertThat(body.path("is_auto_deploy_enabled").asBoolean()).isFalse();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void updateDockerImageApplicationSendsOnlyPatchAcceptedFields() throws Exception {
        AtomicReference<String> observedBody = new AtomicReference<>();
        HttpServer server = patchServer(observedBody);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            client.updateDockerImageApplication(
                connection(server),
                "app-uuid",
                new CoolifyCreateDockerImageApplicationRequest(
                    "project-uuid",
                    "server-uuid",
                    "staging",
                    "environment-uuid",
                    "ghcr.io/example/runtime",
                    "sha-123",
                    "8080",
                    "destination-uuid",
                    "runtime-dep-123",
                    "Managed by AI Fabric deployment dep-123",
                    "http://dep-123.example.test",
                    true,
                    "/actuator/health",
                    "8080",
                    false,
                    false,
                    false
                )
            );

            JsonNode body = objectMapper.readTree(observedBody.get());
            assertThat(body.has("project_uuid")).isFalse();
            assertThat(body.has("server_uuid")).isFalse();
            assertThat(body.has("environment_name")).isFalse();
            assertThat(body.has("environment_uuid")).isFalse();
            assertThat(body.has("destination_uuid")).isFalse();
            assertThat(body.has("autogenerate_domain")).isFalse();
            assertThat(body.path("docker_registry_image_name").asText()).isEqualTo("ghcr.io/example/runtime");
            assertThat(body.path("docker_registry_image_tag").asText()).isEqualTo("sha-123");
            assertThat(body.path("domains").asText()).isEqualTo("http://dep-123.example.test");
        } finally {
            server.stop(0);
        }
    }

    private HttpServer patchServer(AtomicReference<String> observedBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/applications/app-uuid", exchange -> {
            if (!"PATCH".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }
            observedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"uuid\":\"app-uuid\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }

    private CoolifyConnection connection(HttpServer server) {
        return new CoolifyConnection(
            "http://127.0.0.1:" + server.getAddress().getPort(),
            "test-token",
            new CoolifyTargetProfileConfig(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                "project-uuid",
                "staging",
                "environment-uuid",
                "server-uuid",
                "destination-uuid",
                "example.test",
                "4.0.0",
                5,
                600,
                false,
                false,
                "8080",
                "/actuator/health",
                "8080"
            )
        );
    }
}
