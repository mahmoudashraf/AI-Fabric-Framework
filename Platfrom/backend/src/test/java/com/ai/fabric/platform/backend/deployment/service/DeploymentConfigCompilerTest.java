package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentDraftEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

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
}
