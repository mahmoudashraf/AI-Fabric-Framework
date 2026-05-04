package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class DeploymentMarketplaceDraftCompilerServiceTest {

    @Autowired
    private DeploymentMarketplaceDraftCompilerService compilerService;

    @Autowired
    private MarketplaceCatalogService marketplaceCatalogService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void compileTemplateShellBaselineCanPopulateGreetingWhenExistingGreetingObjectIsEmpty() {
        MarketplacePluginEntity plugin = marketplaceCatalogService.requirePluginEntity("mkp-template-support-desk-shell");
        MarketplacePluginVersionEntity version = marketplaceCatalogService.requirePluginVersionEntity(plugin.getId(), "1.0.0");

        ObjectNode existingShell = objectMapper.createObjectNode();
        existingShell.put("contractVersion", "SHELL_CONFIG_V1");
        existingShell.set("modules", objectMapper.createArrayNode());
        existingShell.set("cards", objectMapper.createArrayNode());
        existingShell.set("starterPrompts", objectMapper.createArrayNode());
        existingShell.set("greeting", objectMapper.createObjectNode());

        ObjectNode compiled = compilerService.compileTemplateShellBaseline(plugin, version, existingShell);

        assertThat(compiled.path("greeting").path("title").asText()).isEqualTo("Support Desk");
        assertThat(compiled.path("greeting").path("message").asText()).contains("help-center guidance");
        ArrayNode starterPrompts = (ArrayNode) compiled.path("starterPrompts");
        assertThat(starterPrompts).hasSize(3);
        assertThat(starterPrompts).extracting(node -> node.path("id").asText())
            .contains("support-capabilities", "refund-policy", "notification-troubleshooting");
    }

    @Test
    void pruneRoutesWithoutActionsRemovesOrphanedMarketplaceRoutes() {
        ObjectNode routing = objectMapper.createObjectNode();
        ObjectNode routes = routing.putObject("actions");
        routes.putObject("relationship_query").put("path", "/stale");
        routes.putObject("search_products").put("path", "/active");

        boolean changed = DeploymentMarketplaceDraftCompilerService.pruneRoutesWithoutActions(
            routing,
            Set.of("search_products")
        );

        assertThat(changed).isTrue();
        assertThat(routes.has("relationship_query")).isFalse();
        assertThat(routes.path("search_products").path("path").asText()).isEqualTo("/active");
    }

    @Test
    void compileActionContributionPreservesMcpExecutionMetadata() throws Exception {
        JsonNode action = objectMapper.readTree("""
            {
              "actionId": "shopify_search_catalog",
              "adapterType": "mcp-tool",
              "description": "Search the Shopify catalog",
              "category": "shopify-companion",
              "readOnly": true,
              "anonymousAllowed": true,
              "groundingEligible": true,
              "readActionResolutionEligible": true,
              "params": [
                {
                  "name": "query",
                  "type": "STRING",
                  "required": true
                }
              ],
              "execution": {
                "adapterType": "mcp-tool",
                "mcp": {
                  "serverRef": "shopify-storefront-ucp",
                  "endpointKind": "UCP_CATALOG",
                  "toolName": "search_catalog"
                }
              }
            }
            """);

        ObjectNode compiled = compilerService.compileActionContribution(
            action,
            install(),
            plugin(),
            version()
        );

        assertThat(compiled.path("name").asText()).isEqualTo("shopify_search_catalog");
        assertThat(compiled.path("adapterType").asText()).isEqualTo("mcp-tool");
        assertThat(compiled.path("accessMode").asText()).isEqualTo("READ");
        assertThat(compiled.path("execution").path("adapterType").asText()).isEqualTo("mcp-tool");
        assertThat(compiled.path("execution").path("mcp").path("serverRef").asText()).isEqualTo("shopify-storefront-ucp");
        assertThat(compiled.path("execution").path("mcp").path("toolName").asText()).isEqualTo("search_catalog");
        assertThat(compiled.path("marketplacePluginId").asText()).isEqualTo("mkp-action-shopify-companion-read");
    }

    @Test
    void compileActionContributionPreservesStandardStorefrontMcpMetadata() throws Exception {
        JsonNode action = objectMapper.readTree("""
            {
              "actionId": "shopify_search_policies",
              "adapterType": "mcp-tool",
              "description": "Search Shopify policies",
              "category": "shopify-companion",
              "readOnly": true,
              "anonymousAllowed": true,
              "groundingEligible": true,
              "readActionResolutionEligible": true,
              "params": [
                {
                  "name": "query",
                  "type": "STRING",
                  "required": true
                }
              ],
              "execution": {
                "adapterType": "mcp-tool",
                "mcp": {
                  "serverRef": "shopify-storefront",
                  "endpointKind": "STOREFRONT_STANDARD",
                  "toolName": "search_shop_policies_and_faqs"
                }
              }
            }
            """);

        ObjectNode compiled = compilerService.compileActionContribution(
            action,
            install(),
            plugin(),
            version()
        );

        assertThat(compiled.path("name").asText()).isEqualTo("shopify_search_policies");
        assertThat(compiled.path("adapterType").asText()).isEqualTo("mcp-tool");
        assertThat(compiled.path("execution").path("mcp").path("serverRef").asText()).isEqualTo("shopify-storefront");
        assertThat(compiled.path("execution").path("mcp").path("endpointKind").asText()).isEqualTo("STOREFRONT_STANDARD");
        assertThat(compiled.path("execution").path("mcp").path("toolName").asText()).isEqualTo("search_shop_policies_and_faqs");
    }

    @Test
    void compileActionContributionPreservesCustomerAccountMcpAuthMetadata() throws Exception {
        JsonNode action = objectMapper.readTree("""
            {
              "actionId": "shopify_lookup_order",
              "adapterType": "mcp-tool",
              "description": "Lookup customer order",
              "category": "shopify-companion",
              "readOnly": true,
              "anonymousAllowed": false,
              "params": [
                {"name": "order_id", "type": "STRING", "required": true}
              ],
              "execution": {
                "adapterType": "mcp-tool",
                "mcp": {
                  "serverRef": "shopify-customer-account",
                  "endpointKind": "CUSTOMER_ACCOUNT",
                  "authMode": "CUSTOMER_OAUTH_PKCE",
                  "requiredCustomerScopes": ["customer-account-mcp-api:full"],
                  "toolName": "lookup_order"
                }
              }
            }
            """);

        ObjectNode compiled = compilerService.compileActionContribution(action, install(), plugin(), version());

        assertThat(compiled.path("name").asText()).isEqualTo("shopify_lookup_order");
        assertThat(compiled.path("anonymousAllowed").asBoolean()).isFalse();
        assertThat(compiled.path("execution").path("mcp").path("serverRef").asText()).isEqualTo("shopify-customer-account");
        assertThat(compiled.path("execution").path("mcp").path("authMode").asText()).isEqualTo("CUSTOMER_OAUTH_PKCE");
        assertThat(compiled.path("execution").path("mcp").path("requiredCustomerScopes").get(0).asText())
            .isEqualTo("customer-account-mcp-api:full");
    }

    @Test
    void compileActionContributionPreservesCheckoutMcpTerminalMetadata() throws Exception {
        JsonNode action = objectMapper.readTree("""
            {
              "actionId": "shopify_complete_checkout",
              "adapterType": "mcp-tool",
              "description": "Complete checkout",
              "category": "shopify-companion",
              "readOnly": false,
              "anonymousAllowed": false,
              "requiresConfirmation": true,
              "execution": {
                "adapterType": "mcp-tool",
                "mcp": {
                  "serverRef": "shopify-checkout",
                  "endpointKind": "CHECKOUT_UCP",
                  "authMode": "SHOPIFY_AGENTIC_CLIENT_CREDENTIALS",
                  "requiresTerminalCheckoutEnablement": true,
                  "requiresIdempotencyKey": true,
                  "toolName": "complete_checkout"
                }
              }
            }
            """);

        ObjectNode compiled = compilerService.compileActionContribution(action, install(), plugin(), version());

        assertThat(compiled.path("name").asText()).isEqualTo("shopify_complete_checkout");
        assertThat(compiled.path("accessMode").asText()).isEqualTo("WRITE_ONLY");
        assertThat(compiled.path("requiresConfirmation").asBoolean()).isTrue();
        assertThat(compiled.path("execution").path("mcp").path("serverRef").asText()).isEqualTo("shopify-checkout");
        assertThat(compiled.path("execution").path("mcp").path("requiresIdempotencyKey").asBoolean()).isTrue();
    }

    @Test
    void compileMcpServerContributionResolvesInstallConfigAndSecretRefsWithoutSecretValues() throws Exception {
        JsonNode server = objectMapper.readTree("""
            {
              "serverRef": "inventory-mcp",
              "transport": "STREAMABLE_HTTP",
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
            """);
        JsonNode installConfig = objectMapper.readTree("""
            {"inventoryMcpEndpoint": "https://inventory.example/mcp"}
            """);
        JsonNode installSecretRefs = objectMapper.readTree("""
            {"inventoryMcpApiKeyRef": "INV_MCP_API_KEY"}
            """);

        ObjectNode compiled = compilerService.compileMcpServerContribution(
            server,
            installConfig,
            installSecretRefs,
            install(),
            plugin(),
            version()
        );

        assertThat(compiled.path("serverRef").asText()).isEqualTo("inventory-mcp");
        assertThat(compiled.path("endpointUrl").asText()).isEqualTo("https://inventory.example/mcp");
        assertThat(compiled.path("allowedTools").get(0).asText()).isEqualTo("inventory.search");
        assertThat(compiled.path("auth").path("mode").asText()).isEqualTo("API_KEY_HEADER_SECRET");
        assertThat(compiled.path("auth").path("headerName").asText()).isEqualTo("X-MCP-API-Key");
        assertThat(compiled.path("auth").path("secretRef").asText()).isEqualTo("INV_MCP_API_KEY");
        assertThat(compiled.path("auth").has("value")).isFalse();
        assertThat(compiled.path("verification").path("schemaDriftPolicy").asText()).isEqualTo("WARN_ONLY");
        assertThat(compiled.path("marketplacePluginId").asText()).isEqualTo("mkp-action-shopify-companion-read");
    }

    @Test
    void stripGreenfieldShopifyLegacyActionsRemovesStaleShopifyActionsWhenMcpBundleIsEnabled() {
        ObjectNode actionsRoot = objectMapper.createObjectNode();
        ArrayNode actions = actionsRoot.putArray("actions");
        actions.addObject()
            .put("name", "shopify_search_catalog")
            .put("adapterType", "connector-http");
        actions.addObject()
            .put("name", "shopify_get_product_details")
            .put("adapterType", "connector-http");
        actions.addObject()
            .put("name", "shopify_lookup_order")
            .put("adapterType", "connector-http");
        actions.addObject()
            .put("name", "shopify_create_checkout")
            .put("adapterType", "connector-http");
        actions.addObject()
            .put("name", "operator_custom_action")
            .put("adapterType", "connector-http");
        DeploymentMarketplacePluginInstallEntity install = install();
        install.setPluginId("mkp-action-shopify-customer-account-mcp");

        boolean changed = compilerService.stripGreenfieldShopifyLegacyActions(actionsRoot, java.util.List.of(install));

        assertThat(changed).isTrue();
        assertThat((ArrayNode) actionsRoot.path("actions")).extracting(node -> node.path("name").asText())
            .containsExactly("operator_custom_action");
    }

    @Test
    void stripGreenfieldShopifyLegacyActionsKeepsActionsWhenMcpBundleIsNotEnabled() {
        ObjectNode actionsRoot = objectMapper.createObjectNode();
        ArrayNode actions = actionsRoot.putArray("actions");
        actions.addObject()
            .put("name", "shopify_search_catalog")
            .put("adapterType", "connector-http");
        DeploymentMarketplacePluginInstallEntity install = install();
        install.setPluginId("mkp-action-shopify-storefront-read-mcp");
        install.setStatus("DISABLED");

        boolean changed = compilerService.stripGreenfieldShopifyLegacyActions(actionsRoot, java.util.List.of(install));

        assertThat(changed).isFalse();
        assertThat((ArrayNode) actionsRoot.path("actions")).extracting(node -> node.path("name").asText())
            .containsExactly("shopify_search_catalog");
    }

    @Test
    void replaceGreenfieldShopifyActionConflictReplacesExistingActionByName() {
        ArrayNode actions = objectMapper.createArrayNode();
        actions.addObject()
            .put("name", "shopify_search_catalog")
            .put("adapterType", "connector-http");
        actions.addObject()
            .put("name", "operator_custom_action")
            .put("adapterType", "connector-http");
        Set<String> existingActionNames = new java.util.LinkedHashSet<>(
            java.util.List.of("shopify_search_catalog", "operator_custom_action")
        );

        boolean replaced = compilerService.replaceGreenfieldShopifyActionConflict(
            actions,
            existingActionNames,
            "mkp-action-shopify-storefront-read-mcp",
            "shopify_search_catalog"
        );

        assertThat(replaced).isTrue();
        assertThat(actions).extracting(node -> node.path("name").asText())
            .containsExactly("operator_custom_action");
        assertThat(existingActionNames).contains("shopify_search_catalog", "operator_custom_action");
    }

    @Test
    void replaceGreenfieldShopifyActionConflictRejectsNonShopifyMcpPlugins() {
        ArrayNode actions = objectMapper.createArrayNode();
        actions.addObject()
            .put("name", "shopify_search_catalog")
            .put("adapterType", "connector-http");
        Set<String> existingActionNames = new java.util.LinkedHashSet<>(java.util.List.of("shopify_search_catalog"));

        boolean replaced = compilerService.replaceGreenfieldShopifyActionConflict(
            actions,
            existingActionNames,
            "mkp-action-other",
            "shopify_search_catalog"
        );

        assertThat(replaced).isFalse();
        assertThat(actions).hasSize(1);
    }

    private DeploymentMarketplacePluginInstallEntity install() {
        DeploymentMarketplacePluginInstallEntity install = new DeploymentMarketplacePluginInstallEntity();
        install.setId("mpi-test");
        install.setDeploymentId("dep-test");
        install.setPluginId("mkp-action-shopify-companion-read");
        install.setPluginVersionId("mkv-action-shopify-companion-read-v1");
        install.setStatus("ENABLED");
        install.setConfigJson("{}");
        install.setSecretRefsJson("{}");
        install.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        install.setUpdatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        return install;
    }

    private MarketplacePluginEntity plugin() {
        MarketplacePluginEntity plugin = new MarketplacePluginEntity();
        plugin.setId("mkp-action-shopify-companion-read");
        plugin.setSlug("shopify-companion-read");
        plugin.setDisplayName("Shopify Companion Storefront Actions");
        plugin.setPluginType("ACTION");
        plugin.setPublisherSlug("loom");
        plugin.setPublisherDisplayName("Loom");
        plugin.setShortDescription("Shopify actions");
        plugin.setStatus("PUBLISHED");
        plugin.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        plugin.setUpdatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        return plugin;
    }

    private MarketplacePluginVersionEntity version() {
        MarketplacePluginVersionEntity version = new MarketplacePluginVersionEntity();
        version.setId("mkv-action-shopify-companion-read-v1");
        version.setPluginId("mkp-action-shopify-companion-read");
        version.setVersion("1.0.0");
        version.setReleaseChannel("stable");
        version.setStatus("PUBLISHED");
        version.setManifestJson("{}");
        version.setCreatedAt(Instant.parse("2026-05-04T00:00:00Z"));
        version.setPublishedAt(Instant.parse("2026-05-04T00:00:00Z"));
        return version;
    }
}
