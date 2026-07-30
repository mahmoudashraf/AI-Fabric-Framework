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
                    "execution": {
                      "adapterType": "mcp-tool",
                      "mcp": {
                        "serverRef": "shopify-storefront-ucp",
                        "toolName": "search_catalog",
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
}
