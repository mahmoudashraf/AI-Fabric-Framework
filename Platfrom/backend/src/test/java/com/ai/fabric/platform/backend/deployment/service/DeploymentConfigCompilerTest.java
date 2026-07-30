package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentConfigCompilerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final DeploymentConfigCompiler compiler = new DeploymentConfigCompiler(objectMapper);

    @Test
    void compileProjectsInlineActionRouteIntoRoutingArtifact() throws Exception {
        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = compiler.compile(
            deployment(),
            draft(
                """
                    {
                      "actions": [
                        {
                          "name": "search_products",
                          "description": "Search products",
                          "route": {
                            "method": "GET",
                            "url": "https://catalog.example/api/products/search",
                            "request": {
                              "query": {
                                "q": "{{params.query}}"
                              }
                            },
                            "response": {
                              "success-http-status": [200]
                            }
                          }
                        }
                      ]
                    }
                    """,
                """
                    {
                      "connector": {
                        "inbound-auth": {
                          "allow-unauthenticated": true,
                          "api-key": {
                            "enabled": false
                          }
                        }
                      }
                    }
                    """,
                """
                    {
                      "connectorApiKeyEnabled": true
                    }
                    """
            ),
            "ver-1",
            "v1",
            false
        );

        JsonNode routingArtifact = yamlMapper.readTree(compiled.routingArtifactYaml());
        JsonNode route = routingArtifact.path("actions").path("search_products");

        assertThat(route.path("url").asText()).isEqualTo("https://catalog.example/api/products/search");
        assertThat(route.path("method").asText()).isEqualTo("GET");
        assertThat(route.path("request").path("query").path("q").asText()).isEqualTo("{{params.query}}");
        assertThat(route.path("response").path("success-http-status")).hasSize(1);
        assertThat(routingArtifact.path("connector").path("inbound-auth").path("allow-unauthenticated").asBoolean()).isFalse();
    }

    @Test
    void compileMergesExplicitRoutingOverridesOverInlineActionRoute() throws Exception {
        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = compiler.compile(
            deployment(),
            draft(
                """
                    {
                      "actions": [
                        {
                          "name": "search_products",
                          "description": "Search products",
                          "route": {
                            "method": "GET",
                            "path": "/api/products/search",
                            "request": {
                              "query": {
                                "q": "{{params.query}}"
                              }
                            },
                            "response": {
                              "success-http-status": [200]
                            }
                          }
                        }
                      ]
                    }
                    """,
                """
                    {
                      "connector": {
                        "inbound-auth": {
                          "allow-unauthenticated": true,
                          "api-key": {
                            "enabled": false
                          }
                        },
                        "upstream": {
                          "base-url": "https://customer.example"
                        }
                      },
                      "actions": {
                        "search_products": {
                          "url": "https://catalog.example/api/search",
                          "response": {
                            "message": "Products"
                          }
                        }
                      }
                    }
                    """,
                """
                    {
                      "connectorApiKeyEnabled": false
                    }
                    """
            ),
            "ver-1",
            "v1",
            false
        );

        JsonNode routingArtifact = yamlMapper.readTree(compiled.routingArtifactYaml());
        JsonNode route = routingArtifact.path("actions").path("search_products");

        assertThat(route.path("url").asText()).isEqualTo("https://catalog.example/api/search");
        assertThat(route.path("path").isMissingNode()).isTrue();
        assertThat(route.path("method").asText()).isEqualTo("GET");
        assertThat(route.path("request").path("query").path("q").asText()).isEqualTo("{{params.query}}");
        assertThat(route.path("response").path("success-http-status")).hasSize(1);
        assertThat(route.path("response").path("message").asText()).isEqualTo("Products");
    }

    @Test
    void compileNormalizesAndRoundTripsV04EntityArtifact() throws Exception {
        DeploymentDraftEntity draft = draft("{\"actions\":[]}", "{}", "{\"connectorApiKeyEnabled\":false}");
        draft.setEntityConfigJson(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "document": {
                      "indexing": {
                        "enabled": true
                      },
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["RAG_CONTEXT", "SEMANTIC_SEARCH"]
                        }
                      ],
                      "marketplaceManaged": true,
                      "marketplacePluginId": "plugin-1",
                      "marketplaceInstallId": "install-1",
                      "marketplacePluginVersion": "1.0.0"
                    }
                  }
                }
                """
        );

        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = compiler.compile(
            deployment(),
            draft,
            "ver-1",
            "v1",
            false
        );

        JsonNode entityArtifact = yamlMapper.readTree(compiled.entityArtifactYaml());
        JsonNode document = entityArtifact.path("ai-entities").path("document");
        JsonNode manifest = objectMapper.readTree(compiled.manifestJson());
        assertThat(document.path("indexing").path("max-characters").asInt()).isEqualTo(8000);
        assertThat(document.path("searchable-fields").get(0).path("preprocessing").asText())
            .isEqualTo("NORMALIZE");
        assertThat(document.has("marketplaceManaged")).isFalse();
        assertThat(manifest.path("aiFabricFrameworkVersion").asText()).isEqualTo("0.5.0");
        assertThat(manifest.path("entityConfigContractVersion").asText())
            .isEqualTo("AI_ENTITY_CONFIG_V0_4");
        assertThat(manifest.path("entityConfigHash").asText()).hasSize(64);
    }

    @Test
    void compileRejectsLegacyEntityPropertiesBeforeYamlGeneration() {
        DeploymentDraftEntity draft = draft("{\"actions\":[]}", "{}", "{\"connectorApiKeyEnabled\":false}");
        draft.setEntityConfigJson(
            """
                {
                  "ai-config": {
                    "vector-dimensions": 512
                  },
                  "ai-entities": {
                    "document": {
                      "indexable": true,
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH"]
                        }
                      ]
                    }
                  }
                }
                """
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> compiler.compile(
            deployment(),
            draft,
            "ver-1",
            "v1",
            false
        ))
            .isInstanceOf(com.ai.fabric.platform.backend.deployment.entityconfig.EntityConfigContractException.class)
            .hasMessageContaining("LEGACY_ENTITY_PROPERTY_REMOVED");
    }

    @Test
    void configHashDoesNotDependOnInputJsonKeyOrder() {
        DeploymentDraftEntity first = draft("{\"actions\":[]}", "{}", "{\"connectorApiKeyEnabled\":false}");
        first.setEntityConfigJson(validDocumentEntityConfig(false));
        DeploymentDraftEntity second = draft("{\"actions\":[]}", "{}", "{\"connectorApiKeyEnabled\":false}");
        second.setEntityConfigJson(validDocumentEntityConfig(true));

        String firstHash = compiler.compile(deployment(), first, "ver-1", "v1", false).configHash();
        String secondHash = compiler.compile(deployment(), second, "ver-2", "v2", false).configHash();

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void runtimeCompatibilityAcceptsMatchingV04ArtifactAndManifest() {
        compiler.requireRuntimeArtifactCompatible(compiledVersion());
    }

    @Test
    void runtimeCompatibilityRejectsHistoricalContractAndFrameworkVersion() {
        DeploymentVersionEntity version = compiledVersion();
        version.setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V03);
        version.setAiFabricFrameworkVersion("0.3.1");

        assertThatThrownBy(() -> compiler.requireRuntimeArtifactCompatible(version))
            .hasMessageContaining("AI_FABRIC_RUNTIME_ARTIFACT_INCOMPATIBLE")
            .hasMessageContaining("AI_ENTITY_CONFIG_V0_3")
            .hasMessageContaining("0.3.1")
            .hasMessageContaining("0.5.0");
    }

    @Test
    void runtimeCompatibilityRejectsArtifactThatDoesNotMatchPersistedProjection() throws Exception {
        DeploymentVersionEntity version = compiledVersion();
        ObjectNode artifact = (ObjectNode) yamlMapper.readTree(version.getEntityArtifactYaml());
        ((ObjectNode) artifact.path("ai-entities").path("document").path("searchable-fields").get(0))
            .put("name", "title");
        version.setEntityArtifactYaml(yamlMapper.writeValueAsString(artifact));

        assertThatThrownBy(() -> compiler.requireRuntimeArtifactCompatible(version))
            .hasMessageContaining("AI_FABRIC_RUNTIME_ARTIFACT_INCOMPATIBLE")
            .hasMessageContaining("artifact does not match");
    }

    @Test
    void runtimeCompatibilityRejectsTamperedManifestHash() throws Exception {
        DeploymentVersionEntity version = compiledVersion();
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(version.getManifestJson());
        manifest.put("entityConfigHash", "tampered");
        version.setManifestJson(objectMapper.writeValueAsString(manifest));

        assertThatThrownBy(() -> compiler.requireRuntimeArtifactCompatible(version))
            .hasMessageContaining("AI_FABRIC_RUNTIME_ARTIFACT_INCOMPATIBLE")
            .hasMessageContaining("entityConfigHash")
            .hasMessageContaining("tampered");
    }

    private DeploymentVersionEntity compiledVersion() {
        DeploymentEntity deployment = deployment();
        DeploymentDraftEntity draft = draft("{\"actions\":[]}", "{}", "{\"connectorApiKeyEnabled\":false}");
        draft.setEntityConfigJson(validDocumentEntityConfig(false));
        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = compiler.compile(
            deployment,
            draft,
            "ver-1",
            "v1",
            false
        );
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId(deployment.getId());
        version.setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V04);
        version.setAiFabricFrameworkVersion(compiler.frameworkVersion());
        version.setEntityConfigJson(draft.getEntityConfigJson());
        version.setProviderConfigJson(draft.getProviderConfigJson());
        version.setEntityArtifactYaml(compiled.entityArtifactYaml());
        version.setManifestJson(compiled.manifestJson());
        return version;
    }

    private DeploymentEntity deployment() {
        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setName("Sample");
        deployment.setEnvironmentName("dev");
        deployment.setTemplateId("template");
        return deployment;
    }

    private DeploymentDraftEntity draft(String actionsConfigJson,
                                        String routingConfigJson,
                                        String securityConfigJson) {
        DeploymentDraftEntity draft = new DeploymentDraftEntity();
        draft.setActionsConfigJson(actionsConfigJson);
        draft.setEntityConfigJson(
            """
                {
                  "ai-config": { "vector-dimensions": 512 },
                  "ai-entities": {}
                }
                """
        );
        draft.setRoutingConfigJson(routingConfigJson);
        draft.setProviderConfigJson(
            """
                {
                  "llmProvider": "openai",
                  "embeddingProvider": "openai",
                  "vectorStrategy": "lucene",
                  "runtimeProfile": "runtime-dev",
                  "connectorProfile": "connector-hosted"
                }
                """
        );
        draft.setSecurityConfigJson(securityConfigJson);
        draft.setPromptConfigJson("{}");
        draft.setKnowledgeSourceConfigJson("{}");
        draft.setShellConfigJson("{}");
        return draft;
    }

    private String validDocumentEntityConfig(boolean reverseRootOrder) {
        if (reverseRootOrder) {
            return """
                {
                  "ai-entities": {
                    "document": {
                      "searchable-fields": [
                        {
                          "destinations": ["RAG_CONTEXT", "SEMANTIC_SEARCH"],
                          "name": "content"
                        }
                      ],
                      "indexing": {
                        "enabled": true
                      }
                    }
                  },
                  "ai-config": {
                    "vector-dimensions": 512
                  }
                }
                """;
        }
        return """
            {
              "ai-config": {
                "vector-dimensions": 512
              },
              "ai-entities": {
                "document": {
                  "indexing": {
                    "enabled": true
                  },
                  "searchable-fields": [
                    {
                      "name": "content",
                      "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"]
                    }
                  ]
                }
              }
            }
            """;
    }
}
