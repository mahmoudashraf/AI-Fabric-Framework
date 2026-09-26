package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarketplaceManifestServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MarketplaceManifestService service = new MarketplaceManifestService(objectMapper);

    @Test
    void sourceAttestedAgenticSpecialistManifestIsAccepted() {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
            specialistPlugin(),
            specialistVersion(validAgenticSpecialistManifest())
        );

        assertThat(parsed.pluginType()).isEqualTo("SPECIALIST");
        assertThat(parsed.contributions().specialistCompatibleBehaviorTypes())
            .containsExactly("AGENTIC_SPECIALIST_TEAM");
        assertThat(parsed.contributions().specialistBundleRefs())
            .extracting(com.ai.fabric.platform.backend.marketplace.model.MarketplaceSpecialistBundleRefSummary::bundleId)
            .containsExactly("deployment-intelligence-team@1");
        assertThat(parsed.permissions().contributesSpecialists()).isTrue();
    }

    @Test
    void previouslyPublishedAgenticSpecialistManifestRemainsAccepted() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validAgenticSpecialistManifest());
        ((ObjectNode) manifest.path("contributions").path("specialist").path("sourceBundleRefs").get(0))
            .put(
                "contentHash",
                "sha256:00b9f8f582195eb18857361d94c02c48ab703e72a9a5d70d9e4c2cd8ea51a0d8"
            );

        MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
            specialistPlugin(),
            specialistVersion(objectMapper.writeValueAsString(manifest))
        );

        assertThat(parsed.contributions().specialistBundleRefs())
            .extracting(com.ai.fabric.platform.backend.marketplace.model.MarketplaceSpecialistBundleRefSummary::contentHash)
            .containsExactly("sha256:00b9f8f582195eb18857361d94c02c48ab703e72a9a5d70d9e4c2cd8ea51a0d8");
    }

    @Test
    void specialistManifestRejectsInlineExecutableDefinitions() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validAgenticSpecialistManifest());
        ((ObjectNode) manifest.path("contributions").path("specialist"))
            .putArray("definitions")
            .addObject()
            .put("id", "unreviewed-worker@1");

        assertThatThrownBy(() -> service.parseAndValidate(
            specialistPlugin(),
            specialistVersion(objectMapper.writeValueAsString(manifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unsupported field")
            .hasMessageContaining("definitions");
    }

    @Test
    void specialistManifestRejectsAlteredSourceBundleHash() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validAgenticSpecialistManifest());
        ((ObjectNode) manifest.path("contributions").path("specialist").path("sourceBundleRefs").get(0))
            .put("contentHash", "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");

        assertThatThrownBy(() -> service.parseAndValidate(
            specialistPlugin(),
            specialistVersion(objectMapper.writeValueAsString(manifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not match the reviewed behavior contract");
    }

    @Test
    void mcpToolActionRequiresExecutionMcpServerRefAndToolName() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "actions": [
                  {
                    "actionId": "shopify_search_catalog",
                    "adapterType": "mcp-tool",
                    "description": "Search catalog",
                    "readOnly": true,
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "shopify-storefront-ucp"
                      }
                    }
                  }
                ]
              }
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("execution.mcp.toolName is required");
    }

    @Test
    void mcpToolActionManifestIsAcceptedWhenExecutionMcpMetadataIsComplete() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "actions": [
                  {
                    "actionId": "shopify_search_catalog",
                    "adapterType": "mcp-tool",
                    "description": "Search catalog",
                    "readOnly": true,
                    "params": [
                      {"name": "query", "type": "STRING", "required": true}
                    ],
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "shopify-storefront-ucp",
                        "toolName": "search_catalog",
                        "dispatchMode": "CONNECTOR",
                        "requiredAnyParams": ["query"],
                        "requiredAnyArguments": ["catalog.query"],
                        "argumentTemplate": {
                          "catalog": {
                            "query": "{{params.query}}"
                          }
                        }
                      }
                    }
                  }
                ]
              }
            }
            """;

        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            service.parseAndValidate(actionPlugin(), version(manifest));

        assertThat(parsed.contributions().actionIds()).containsExactly("shopify_search_catalog");
    }

    @Test
    void mcpToolActionRejectsUnknownDispatchMode() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "actions": [
                  {
                    "actionId": "inventory_search",
                    "adapterType": "mcp-tool",
                    "readOnly": true,
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "inventory-mcp",
                        "toolName": "inventory.search",
                        "dispatchMode": "UNSAFE_FALLBACK"
                      }
                    }
                  }
                ]
              }
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("execution.mcp.dispatchMode must be DIRECT_GATEWAY or CONNECTOR");
    }

    @Test
    void mcpToolActionRejectsRequiredAnyParamsThatAreNotDeclared() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "actions": [
                  {
                    "actionId": "inventory_search",
                    "adapterType": "mcp-tool",
                    "readOnly": true,
                    "params": [{"name": "query", "type": "STRING"}],
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "inventory-mcp",
                        "toolName": "inventory.search",
                        "requiredAnyParams": ["missing_query"],
                        "argumentTemplate": {"query": "{{params.query}}"}
                      }
                    }
                  }
                ]
              }
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("requiredAnyParams references undeclared action parameter 'missing_query'");
    }

    @Test
    void mcpToolActionRejectsRequiredAnyArgumentsMissingFromRenderedTemplate() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "actions": [
                  {
                    "actionId": "inventory_search",
                    "adapterType": "mcp-tool",
                    "readOnly": true,
                    "params": [{"name": "query", "type": "STRING"}],
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "inventory-mcp",
                        "toolName": "inventory.search",
                        "requiredAnyArguments": ["catalog.query"],
                        "argumentTemplate": {"query": "{{params.query}}"}
                      }
                    }
                  }
                ]
              }
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("requiredAnyArguments path 'catalog.query' is not emitted");
    }

    @Test
    void mcpServerContributionValidatesTransportAllowlistedAuthAndResponseMapping() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "mcpServers": [
                  {
                    "serverRef": "inventory-mcp",
                    "transport": "streamable-http",
                    "endpointUrlField": "inventoryMcpEndpoint",
                    "allowedTools": ["inventory.search"],
                    "auth": {
                      "mode": "API_KEY_HEADER_SECRET",
                      "headerName": "X-MCP-API-Key",
                      "secretRefField": "inventoryMcpApiKeyRef"
                    },
                    "verification": {
                      "mode": "INITIALIZE_AND_TOOLS_LIST",
                      "schemaDriftPolicy": "WARN_ONLY"
                    }
                  }
                ],
                "actions": [
                  {
                    "actionId": "inventory_search",
                    "adapterType": "mcp-tool",
                    "readOnly": true,
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "inventory-mcp",
                        "toolName": "inventory.search",
                        "toolSchemaHash": "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        "schemaDriftPolicy": "DISABLE_ACTION",
                        "argumentTemplate": {"query": "{{params.query}}"},
                        "responseMapping": {
                          "resultPath": "$.structuredContent.products",
                          "contentPath": "$.content[0].text"
                        }
                      }
                    }
                  }
                ]
              }
            }
            """;

        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            service.parseAndValidate(actionPlugin(), version(manifest));

        assertThat(parsed.contributions().actionIds()).containsExactly("inventory_search");
    }

    @Test
    void mcpServerContributionRejectsBlockedApiKeyHeaderNames() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "mcpServers": [
                  {
                    "serverRef": "inventory-mcp",
                    "transport": "STREAMABLE_HTTP",
                    "endpointUrlField": "inventoryMcpEndpoint",
                    "auth": {
                      "mode": "API_KEY_HEADER_SECRET",
                      "headerName": "Authorization",
                      "secretRefField": "inventoryMcpApiKeyRef"
                    }
                  }
                ],
                "actions": [
                  {
                    "actionId": "inventory_search",
                    "adapterType": "mcp-tool",
                    "readOnly": true,
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "inventory-mcp",
                        "toolName": "inventory.search"
                      }
                    }
                  }
                ]
              }
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("auth headerName is not allowlisted");
    }

    @Test
    void mcpActionMustReferenceDeclaredServerWhenServersAreProvided() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {
                "mcpServers": [
                  {
                    "serverRef": "inventory-mcp",
                    "transport": "STREAMABLE_HTTP",
                    "endpointUrl": "https://inventory.example/mcp"
                  }
                ],
                "actions": [
                  {
                    "actionId": "inventory_search",
                    "adapterType": "mcp-tool",
                    "readOnly": true,
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "other-mcp",
                        "toolName": "inventory.search"
                      }
                    }
                  }
                ]
              }
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("does not match contributions.mcpServers");
    }

    @Test
    void connectorHttpActionManifestStillAllowsRouteBackedActions() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {
                "contributesActions": true,
                "requiresExternalHttpExecution": true
              },
              "contributions": {
                "actions": [
                  {
                    "actionId": "list_products",
                    "adapterType": "connector-http",
                    "description": "List products",
                    "readOnly": true,
                    "route": {
                      "method": "POST",
                      "path": "/actions/execute"
                    }
                  }
                ]
              }
            }
            """;

        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            service.parseAndValidate(actionPlugin(), version(manifest));

        assertThat(parsed.contributions().actionIds()).containsExactly("list_products");
    }

    @Test
    void dataManifestAcceptsTypedV04EntityContributionWithTenantMetadata() {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            service.parseAndValidate(
                dataPlugin(),
                dataVersion(validDataManifest())
            );

        assertThat(parsed.datasets())
            .extracting(
                MarketplaceManifestService.ParsedMarketplaceDatasetDefinition::entityType
            )
            .containsExactly("support-policy");
    }

    @Test
    void dataManifestRejectsLegacyEntityContribution() throws Exception {
        ObjectNode manifestNode =
            (ObjectNode) objectMapper.readTree(validDataManifest());
        ObjectNode entity = (ObjectNode) manifestNode
            .path("contributions")
            .path("entityConfig")
            .path("ai-entities")
            .path("support-policy");
        entity.removeAll();
        entity.put("entity-type", "support-policy");
        entity.put("auto-embedding", true);
        entity.put("indexable", true);
        entity.put("enable-search", true);
        String manifest = objectMapper.writeValueAsString(manifestNode);

        assertThatThrownBy(() ->
            service.parseAndValidate(dataPlugin(), dataVersion(manifest))
        )
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("AI_ENTITY_CONFIG_V0_4")
            .hasMessageContaining("LEGACY_ENTITY_PROPERTY_REMOVED");
    }

    @Test
    void documentDataManifestAcceptsOnlyTheBoundedCustomerStorageContract() {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
            dataPlugin(),
            dataVersion(validDocumentDataManifest())
        );

        assertThat(parsed.datasets()).singleElement().satisfies(dataset -> {
            assertThat(dataset.entityType()).isEqualTo("document");
            assertThat(dataset.storageScope()).isEqualTo("CUSTOMER_MANAGED");
            assertThat(dataset.sharingScope()).isEqualTo("DEPLOYMENT_ONLY");
            assertThat(dataset.ingestionMode()).isEqualTo("EXTERNAL_DOCUMENT_STORAGE");
            assertThat(dataset.updateStrategy()).isEqualTo("VERSIONED_REPLACE");
            assertThat(dataset.connectorType()).isEqualTo("S3_COMPATIBLE_OBJECT_STORAGE");
            assertThat(dataset.connectionRefField()).isEqualTo("documentStorageBindingRef");
            assertThat(dataset.sourceConnector().path("deleteSourceOnRemoval").asBoolean()).isFalse();
            assertThat(dataset.documentPolicy().path("maxSourceBytes").asLong()).isEqualTo(1_048_576L);
        });
    }

    @Test
    void documentDataManifestRejectsWriteAuthorityAndStaticConnectorEndpoint() throws Exception {
        ObjectNode writeManifest = (ObjectNode) objectMapper.readTree(validDocumentDataManifest());
        ObjectNode connector = (ObjectNode) writeManifest.path("contributions").path("datasets").get(0)
            .path("sourceConnector");
        connector.withArray("allowedOperations").add("DELETE");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(writeManifest))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("only LIST, READ, and HEAD");

        ObjectNode endpointManifest = (ObjectNode) objectMapper.readTree(validDocumentDataManifest());
        ((ObjectNode) endpointManifest.path("contributions").path("datasets").get(0).path("sourceConnector"))
            .put("endpoint", "https://storage.example");
        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(endpointManifest))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unsupported field: endpoint");
    }

    @Test
    void documentDataManifestRejectsProtectedMetadataAndUnconfirmedIndexing() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validDocumentDataManifest());
        ObjectNode policy = (ObjectNode) manifest.path("contributions").path("datasets").get(0)
            .path("documentPolicy");
        policy.withArray("allowedMetadataKeys").add("sourceVersion");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(manifest))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("invalid or protected key");

        ObjectNode noConfirmation = (ObjectNode) objectMapper.readTree(validDocumentDataManifest());
        ((ObjectNode) noConfirmation.path("contributions").path("datasets").get(0).path("documentPolicy"))
            .put("initialIndexRequiresConfirmation", false);
        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(noConfirmation))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("explicit initial indexing confirmation");
    }

    private MarketplacePluginEntity actionPlugin() {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId("mkp-action-test");
        plugin.setSlug("action-test");
        plugin.setDisplayName("Action Test");
        plugin.setPluginType("ACTION");
        plugin.setPublisherSlug("loom");
        plugin.setPublisherDisplayName("Loom");
        plugin.setShortDescription("Test action plugin.");
        plugin.setStatus("PUBLISHED");
        plugin.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        plugin.setUpdatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        return plugin;
    }

    private MarketplacePluginEntity dataPlugin() {
        MarketplacePluginEntity plugin = actionPlugin();
        plugin.setId("mkp-data-test");
        plugin.setSlug("data-test");
        plugin.setDisplayName("Data Test");
        plugin.setPluginType("DATA");
        plugin.setShortDescription("Test data plugin.");
        return plugin;
    }

    private MarketplacePluginEntity specialistPlugin() {
        MarketplacePluginEntity plugin = actionPlugin();
        plugin.setId("mkp-specialist-test");
        plugin.setSlug("specialist-test");
        plugin.setDisplayName("Specialist Test");
        plugin.setPluginType("SPECIALIST");
        plugin.setShortDescription("Test specialist plugin.");
        return plugin;
    }

    private MarketplacePluginVersionEntity version(String manifestJson) {
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId("mkv-action-test-v1");
        version.setPluginId("mkp-action-test");
        version.setVersion("1.0.0");
        version.setReleaseChannel("stable");
        version.setStatus("PUBLISHED");
        version.setManifestJson(manifestJson);
        version.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        version.setPublishedAt(Instant.parse("2026-05-04T00:00:00Z"));
        return version;
    }

    private MarketplacePluginVersionEntity dataVersion(String manifestJson) {
        MarketplacePluginVersionEntity version = version(manifestJson);
        version.setId("mkv-data-test-v1");
        version.setPluginId("mkp-data-test");
        return version;
    }

    private MarketplacePluginVersionEntity specialistVersion(String manifestJson) {
        MarketplacePluginVersionEntity version = version(manifestJson);
        version.setId("mkv-specialist-test-v1");
        version.setPluginId("mkp-specialist-test");
        return version;
    }

    private String validAgenticSpecialistManifest() {
        return """
            {
              "schemaVersion": 1,
              "pluginType": "SPECIALIST",
              "compatibility": {"requiredCapabilities": ["specialists"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesSpecialists": true},
              "contributions": {
                "specialist": {
                  "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
                  "compatibleBehaviorTypes": ["AGENTIC_SPECIALIST_TEAM"],
                  "sourceBundleRefs": [
                    {
                      "bundleId": "deployment-intelligence-team@1",
                      "contractVersion": "LOOMAI_SOURCE_ATTESTED_SPECIALIST_BUNDLE_V1",
                      "contentHash": "sha256:ab1a1185dbe5f8ba5dc6c67c10c196bd9a569f211c537a39efb2d47fef05a025",
                      "specialistRefs": [
                        "deployment-intelligence-manager@1",
                        "deployment-knowledge-specialist@1",
                        "deployment-runtime-state-specialist@1"
                      ],
                      "chainRefs": ["deployment-intelligence-team@1"]
                    }
                  ],
                  "requiredRuntimeCapabilityIds": [
                    "ai-fabric-execution",
                    "specialist-chains",
                    "jdbc-specialist-chain-state"
                  ],
                  "requiredMigrationIds": ["ai-specialist-chain-execution-v1"],
                  "requiredSecretNames": [],
                  "verificationPackIds": ["agentic-specialist-team-v1"],
                  "unsupportedClaims": ["No customer executable definitions."]
                }
              }
            }
            """;
    }

    private String validDataManifest() {
        return """
            {
              "schemaVersion": 1,
              "pluginType": "DATA",
              "compatibility": {
                "requiredCapabilities": ["knowledgeSources"]
              },
              "pricing": {"pricingModel": "FREE"},
              "permissions": {
                "contributesKnowledgeSources": true,
                "requiresSharedDatasetAccess": true
              },
              "contributions": {
                "entityConfig": {
                  "ai-entities": {
                    "support-policy": {
                      "indexing": {"enabled": true, "max-characters": 8000},
                      "analysis": {"enabled": false, "after": []},
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "max-length": 8000,
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true,
                          "sanitize-pii": false
                        }
                      ]
                    }
                  }
                },
                "datasets": [
                  {
                    "datasetId": "policy-seed",
                    "entityType": "support-policy",
                    "storageScope": "PLUGIN_SCOPED",
                    "sharingScope": "TENANT_SHARED",
                    "ingestionMode": "PACKAGED_SEED",
                    "updateStrategy": "UPSERT_BY_ID",
                    "seedDatasetRef": "classpath:marketplace/policy.jsonl"
                  }
                ],
                "knowledgeSources": [
                  {
                    "sourceType": "shared-index",
                    "sourceKey": "policy",
                    "datasetRef": "policy-seed",
                    "entityType": "support-policy",
                    "attributionLabel": "Policy data"
                  }
                ]
              }
            }
            """;
    }

    private String validDocumentDataManifest() {
        return """
            {
              "schemaVersion": 1,
              "pluginType": "DATA",
              "compatibility": {"requiredCapabilities": ["knowledgeSources"]},
              "pricing": {"pricingModel": "FREE"},
              "installForm": [
                {"id": "documentStorageBindingRef", "label": "Binding", "type": "text", "required": true}
              ],
              "permissions": {
                "contributesKnowledgeSources": true,
                "requiresSharedDatasetAccess": false
              },
              "contributions": {
                "entityConfig": {
                  "ai-entities": {
                    "document": {
                      "indexing": {"enabled": true, "max-characters": 8000},
                      "analysis": {"enabled": false, "after": []},
                      "searchable-fields": [
                        {
                          "name": "content",
                          "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                          "preprocessing": "CLEAN",
                          "max-length": 8000,
                          "priority": 100,
                          "required": true
                        }
                      ],
                      "metadata-fields": [
                        {
                          "name": "tenantId",
                          "data-type": "ID",
                          "destinations": ["VECTOR_METADATA"],
                          "priority": 100,
                          "required": true,
                          "sanitize-pii": false
                        }
                      ]
                    }
                  }
                },
                "datasets": [
                  {
                    "datasetId": "document-knowledge",
                    "entityType": "document",
                    "storageScope": "CUSTOMER_MANAGED",
                    "sharingScope": "DEPLOYMENT_ONLY",
                    "ingestionMode": "EXTERNAL_DOCUMENT_STORAGE",
                    "updateStrategy": "VERSIONED_REPLACE",
                    "handleTemplate": "documents/{deploymentId}/document-knowledge",
                    "sourceConnector": {
                      "connectorType": "S3_COMPATIBLE_OBJECT_STORAGE",
                      "storageOwnership": "CUSTOMER_MANAGED",
                      "bindingRefField": "documentStorageBindingRef",
                      "allowedOperations": ["LIST", "READ", "HEAD"],
                      "deleteSourceOnRemoval": false
                    },
                    "documentPolicy": {
                      "allowedMediaTypes": ["text/plain", "application/json"],
                      "allowedExtensions": [".txt", ".json"],
                      "jsonContentKeys": ["content", "text", "body"],
                      "maxSourceBytes": 1048576,
                      "maxSources": 100,
                      "maxTotalIndexedBytes": 104857600,
                      "maxChunksPerSource": 100,
                      "maxChunkCharacters": 8000,
                      "maxTotalCharacters": 250000,
                      "previewMaxChunks": 10,
                      "previewMaxCharactersPerChunk": 500,
                      "evidenceRetentionDays": 30,
                      "commandRetentionDays": 30,
                      "retentionBatchSize": 100,
                      "allowedMetadataKeys": ["originalFilename", "locale", "sourceCategory"],
                      "initialIndexRequiresConfirmation": true,
                      "trustedAutoIndexingAllowed": false
                    }
                  }
                ],
                "knowledgeSources": [
                  {
                    "sourceType": "deployment-private-vector",
                    "sourceKey": "document-knowledge",
                    "datasetRef": "document-knowledge",
                    "entityType": "document",
                    "attributionLabel": "Approved documents"
                  }
                ]
              }
            }
            """;
    }
}
