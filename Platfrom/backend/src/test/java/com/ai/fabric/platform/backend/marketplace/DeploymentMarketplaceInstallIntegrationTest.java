package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.security.PlatformTestSecurity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class DeploymentMarketplaceInstallIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void authenticate() {
        PlatformTestSecurity.authenticateAsPlatformAdmin();
    }

    @AfterEach
    void clearAuthentication() {
        PlatformTestSecurity.clearAuthentication();
    }

    @Test
    void installingMarketplacePluginPersistsDeploymentInstall() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Install", "dev", "dev-openai-lucene")
        );

        String payload = objectMapper.writeValueAsString(java.util.Map.of(
            "pluginVersionId", "mkv-action-shopify-admin-v1",
            "config", java.util.Map.of("storefront", "primary"),
            "secretRefs", java.util.List.of(java.util.Map.of("secretPurpose", "SHOPIFY_ADMIN_TOKEN", "secretRef", "secret://shopify-admin"))
        ));

        mockMvc.perform(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .contentType(APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginId", is("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.pluginVersionId", is("mkv-action-shopify-admin-v1")))
            .andExpect(jsonPath("$.pluginVersion", is("1.0.0")))
            .andExpect(jsonPath("$.config.storefront", is("primary")))
            .andExpect(jsonPath("$.secretRefs[0].secretRef", is("secret://shopify-admin")));

        mockMvc.perform(
                get("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].pluginDisplayName", is("Shopify Admin Actions")))
            .andExpect(jsonPath("$[0].status", is("INSTALLED")));
    }

    @Test
    void uninstallingMarketplacePluginRemovesDeploymentInstall() throws Exception {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Uninstall", "dev", "dev-openai-lucene")
        );

        String payload = objectMapper.writeValueAsString(java.util.Map.of(
            "pluginVersionId", "mkv-template-commerce-shell-v1",
            "config", java.util.Map.of("branding", java.util.Map.of("assistantName", "Commerce Shell")),
            "secretRefs", java.util.List.of()
        ));

        String installId = mockMvc.perform(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .contentType(APPLICATION_JSON)
                    .content(payload)
            )
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String resolvedInstallId = objectMapper.readTree(installId).path("installId").asText();

        mockMvc.perform(
                delete("/api/deployments/{deploymentId}/marketplace-installs/{installId}", deployment.id(), resolvedInstallId)
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
            )
            .andExpect(status().isNoContent());

        mockMvc.perform(
                get("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY)
                    .accept(APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
    }
}
