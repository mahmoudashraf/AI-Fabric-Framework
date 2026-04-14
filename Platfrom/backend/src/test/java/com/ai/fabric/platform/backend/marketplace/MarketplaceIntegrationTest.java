package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class MarketplaceIntegrationTest {

    private static final String PLATFORM_API_KEY_HEADER = "X-PLATFORM-API-KEY";
    private static final String ADMIN_API_KEY = "admin-test-key";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeploymentService deploymentService;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void catalogEndpointsExposeSeededMarketplacePluginsAndVersions() throws Exception {
        mockMvc.perform(asAdmin(get("/api/marketplace/plugins")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='mkp-template-commerce-shell')].pluginType", is(List.of("TEMPLATE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-shopify-admin')].latestVersion", is(List.of("1.0.0"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-commerce-catalog')].contributions.knowledgeSourceIds[0]", is(List.of("commerce-catalog"))));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-action-shopify-admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.versions[0].version", is("1.0.0")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("shopify-order-read")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("shopify-order-cancel")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}/versions/{version}", "mkp-data-commerce-catalog", "1.0.0")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pluginId", is("mkp-data-commerce-catalog")))
            .andExpect(jsonPath("$.manifest.pluginType", is("DATA")))
            .andExpect(jsonPath("$.contributions.knowledgeSourceIds[0]", is("commerce-catalog")));

        mockMvc.perform(asAdmin(get("/api/marketplace/categories")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='template')].pluginCount", is(List.of(1))))
            .andExpect(jsonPath("$[?(@.id=='action')].pluginCount", is(List.of(1))))
            .andExpect(jsonPath("$[?(@.id=='data')].pluginCount", is(List.of(1))));
    }

    @Test
    void deploymentMarketplaceInstallsPersistAndExposeImpactPreview() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Install Flow", "dev", "dev-openai-lucene")
        ));

        String actionInstallId = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-action-shopify-admin",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("store", "shopify-admin"),
                        "secretRefs", java.util.Map.of("apiKey", "sec-shopify-admin")
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginId", is("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.pluginType", is("ACTION")))
            .andExpect(jsonPath("$.pluginVersion", is("1.0.0")))
            .andExpect(jsonPath("$.status", is("ENABLED")))
            .andExpect(jsonPath("$.liveState", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.contributions.actionIds", hasItem("shopify-order-read")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String actionInstall = objectMapper.readTree(actionInstallId).path("id").asText();

        String dataInstallId = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-data-commerce-catalog",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("scope", "refund-policy"),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginId", is("mkp-data-commerce-catalog")))
            .andExpect(jsonPath("$.pluginType", is("DATA")))
            .andExpect(jsonPath("$.liveState", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.contributions.knowledgeSourceIds[0]", is("commerce-catalog")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String dataInstall = objectMapper.readTree(dataInstallId).path("id").asText();

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(2)))
            .andExpect(jsonPath("$[?(@.id=='" + actionInstall + "')].pluginDisplayName", is(List.of("Shopify Admin Actions"))))
            .andExpect(jsonPath("$[?(@.id=='" + dataInstall + "')].pluginDisplayName", is(List.of("Commerce Catalog Data"))));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-read')].marketplaceInstallId", is(List.of(actionInstall))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-cancel')].requiresConfirmation", is(List.of(true))))
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')].handleRef", is(List.of("commerce-catalog"))))
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='actions')].marketplaceInstallId", is(List.of(actionInstall))))
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='docs')].marketplaceInstallId", is(List.of(dataInstall))));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-impact", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId", is(deployment.id())))
            .andExpect(jsonPath("$.totalInstalls", is(2)))
            .andExpect(jsonPath("$.actionPluginCount", is(1)))
            .andExpect(jsonPath("$.dataPluginCount", is(1)))
            .andExpect(jsonPath("$.templatePluginCount", is(0)))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-data-commerce-catalog")))
            .andExpect(jsonPath("$.actionIds", hasItem("shopify-order-read")))
            .andExpect(jsonPath("$.knowledgeSourceIds", hasItem("commerce-catalog")))
            .andExpect(jsonPath("$.shellModuleIds", hasItem("actions")))
            .andExpect(jsonPath("$.shellModuleIds", hasItem("docs")));

        mockMvc.perform(asAdmin(post("/api/deployments/{deploymentId}/marketplace-installs/{installId}/resolve", deployment.id(), actionInstall)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.install.id", is(actionInstall)))
            .andExpect(jsonPath("$.impact.totalInstalls", is(2)))
            .andExpect(jsonPath("$.impact.actionIds", hasItem("shopify-order-cancel")));

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}", deployment.id(), dataInstall)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "status", "DISABLED",
                        "config", java.util.Map.of("scope", "refund-only")
                    )))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("DISABLED")))
            .andExpect(jsonPath("$.liveState", is("NOT_APPLIED")))
            .andExpect(jsonPath("$.config.scope", is("refund-only")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')]").isEmpty())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='docs')]").isEmpty());

        mockMvc.perform(asAdmin(delete("/api/deployments/{deploymentId}/marketplace-installs/{installId}", deployment.id(), actionInstall)))
            .andExpect(status().isNoContent());

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-impact", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInstalls", is(1)))
            .andExpect(jsonPath("$.installedPluginIds", is(List.of("mkp-data-commerce-catalog"))));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-read')]").isEmpty())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='actions')]").isEmpty());
    }

    @Test
    void templatePluginsAreRejectedFromDeploymentInstallApis() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Template Guard", "dev", "dev-openai-lucene")
        ));

        mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-template-commerce-shell",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", is(
                "Template plugins must be used through marketplace bootstrap flow, not deployment install APIs: mkp-template-commerce-shell"
            )));
    }

    @Test
    void templatePluginsCanBootstrapDeploymentAndRecordTemplateInstall() throws Exception {
        String response = mockMvc.perform(asAdmin(
                post("/api/marketplace/templates/{pluginId}/bootstrap", "mkp-template-commerce-shell")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginVersion", "1.0.0",
                        "name", "Marketplace Template Bootstrap",
                        "environment", "dev",
                        "templateId", "dev-openai-lucene"
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.templateId", is("dev-openai-lucene")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String deploymentId = objectMapper.readTree(response).path("id").asText();

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='docs')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='products')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='ai-search')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='actions')]").exists())
            .andExpect(jsonPath("$.shellConfig.defaultConversationMode", is("guided-commerce")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].pluginId", is("mkp-template-commerce-shell")))
            .andExpect(jsonPath("$[0].status", is("BOOTSTRAPPED")))
            .andExpect(jsonPath("$[0].liveState", is("BOOTSTRAPPED")));
    }

    @Test
    void deletingLiveMarketplaceInstallDisablesItUntilLiveReleaseMovesPastIt() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Live Removal Guard", "dev", "dev-openai-lucene")
        ));

        String actionInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-action-shopify-admin",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String installId = objectMapper.readTree(actionInstallResponse).path("id").asText();
        String draftId = runAsAdmin(() -> deploymentService.getActiveDraftForDeployment(deployment.id()).id());
        String versionId = runAsAdmin(() -> deploymentService.publishDraft(draftId).id());
        runAsAdmin(() -> {
            var entity = deploymentRepository.findById(deployment.id()).orElseThrow();
            entity.setActiveVersionId(versionId);
            deploymentRepository.save(entity);
            return null;
        });

        mockMvc.perform(asAdmin(delete("/api/deployments/{deploymentId}/marketplace-installs/{installId}", deployment.id(), installId)))
            .andExpect(status().isNoContent());

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()", is(1)))
            .andExpect(jsonPath("$[0].id", is(installId)))
            .andExpect(jsonPath("$[0].status", is("DISABLED")))
            .andExpect(jsonPath("$[0].liveState", is("LIVE_PENDING_REMOVAL")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-read')]").isEmpty())
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-cancel')]").isEmpty());
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY);
    }

    private <T> T runAsAdmin(Supplier<T> supplier) {
        authenticateAdmin();
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateAdmin() {
        PlatformPrincipal principal = new PlatformPrincipal(
            "admin@example.com",
            PlatformRole.PLATFORM_ADMIN,
            "Platform Admin",
            "SESSION"
        );
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
