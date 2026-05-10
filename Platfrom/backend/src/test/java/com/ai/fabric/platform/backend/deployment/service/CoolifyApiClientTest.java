package com.ai.fabric.platform.backend.deployment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
            assertThat(body.path("git_repository").asText()).isEqualTo("example/repo");
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

    @Test
    void createPublicApplicationSendsFullGitUrlForCoolifyCreateValidation() throws Exception {
        AtomicReference<String> observedBody = new AtomicReference<>();
        HttpServer server = createPublicApplicationServer(observedBody);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            String uuid = client.createPublicApplication(
                connection(server),
                new CoolifyCreatePublicApplicationRequest(
                    "project-uuid",
                    "server-uuid",
                    "staging",
                    "environment-uuid",
                    "example/repo",
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
            assertThat(uuid).isEqualTo("app-uuid");
            assertThat(body.path("git_repository").asText()).isEqualTo("https://github.com/example/repo");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void projectAndEnvironmentEndpointsUseCoolifyApiShape() throws Exception {
        AtomicReference<String> observedProjectBody = new AtomicReference<>();
        AtomicReference<String> observedEnvironmentBody = new AtomicReference<>();
        HttpServer server = projectEnvironmentServer(observedProjectBody, observedEnvironmentBody);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);
            CoolifyConnection connection = connection(server);

            assertThat(client.listProjects(connection))
                .extracting(CoolifyProjectSummary::uuid)
                .containsExactly("project-uuid");
            assertThat(client.createProject(connection, "customer-acme", "Managed project"))
                .isEqualTo("created-project-uuid");
            JsonNode projectBody = objectMapper.readTree(observedProjectBody.get());
            assertThat(projectBody.path("name").asText()).isEqualTo("customer-acme");
            assertThat(projectBody.path("description").asText()).isEqualTo("Managed project");

            assertThat(client.listEnvironments(connection, "project-uuid"))
                .extracting(CoolifyEnvironmentSummary::name)
                .containsExactly("staging");
            assertThat(client.getEnvironment(connection, "project-uuid", "staging"))
                .get()
                .extracting(CoolifyEnvironmentSummary::uuid)
                .isEqualTo("environment-uuid");
            assertThat(client.createEnvironment(connection, "project-uuid", "production"))
                .isEqualTo("created-environment-uuid");
            JsonNode environmentBody = objectMapper.readTree(observedEnvironmentBody.get());
            assertThat(environmentBody.path("name").asText()).isEqualTo("production");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void updateEnvironmentVariablesWritesPreviewRowsIndividually() throws Exception {
        AtomicReference<String> observedBulkBody = new AtomicReference<>();
        AtomicReference<String> observedPreviewBody = new AtomicReference<>();
        AtomicInteger bulkRequests = new AtomicInteger();
        AtomicInteger previewRequests = new AtomicInteger();
        HttpServer server = environmentServer(observedBulkBody, observedPreviewBody, bulkRequests, previewRequests);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            int updated = client.updateEnvironmentVariables(
                connection(server),
                "app-uuid",
                List.of(
                    new CoolifyEnvVar("PLATFORM_DEPLOYMENT_VERSION_ID", "ver-new", false, true, false, false),
                    new CoolifyEnvVar("PLATFORM_DEPLOYMENT_VERSION_ID", "ver-new", true, true, false, false)
                )
            );

            assertThat(updated).isEqualTo(2);
            assertThat(bulkRequests.get()).isEqualTo(1);
            assertThat(previewRequests.get()).isEqualTo(1);

            JsonNode bulkBody = objectMapper.readTree(observedBulkBody.get());
            assertThat(bulkBody.path("data")).hasSize(1);
            assertThat(bulkBody.path("data").get(0).path("key").asText()).isEqualTo("PLATFORM_DEPLOYMENT_VERSION_ID");
            assertThat(bulkBody.path("data").get(0).path("value").asText()).isEqualTo("ver-new");
            assertThat(bulkBody.path("data").get(0).path("is_preview").asBoolean()).isFalse();
            assertThat(bulkBody.path("data").get(0).path("is_buildtime").asBoolean()).isFalse();
            assertThat(bulkBody.path("data").get(0).path("is_runtime").asBoolean()).isTrue();

            JsonNode previewBody = objectMapper.readTree(observedPreviewBody.get());
            assertThat(previewBody.path("key").asText()).isEqualTo("PLATFORM_DEPLOYMENT_VERSION_ID");
            assertThat(previewBody.path("value").asText()).isEqualTo("ver-new");
            assertThat(previewBody.path("is_preview").asBoolean()).isTrue();
            assertThat(previewBody.path("is_buildtime").asBoolean()).isFalse();
            assertThat(previewBody.path("is_runtime").asBoolean()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    @Test
    void updateEnvironmentVariablesDeletesOlderDuplicateRuntimeRowsForUpdatedKeys() throws Exception {
        AtomicInteger deleteRequests = new AtomicInteger();
        AtomicReference<String> deletedPath = new AtomicReference<>();
        HttpServer server = environmentDuplicateServer(deleteRequests, deletedPath);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            int updated = client.updateEnvironmentVariables(
                connection(server),
                "app-uuid",
                List.of(new CoolifyEnvVar(
                    "SHOPIFY_BRIDGE_PUBLIC_BASE_URL",
                    "https://bridge.example",
                    false,
                    true,
                    false,
                    false
                ))
            );

            assertThat(updated).isEqualTo(1);
            assertThat(deleteRequests.get()).isEqualTo(1);
            assertThat(deletedPath.get()).isEqualTo("/api/v1/applications/app-uuid/envs/env-old");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void logsRetriesTransientRateLimitResponses() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = logsRateLimitServer(requests);
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            String logs = client.logs(connection(server), "app-uuid", 50);

            assertThat(logs).contains("Bridge started");
            assertThat(requests.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void getDeploymentReadsCoolifyDeploymentStatusByUuid() throws Exception {
        HttpServer server = deploymentServer();
        try {
            CoolifyApiClient client = new CoolifyApiClient(objectMapper);

            CoolifyDeploymentSummary deployment = client.getDeployment(connection(server), "deploy-uuid")
                .orElseThrow();

            assertThat(deployment.deploymentUuid()).isEqualTo("deploy-uuid");
            assertThat(deployment.applicationName()).isEqualTo("runtime-dep-123");
            assertThat(deployment.applicationUuid()).isEqualTo("app-uuid");
            assertThat(deployment.status()).isEqualTo("finished");
            assertThat(deployment.finishedAt()).isEqualTo("2026-05-05T18:28:52.000000Z");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transportFailuresUseCoolifyUpstreamExceptionContract() {
        CoolifyApiClient client = new CoolifyApiClient(objectMapper);

        assertThatThrownBy(() -> client.version(connection("http://127.0.0.1:1")))
            .isInstanceOfSatisfying(CoolifyApiException.class, exception -> {
                assertThat(exception.statusCode()).isEqualTo(502);
                assertThat(exception.path()).isEqualTo("/version");
                assertThat(exception).hasMessageContaining("Coolify API transport failed");
            });
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

    private HttpServer createPublicApplicationServer(AtomicReference<String> observedBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/applications/public", exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            observedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 201, "{\"uuid\":\"app-uuid\"}");
        });
        server.start();
        return server;
    }

    private HttpServer environmentServer(AtomicReference<String> observedBulkBody,
                                         AtomicReference<String> observedPreviewBody,
                                         AtomicInteger bulkRequests,
                                         AtomicInteger previewRequests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/applications/app-uuid/envs/bulk", exchange -> {
            if (!"PATCH".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            bulkRequests.incrementAndGet();
            observedBulkBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 201, """
                [
                  {
                    "uuid": "env-standard",
                    "key": "PLATFORM_DEPLOYMENT_VERSION_ID",
                    "value": "ver-new",
                    "is_preview": false
                  }
                ]
                """);
        });
        server.createContext("/api/v1/applications/app-uuid/envs", exchange -> {
            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, """
                    [
                      {
                        "uuid": "env-standard",
                        "key": "PLATFORM_DEPLOYMENT_VERSION_ID",
                        "value": "ver-new",
                        "is_preview": false,
                        "updated_at": "2026-05-06T20:00:00.000000Z"
                      },
                      {
                        "uuid": "env-preview",
                        "key": "PLATFORM_DEPLOYMENT_VERSION_ID",
                        "value": "ver-new",
                        "is_preview": true,
                        "updated_at": "2026-05-06T20:00:01.000000Z"
                      }
                    ]
                    """);
                return;
            }
            if (!"PATCH".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            previewRequests.incrementAndGet();
            observedPreviewBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            sendJson(exchange, 201, """
                {
                  "uuid": "env-preview",
                  "key": "PLATFORM_DEPLOYMENT_VERSION_ID",
                  "value": "ver-new",
                  "is_preview": true
                }
                """);
        });
        server.start();
        return server;
    }

    private HttpServer environmentDuplicateServer(AtomicInteger deleteRequests,
                                                  AtomicReference<String> deletedPath) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/applications/app-uuid/envs/bulk", exchange -> {
            if (!"PATCH".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            sendJson(exchange, 201, """
                [
                  {
                    "uuid": "env-new",
                    "key": "SHOPIFY_BRIDGE_PUBLIC_BASE_URL",
                    "value": "https://bridge.example",
                    "is_preview": false,
                    "updated_at": "2026-05-06T20:42:28.000000Z"
                  }
                ]
                """);
        });
        server.createContext("/api/v1/applications/app-uuid/envs", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("GET".equals(exchange.getRequestMethod()) && "/api/v1/applications/app-uuid/envs".equals(path)) {
                sendJson(exchange, 200, """
                    [
                      {
                        "uuid": "env-old",
                        "key": "SHOPIFY_BRIDGE_PUBLIC_BASE_URL",
                        "value": "https://old.example",
                        "is_preview": false,
                        "updated_at": "2026-05-06T11:44:58.000000Z"
                      },
                      {
                        "uuid": "env-new",
                        "key": "SHOPIFY_BRIDGE_PUBLIC_BASE_URL",
                        "value": "https://bridge.example",
                        "is_preview": false,
                        "updated_at": "2026-05-06T20:42:28.000000Z"
                      },
                      {
                        "uuid": "env-preview",
                        "key": "SHOPIFY_BRIDGE_PUBLIC_BASE_URL",
                        "value": "https://preview.example",
                        "is_preview": true,
                        "updated_at": "2026-05-06T10:00:00.000000Z"
                      }
                    ]
                    """);
                return;
            }
            if ("DELETE".equals(exchange.getRequestMethod()) && path.endsWith("/env-old")) {
                deleteRequests.incrementAndGet();
                deletedPath.set(path);
                sendJson(exchange, 200, "{\"message\":\"deleted\"}");
                return;
            }
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer projectEnvironmentServer(AtomicReference<String> observedProjectBody,
                                                AtomicReference<String> observedEnvironmentBody) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            if ("/api/v1/projects".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, "[{\"uuid\":\"project-uuid\",\"name\":\"customer-existing\",\"description\":\"Existing\"}]");
                return;
            }
            if ("/api/v1/projects".equals(path) && "POST".equals(method)) {
                observedProjectBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                sendJson(exchange, 201, "{\"uuid\":\"created-project-uuid\"}");
                return;
            }
            if ("/api/v1/projects/project-uuid/environments".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, "[{\"uuid\":\"environment-uuid\",\"name\":\"staging\",\"description\":\"Main\"}]");
                return;
            }
            if ("/api/v1/projects/project-uuid/staging".equals(path) && "GET".equals(method)) {
                sendJson(exchange, 200, "{\"uuid\":\"environment-uuid\",\"name\":\"staging\",\"description\":\"Main\"}");
                return;
            }
            if ("/api/v1/projects/project-uuid/environments".equals(path) && "POST".equals(method)) {
                observedEnvironmentBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                sendJson(exchange, 201, "{\"uuid\":\"created-environment-uuid\"}");
                return;
            }
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
        return server;
    }

    private HttpServer logsRateLimitServer(AtomicInteger requests) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/applications/app-uuid/logs", exchange -> {
            int attempt = requests.incrementAndGet();
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            if (attempt == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                sendJson(exchange, 429, "{\"message\":\"rate limited\"}");
                return;
            }
            sendJson(exchange, 200, "{\"logs\":\"Bridge started\\nReady\"}");
        });
        server.start();
        return server;
    }

    private HttpServer deploymentServer() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/deployments/deploy-uuid", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            sendJson(exchange, 200, """
                {
                  "deployment_uuid": "deploy-uuid",
                  "status": "finished",
                  "commit": "cdc9ef4",
                  "commit_message": "Resolve managed vector env for Coolify deployments",
                  "created_at": "2026-05-05T18:26:01.000000Z",
                  "updated_at": "2026-05-05T18:28:52.000000Z",
                  "finished_at": "2026-05-05T18:28:52.000000Z",
                  "application": {
                    "uuid": "app-uuid",
                    "name": "runtime-dep-123"
                  }
                }
                """);
        });
        server.start();
        return server;
    }

    private void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private CoolifyConnection connection(HttpServer server) {
        return connection("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private CoolifyConnection connection(String baseUrl) {
        return new CoolifyConnection(
            baseUrl,
            "test-token",
            new CoolifyTargetProfileConfig(
                baseUrl,
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
