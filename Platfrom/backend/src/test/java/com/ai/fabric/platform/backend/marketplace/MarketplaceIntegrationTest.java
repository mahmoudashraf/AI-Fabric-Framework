package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.model.CreateMarketplacePublisherRequest;
import com.ai.fabric.platform.backend.marketplace.model.MarketplacePublisherSummary;
import com.ai.fabric.platform.backend.marketplace.service.MarketplacePublisherService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private MarketplacePublisherService marketplacePublisherService;

    @Test
    void catalogEndpointsExposeSeededMarketplacePluginsAndVersions() throws Exception {
        mockMvc.perform(asAdmin(get("/api/marketplace/plugins")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='mkp-template-commerce-shell')].pluginType", is(List.of("TEMPLATE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-template-support-desk-shell')].pluginType", is(List.of("TEMPLATE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-shopify-admin')].latestVersion", is(List.of("1.0.0"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-notifications')].latestVersion", is(List.of("1.0.0"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-shopify-admin')].pricing.pricingModel", is(List.of("ONE_OFF"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-commerce-catalog')].pricing.pricingModel", is(List.of("SUBSCRIPTION"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-help-center')].pricing.pricingModel", is(List.of("FREE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-commerce-catalog')].contributions.knowledgeSourceIds[0]", is(List.of("commerce-catalog"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-automation-order-retention')].pluginType", is(List.of("AUTOMATION"))));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-action-shopify-admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.versions[0].version", is("1.0.0")))
            .andExpect(jsonPath("$.versions[0].pricing.pricingModel", is("ONE_OFF")))
            .andExpect(jsonPath("$.versions[0].compatibility.supportedProviderModes", hasItem("llm:openai")))
            .andExpect(jsonPath("$.versions[0].installForm[?(@.id=='store')].type", is(List.of("text"))))
            .andExpect(jsonPath("$.versions[0].installForm[?(@.id=='apiKey')].type", is(List.of("secretRef"))))
            .andExpect(jsonPath("$.versions[0].permissions.requiresDeploymentSecrets", is(true)))
            .andExpect(jsonPath("$.versions[0].capabilityProfiles", hasItem("SURFACE")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("shopify-order-read")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("shopify-order-cancel")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}/versions/{version}", "mkp-data-commerce-catalog", "1.0.0")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pluginId", is("mkp-data-commerce-catalog")))
            .andExpect(jsonPath("$.manifest.pluginType", is("DATA")))
            .andExpect(jsonPath("$.pricing.pricingModel", is("SUBSCRIPTION")))
            .andExpect(jsonPath("$.compatibility.supportedAuthModes", hasItem("PUBLIC_RUNTIME_AUTHENTICATED")))
            .andExpect(jsonPath("$.installForm[0].id", is("scope")))
            .andExpect(jsonPath("$.contributions.knowledgeSourceIds[0]", is("commerce-catalog")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-automation-order-retention")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-automation-order-retention")))
            .andExpect(jsonPath("$.versions[0].permissions.contributesAutomation", is(true)))
            .andExpect(jsonPath("$.versions[0].permissions.contributesPolicyLogicCapabilities", is(true)))
            .andExpect(jsonPath("$.versions[0].capabilityProfiles", hasItem("POLICY_LOGIC")))
            .andExpect(jsonPath("$.versions[0].contributions.automationIds", hasItem("order-cancel-retention")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-template-commerce-shell")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-data-commerce-catalog")))
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-automation-order-retention")));

        mockMvc.perform(asAdmin(get("/api/marketplace/categories")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='template')].pluginCount", is(List.of(2))))
            .andExpect(jsonPath("$[?(@.id=='action')].pluginCount", is(List.of(2))))
            .andExpect(jsonPath("$[?(@.id=='data')].pluginCount", is(List.of(2))))
            .andExpect(jsonPath("$[?(@.id=='automation')].pluginCount", is(List.of(1))));
    }

    @Test
    void supportStarterCatalogCompilesAllPluginTypesIntoDeploymentConfig() throws Exception {
        String bootstrapResponse = mockMvc.perform(asAdmin(
                post("/api/marketplace/templates/{pluginId}/bootstrap", "mkp-template-support-desk-shell")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginVersion", "1.0.0",
                        "name", "Marketplace Support Starter",
                        "environment", "dev",
                        "templateId", "dev-openai-qdrant",
                        "vectorProvisioningMode", "PLATFORM_MANAGED"
                    )))
            ))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String deploymentId = objectMapper.readTree(bootstrapResponse).path("id").asText();
        runAsAdmin(() -> {
            configureSharedQdrantDeployment(deploymentId);
            return null;
        });

        String notificationInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-action-notifications",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("provider", "sendgrid", "defaultSender", "support@loom.test"),
                        "secretRefs", java.util.Map.of("credentialSecretRef", "sec-sendgrid")
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("ACTION")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String notificationInstallId = objectMapper.readTree(notificationInstallResponse).path("id").asText();

        String helpCenterInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-data-help-center",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("scope", "all"),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("DATA")))
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String helpCenterInstallId = objectMapper.readTree(helpCenterInstallResponse).path("id").asText();

        String automationInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-automation-order-retention",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("discountPercent", 10, "cooldownDays", 7),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("AUTOMATION")))
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String automationInstallId = objectMapper.readTree(automationInstallResponse).path("id").asText();

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deploymentId, notificationInstallId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.securityConfig.authzMode", is("ALLOW_VERIFIED")))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='send-email')].marketplaceInstallId", is(List.of(notificationInstallId))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='send-sms')].marketplaceInstallId", is(List.of(notificationInstallId))))
            .andExpect(jsonPath("$.entityConfig['ai-entities']['faq-article'].marketplaceInstallId", is(helpCenterInstallId)))
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='help-center')].marketplaceInstallId", is(List.of(helpCenterInstallId))))
            .andExpect(jsonPath("$.automationConfig.workflows[?(@.id=='order-cancel-retention')].marketplaceInstallId", is(List.of(automationInstallId))))
            .andExpect(jsonPath("$.automationConfig.triggers[?(@.id=='order-cancel-requested')].eventType", is(List.of("order.cancel.requested"))))
            .andExpect(jsonPath("$.automationConfig.actions[?(@.id=='offer-retention-discount')].actionRef", is(List.of("offer_order_discount"))))
            .andExpect(jsonPath("$.automationConfig.schedules[?(@.id=='retention-follow-up')].workflowRef", is(List.of("order-cancel-retention"))))
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='docs')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='ai-search')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='support')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='actions')]").exists())
            .andExpect(jsonPath("$.shellConfig.defaultConversationMode", is("guided-support")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-impact", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInstalls", is(4)))
            .andExpect(jsonPath("$.templatePluginCount", is(1)))
            .andExpect(jsonPath("$.actionPluginCount", is(1)))
            .andExpect(jsonPath("$.dataPluginCount", is(1)))
            .andExpect(jsonPath("$.automationPluginCount", is(1)))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-template-support-desk-shell")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-action-notifications")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-data-help-center")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-automation-order-retention")))
            .andExpect(jsonPath("$.actionIds", hasItem("send-email")))
            .andExpect(jsonPath("$.knowledgeSourceIds", hasItem("help-center")))
            .andExpect(jsonPath("$.automationIds", hasItem("order-cancel-retention")))
            .andExpect(jsonPath("$.shellModuleIds", hasItem("support")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-template-support-desk-shell')].status", is(List.of("BOOTSTRAPPED"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-action-notifications')].readinessStatus", is(List.of("READY"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-data-help-center')].readinessStatus", is(List.of("READY"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-automation-order-retention')].readinessStatus", is(List.of("READY"))));
    }

    @Test
    void deploymentMarketplaceInstallsPersistAndExposeImpactPreview() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> createSharedQdrantDeployment("Marketplace Install Flow"));

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
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_REQUIRED")))
            .andExpect(jsonPath("$.entitlement.pricingModel", is("ONE_OFF")))
            .andExpect(jsonPath("$.entitlement.status", is("PENDING")))
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
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_REQUIRED")))
            .andExpect(jsonPath("$.entitlement.pricingModel", is("SUBSCRIPTION")))
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
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-read')]").isEmpty())
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')]").isEmpty());

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deployment.id(), actionInstall)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andExpect(jsonPath("$.entitlement.status", is("ACTIVE")));

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deployment.id(), dataInstall)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andExpect(jsonPath("$.entitlement.status", is("ACTIVE")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-read')].marketplaceInstallId", is(List.of(actionInstall))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='shopify-order-cancel')].requiresConfirmation", is(List.of(true))))
            .andExpect(jsonPath("$.entityConfig['ai-entities']['product'].marketplaceInstallId", is(dataInstall)))
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')].handleRef", is(List.of("commerce-catalog"))))
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='actions')].marketplaceInstallId", is(List.of(actionInstall))))
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='docs')].marketplaceInstallId", is(List.of(dataInstall))));

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deployment.id(), dataInstall)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "PAST_DUE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_GRACE")))
            .andExpect(jsonPath("$.warnings[0]", containsString("past due")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')].handleRef", is(List.of("commerce-catalog"))));

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
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deployment.id(), dataInstall)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "SUSPENDED")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_BLOCKED")));

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
    void actionPluginInstallRequiresDeclaredInputsAndCompatibility() throws Exception {
        DeploymentSummary openAiDeployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Input Guard", "dev", "dev-openai-lucene")
        ));
        DeploymentSummary anthropicDeployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Compatibility Guard", "dev", "dev-anthropic-lucene")
        ));

        mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", openAiDeployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-action-shopify-admin",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("store", "shopify-admin"),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message", is("Missing required secret ref field 'apiKey'.")));

        mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", anthropicDeployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-action-shopify-admin",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("store", "shopify-admin"),
                        "secretRefs", java.util.Map.of("apiKey", "sec-shopify-admin")
                    )))
            ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Incompatible deployment target.")))
            .andExpect(jsonPath("$.message", containsString("Incompatible provider mode for llm. Supported: openai.")));
    }

    @Test
    void sharedIndexDataPluginInstallIsRejectedOnEmbeddedVectorDeployment() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Marketplace Shared Data Guard", "dev", "dev-openai-lucene")
        ));

        mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-data-help-center",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of("scope", "all"),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Shared-index knowledge sources require vectorStoragePosture=SHARED")))
            .andExpect(jsonPath("$.message", containsString("shared-storage-capable vector provider")));
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
                        "config", java.util.Map.of("store", "shopify-admin"),
                        "secretRefs", java.util.Map.of("apiKey", "sec-shopify-admin")
                    )))
            ))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String installId = objectMapper.readTree(actionInstallResponse).path("id").asText();
        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deployment.id(), installId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")));
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

    @Test
    void publisherWorkflowCanSubmitValidatePublishAndBootstrapTemplatePlugin() throws Exception {
        String publisherResponse = mockMvc.perform(asAdmin(
                post("/api/marketplace/publishers")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "slug", "acme-marketplace",
                        "displayName", "Acme Marketplace",
                        "contactEmail", "plugins@acme.test"
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.verificationStatus", is("PENDING")))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String publisherId = objectMapper.readTree(publisherResponse).path("id").asText();
        String manifestJson = objectMapper.writeValueAsString(java.util.Map.of(
            "schemaVersion", 1,
            "pluginId", "mkp-template-acme-support",
            "version", "1.0.0",
            "pluginType", "TEMPLATE",
            "displayName", "Acme Support Shell",
            "description", "External publisher template for support-oriented assistant experiences.",
            "compatibility", java.util.Map.of(
                "minPlatformVersion", "0.1.0",
                "requiredCapabilities", java.util.List.of("templates", "shellConfig"),
                "supportedDeploymentTargets", java.util.List.of("dev-openai-lucene", "custom-start-from-scratch"),
                "supportedProviderModes", java.util.List.of("llm:openai")
            ),
            "pricing", java.util.Map.of("pricingModel", "FREE"),
            "permissions", java.util.Map.of(
                "contributesTemplate", true,
                "contributesShellPresentation", true
            ),
            "contributions", java.util.Map.of(
                "template", java.util.Map.of(
                    "curatedModuleId", "commerce",
                    "shell", java.util.Map.of(
                        "enabledModuleIds", java.util.List.of("docs", "actions"),
                        "defaultConversationMode", "support"
                    )
                )
            )
        ));

        mockMvc.perform(asAdmin(
                post("/api/marketplace/publishers/{publisherId}/submissions", publisherId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginSlug", "acme-support-shell",
                        "releaseChannel", "beta",
                        "manifest", objectMapper.readTree(manifestJson)
                    )))
            ))
            .andExpect(status().isConflict());

        mockMvc.perform(asAdmin(
                put("/api/marketplace/publishers/{publisherId}/verification", publisherId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "verificationStatus", "VERIFIED",
                        "status", "ACTIVE"
                    )))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationStatus", is("VERIFIED")));

        String submissionResponse = mockMvc.perform(asAdmin(
                post("/api/marketplace/publishers/{publisherId}/submissions", publisherId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginSlug", "acme-support-shell",
                        "releaseChannel", "beta",
                        "manifest", objectMapper.readTree(manifestJson)
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status", is("SUBMITTED")))
            .andExpect(jsonPath("$.bundleSha256", notNullValue()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        String pluginVersionId = objectMapper.readTree(submissionResponse).path("pluginVersionId").asText();

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='mkp-template-acme-support')]").isEmpty());

        mockMvc.perform(asAdmin(
                post("/api/marketplace/submissions/{pluginVersionId}/validate", pluginVersionId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("reviewNotes", "Contract checks passed.")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("VALIDATED")))
            .andExpect(jsonPath("$.reviewNotes", containsString("Contract checks passed")));

        mockMvc.perform(asAdmin(
                post("/api/marketplace/submissions/{pluginVersionId}/publish", pluginVersionId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("reviewNotes", "Approved for catalog release.")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status", is("PUBLISHED")))
            .andExpect(jsonPath("$.reviewNotes", containsString("Approved")));

        mockMvc.perform(asAdmin(get("/api/marketplace/publishers/{publisherId}", publisherId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.publisher.slug", is("acme-marketplace")))
            .andExpect(jsonPath("$.submissions[0].status", is("PUBLISHED")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='mkp-template-acme-support')].latestVersion", is(List.of("1.0.0"))));

        mockMvc.perform(asAdmin(
                post("/api/marketplace/templates/{pluginId}/bootstrap", "mkp-template-acme-support")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginVersion", "1.0.0",
                        "name", "Acme Publisher Template Deployment",
                        "environment", "dev",
                        "templateId", "dev-openai-lucene"
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name", is("Acme Publisher Template Deployment")));
    }

    @Test
    void publisherVisibilityIsScopedToOwnerForNonAdmins() {
        String ownerOnePublisherId = runAsPrincipal(
            principal("publisher-owner-one", PlatformRole.PLATFORM_OPERATOR, "Publisher Owner One"),
            () -> marketplacePublisherService.createPublisher(
                new CreateMarketplacePublisherRequest("owner-one", "Owner One", "owner-one@example.com")
            ).id()
        );
        runAsPrincipal(
            principal("publisher-owner-two", PlatformRole.PLATFORM_OPERATOR, "Publisher Owner Two"),
            () -> marketplacePublisherService.createPublisher(
                new CreateMarketplacePublisherRequest("owner-two", "Owner Two", "owner-two@example.com")
            ).id()
        );

        List<MarketplacePublisherSummary> visibleToOwnerOne = runAsPrincipal(
            principal("publisher-owner-one", PlatformRole.PLATFORM_OPERATOR, "Publisher Owner One"),
            marketplacePublisherService::listPublishers
        );
        assertEquals(1, visibleToOwnerOne.size());
        assertEquals(ownerOnePublisherId, visibleToOwnerOne.getFirst().id());

        ResponseStatusException forbidden = assertThrows(
            ResponseStatusException.class,
            () -> runAsPrincipal(
                principal("publisher-owner-one", PlatformRole.PLATFORM_OPERATOR, "Publisher Owner One"),
                () -> marketplacePublisherService.getPublisher("owner-two")
            )
        );
        assertEquals(FORBIDDEN, forbidden.getStatusCode());
    }

    private MockHttpServletRequestBuilder asAdmin(MockHttpServletRequestBuilder builder) {
        return builder.header(PLATFORM_API_KEY_HEADER, ADMIN_API_KEY);
    }

    private DeploymentSummary createSharedQdrantDeployment(String name) {
        DeploymentSummary deployment = deploymentService.createDeployment(
            new CreateDeploymentRequest(name, "dev", "dev-openai-qdrant", null, "PLATFORM_MANAGED")
        );
        configureSharedQdrantDeployment(deployment.id());
        return deployment;
    }

    private void configureSharedQdrantDeployment(String deploymentId) {
        DeploymentDraftResponse draft = deploymentService.getActiveDraftForDeployment(deploymentId);
        ObjectNode providerConfig = objectMapper.createObjectNode();
        providerConfig.setAll((ObjectNode) objectMapper.valueToTree(draft.providerConfig()));
        providerConfig.put("vectorProvisioningMode", "PLATFORM_MANAGED");
        providerConfig.put("vectorStoragePosture", "SHARED");
        providerConfig.put("qdrantManagedCollectionsEnabled", true);
        providerConfig.put("qdrantCloudProviderId", "aws");
        providerConfig.put("qdrantCloudRegionId", "eu-west-1");
        deploymentService.updateDraft(
            draft.id(),
            new UpdateDeploymentDraftRequest(
                null,
                null,
                null,
                providerConfig,
                null,
                null,
                null,
                null,
                null
            )
        );
    }

    private <T> T runAsAdmin(Supplier<T> supplier) {
        authenticate(principal("admin@example.com", PlatformRole.PLATFORM_ADMIN, "Platform Admin"));
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private <T> T runAsPrincipal(PlatformPrincipal principal, Supplier<T> supplier) {
        authenticate(principal);
        try {
            return supplier.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private PlatformPrincipal principal(String actorId, PlatformRole role, String displayName) {
        return new PlatformPrincipal(actorId, role, displayName, "SESSION");
    }

    private void authenticate(PlatformPrincipal principal) {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            List.of(new SimpleGrantedAuthority(principal.role().authority()))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
