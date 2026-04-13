package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "platform.auth.enabled=true",
    "platform.auth.header-name=X-PLATFORM-API-KEY",
    "platform.auth.operator-api-key=operator-test-key",
    "platform.auth.admin-api-key=admin-test-key",
    "platform.auth.bootstrap-admin-enabled=false",
    "platform.bootstrap.sample-enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MarketplaceCatalogIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }

    @Test
    void listMarketplacePluginsReturnsSeededCatalog() throws Exception {
        mockMvc.perform(
                get("/api/marketplace/plugins")
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].pluginId", hasItems(
                "mkp-template-commerce-shell",
                "mkp-action-shopify-admin",
                "mkp-data-commerce-catalog"
            )))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-action-shopify-admin')].latestVersion.version", is(java.util.List.of("1.0.0"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-data-commerce-catalog')].pluginType", is(java.util.List.of("DATA"))));
    }

    @Test
    void listMarketplacePluginVersionsReturnsPublishedManifest() throws Exception {
        mockMvc.perform(
                get("/api/marketplace/plugins/{pluginId}/versions", "mkp-template-commerce-shell")
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].versionId", is("mkv-template-commerce-shell-v1")))
            .andExpect(jsonPath("$[0].releaseChannel", is("GA")))
            .andExpect(jsonPath("$[0].manifest.pluginType", is("TEMPLATE")))
            .andExpect(jsonPath("$[0].manifest.contributions.template.shell.enabledModuleIds[0]", is("docs")));
    }
}
