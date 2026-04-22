package com.ai.fabric.product.shopify.bridge.client.platform;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeResolvedStoreCredentials;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWebhookEventRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreCredentialsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSourcePreflightCategorySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpdateWidgetSettingsRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeUpsertStoreRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PlatformShopifyStoreClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private PlatformShopifyStoreClient client;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
        client = new PlatformShopifyStoreClient(
            restClientBuilder,
            new ShopifyBridgeProperties(
                "Shopify Bridge",
                "shopify-bridge-test",
                "SHOPIFY",
                "SHOPIFY_BRIDGE_SERVICE",
                "test",
                "https://bridge.example.com",
                "2026-04",
                "shopify-api-key",
                "shopify-api-secret",
                "https://platform.example.com",
                "platform-admin-key",
                "X-PLATFORM-API-KEY",
                "webhook-secret",
                "bridge-admin-key",
                "X-BRIDGE-API-KEY"
            )
        );
    }

    @Test
    void listStoresUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess("""
                [
                  {
                    "id":"shp-1",
                    "shopDomain":"alpha.myshopify.com",
                    "displayName":"Alpha",
                    "productServiceRef":"shopify-bridge-prod",
                    "productServiceDisplayName":"Shopify Bridge Prod",
                    "customerId":"cust-1",
                    "customerName":"Alpha Customer",
                    "deploymentId":"dep-1",
                    "deploymentName":"Alpha Deployment",
                    "deploymentStatus":"DRAFT",
                    "consumerId":"consumer-alpha",
                    "consumerDisplayName":"Alpha Storefront",
                    "installStatus":"INSTALLED",
                    "syncStatus":"NOT_SYNCED",
                    "sourceReadinessStatus":"NOT_RUN",
                    "widgetStatus":"NOT_ENABLED",
                    "onboardingStatus":"PLATFORM_BOOTSTRAPPED",
                    "productsEnabled":true,
                    "collectionsEnabled":true,
                    "pagesEnabled":false,
                    "policiesEnabled":true,
                    "sourcePreflight":null,
                    "lastSourcePreflightAt":null,
                    "lastSyncAt":null,
                    "lastWebhookAt":null,
                    "createdAt":"2026-04-18T00:00:00Z",
                    "updatedAt":"2026-04-18T00:00:00Z"
                  }
                ]
                """, MediaType.APPLICATION_JSON));

        List<ShopifyBridgeStoreSummary> stores = client.listStores();

        assertThat(stores).hasSize(1);
        assertThat(stores.get(0).shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(stores.get(0).productServiceRef()).isEqualTo("shopify-bridge-prod");
        server.verify();
    }

    @Test
    void bootstrapUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/bootstrap"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess("""
                {
                  "shopDomain":"alpha.myshopify.com",
                  "customerId":"cust-1",
                  "deploymentId":"dep-1",
                  "consumerId":"consumer-alpha",
                  "createdCustomer":true,
                  "createdDeployment":true,
                  "createdConsumer":true,
                  "installedPluginIds":["mkp-template-shopify-companion","mkp-action-shopify-companion-read","mkp-data-shopify-catalog","mkp-data-shopify-policies","mkp-inference-shopify-companion-default"],
                  "store":{
                    "id":"shp-1",
                    "shopDomain":"alpha.myshopify.com",
                    "displayName":"Alpha",
                    "productServiceRef":"shopify-bridge-prod",
                    "productServiceDisplayName":"Shopify Bridge Prod",
                    "customerId":"cust-1",
                    "customerName":"Alpha Customer",
                    "deploymentId":"dep-1",
                    "deploymentName":"Alpha Deployment",
                    "deploymentStatus":"DRAFT",
                    "consumerId":"consumer-alpha",
                    "consumerDisplayName":"Alpha Storefront",
                    "installStatus":"INSTALLED",
                    "syncStatus":"NOT_SYNCED",
                    "sourceReadinessStatus":"NOT_RUN",
                    "widgetStatus":"NOT_ENABLED",
                    "onboardingStatus":"PLATFORM_BOOTSTRAPPED",
                    "productsEnabled":true,
                    "collectionsEnabled":true,
                    "pagesEnabled":false,
                    "policiesEnabled":true,
                    "sourcePreflight":null,
                    "lastSourcePreflightAt":null,
                    "lastSyncAt":null,
                    "lastWebhookAt":null,
                    "createdAt":"2026-04-18T00:00:00Z",
                    "updatedAt":"2026-04-18T00:00:00Z"
                  }
                }
                """, MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreBootstrapResponse response = client.bootstrap("alpha.myshopify.com");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        assertThat(response.createdDeployment()).isTrue();
        assertThat(response.installedPluginIds()).contains("mkp-action-shopify-companion-read");
        server.verify();
    }

    @Test
    void goLiveUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/go-live"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "NOT_SYNCED", "NOT_ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.goLive("alpha.myshopify.com");

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        server.verify();
    }

    @Test
    void markUninstalledUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/uninstall"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("NOT_RUN", "NOT_SYNCED", "NOT_ENABLED", "UNINSTALLED", "BLOCKED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.markUninstalled("alpha.myshopify.com");

        assertThat(response.installStatus()).isEqualTo("UNINSTALLED");
        server.verify();
    }

    @Test
    void deleteStoreUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com?force=true"))
            .andExpect(method(HttpMethod.DELETE))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess());

        client.deleteStore("alpha.myshopify.com", true);

        server.verify();
    }

    @Test
    void recordSourcePreflightUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/source-preflight"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "NOT_SYNCED", "NOT_ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.recordSourcePreflight(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordSourcePreflightRequest(List.of(
                new ShopifyBridgeStoreSourcePreflightCategorySummary("products", true, "READY", 120, "Products reachable")
            ))
        );

        assertThat(response.sourceReadinessStatus()).isEqualTo("READY");
        server.verify();
    }

    @Test
    void resolveCredentialMaterialUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/credentials/material"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess("""
                {
                  "accessToken":"shpat_access",
                  "refreshToken":"shprt_refresh",
                  "accessTokenExpiresAt":"2026-04-18T01:00:00Z",
                  "refreshTokenExpiresAt":"2026-07-18T00:00:00Z",
                  "scopesText":"read_products,read_content,read_legal_policies",
                  "expiring":true
                }
                """, MediaType.APPLICATION_JSON));

        ShopifyBridgeResolvedStoreCredentials response = client.resolveCredentialMaterial("alpha.myshopify.com");

        assertThat(response.accessToken()).isEqualTo("shpat_access");
        assertThat(response.refreshToken()).isEqualTo("shprt_refresh");
        server.verify();
    }

    @Test
    void recordSyncUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/sync-status"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "SYNCED", "NOT_ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.recordSyncStatus(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordSyncStatusRequest("SYNCED", "FULL", 128, "Initial import completed")
        );

        assertThat(response.syncStatus()).isEqualTo("SYNCED");
        server.verify();
    }

    @Test
    void recordWidgetUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/widget-status"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "SYNCED", "ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.recordWidgetStatus(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordWidgetStatusRequest("ENABLED", "THEME_APP_EXTENSION", "Theme embed enabled")
        );

        assertThat(response.widgetStatus()).isEqualTo("ENABLED");
        server.verify();
    }

    @Test
    void updateWidgetSettingsUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/widget-settings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "SYNCED", "ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.updateWidgetSettings(
            "alpha.myshopify.com",
            new ShopifyBridgeUpdateWidgetSettingsRequest(
                "Need help?",
                "Ask me about products and policies.",
                "GUIDED_COMMERCE",
                List.of("ai-search", "comparison")
            )
        );

        assertThat(response.shopDomain()).isEqualTo("alpha.myshopify.com");
        server.verify();
    }

    @Test
    void recordWebhookUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/webhook-events"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "NOT_SYNCED", "ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.recordWebhookEvent(
            "alpha.myshopify.com",
            new ShopifyBridgeRecordWebhookEventRequest(
                "products/update",
                "CONTENT_CHANGED",
                "products",
                "UPDATE",
                "gid://shopify/Product/1",
                "2026-04-22T00:00:00Z",
                "wh_123",
                "checksum-123",
                1,
                "Shopify product content changed. Incremental sync is required.",
                true
            )
        );

        assertThat(response.syncStatus()).isEqualTo("NOT_SYNCED");
        server.verify();
    }

    @Test
    void upsertUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "NOT_SYNCED", "NOT_ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.upsertStore(new ShopifyBridgeUpsertStoreRequest(
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "cust-1",
            "dep-1",
            "consumer-alpha",
            "UNINSTALLED",
            "NOT_SYNCED",
            "READY",
            "NOT_ENABLED",
            "BLOCKED",
            true,
            true,
            false,
            true
        ));

        assertThat(response.installStatus()).isEqualTo("INSTALLED");
        server.verify();
    }

    @Test
    void upsertCredentialsUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/shopify/stores/alpha.myshopify.com/credentials"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess(storeBody("READY", "NOT_SYNCED", "NOT_ENABLED"), MediaType.APPLICATION_JSON));

        ShopifyBridgeStoreSummary response = client.upsertCredentials(
            "alpha.myshopify.com",
            new ShopifyBridgeUpsertStoreCredentialsRequest(
                "shpat_access",
                "shprt_refresh",
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products",
                true
            )
        );

        assertThat(response.credentials()).isNotNull();
        assertThat(response.credentials().status()).isEqualTo("READY");
        server.verify();
    }

    @Test
    void getConsumerCredentialsUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/public/consumers/consumer-alpha/credentials"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess("""
                {
                  "consumerId":"consumer-alpha",
                  "deploymentId":"dep-1",
                  "runtimeBaseUrl":"https://runtime.example.com",
                  "integration":{
                    "preferredIntegrationMode":"PRIVATE_RUNTIME_BACKEND_MEDIATED",
                    "posture":{
                      "runtimeAuthMode":"SIGNED_PRIVATE_RUNTIME",
                      "verifiedAuthContextRequired":true,
                      "hostBackedRuntimeRequired":true,
                      "directConnectorAccessSupported":false
                    },
                    "runtime":{
                      "chatBaseUrl":"https://runtime.example.com/api/chat/me",
                      "chatQueryUrl":"https://runtime.example.com/api/chat/me/query",
                      "suggestionsUrl":"https://runtime.example.com/api/chat/me/suggestions",
                      "conversationsUrl":"https://runtime.example.com/api/chat/me/conversations",
                      "authContextUrl":"https://runtime.example.com/api/chat/me/auth-context"
                    },
                    "guidance":"Route customer traffic through your host or storefront backend."
                  }
                }
                """, MediaType.APPLICATION_JSON));

        var response = client.getConsumerCredentials("consumer-alpha");

        assertThat(response.consumerId()).isEqualTo("consumer-alpha");
        assertThat(response.integration()).isNotNull();
        assertThat(response.integration().preferredIntegrationMode()).isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        server.verify();
    }

    @Test
    void queryConsumerBridgeChatUsesPlatformAdminApiKeyAndShopperSessionHeader() {
        server.expect(requestTo("https://platform.example.com/api/public/consumers/consumer-alpha/bridge/chat/query"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andExpect(header("X-AI-FABRIC-SHOPPER-SESSION-ID", "shopper-session-1"))
            .andRespond(withSuccess("""
                {"success":true,"conversationId":"conv-1","result":{"message":"Hello from runtime"}}
                """, MediaType.APPLICATION_JSON));

        var response = client.queryConsumerBridgeChat("consumer-alpha", null, "shopper-session-1");

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        server.verify();
    }

    @Test
    void suggestConsumerBridgeChatUsesPlatformAdminApiKey() {
        server.expect(requestTo("https://platform.example.com/api/public/consumers/consumer-alpha/bridge/chat/suggestions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-PLATFORM-API-KEY", "platform-admin-key"))
            .andRespond(withSuccess("""
                {"success":true,"suggestions":["Show me backpacks","What is your return policy?"]}
                """, MediaType.APPLICATION_JSON));

        var response = client.suggestConsumerBridgeChat("consumer-alpha", null, null);

        assertThat(response.path("suggestions")).hasSize(2);
        server.verify();
    }

    private String storeBody(String sourceReadinessStatus, String syncStatus, String widgetStatus) {
        return storeBody(sourceReadinessStatus, syncStatus, widgetStatus, "INSTALLED", "PLATFORM_BOOTSTRAPPED");
    }

    private String storeBody(String sourceReadinessStatus,
                             String syncStatus,
                             String widgetStatus,
                             String installStatus,
                             String onboardingStatus) {
        return """
            {
              "id":"shp-1",
              "shopDomain":"alpha.myshopify.com",
              "displayName":"Alpha",
              "productServiceRef":"shopify-bridge-prod",
              "productServiceDisplayName":"Shopify Bridge Prod",
              "customerId":"cust-1",
              "customerName":"Alpha Customer",
              "deploymentId":"dep-1",
              "deploymentName":"Alpha Deployment",
              "deploymentStatus":"DRAFT",
              "consumerId":"consumer-alpha",
              "consumerDisplayName":"Alpha Storefront",
              "installStatus":"%s",
              "syncStatus":"%s",
              "sourceReadinessStatus":"%s",
              "widgetStatus":"%s",
              "onboardingStatus":"%s",
              "productsEnabled":true,
              "collectionsEnabled":true,
              "pagesEnabled":false,
              "policiesEnabled":true,
              "credentials":{
                "status":"READY",
                "accessTokenPresent":true,
                "refreshTokenPresent":true,
                "accessTokenSecretRef":"MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "refreshTokenSecretRef":"MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                "checkedAt":"2026-04-18T00:00:00Z",
                "accessTokenExpiresAt":"2026-04-18T01:00:00Z",
                "refreshTokenExpiresAt":"2026-07-18T00:00:00Z",
                "scopesText":"read_products",
                "expiring":true
              },
              "sourcePreflight":null,
              "syncDetail":null,
              "widgetDetail":null,
              "lastSourcePreflightAt":null,
              "lastSyncAt":null,
              "lastWebhookAt":null,
              "createdAt":"2026-04-18T00:00:00Z",
              "updatedAt":"2026-04-18T00:00:00Z"
            }
            """.formatted(installStatus, syncStatus, sourceReadinessStatus, widgetStatus, onboardingStatus);
    }
}
