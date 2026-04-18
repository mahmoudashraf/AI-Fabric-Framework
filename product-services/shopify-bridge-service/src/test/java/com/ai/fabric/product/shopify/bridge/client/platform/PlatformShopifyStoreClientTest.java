package com.ai.fabric.product.shopify.bridge.client.platform;

import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreBootstrapResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

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
                "https://platform.example.com",
                "platform-admin-key",
                "X-PLATFORM-API-KEY",
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
                  "installedPluginIds":["mkp-template-commerce-shell","mkp-action-shopify-admin"],
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
        assertThat(response.installedPluginIds()).contains("mkp-action-shopify-admin");
        server.verify();
    }
}
