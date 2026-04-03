package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.config.PlatformVerificationProperties;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVerificationRunEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentArtifactBundleSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivityProbeSummary;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderConnectivitySummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightCheckSummary;
import com.ai.fabric.platform.backend.deployment.model.RailwayPreflightSummary;
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
            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped."
                )
            );

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
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService
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

    @Test
    void verifyPreApplyBlocksWhenManagedSecretIsMissing() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")).thenReturn(false);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                java.util.List.of(
                    new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API"),
                    new RailwayPreflightCheckSummary("public_base_url", "PASSED", "Public base URL is reachable.", "https://platform.example")
                )
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "openai",
                    "lucene",
                    "LOCAL_MANAGED",
                    false,
                    "NONE",
                    java.util.List.of(),
                    "Platform-managed external vector provisioning is not enabled for this draft.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "local_vector_backend",
                            "Local vector backend",
                            "SKIPPED",
                            "lucene",
                            "Selected vector backend is local to the runtime and does not require an external vendor connectivity probe."
                        )
                    ),
                    "0 ready, 0 blocked, 0 failed, 1 skipped."
                )
            );

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
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService
            );

            DeploymentVerificationRunEntity run = service.verify(
                deployment("https://runtime.example", "https://connector.example"),
                version(),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses)
                .containsEntry("actions_artifact_fetch_probe", "PASSED")
                .containsEntry("routing_artifact_fetch_probe", "PASSED")
                .containsEntry("railway_preflight_provisioning_mode", "PASSED")
                .containsEntry("admin_api_key_available", "FAILED");
        } finally {
            artifactServer.stop(0);
        }
    }

    @Test
    void verifyPreApplyFailsWhenExternalVendorConnectivityProbeFails() throws Exception {
        HttpServer artifactServer = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            artifactServer.createContext("/artifacts/ai-actions.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-entity-config.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/actions-routing.yml", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/ai-prompt-config.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.createContext("/artifacts/deployment-manifest.json", exchange -> writeJson(exchange, 200, "{\"ok\":true}"));
            artifactServer.start();

            String baseUrl = "http://127.0.0.1:" + artifactServer.getAddress().getPort();
            DeploymentArtifactBundleSummary artifacts = new DeploymentArtifactBundleSummary(
                "dep-123",
                "ver-123",
                "v1",
                "hash-123",
                baseUrl + "/artifacts/ai-actions.yml",
                baseUrl + "/artifacts/ai-entity-config.yml",
                baseUrl + "/artifacts/actions-routing.yml",
                baseUrl + "/artifacts/ai-prompt-config.json",
                baseUrl + "/artifacts/deployment-manifest.json"
            );

            PlatformSecretService platformSecretService = mock(PlatformSecretService.class);
            when(platformSecretService.isSecretPresent("OPENAI_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("ACTIONS_CONNECTOR_API_KEY")).thenReturn(true);
            when(platformSecretService.isSecretPresent("APP_ADMIN_API_KEY")).thenReturn(true);

            DeploymentArtifactService artifactService = mock(DeploymentArtifactService.class);
            when(artifactService.toBundleSummary(any())).thenReturn(artifacts);

            RailwayPreflightService railwayPreflightService = mock(RailwayPreflightService.class);
            when(railwayPreflightService.run()).thenReturn(new RailwayPreflightSummary(
                "RAILWAY_API",
                true,
                Instant.parse("2026-03-31T00:00:00Z").toString(),
                "https://platform.example",
                "workspace-123",
                "AI Fabric",
                "mahmoudashraf/AI-Fabric-Framework",
                "Platformv-V2",
                java.util.List.of(
                    new RailwayPreflightCheckSummary("provisioning_mode", "PASSED", "Provisioning mode is ready.", "RAILWAY_API")
                )
            ));
            DeploymentProviderConnectivityService deploymentProviderConnectivityService = mock(DeploymentProviderConnectivityService.class);
            when(deploymentProviderConnectivityService.probe(any(), any(), any(), any())).thenReturn(
                new DeploymentProviderConnectivitySummary(
                    "dep-123",
                    "Sample Commerce Dev",
                    "openai",
                    "rest",
                    "pinecone",
                    "PLATFORM_MANAGED",
                    true,
                    "MANAGED_SERVERLESS_INDEX",
                    java.util.List.of("dep-123 (aws/eu-west-1)"),
                    "Apply will create or reconcile the Pinecone serverless index for this deployment.",
                    java.util.List.of(
                        new DeploymentProviderConnectivityProbeSummary(
                            "pinecone_control_plane",
                            "Pinecone control plane",
                            "FAILED",
                            "https://api.pinecone.io/indexes",
                            "Pinecone control plane responded with HTTP 503."
                        )
                    ),
                    "0 ready, 0 blocked, 1 failed, 0 skipped."
                )
            );

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
                artifactService,
                railwayPreflightService,
                deploymentProviderConnectivityService
            );

            DeploymentVerificationRunEntity run = service.verify(
                deployment("https://runtime.example", "https://connector.example"),
                version("""
                    {
                      "llmProvider": "openai",
                      "embeddingProvider": "rest",
                      "vectorStrategy": "pinecone"
                    }
                    """),
                release(),
                "PRE_APPLY"
            );

            JsonNode checks = objectMapper.readTree(run.getChecksJson());
            Map<String, String> statuses = StreamSupport.stream(checks.spliterator(), false)
                .collect(Collectors.toMap(
                    check -> check.path("name").asText(),
                    check -> check.path("status").asText(),
                    (left, right) -> right,
                    LinkedHashMap::new
                ));

            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(statuses)
                .containsEntry("provider_connectivity_summary", "FAILED")
                .containsEntry("provider_connectivity_pinecone_control_plane", "FAILED");
        } finally {
            artifactServer.stop(0);
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
        return version("""
            {
              "llmProvider": "openai",
              "embeddingProvider": "openai"
            }
            """);
    }

    private DeploymentVersionEntity version(String providerConfigJson) {
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
        version.setProviderConfigJson(providerConfigJson);
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
