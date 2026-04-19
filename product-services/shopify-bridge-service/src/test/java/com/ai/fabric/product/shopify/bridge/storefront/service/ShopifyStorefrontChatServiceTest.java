package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentVersionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryForwardsReadyStoreTrafficToPlatformConsumerBridge() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = new ShopifyStorefrontChatService(platformClient);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Show me backpacks",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "pageType":"product",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Here are some backpacks."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Show me backpacks",
                  "storefrontContext":{
                    "pageType":"product",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        verify(platformClient).queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Show me backpacks",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "pageType":"product",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1");
    }

    @Test
    void suggestionsNormalizesStorefrontContextBeforeForwarding() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = new ShopifyStorefrontChatService(platformClient);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(platformClient.suggestConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "content":"Current page: Travel Pack",
              "maxSuggestions":4,
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Page title: Travel Pack. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "pageType":"product",
                    "pageTitle":"Travel Pack",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"suggestions":["Tell me about Travel Pack"]}
            """));

        JsonNode response = service.suggestions(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "content":"Current page: Travel Pack",
                  "maxSuggestions":4,
                  "storefrontContext":{
                    "pageType":"product",
                    "pageTitle":"Travel Pack",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("suggestions")).hasSize(1);
        verify(platformClient).suggestConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "content":"Current page: Travel Pack",
              "maxSuggestions":4,
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Page title: Travel Pack. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "pageType":"product",
                    "pageTitle":"Travel Pack",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1");
    }

    @Test
    void suggestionsRejectsStoreWhenSourceReadinessIsNotReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = new ShopifyStorefrontChatService(platformClient);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "PENDING"));

        assertThatThrownBy(() -> service.suggestions("alpha.myshopify.com", objectMapper.createObjectNode(), null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Store data is not ready yet");
    }

    private ShopifyBridgeStoreSummary store(String installStatus,
                                            String sourceReadinessStatus) {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            "dep-1",
            "Alpha Deployment",
            "APPLIED_VERIFIED",
            "consumer-alpha",
            "Alpha Storefront",
            installStatus,
            "SYNCED",
            sourceReadinessStatus,
            "ENABLED",
            "PREFLIGHT_READY",
            true,
            true,
            true,
            true,
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content,read_legal_policies",
                true
            ),
            null,
            null,
            null,
            null,
            null,
            readiness(sourceReadinessStatus),
            new ShopifyBridgeStoreDeploymentVersionSummary(
                "ver-1",
                "v1",
                "PUBLISHED",
                Instant.parse("2026-04-18T00:00:00Z")
            ),
            new ShopifyBridgeStoreDeploymentReleaseSummary(
                "rel-1",
                "ver-1",
                "APPLIED_VERIFIED",
                "PASSED",
                "SUCCEEDED",
                "completed",
                "Release applied and verified.",
                null,
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T00:00:00Z")
            ),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z"),
            Instant.parse("2026-04-18T00:00:00Z")
        );
    }

    private ShopifyBridgeStoreReadinessSummary readiness(String sourceReadinessStatus) {
        if ("READY".equalsIgnoreCase(sourceReadinessStatus)) {
            return new ShopifyBridgeStoreReadinessSummary(
                "STOREFRONT_READY",
                true,
                true,
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
            );
        }
        return new ShopifyBridgeStoreReadinessSummary(
            "BLOCKED",
            false,
            false,
            java.util.List.of("Shopify source readiness is not READY yet."),
            java.util.List.of("Store data is not ready yet for alpha.myshopify.com. Complete source preflight and apply-time sync first."),
            java.util.List.of("Run source preflight and resolve any blocked Shopify source categories.")
        );
    }
}
