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
    void compileProjectsExternalHttpDatasetIntoDeploymentLocalConnectorArtifact() throws Exception {
        DeploymentEntity deployment = deployment();
        deployment.setTenantId("tenant-neutral");
        DeploymentDraftEntity draft = draft(
            """
                {"actions":[{
                  "name":"neutral_search",
                  "route":{
                    "method":"GET",
                    "path":"/scopes/{scope}/search",
                    "connectionProfileRef":"neutral-provider",
                    "protectedResourceBindingRef":"neutral-scope",
                    "requiredCapabilityGrants":["records:read"],
                    "trustedResourcePlacements":[{"target":"PATH","field":"scope"}]
                  }
                }]}
                """,
            "{}",
            "{\"connectorApiKeyEnabled\":true}"
        );
        draft.setMarketplaceDatasetConfigJson(
            """
                {
                  "datasets": [{
                    "datasetId": "neutral-records",
                    "entityType": "neutral-record",
                    "ingestionMode": "EXTERNAL_SYNC_HTTP",
                    "datasetHash": "fixture-dataset-hash",
                    "handleRef": "plugin/neutral/tenant/tenant-neutral/neutral-records/fixture/neutral-record",
                    "syncConnector": {
                      "connectorType": "HTTP_JSON",
                      "connectionProfile": {
                        "profileId": "neutral-provider",
                        "environment": "sandbox",
                        "baseUrl": "https://provider.fixture.invalid",
                        "allowedHosts": ["provider.fixture.invalid"],
                        "auth": {
                          "strategy": "FORM_TOKEN_EXCHANGE",
                          "tokenPath": "/authenticate",
                          "credentialFields": {"key": "${FIXTURE_KEY}", "secret": "${FIXTURE_SECRET}"},
                          "tokenJsonPointer": "/token",
                          "relativeExpiryJsonPointer": "/expiresIn"
                        },
                        "ratePolicy": {"maxAttempts": 2, "retryStatuses": [429, 503]},
                        "errorMappings": [{
                          "status": 403,
                          "bodyJsonPointer": "/code",
                          "equalsValue": "MISSING_CAPABILITY",
                          "errorClass": "CAPABILITY_DENIED"
                        }],
                        "capabilityGrants": ["records:read"]
                      },
                      "protectedResource": {
                        "bindingId": "neutral-scope",
                        "connectionProfileRef": "neutral-provider",
                        "environment": "sandbox",
                        "resourceType": "scope",
                        "resourceId": "scope-7",
                        "capabilityGrants": ["records:read"]
                      },
                      "httpSource": {
                        "sourceId": "neutral-source",
                        "connectionProfileRef": "neutral-provider",
                        "protectedResourceBindingRef": "neutral-scope",
                        "path": "/scopes/{scope}/records",
                        "trustedResourcePlacements": [{"target": "PATH", "field": "scope"}],
                        "pagination": {"strategy": "CURSOR", "cursorQuery": "after", "nextCursorJsonPointer": "/next"},
                        "mapping": {
                          "recordsJsonPointer": "/records",
                          "idJsonPointer": "/id",
                          "contentFields": {"title": "/title"}
                        },
                        "tombstonePolicy": {
                          "strategy": "FIELD_VALUE",
                          "operationJsonPointer": "/state",
                          "deleteValues": ["deleted"]
                        },
                        "vectorSpace": "neutral-record",
                        "entityType": "neutral-record"
                      },
                      "webhook": {
                        "sourceId": "neutral-events",
                        "method": "POST",
                        "protectedResourceBindingRef": "neutral-scope",
                        "verification": {
                          "strategy": "HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY",
                          "signatureHeader": "X-Fixture-Signature",
                          "secret": "${FIXTURE_WEBHOOK_SECRET}"
                        },
                        "eventIdJsonPointer": "/eventId",
                        "eventTypeJsonPointer": "/eventType",
                        "resourceJsonPointer": "/scope",
                        "allowedEventTypes": ["record.changed"],
                        "registrationExpected": true,
                        "manualReplayEnabled": true,
                        "reconcileDataSourceRef": "neutral-source"
                      }
                    }
                  }]
                }
                """
        );

        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = compiler.compile(
            deployment,
            draft,
            "ver-http",
            "http-v1",
            false
        );

        JsonNode routing = yamlMapper.readTree(compiled.routingArtifactYaml());
        assertThat(routing.path("connection-profiles").path("neutral-provider").path("auth")
            .path("credential-fields").path("key").asText()).isEqualTo("${FIXTURE_KEY}");
        assertThat(routing.path("connection-profiles").path("neutral-provider").path("error-mappings")
            .get(0).path("error-class").asText()).isEqualTo("CAPABILITY_DENIED");
        assertThat(routing.path("protected-resources").path("neutral-scope").path("resource-id").asText())
            .isEqualTo("scope-7");
        assertThat(routing.path("data-sources").path("neutral-source").path("vector-space").asText())
            .isEqualTo("neutral-record");
        assertThat(routing.path("data-sources").path("neutral-source").path("source-version").asText())
            .isEqualTo("fixture-dataset-hash");
        assertThat(routing.path("data-sources").path("neutral-source").path("knowledge-source-handle-ref").asText())
            .isEqualTo("plugin/neutral/tenant/tenant-neutral/neutral-records/fixture/neutral-record");
        assertThat(routing.path("data-sources").path("neutral-source").path("tombstone-policy")
            .path("strategy").asText()).isEqualTo("FIELD_VALUE");
        assertThat(routing.path("data-sources").path("neutral-source").path("tombstone-policy")
            .path("operation-json-pointer").asText()).isEqualTo("/state");
        assertThat(routing.path("data-sources").path("neutral-source").path("tombstone-policy")
            .path("delete-values").get(0).asText()).isEqualTo("deleted");
        assertThat(routing.path("webhooks").path("neutral-events").path("reconcile-data-source-ref").asText())
            .isEqualTo("neutral-source");
        assertThat(routing.path("webhooks").path("neutral-events").path("registration-expected").asBoolean())
            .isTrue();
        assertThat(routing.path("webhooks").path("neutral-events").path("manual-replay-enabled").asBoolean())
            .isTrue();
        assertThat(routing.path("runtime-data-sync").path("enabled").asBoolean()).isTrue();
        assertThat(routing.path("runtime-data-sync").path("deployment-id").asText()).isEqualTo("dep-1");
        assertThat(routing.path("runtime-data-sync").path("tenant-id").asText()).isEqualTo("tenant-neutral");
        assertThat(routing.path("actions").path("neutral_search").path("connection-profile-ref").asText())
            .isEqualTo("neutral-provider");
    }

    @Test
    void compileRejectsProviderActionWithoutCompiledAuthority() {
        DeploymentDraftEntity draft = draft(
            """
                {"actions":[{
                  "name":"unbound_provider_search",
                  "route":{
                    "method":"GET",
                    "path":"/records",
                    "connectionProfileRef":"missing-profile",
                    "protectedResourceBindingRef":"missing-binding",
                    "requiredCapabilityGrants":["records:read"],
                    "trustedResourcePlacements":[{"target":"QUERY","field":"scopeId"}]
                  }
                }]}
                """,
            "{}",
            "{\"connectorApiKeyEnabled\":true}"
        );

        assertThatThrownBy(() -> compiler.compile(
            deployment(),
            draft,
            "ver-unbound",
            "unbound",
            false
        ))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("references an unavailable connection profile");
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
        assertThat(manifest.path("aiFabricFrameworkVersion").asText()).isEqualTo("0.8.4");
        assertThat(manifest.path("entityConfigContractVersion").asText())
            .isEqualTo("AI_ENTITY_CONFIG_V0_4");
        assertThat(manifest.path("entityConfigHash").asText()).hasSize(64);
    }

    @Test
    void compileOmitsEmptyEntityMapFromSpringBoundArtifactWithoutChangingCanonicalContract() throws Exception {
        DeploymentDraftEntity draft = draft("{\"actions\":[]}", "{}", "{\"connectorApiKeyEnabled\":false}");

        DeploymentConfigCompiler.CompiledDeploymentVersion compiled = compiler.compile(
            deployment(),
            draft,
            "ver-1",
            "v1",
            false
        );

        JsonNode entityArtifact = yamlMapper.readTree(compiled.entityArtifactYaml());
        JsonNode manifest = objectMapper.readTree(compiled.manifestJson());
        assertThat(entityArtifact.has("ai-entities")).isFalse();
        assertThat(entityArtifact.path("ai-config").path("vector-dimensions").asInt()).isEqualTo(512);
        assertThat(manifest.path("entityConfig").path("ai-entities").isObject()).isTrue();
        assertThat(manifest.path("entityConfig").path("ai-entities").isEmpty()).isTrue();

        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        version.setDeploymentId("dep-1");
        version.setEntityConfigContractVersion(EntityConfigContractService.CONTRACT_VERSION_V04);
        version.setAiFabricFrameworkVersion(compiler.frameworkVersion());
        version.setEntityConfigJson(draft.getEntityConfigJson());
        version.setProviderConfigJson(draft.getProviderConfigJson());
        version.setBehaviorConfigJson(draft.getBehaviorConfigJson());
        version.setCompositionProvenanceJson(compiled.compositionProvenanceJson());
        version.setEntityArtifactYaml(compiled.entityArtifactYaml());
        version.setManifestJson(compiled.manifestJson());

        compiler.requireRuntimeArtifactCompatible(version);
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
            .hasMessageContaining("0.8.4");
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
        version.setBehaviorConfigJson(draft.getBehaviorConfigJson());
        version.setCompositionProvenanceJson(compiled.compositionProvenanceJson());
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
        deployment.setBehaviorType("CONVERSATIONAL");
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
