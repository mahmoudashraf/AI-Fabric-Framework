package com.ai.fabric.platform.backend.marketplace;

import com.ai.fabric.platform.backend.deployment.model.CreateDeploymentRequest;
import com.ai.fabric.platform.backend.deployment.model.DeploymentDraftResponse;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSummary;
import com.ai.fabric.platform.backend.deployment.model.UpdateDeploymentDraftRequest;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceEndpointEntity;
import com.ai.fabric.platform.backend.marketplace.entity.PlatformManagedInferenceServiceEntity;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceEndpointRepository;
import com.ai.fabric.platform.backend.marketplace.repository.PlatformManagedInferenceServiceRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
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
    private DeploymentVersionRepository deploymentVersionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MarketplacePublisherService marketplacePublisherService;

    @Autowired
    private PlatformManagedInferenceServiceRepository platformManagedInferenceServiceRepository;

    @Autowired
    private PlatformManagedInferenceEndpointRepository platformManagedInferenceEndpointRepository;

    @Test
    void catalogEndpointsExposeSeededMarketplacePluginsAndVersions() throws Exception {
        mockMvc.perform(asAdmin(get("/api/marketplace/plugins")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='mkp-template-commerce-shell')].pluginType", is(List.of("TEMPLATE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-template-support-desk-shell')].pluginType", is(List.of("TEMPLATE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-template-shopify-companion')].pluginType", is(List.of("TEMPLATE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-shopify-admin')].latestVersion", is(List.of("1.0.0"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-notifications')].latestVersion", is(List.of("1.0.0"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-shopify-companion-read')].pricing.pricingModel", is(List.of("FREE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-action-shopify-admin')].pricing.pricingModel", is(List.of("ONE_OFF"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-commerce-catalog')].pricing.pricingModel", is(List.of("SUBSCRIPTION"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-help-center')].pricing.pricingModel", is(List.of("FREE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-shopify-catalog')].pricing.pricingModel", is(List.of("FREE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-shopify-policies')].pricing.pricingModel", is(List.of("FREE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-inference-local-embeddings')].pluginType", is(List.of("INFERENCE_PROFILE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-inference-shopify-companion-default')].pluginType", is(List.of("INFERENCE_PROFILE"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-inference-optimized-orchestration')].pricing.pricingModel", is(List.of("SUBSCRIPTION"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-policy-folder')].pluginType", is(List.of("DATA"))))
            .andExpect(jsonPath("$[?(@.id=='mkp-data-commerce-catalog')].contributions.knowledgeSourceIds[0]", is(List.of("commerce-catalog"))));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-action-shopify-admin")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.versions[0].version", is("1.0.0")))
            .andExpect(jsonPath("$.versions[0].pricing.pricingModel", is("ONE_OFF")))
            .andExpect(jsonPath("$.versions[0].compatibility.supportedProviderModes", hasItem("llm:openai")))
            .andExpect(jsonPath("$.versions[0].installForm[?(@.id=='store')].type", is(List.of("text"))))
            .andExpect(jsonPath("$.versions[0].installForm[?(@.id=='apiKey')].type", is(List.of("secretRef"))))
            .andExpect(jsonPath("$.versions[0].permissions.requiresDeploymentSecrets", is(true)))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("shopify-order-read")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("shopify-order-cancel")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}/versions/{version}", "mkp-data-commerce-catalog", "1.0.0")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pluginId", is("mkp-data-commerce-catalog")))
            .andExpect(jsonPath("$.manifest.pluginType", is("DATA")))
            .andExpect(jsonPath("$.pricing.pricingModel", is("SUBSCRIPTION")))
            .andExpect(jsonPath("$.compatibility.supportedAuthModes", hasItem("PUBLIC_RUNTIME_AUTHENTICATED")))
            .andExpect(jsonPath("$.installForm[0].id", is("scope")))
            .andExpect(jsonPath("$.contributions.knowledgeSourceIds[0]", is("commerce-catalog")))
            .andExpect(jsonPath("$.manifest.contributions.datasets[0].datasetId", is("commerce-catalog-sql")))
            .andExpect(jsonPath("$.manifest.contributions.datasets[0].ingestionMode", is("EXTERNAL_SYNC_SQL")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-inference-byok-openai")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-inference-byok-openai")))
            .andExpect(jsonPath("$.versions[0].pluginId", is("mkp-inference-byok-openai")))
            .andExpect(jsonPath("$.versions[0].permissions.contributesProviders", is(true)))
            .andExpect(jsonPath("$.versions[0].installForm[?(@.id=='apiKey')].type", is(List.of("secretRef"))))
            .andExpect(jsonPath("$.versions[0].installForm[?(@.id=='baseUrl')].type", is(List.of("url"))))
            .andExpect(jsonPath("$.versions[0].contributions.inferenceProfileIds", hasItem("customer-openai")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-template-commerce-shell")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-action-shopify-admin")))
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-data-commerce-catalog")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-template-shopify-companion")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-action-shopify-companion-read")))
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-data-shopify-catalog")))
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-data-shopify-policies")))
            .andExpect(jsonPath("$.versions[0].recommendedPluginIds", hasItem("mkp-inference-shared-embeddings")));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-action-shopify-companion-read")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-action-shopify-companion-read")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("list_products")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("search_products")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("get_product_details")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("check_availability")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("get_policy")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("add_product_to_cart")))
            .andExpect(jsonPath("$.versions[0].contributions.actionIds", hasItem("update_cart_quantity")));

        mockMvc.perform(asAdmin(
                get("/api/marketplace/plugins/{pluginId}/versions/{version}", "mkp-action-shopify-companion-read", "1.0.0")
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='list_products')].groundingEligible", is(List.of(true))))
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='list_products')].readActionResolutionEligible", is(List.of(true))))
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='check_availability')].groundingEligible", is(List.of(true))))
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='check_availability')].readActionResolutionEligible", is(List.of(true))))
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='get_policy')].anonymousAllowed", is(List.of(true))))
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='add_product_to_cart')].requiresConfirmation", is(List.of(true))))
            .andExpect(jsonPath("$.manifest.contributions.actions[?(@.actionId=='add_product_to_cart')].groundingEligible", is(List.of(false))));

        mockMvc.perform(asAdmin(get("/api/marketplace/categories")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='template')].pluginCount", is(List.of(3))))
            .andExpect(jsonPath("$[?(@.id=='action')].pluginCount", is(List.of(3))))
            .andExpect(jsonPath("$[?(@.id=='data')].pluginCount", is(List.of(5))))
            .andExpect(jsonPath("$[?(@.id=='inference-profile')].pluginCount", is(List.of(8))));

        mockMvc.perform(asAdmin(get("/api/marketplace/plugins/{pluginId}", "mkp-inference-shared-embeddings")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.plugin.id", is("mkp-inference-shared-embeddings")))
            .andExpect(jsonPath("$.versions[0].contributions.inferenceManagedServiceRefs", hasItem("shared-embeddings-standard")))
            .andExpect(jsonPath("$.versions[0].pricing.pricingModel", is("SUBSCRIPTION")));

        mockMvc.perform(asAdmin(get("/api/marketplace/inference-services")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.serviceRef=='shared-embeddings-standard')].serviceKind", is(List.of("SHARED_EMBEDDING_SERVICE"))))
            .andExpect(jsonPath("$[?(@.serviceRef=='shared-ollama-orchestration')].serviceKind", is(List.of("SHARED_OLLAMA_SERVICE"))));
    }

    @Test
    void shopifyCompanionTemplateBootstrapsOnCustomStartFromScratch() throws Exception {
        mockMvc.perform(asAdmin(
                post("/api/marketplace/templates/{pluginId}/bootstrap", "mkp-template-shopify-companion")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginVersion", "1.0.0",
                        "name", "Shopify Companion Bootstrap Smoke",
                        "environment", "dev",
                        "templateId", "custom-start-from-scratch"
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id", notNullValue()))
            .andExpect(jsonPath("$.templateId", is("custom-start-from-scratch")));
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

        String inferenceInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-inference-byok-openai",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(
                            "baseUrl", "https://api.openai.com/v1",
                            "generationModel", "gpt-4.1-mini",
                            "embeddingModel", "text-embedding-3-small"
                        ),
                        "secretRefs", java.util.Map.of("apiKey", "sec-openai-byok")
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("INFERENCE_PROFILE")))
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String inferenceInstallId = objectMapper.readTree(inferenceInstallResponse).path("id").asText();

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
            .andExpect(jsonPath("$.marketplaceDatasetConfig.contractVersion", is("MARKETPLACE_DATASET_CONFIG_V1")))
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='help-center-seed')].marketplaceInstallId", is(List.of(helpCenterInstallId))))
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='help-center-seed')].ingestionMode", is(List.of("PACKAGED_SEED"))))
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='help-center-seed')].handleRef").exists())
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='help-center-seed')].datasetHash").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='docs')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='ai-search')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='support')]").exists())
            .andExpect(jsonPath("$.shellConfig.modules[?(@.id=='actions')]").exists())
            .andExpect(jsonPath("$.shellConfig.greeting.title", is("Support Desk")))
            .andExpect(jsonPath("$.shellConfig.starterPrompts[?(@.id=='support-capabilities')].query", is(List.of("What can you help me with?"))))
            .andExpect(jsonPath("$.shellConfig.starterPrompts[?(@.id=='refund-policy')].moduleId", is(List.of("docs"))))
            .andExpect(jsonPath("$.shellConfig.starterPrompts[?(@.id=='notification-troubleshooting')].moduleId", is(List.of("support"))))
            .andExpect(jsonPath("$.shellConfig.defaultConversationMode", is("guided-support")))
            .andExpect(jsonPath("$.providerConfig.generationBaseUrl", is("https://api.openai.com/v1")))
            .andExpect(jsonPath("$.providerConfig.generationApiKeySecretRef", is("sec-openai-byok")))
            .andExpect(jsonPath("$.providerConfig.generationModel", is("gpt-4.1-mini")))
            .andExpect(jsonPath("$.providerConfig.embeddingProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.embeddingApiKeySecretRef", is("sec-openai-byok")))
            .andExpect(jsonPath("$.providerConfig.openaiEmbeddingModel", is("text-embedding-3-small")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.contractVersion", is("MARKETPLACE_INFERENCE_PROVIDER_CONFIG_V1")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.profileIds", hasItem("customer-openai")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.installIds", hasItem(inferenceInstallId)));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-impact", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalInstalls", is(4)))
            .andExpect(jsonPath("$.templatePluginCount", is(1)))
            .andExpect(jsonPath("$.actionPluginCount", is(1)))
            .andExpect(jsonPath("$.dataPluginCount", is(1)))
            .andExpect(jsonPath("$.inferenceProfilePluginCount", is(1)))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-template-support-desk-shell")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-action-notifications")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-data-help-center")))
            .andExpect(jsonPath("$.installedPluginIds", hasItem("mkp-inference-byok-openai")))
            .andExpect(jsonPath("$.actionIds", hasItem("send-email")))
            .andExpect(jsonPath("$.knowledgeSourceIds", hasItem("help-center")))
            .andExpect(jsonPath("$.shellModuleIds", hasItem("support")))
            .andExpect(jsonPath("$.inferenceProfileIds", hasItem("customer-openai")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-installs", deploymentId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-template-support-desk-shell')].status", is(List.of("BOOTSTRAPPED"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-action-notifications')].readinessStatus", is(List.of("READY"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-data-help-center')].readinessStatus", is(List.of("READY"))))
            .andExpect(jsonPath("$[?(@.pluginId=='mkp-inference-byok-openai')].readinessStatus", is(List.of("READY"))));
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
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')].datasetRef", is(List.of("commerce-catalog-sql"))))
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='commerce-catalog-sql')].marketplaceInstallId", is(List.of(dataInstall))))
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='commerce-catalog-sql')].ingestionMode", is(List.of("EXTERNAL_SYNC_SQL"))))
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='commerce-catalog-sql')].syncConnector.connectionRef", is(List.of("platform-marketplace-demo-sql"))))
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
            .andExpect(jsonPath("$.knowledgeSourceConfig.sources[?(@.id=='commerce-catalog')].datasetRef", is(List.of("commerce-catalog-sql"))));

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
            .andExpect(jsonPath("$.marketplaceDatasetConfig.datasets[?(@.datasetId=='commerce-catalog-sql')]").isEmpty())
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
    void managedInferenceProfileInstallCanMigrateProviderModesAndCompilesIntoProviderConfig() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Inference Managed Profile", "dev", "dev-anthropic-lucene")
        ));

        String installResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-inference-optimized-orchestration",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("INFERENCE_PROFILE")))
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_REQUIRED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String installId = objectMapper.readTree(installResponse).path("id").asText();

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", deployment.id(), installId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerConfig.llmProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.orchestrationLlmProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.generationLlmProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.orchestrationEndpointProfile", is("openai-cloud-orchestration")))
            .andExpect(jsonPath("$.providerConfig.generationEndpointProfile", is("openai-cloud-default")))
            .andExpect(jsonPath("$.providerConfig.embeddingProvider", is("onnx")))
            .andExpect(jsonPath("$.providerConfig.embeddingEndpointProfile", is("onnx-bundled")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.contractVersion", is("MARKETPLACE_INFERENCE_PROVIDER_CONFIG_V1")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.profileIds", hasItem("optimized-orchestration")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.endpointProfileRefs", hasItem("openai-cloud-orchestration")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.endpointProfileRefs", hasItem("openai-cloud-default")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.endpointProfileRefs", hasItem("onnx-bundled")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.installIds", hasItem(installId)));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/marketplace-impact", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.inferenceProfilePluginCount", is(1)))
            .andExpect(jsonPath("$.inferenceProfileIds", hasItem("optimized-orchestration")))
            .andExpect(jsonPath("$.inferenceEndpointProfileRefs", hasItem("openai-cloud-orchestration")))
            .andExpect(jsonPath("$.inferenceEndpointProfileRefs", hasItem("onnx-bundled")));
    }

    @Test
    void sharedAndDedicatedInferenceProfilesCompileManagedServiceBindingsIntoProviderConfig() throws Exception {
        activateManagedInferenceService(
            "shared-embeddings-standard",
            "shared-embeddings-standard",
            "EMBEDDING",
            "https://shared-embeddings.dev.loom.test/v1"
        );
        activateManagedInferenceService(
            "shared-ollama-orchestration",
            "shared-ollama-orchestration",
            "ORCHESTRATION",
            "https://shared-ollama.dev.loom.test/v1"
        );

        DeploymentSummary sharedDeployment = runAsAdmin(() -> createSharedQdrantDeployment("Shared Inference Profile"));

        String sharedInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", sharedDeployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-inference-shared-embeddings",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("INFERENCE_PROFILE")))
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_REQUIRED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String sharedInstallId = objectMapper.readTree(sharedInstallResponse).path("id").asText();

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", sharedDeployment.id(), sharedInstallId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", sharedDeployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerConfig.embeddingProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.embeddingServiceMode", is("SHARED_PLATFORM_SERVICE")))
            .andExpect(jsonPath("$.providerConfig.embeddingManagedServiceRef", is("shared-embeddings-standard")))
            .andExpect(jsonPath("$.providerConfig.embeddingEndpointProfile", is("shared-embeddings-standard")))
            .andExpect(jsonPath("$.providerConfig.embeddingBaseUrl", is("https://shared-embeddings.dev.loom.test/v1")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.managedServiceRefs", hasItem("shared-embeddings-standard")));

        DeploymentSummary dedicatedDeployment = runAsAdmin(() -> createSharedQdrantDeployment("Dedicated Embedding Worker"));

        String dedicatedInstallResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", dedicatedDeployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-inference-dedicated-embedding-worker",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("INFERENCE_PROFILE")))
            .andExpect(jsonPath("$.readinessStatus", is("ENTITLEMENT_REQUIRED")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String dedicatedInstallId = objectMapper.readTree(dedicatedInstallResponse).path("id").asText();

        mockMvc.perform(asAdmin(
                put("/api/deployments/{deploymentId}/marketplace-installs/{installId}/entitlement", dedicatedDeployment.id(), dedicatedInstallId)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of("status", "ACTIVE")))
            ))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.readinessStatus", is("READY")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", dedicatedDeployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerConfig.embeddingProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.embeddingServiceMode", is("DEPLOYMENT_DEDICATED_SERVICE")))
            .andExpect(jsonPath("$.providerConfig.embeddingManagedServiceRef", containsString("dedicated-embedding-" + dedicatedDeployment.id())))
            .andExpect(jsonPath("$.providerConfig.embeddingEndpointProfile", is("dep-" + dedicatedDeployment.id() + "-embedding-worker")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.managedServiceRefs[0]", containsString("dedicated-embedding-" + dedicatedDeployment.id())))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.installIds", hasItem(dedicatedInstallId)));
    }

    @Test
    void byokInferenceProfileInstallUsesInstallFieldsAndOnlyOneInferenceProfileCanBeEnabled() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> deploymentService.createDeployment(
            new CreateDeploymentRequest("Inference BYOK Profile", "dev", "dev-openai-lucene")
        ));

        String installResponse = mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-inference-byok-openai",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(
                            "baseUrl", "https://api.openai.com/v1",
                            "generationModel", "gpt-4.1-mini",
                            "embeddingModel", "text-embedding-3-small"
                        ),
                        "secretRefs", java.util.Map.of("apiKey", "sec-openai-byok")
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginType", is("INFERENCE_PROFILE")))
            .andExpect(jsonPath("$.readinessStatus", is("READY")))
            .andReturn()
            .getResponse()
            .getContentAsString();
        String installId = objectMapper.readTree(installResponse).path("id").asText();

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.providerConfig.llmProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.generationLlmProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.generationBaseUrl", is("https://api.openai.com/v1")))
            .andExpect(jsonPath("$.providerConfig.generationApiKeySecretRef", is("sec-openai-byok")))
            .andExpect(jsonPath("$.providerConfig.generationModel", is("gpt-4.1-mini")))
            .andExpect(jsonPath("$.providerConfig.embeddingProvider", is("openai")))
            .andExpect(jsonPath("$.providerConfig.embeddingApiKeySecretRef", is("sec-openai-byok")))
            .andExpect(jsonPath("$.providerConfig.openaiEmbeddingModel", is("text-embedding-3-small")))
            .andExpect(jsonPath("$.providerConfig.openaiEmbeddingDimensions", is(1024)))
            .andExpect(jsonPath("$.providerConfig.embeddingEndpointProfile").doesNotExist())
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.profileIds", hasItem("customer-openai")))
            .andExpect(jsonPath("$.providerConfig.marketplaceInference.installIds", hasItem(installId)));

        mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-inference-local-embeddings",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message", containsString("Only one inference-profile plugin may be enabled per deployment")));
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
    void shopifyCompanionReadInstallCompilesGroundedReadResolutionActionsIntoDraftAndArtifact() throws Exception {
        DeploymentSummary deployment = runAsAdmin(() -> createSharedQdrantDeployment("Marketplace Shopify Companion Read Flags"));

        mockMvc.perform(asAdmin(
                post("/api/deployments/{deploymentId}/marketplace-installs", deployment.id())
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "pluginId", "mkp-action-shopify-companion-read",
                        "pluginVersion", "1.0.0",
                        "config", java.util.Map.of(),
                        "secretRefs", java.util.Map.of()
                    )))
            ))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.pluginId", is("mkp-action-shopify-companion-read")))
            .andExpect(jsonPath("$.pluginType", is("ACTION")))
            .andExpect(jsonPath("$.readinessStatus", is("READY")));

        mockMvc.perform(asAdmin(get("/api/deployments/{deploymentId}/draft", deployment.id())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='list_products')].groundingEligible", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='list_products')].readActionResolutionEligible", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='list_products')].anonymousAllowed", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='check_availability')].groundingEligible", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='check_availability')].readActionResolutionEligible", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='get_policy')].groundingEligible", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='get_policy')].readActionResolutionEligible", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='add_product_to_cart')].requiresConfirmation", is(List.of(true))))
            .andExpect(jsonPath("$.actionsConfig.actions[?(@.name=='add_product_to_cart')].accessMode", is(List.of("WRITE_ONLY"))));

        String draftId = runAsAdmin(() -> deploymentService.getActiveDraftForDeployment(deployment.id()).id());
        String versionId = runAsAdmin(() -> deploymentService.publishDraft(draftId).id());
        String actionsArtifactYaml = deploymentVersionRepository.findById(versionId)
            .orElseThrow()
            .getActionsArtifactYaml();

        assertThat(actionsArtifactYaml).contains("name: \"list_products\"");
        assertThat(actionsArtifactYaml).contains("name: \"check_availability\"");
        assertThat(actionsArtifactYaml).contains("name: \"add_product_to_cart\"");
        assertThat(actionsArtifactYaml).contains("requiresConfirmation: true");
        assertThat(actionsArtifactYaml).doesNotContain("name: \"relationship_query\"");
        assertThat(actionsArtifactYaml).doesNotContain("name: \"find_similar_products\"");
        assertThat(actionsArtifactYaml).doesNotContain("name: \"compare_products\"");
        assertThat(actionsArtifactYaml).contains("groundingEligible: true");
        assertThat(actionsArtifactYaml).contains("readActionResolutionEligible: true");
        assertThat(actionsArtifactYaml).contains("anonymousAllowed: true");
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

    private void activateManagedInferenceService(String serviceRef,
                                                 String endpointProfileRef,
                                                 String endpointPurpose,
                                                 String baseUrl) {
        PlatformManagedInferenceServiceEntity service = platformManagedInferenceServiceRepository
            .findByServiceRefIgnoreCase(serviceRef)
            .orElseThrow();
        service.setStatus("ACTIVE");
        service.setBaseUrl(baseUrl.substring(0, baseUrl.lastIndexOf("/v1")));
        service.setSecretName("MANAGED_TEST_" + serviceRef.toUpperCase().replace('-', '_') + "_KEY");
        service.setActualReplicas(1);
        platformManagedInferenceServiceRepository.save(service);

        PlatformManagedInferenceEndpointEntity endpoint = platformManagedInferenceEndpointRepository
            .findByProfileRefIgnoreCase(endpointProfileRef)
            .orElseThrow();
        endpoint.setServiceId(service.getId());
        endpoint.setEndpointPurpose(endpointPurpose);
        endpoint.setBaseUrl(baseUrl);
        endpoint.setStatus("ACTIVE");
        endpoint.setSecretName(service.getSecretName());
        platformManagedInferenceEndpointRepository.save(endpoint);
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
