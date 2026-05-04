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
            .put("name", "operator_custom_action")
            .put("adapterType", "connector-http");
        DeploymentMarketplacePluginInstallEntity install = install();
        install.setPluginId("mkp-action-shopify-storefront-read-mcp");

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
