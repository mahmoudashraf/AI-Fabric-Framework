package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeploymentReleaseVerificationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifyChecksRuntimeAndConnectorAdminStateAgainstPublishedVersion() throws Exception {
        HttpServer runtimeServer = HttpServer.create(new InetSocketAddress(0), 0);
        HttpServer connectorServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-actions.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-entity-config.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/actions-routing.yml",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/ai-prompt-config.json",
                "https://platform.example/api/deployments/dep-123/versions/ver-123/artifacts/deployment-manifest.json"
            );

            registerRuntimeHandlers(runtimeServer, artifacts);
            registerConnectorHandlers(connectorServer, artifacts);
            runtimeServer.start();
            connectorServer.start();

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.resolveSecret("APP_ADMIN_API_KEY")).thenReturn("admin-secret");
            when(platformSecretService.resolveSecret("CONNECTOR_API_KEY")).thenReturn("connector-secret");

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            DeploymentReleaseVerificationService service = new DeploymentReleaseVerificationService(
                objectMapper,
                new PlatformVerificationProperties(
                    Duration.ofSeconds(2),
                    "/actuator/health",
                    "/actuator/health",
                    "/api/admin/overview",
                    "/api/admin/actions/overview",
                    "/api/admin/indexing/overview",
                    "/api/admin/overview",
                    "/api/admin/actions/overview"
                ),
                platformSecretService,
                artifactService
            );

            DeploymentEntity deployment = deployment(
                "http://127.0.0.1:" + runtimeServer.getAddress().getPort(),
                "http://127.0.0.1:" + connectorServer.getAddress().getPort()
            );
            DeploymentVersionEntity version = version();
            DeploymentReleaseEntity release = release();

            DeploymentVerificationRunEntity run = service.verify(deployment, version, release, "POST_DEPLOY");

            assertThat(run.getStatus()).isEqualTo("PASSED");
            assertThat(run.getSummaryMessage()).isEqualTo("19 passed, 0 failed, 0 skipped");

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(statuses).hasSize(19);
            assertThat(statuses.values()).containsOnly("PASSED");
            assertThat(statuses)
                .containsEntry("runtime_admin_overview_http_probe", "PASSED")
                .containsEntry("runtime_config_matches_expected", "PASSED")
                .containsEntry("runtime_prompt_config_matches_expected", "PASSED")
                .containsEntry("runtime_actions_match_expected", "PASSED")
                .containsEntry("runtime_entity_types_match_expected", "PASSED")
                .containsEntry("connector_admin_overview_http_probe", "PASSED")
                .containsEntry("connector_config_matches_expected", "PASSED")
                .containsEntry("connector_actions_match_expected", "PASSED")
                .containsEntry("connector_authz_configuration_matches_expected", "PASSED");
        } finally {
            runtimeServer.stop(0);
            connectorServer.stop(0);
        }
    }

    private void registerRuntimeHandlers(HttpServer server, DeploymentArtifactBundleSummary artifacts) {
        server.createContext("/actuator/health", exchange -> writeJson(exchange, 200, """
            {"status":"UP"}
            """));
        server.createContext(
            "/api/admin/overview",
            jsonHandler(
                "X-ADMIN-API-KEY",
                "admin-secret",
                """
                    {
                      "success": true,
                      "entityConfigLocation": "%s",
                      "promptConfigLocation": "%s",
                      "actionCatalogSources": [
                        {
                          "type": "FILE",
                          "path": "%s",
                          "optional": false
                        }
                      ],
                      "actionsCount": 2,
                      "supportedEntityTypes": ["product", "policy"]
                    }
                    """.formatted(
                        artifacts.entityArtifactUrl(),
                        artifacts.promptArtifactUrl(),
                        artifacts.actionsArtifactUrl()
                    )
            )
        );
        server.createContext(
            "/api/admin/actions/overview",
            jsonHandler(
                "X-ADMIN-API-KEY",
                "admin-secret",
                """
                    {
                      "success": true,
                      "count": 2,
                      "actions": [
                        {"name": "list_products"},
                        {"name": "view_cart"}
                      ]
                    }
                    """
            )
        );
        server.createContext(
            "/api/admin/indexing/overview",
            jsonHandler(
                "X-ADMIN-API-KEY",
                "admin-secret",
                """
                    {
                      "success": true,
                      "supportsVectorScan": true,
                      "entityTypes": ["product", "policy"],
                      "countsByEntityType": {
                        "product": 4,
                        "policy": 1
                      },
                      "totalVectors": 5
                    }
                    """
            )
        );
    }

    private void registerConnectorHandlers(HttpServer server, DeploymentArtifactBundleSummary artifacts) {
        server.createContext("/actuator/health", exchange -> writeJson(exchange, 200, """
            {"status":"UP"}
            """));
        server.createContext(
            "/api/admin/overview",
            jsonHandler(
                "X-ADMIN-API-KEY",
                "admin-secret",
                """
                    {
                      "success": true,
                      "routingConfigLocation": "%s",
                      "connector": {
                        "inboundAuth": {
                          "apiKey": {
                            "valueConfigured": true
                          }
                        }
                      },
                      "runtimeProxy": {
                        "enabled": true,
                        "baseUrl": "https://runtime.internal"
                      },
                      "authz": {
                        "enabled": true,
                        "path": "/api/authz/check",
                        "upstream": {
                          "baseUrl": "https://commerce.example"
                        }
                      },
                      "actionsCount": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """.formatted(artifacts.routingArtifactUrl())
            )
        );
        server.createContext(
            "/api/admin/actions/overview",
            jsonHandler(
                "X-ADMIN-API-KEY",
                "admin-secret",
                """
                    {
                      "success": true,
                      "count": 2,
                      "actions": [
                        {"actionId": "list_products"},
                        {"actionId": "view_cart"}
                      ]
                    }
                    """
            )
        );
    }

    private HttpHandler jsonHandler(String requiredHeader, String requiredValue, String body) {
        return exchange -> {
            String actual = exchange.getRequestHeaders().getFirst(requiredHeader);
            if (!requiredValue.equals(actual)) {
                writeJson(exchange, 401, """
                    {"success":false,"message":"Unauthorized"}
                    """);
                return;
            }
            writeJson(exchange, 200, body);
        };
    }

    private void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(payload);
        } finally {
            exchange.close();
        }
    }

    private DeploymentEntity deployment(String runtimeBaseUrl, String connectorBaseUrl) {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-123");
        deployment.setName("Sample Commerce Dev");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("dev-openai-lucene");
        deployment.setStatus("ACTIVE");
        deployment.setActiveVersionId("ver-123");
        deployment.setRuntimeBaseUrl(runtimeBaseUrl);
        deployment.setConnectorBaseUrl(connectorBaseUrl);
        deployment.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        deployment.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return deployment;
    }

    private DeploymentVersionEntity version() {
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-123");
        version.setDeploymentId("dep-123");
        version.setSourceDraftId("drf-123");
        version.setVersionLabel("v1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("hash-123");
        version.setReindexRequired(false);
        version.setActionsConfigJson("""
            {
              "actions": [
                {"name": "list_products"},
                {"name": "view_cart"}
              ]
            }
            """);
        version.setEntityConfigJson("""
            {
              "ai-entities": {
                "product": {},
                "policy": {}
              }
            }
            """);
        version.setRoutingConfigJson("""
            {
              "connector": {
                "inbound-auth": {
                  "allow-unauthenticated": false,
                  "api-key": {
                    "enabled": true,
                    "header": "X-AIFABRIC-API-KEY",
                    "value": "ignored"
                  }
                }
              },
              "authz": {
                "enabled": true,
                "path": "/api/authz/check",
                "upstream": {
                  "base-url": "https://commerce.example"
                }
              },
              "actions": {
                "list_products": {},
                "view_cart": {}
              }
            }
            """);
        version.setProviderConfigJson("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai"
            }
            """);
        version.setSecurityConfigJson("""
            {
              "adminApiKeyEnabled": true
            }
            """);
        version.setActionsArtifactYaml("actions: []");
        version.setEntityArtifactYaml("ai-entities: {}");
        version.setRoutingArtifactYaml("actions: {}");
        version.setManifestJson("""
            {"deploymentId":"dep-123","versionId":"ver-123"}
            """);
        version.setPublishedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return version;
    }

    private DeploymentReleaseEntity release() {
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-123");
        release.setDeploymentId("dep-123");
        release.setDeploymentVersionId("ver-123");
        release.setStatus("ACTIVE");
        release.setVerificationStatus("PENDING");
        release.setProvisioningStatus("COMPLETED");
        release.setProvisioningTarget("RAILWAY_API");
        release.setProvisioningDetailsJson("""
            {"projectId":"project-123"}
            """);
        release.setCreatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        release.setAppliedAt(Instant.parse("2026-03-29T00:00:00Z"));
        release.setUpdatedAt(Instant.parse("2026-03-29T00:00:00Z"));
        return release;
    }
}
