package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.JsonNode;
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
    void connectorHttpActionManifestAllowsDeploymentLocalSourceProjectionRoutes() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true, "requiresExternalHttpExecution": false},
              "contributions": {"actions": [{
                "actionId": "inventory_search",
                "adapterType": "connector-http",
                "readOnly": true,
                "route": {
                  "sourceProjection": {
                    "sourceRef": "inventory-source",
                    "filters": [
                      {"param": "query", "fields": ["name"], "operator": "EQUALS_IGNORE_CASE"},
                      {"param": "maxPrice", "fields": ["price"], "operator": "NUMBER_LESS_THAN_OR_EQUAL", "absentValues": ["0"]}
                    ],
                    "outputFields": ["recordId", "name"],
                    "defaultLimit": 10,
                    "maxLimit": 25,
                    "requireSuccessfulSync": true,
                    "maxStalenessSeconds": 1800
                  },
                  "response": {"result": {"results": "{{body.results}}"}}
                }
              }]}
            }
            """;

        MarketplaceManifestService.ParsedMarketplaceManifest parsed =
            service.parseAndValidate(actionPlugin(), version(manifest));

        assertThat(parsed.contributions().actionIds()).containsExactly("inventory_search");
    }

    @Test
    void sourceProjectionActionManifestRejectsNonNumericAbsentValueForNumericFilter() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true, "requiresExternalHttpExecution": false},
              "contributions": {"actions": [{
                "actionId": "inventory_search",
                "adapterType": "connector-http",
                "readOnly": true,
                "route": {
                  "sourceProjection": {
                    "sourceRef": "inventory-source",
                    "filters": [{
                      "param": "maxPrice",
                      "fields": ["price"],
                      "operator": "NUMBER_LESS_THAN_OR_EQUAL",
                      "absentValues": ["not-a-number"]
                    }],
                    "outputFields": ["recordId", "price"]
                  },
                  "response": {"result": {"results": "{{body.results}}"}}
                }
              }]}
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("numeric filter absentValues entries must be numeric");
    }

    @Test
    void sourceProjectionActionManifestRejectsProviderRequestSettings() {
        String manifest = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true},
              "contributions": {"actions": [{
                "actionId": "inventory_search",
                "adapterType": "connector-http",
                "readOnly": true,
                "route": {
                  "sourceProjection": {
                    "sourceRef": "inventory-source",
                    "outputFields": ["recordId"]
                  },
                  "method": "GET",
                  "request": {"query": {"page": 1}}
                }
              }]}
            }
            """;

        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(manifest)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("must not declare HTTP/provider request settings");
    }

    @Test
    void providerActionManifestRequiresRelativeBoundedAuthorityRoute() {
        String valid = """
            {
              "schemaVersion": 1,
              "pluginType": "ACTION",
              "compatibility": {"requiredCapabilities": ["actions"]},
              "pricing": {"pricingModel": "FREE"},
              "permissions": {"contributesActions": true, "requiresExternalHttpExecution": true},
              "contributions": {"actions": [{
                "actionId": "neutral_search",
                "adapterType": "connector-http",
                "readOnly": true,
                "route": {
                  "method": "GET",
                  "path": "/scopes/{scope}/search",
                  "connectionProfileRef": "neutral-provider",
                  "protectedResourceBindingRef": "neutral-scope",
                  "requiredCapabilityGrants": ["records:read"],
                  "trustedResourcePlacements": [{"target": "PATH", "field": "scope"}],
                  "request": {"query": {"q": "{{params.query}}"}},
                  "response": {
                    "collection-field-projections": [{
                      "collection-json-pointer": "/results",
                      "fields": {"recordId": "/metadata/recordId"}
                    }]
                  }
                }
              }]}
            }
            """;

        assertThat(service.parseAndValidate(actionPlugin(), version(valid)).contributions().actionIds())
            .containsExactly("neutral_search");

        String unsafe = valid.replace(
            "\"path\": \"/scopes/{scope}/search\"",
            "\"url\": \"https://caller-selected.invalid/search\""
        );
        assertThatThrownBy(() -> service.parseAndValidate(actionPlugin(), version(unsafe)))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("must use a relative path");
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

    @Test
    void externalHttpDataManifestAcceptsBoundedNeutralProviderContract() {
        MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
            dataPlugin(),
            dataVersion(validHttpDataManifest())
        );

        assertThat(parsed.datasets()).singleElement().satisfies(dataset -> {
            assertThat(dataset.ingestionMode()).isEqualTo("EXTERNAL_SYNC_HTTP");
            assertThat(dataset.connectorType()).isEqualTo("HTTP_JSON");
            assertThat(dataset.syncConnector().path("connectionProfile").path("profileId").asText())
                .isEqualTo("neutral-provider");
            assertThat(dataset.syncConnector().path("connectionProfile").path("auth").path("tokenBaseUrl").asText())
                .isEqualTo("https://auth.fixture.invalid");
            assertThat(dataset.syncConnector().path("webhook").path("orderingPolicy").asText())
                .isEqualTo("RECONCILE_LATEST_STATE");
        });
    }

    @Test
    void externalHttpDataManifestAcceptsTargetedWebhookReconciliationWithMillisecondSignature() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode connector = (ObjectNode) manifest.path("contributions").path("datasets").get(0)
            .path("syncConnector");
        ObjectNode targeted = ((ObjectNode) connector.path("httpSource")).putObject("targetedRecordFetch");
        targeted.put("enabled", true);
        targeted.put("path", "/records/{account}");
        targeted.put("method", "GET");
        targeted.putArray("completeHttpStatuses").add(200);
        targeted.putArray("absentHttpStatuses").add(404);
        targeted.putObject("recordKeyPlacement").put("target", "QUERY").put("field", "recordId");
        ObjectNode webhook = (ObjectNode) connector.path("webhook");
        webhook.put("recordKeyJsonPointer", "/data/recordId");
        webhook.put("reconciliationStrategy", "FETCH_CURRENT_RECORD");
        ((ObjectNode) webhook.path("verification")).put("timestampUnit", "MILLISECONDS");
        webhook.putObject("responseStatuses")
            .put("accepted", 200)
            .put("duplicate", 200)
            .put("resourceMismatch", 422);

        MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(manifest))
        );

        JsonNode parsedConnector = parsed.datasets().getFirst().syncConnector();
        assertThat(parsedConnector.path("httpSource").path("targetedRecordFetch").path("enabled").asBoolean())
            .isTrue();
        assertThat(parsedConnector.path("webhook").path("reconciliationStrategy").asText())
            .isEqualTo("FETCH_CURRENT_RECORD");
        assertThat(parsedConnector.path("webhook").path("verification").path("timestampUnit").asText())
            .isEqualTo("MILLISECONDS");
        assertThat(parsedConnector.path("webhook").path("responseStatuses").path("resourceMismatch").asInt())
            .isEqualTo(422);
    }

    @Test
    void dataManifestAcceptsExplicitCustomerBackendIngestionContract() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode dataset = (ObjectNode) manifest.path("contributions").path("datasets").get(0);
        ((ObjectNode) dataset.path("syncConnector").path("httpSource")).put("enabled", false);
        ObjectNode ingestion = dataset.putObject("customerBackendIngestion");
        ingestion.put("enabled", true);
        ingestion.putArray("operations").add("UPSERT").add("DELETE").add("WORK_STATUS").add("READINESS");

        MarketplaceManifestService.ParsedMarketplaceManifest parsed = service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(manifest))
        );

        assertThat(parsed.datasets()).singleElement().satisfies(parsedDataset -> {
            assertThat(parsedDataset.syncConnector().path("httpSource").path("enabled").asBoolean()).isFalse();
            assertThat(parsedDataset.customerBackendIngestion().path("operations").toString())
                .isEqualTo("[\"UPSERT\",\"DELETE\",\"WORK_STATUS\",\"READINESS\"]");
        });
    }

    @Test
    void dataManifestRejectsTwoActiveHttpIngestionAuthorities() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode ingestion = ((ObjectNode) manifest.path("contributions").path("datasets").get(0))
            .putObject("customerBackendIngestion");
        ingestion.put("enabled", true);
        ingestion.putArray("operations").add("UPSERT");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(manifest))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exactly one active ingestion authority");
    }

    @Test
    void dataManifestRejectsHttpDatasetWithoutAnActiveIngestionAuthority() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode dataset = (ObjectNode) manifest.path("contributions").path("datasets").get(0);
        ((ObjectNode) dataset.path("syncConnector").path("httpSource")).put("enabled", false);

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(manifest))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("exactly one active ingestion authority");
    }

    @Test
    void dataManifestRejectsUnboundedCustomerBackendIngestionContract() throws Exception {
        ObjectNode manifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode ingestion = ((ObjectNode) manifest.path("contributions").path("datasets").get(0))
            .putObject("customerBackendIngestion");
        ingestion.put("enabled", true);
        ingestion.putArray("operations").add("ARBITRARY_WRITE");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(manifest))
        )).isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unsupported operation: ARBITRARY_WRITE");
    }

    @Test
    void externalHttpDataManifestRejectsHostAndPlacementOutsideReviewedContract() throws Exception {
        ObjectNode hostManifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode profile = (ObjectNode) hostManifest.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("connectionProfile");
        profile.putArray("allowedHosts").add("different.fixture.invalid");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(hostManifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("must contain the base URL host");

        ObjectNode placementManifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) placementManifest.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("httpSource").path("trustedResourcePlacements").get(0))
            .put("target", "METADATA");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(placementManifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("placement target is unsupported");
    }

    @Test
    void externalHttpDataManifestRejectsUnboundPathAndAuthHeaderPlacement() throws Exception {
        ObjectNode unboundPath = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) unboundPath.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("httpSource").path("trustedResourcePlacements").get(0))
            .put("target", "QUERY");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(unboundPath))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("unbound protected path placeholder");

        ObjectNode authHeader = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ObjectNode source = (ObjectNode) authHeader.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("httpSource");
        source.put("path", "/records");
        ObjectNode placement = (ObjectNode) source.path("trustedResourcePlacements").get(0);
        placement.put("target", "HEADER");
        placement.put("field", "Authorization");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(authHeader))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("must not target the provider authentication header");
    }

    @Test
    void externalHttpDataManifestRejectsMissingOrMismatchedCapabilityGrants() throws Exception {
        ObjectNode missingGrants = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) missingGrants.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("connectionProfile")).remove("capabilityGrants");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(missingGrants))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("connectionProfile.capabilityGrants");

        ObjectNode mismatchedGrants = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) mismatchedGrants.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("protectedResource"))
            .putArray("capabilityGrants")
            .add("records:metadata");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(mismatchedGrants))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("requires capability grants absent");
    }

    @Test
    void externalHttpDataManifestRejectsUnboundedPaginationAndMapping() throws Exception {
        ObjectNode paginationManifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) paginationManifest.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("httpSource").path("pagination"))
            .put("maxPages", 10_001);

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(paginationManifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("pagination.maxPages must be an integer between 1 and 10000");

        ObjectNode mappingManifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) mappingManifest.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("httpSource").path("mapping"))
            .put("maxResponseBytes", 50 * 1_024 * 1_024 + 1);

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(mappingManifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("mapping.maxResponseBytes must be an integer between 1024 and 52428800");
    }

    @Test
    void externalHttpDataManifestRejectsUnsafeProviderUrlAndWebhookHeader() throws Exception {
        ObjectNode urlManifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) urlManifest.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("connectionProfile"))
            .put("baseUrl", "https://operator@provider.fixture.invalid");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(urlManifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("absolute HTTPS URL without credentials");

        ObjectNode webhookManifest = (ObjectNode) objectMapper.readTree(validHttpDataManifest());
        ((ObjectNode) webhookManifest.path("contributions").path("datasets").get(0)
            .path("syncConnector").path("webhook").path("verification"))
            .put("signatureHeader", "X-Bad\nHeader");

        assertThatThrownBy(() -> service.parseAndValidate(
            dataPlugin(),
            dataVersion(objectMapper.writeValueAsString(webhookManifest))
        ))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("not a valid HTTP header name");
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

    private String validHttpDataManifest() {
        return """
            {
              "schemaVersion": 1,
              "pluginType": "DATA",
              "compatibility": {"requiredCapabilities": ["knowledgeSources"]},
              "pricing": {"pricingModel": "FREE"},
              "installForm": [
                {"id": "providerKey", "label": "Provider key", "type": "secretref", "required": true},
                {"id": "providerSecret", "label": "Provider secret", "type": "secretref", "required": true},
                {"id": "webhookSecret", "label": "Webhook secret", "type": "secretref", "required": true},
                {"id": "scopeId", "label": "Scope", "type": "text", "required": true}
              ],
              "permissions": {
                "contributesKnowledgeSources": true,
                "requiresSharedDatasetAccess": false
              },
              "contributions": {
                "entityConfig": {
                  "ai-entities": {
                    "neutral-record": {
                      "indexing": {"enabled": true, "max-characters": 8000},
                      "analysis": {"enabled": false, "after": []},
                      "searchable-fields": [{
                        "name": "content",
                        "destinations": ["SEMANTIC_SEARCH", "RAG_CONTEXT"],
                        "preprocessing": "CLEAN",
                        "max-length": 8000,
                        "priority": 100,
                        "required": true
                      }],
                      "metadata-fields": [{
                        "name": "tenantId",
                        "data-type": "ID",
                        "destinations": ["VECTOR_METADATA"],
                        "priority": 100,
                        "required": true,
                        "sanitize-pii": false
                      }]
                    }
                  }
                },
                "datasets": [{
                  "datasetId": "neutral-http-records",
                  "entityType": "neutral-record",
                  "storageScope": "CUSTOMER_MANAGED",
                  "sharingScope": "DEPLOYMENT_ONLY",
                  "ingestionMode": "EXTERNAL_SYNC_HTTP",
                  "updateStrategy": "UPSERT_BY_ID",
                  "syncConnector": {
                    "connectorType": "HTTP_JSON",
                    "connectionProfile": {
                      "profileId": "neutral-provider",
                      "environment": "sandbox",
                      "baseUrl": "https://provider.fixture.invalid",
                      "allowedHosts": ["provider.fixture.invalid", "auth.fixture.invalid"],
                      "auth": {
                        "strategy": "FORM_TOKEN_EXCHANGE",
                        "tokenBaseUrl": "https://auth.fixture.invalid",
                        "tokenPath": "/authenticate",
                        "tokenMethod": "POST",
                        "credentialFields": [
                          {"name": "key", "secretRefField": "providerKey"},
                          {"name": "secret", "secretRefField": "providerSecret"}
                        ],
                        "tokenJsonPointer": "/token",
                        "relativeExpiryJsonPointer": "/expiresIn",
                        "authorizationHeader": "Authorization",
                        "authorizationScheme": "Bearer"
                      },
                      "ratePolicy": {
                        "maxConcurrent": 2,
                        "maxAttempts": 2,
                        "retryBackoffMs": 100,
                        "retryStatuses": [429, 503]
                      },
                      "errorMappings": [{
                        "status": 403,
                        "bodyJsonPointer": "/code",
                        "equalsValue": "MISSING_CAPABILITY",
                        "errorClass": "CAPABILITY_DENIED"
                      }],
                      "capabilityGrants": ["records:read"],
                      "correlationResponseHeaders": ["X-Fixture-Request"]
                    },
                    "protectedResource": {
                      "bindingId": "neutral-scope",
                      "environment": "sandbox",
                      "resourceType": "scope",
                      "resourceIdField": "scopeId",
                      "policyRef": "single-scope",
                      "capabilityGrants": ["records:read"]
                    },
                    "httpSource": {
                      "sourceId": "neutral-record-source",
                      "path": "/scopes/{scope}/records",
                      "method": "GET",
                      "trustedResourcePlacements": [{"target": "PATH", "field": "scope"}],
                      "requiredCapabilityGrants": ["records:read"],
                      "pagination": {
                        "strategy": "CURSOR",
                        "cursorQuery": "after",
                        "nextCursorJsonPointer": "/next",
                        "maxPages": 100
                      },
                      "mapping": {
                        "recordsJsonPointer": "/records",
                        "idJsonPointer": "/id",
                        "resourceJsonPointer": "/scope",
                        "contentFields": {"title": "/title"},
                        "entityFields": {"title": "/title"},
                        "metadataFields": {"updatedAt": "/updatedAt"},
                        "maxRecords": 10000,
                        "maxResponseBytes": 1048576
                      },
                      "tombstonePolicy": {
                        "strategy": "FIELD_VALUE",
                        "operationJsonPointer": "/state",
                        "deleteValues": ["deleted"]
                      },
                      "scheduleSeconds": 900
                    },
                    "webhook": {
                      "sourceId": "neutral-record-events",
                      "method": "POST",
                      "allowedContentTypes": ["application/json"],
                      "verification": {
                        "strategy": "HMAC_SHA256_TIMESTAMP_DOT_RAW_BODY",
                        "signatureHeader": "X-Fixture-Signature",
                        "secretRefField": "webhookSecret",
                        "timestampComponent": "t",
                        "signatureComponent": "v1",
                        "replayWindowSeconds": 300
                      },
                      "eventIdJsonPointer": "/eventId",
                      "eventTypeJsonPointer": "/eventType",
                      "resourceJsonPointer": "/scope",
                      "allowedEventTypes": ["record.changed"],
                      "maxBodyBytes": 1048576,
                      "maxReconcileAttempts": 3,
                      "retryDelaySeconds": 30,
                      "registrationExpected": true,
                      "manualReplayEnabled": true,
                      "orderingPolicy": "RECONCILE_LATEST_STATE"
                    }
                  }
                }],
                "knowledgeSources": [{
                  "sourceType": "deployment-private-vector",
                  "sourceKey": "neutral-records",
                  "datasetRef": "neutral-http-records",
                  "entityType": "neutral-record",
                  "attributionLabel": "Neutral provider records"
                }]
              }
            }
            """;
    }
}
