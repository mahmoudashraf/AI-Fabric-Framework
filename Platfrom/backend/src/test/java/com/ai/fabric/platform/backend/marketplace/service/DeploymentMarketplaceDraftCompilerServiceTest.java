package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

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
}
