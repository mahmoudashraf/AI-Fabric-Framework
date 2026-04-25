package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentVersionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void queryForwardsReadyStoreTrafficToPlatformConsumerBridge() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = service(platformClient);
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
        ShopifyStorefrontChatService service = service(platformClient);
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
    void suggestionsAllowsSupportBlockedStoreWhenBaseStorefrontContractIsReady() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = service(platformClient);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(supportBlockedStore());
        when(platformClient.suggestConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {"content":"Current page: Travel Pack","maxSuggestions":4}
            """), null)).thenReturn(objectMapper.readTree("""
            {"success":true,"suggestions":["Tell me about Travel Pack"]}
            """));

        JsonNode response = service.suggestions(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {"content":"Current page: Travel Pack","maxSuggestions":4}
                """),
            null
        );

        assertThat(response.path("suggestions")).hasSize(1);
        verify(platformClient).suggestConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {"content":"Current page: Travel Pack","maxSuggestions":4}
            """), null);
    }

    @Test
    void suggestionsRejectsStoreWhenSourceReadinessIsNotReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = service(platformClient);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "PENDING"));

        assertThatThrownBy(() -> service.suggestions("alpha.myshopify.com", objectMapper.createObjectNode(), null))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Store data is not ready yet");
    }

    @Test
    void queryRejectsStarterOnlySurfaceWhenFreeStorePostsDirectContext() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(freeTierSummary());

        assertThatThrownBy(() -> service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Compare this with alternatives",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"comparison",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));

        verify(platformClient, never()).queryConsumerBridgeChat(anyString(), any(), any());
    }

    @Test
    void queryNormalizesTopLevelShopifyContextIntoHiddenAttachment() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(freeTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Show me backpacks",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: ai-search. Shopify page group: product. Shopify mode: navigator. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"ai-search",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
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
                  "pageType":"product",
                  "shopifySurfaceEntry":"ai-search",
                  "shopifyPageModeGroup":"product",
                  "shopifyEffectiveConversationMode":"navigator",
                  "product":{"handle":"travel-pack","title":"Travel Pack"}
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
    }

    private ShopifyStorefrontChatService service(PlatformShopifyStoreClient platformClient) {
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        when(installCredentialService.resolvePersistedMaterial(anyString())).thenReturn(Optional.empty());
        when(billingService.summarizeForShop(anyString(), isNull())).thenReturn(freeTierSummary());
        return service(platformClient, installCredentialService, billingService);
    }

    private ShopifyStorefrontChatService service(PlatformShopifyStoreClient platformClient,
                                                 ShopifyBridgeInstallCredentialService installCredentialService,
                                                 ShopifyBridgeBillingService billingService) {
        return new ShopifyStorefrontChatService(platformClient, installCredentialService, billingService);
    }

    private ShopifyBridgeBillingSummary freeTierSummary() {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "FREE",
            "Loom Companion Free",
            "ACTIVE",
            false,
            false,
            false,
            false,
            50,
            "DAILY",
            true,
            false,
            false,
            false,
            List.of(),
            List.of("ai-search"),
            List.of(),
            "Free tier is active."
        );
    }

    private ShopifyBridgeStoreSummary store(String installStatus,
                                            String sourceReadinessStatus) {
        return store(installStatus, sourceReadinessStatus, readiness(sourceReadinessStatus));
    }

    private ShopifyBridgeStoreSummary supportBlockedStore() {
        return store(
            "INSTALLED",
            "READY",
            new ShopifyBridgeStoreReadinessSummary(
                "BLOCKED",
                false,
                false,
                java.util.List.of("Customer-safe order lookup and governed support posture are not ready for go-live yet."),
                java.util.List.of("Customer-safe order lookup is waiting for Shopify order-read scope approval on this store."),
                java.util.List.of("Approve the required Shopify order-read scope before enabling customer-safe order lookup.")
            )
        );
    }

    private ShopifyBridgeStoreSummary store(String installStatus,
                                            String sourceReadinessStatus,
                                            ShopifyBridgeStoreReadinessSummary readiness) {
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
            false,
            false,
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
            readiness,
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
