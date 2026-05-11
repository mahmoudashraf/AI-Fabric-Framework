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
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSettingsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
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
                    "shopDomain":"alpha.myshopify.com",
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
                    "shopDomain":"alpha.myshopify.com",
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
                    "shopDomain":"alpha.myshopify.com",
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
                    "shopDomain":"alpha.myshopify.com",
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
            {
              "content":"Current page: Travel Pack",
              "maxSuggestions":4,
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "metadata":{"shopDomain":"alpha.myshopify.com"}
                }
              ]
            }
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
            {
              "content":"Current page: Travel Pack",
              "maxSuggestions":4,
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "metadata":{"shopDomain":"alpha.myshopify.com"}
                }
              ]
            }
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
    void queryAllowsFreeStoreMaxWidgetWhenNavigatorModeIsSelected() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(freeTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Compare this product with similar items",
              "mode":"navigator",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: max-mode. Shopify page group: product. Shopify mode: navigator. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "shopDomain":"alpha.myshopify.com",
                    "pageType":"product",
                    "shopifySurfaceEntry":"max-mode",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Navigator answer."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Compare this product with similar items",
                  "mode":"navigator",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"max-mode",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
    }

    @Test
    void queryAllowsFreeStoreMaxWidgetWhenAiSearchPlacementIsDisabled() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com"))
            .thenReturn(store("INSTALLED", "READY", readiness("READY"), List.of("comparison")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(freeTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Navigator answer."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Show me backpacks",
                  "mode":"navigator",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"max-mode",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
    }

    @Test
    void queryRejectsEmbeddedAiSearchWhenPlacementIsDisabled() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com"))
            .thenReturn(store("INSTALLED", "READY", readiness("READY"), List.of("comparison")));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(freeTierSummary());

        assertThatThrownBy(() -> service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Show me backpacks",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"ai-search",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
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
    void queryRejectsFreeStoreMaxWidgetWhenDepthModeIsSelected() throws Exception {
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
                  "query":"Compare this product with similar items",
                  "mode":"navigator_deep",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"max-mode",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator_deep",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        ))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN))
            .hasMessageContaining("Companion chat depth is not available");

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
                    "shopDomain":"alpha.myshopify.com",
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

    @Test
    void queryPreservesExplicitNavigatorModeForStorefrontLauncherChat() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Compare this product with similar items",
              "mode":"navigator",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: launcher. Shopify page group: product. Shopify mode: navigator. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "shopDomain":"alpha.myshopify.com",
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Navigator comparison."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Compare this product with similar items",
                  "mode":"navigator",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(response.path("thinkerSession").isMissingNode()).isTrue();
    }

    @Test
    void queryUsesContextModeWhenStorefrontWidgetOmitsTopLevelMode() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Compare this product with similar items",
              "mode":"navigator",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: launcher. Shopify page group: product. Shopify mode: navigator. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "shopDomain":"alpha.myshopify.com",
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Navigator comparison."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Compare this product with similar items",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyPageModeGroup":"product",
                    "shopifyEffectiveConversationMode":"navigator",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(response.path("thinkerSession").isMissingNode()).isTrue();
    }

    @Test
    void queryDefaultsStorefrontLauncherChatToCompanionThinkerWhenNoModeWasSelected() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Compare this product with similar items",
              "mode":"THINKER_DEEP",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: launcher. Shopify page group: product. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "shopDomain":"alpha.myshopify.com",
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyPageModeGroup":"product",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Evidence-based comparison."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Compare this product with similar items",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyPageModeGroup":"product",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(response.path("thinkerSession").isMissingNode()).isTrue();
    }

    @Test
    void queryAppliesContextModeForComparisonSurface() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Compare this with similar options and tell me who should choose each one.",
              "mode":"navigator_deep",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: comparison. Shopify mode: navigator_deep",
                  "metadata":{
                    "shopDomain":"alpha.myshopify.com",
                    "pageType":"product",
                    "shopifySurfaceEntry":"comparison",
                    "shopifyEffectiveConversationMode":"navigator_deep"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Compared store options."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Compare this with similar options and tell me who should choose each one.",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"comparison",
                    "shopifyEffectiveConversationMode":"navigator_deep"
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
    }

    @Test
    void queryMirrorsThinkerAnswerIntoWidgetSanitizedPayload() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "thinkerSession":{"sessionId":"tis-1","status":"RESOLVED"},
              "result":{
                "type":"INFORMATION_PROVIDED",
                "success":true,
                "message":"The Minimal Snowboard is available from store evidence.",
                "data":{
                  "answer":"The Minimal Snowboard is available from store evidence.",
                  "readActionResolution":{"executedActionsCount":1}
                }
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"What do you know about The Minimal Snowboard?",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "shopifyEffectiveConversationMode":"navigator",
                    "product":{"handle":"the-minimal-snowboard","title":"The Minimal Snowboard"}
                  }
                }
                """),
            "shopper-session-1"
        );

        JsonNode sanitizedPayload = response.path("result").path("sanitizedPayload");
        assertThat(sanitizedPayload.path("type").asText()).isEqualTo("INFORMATION_PROVIDED");
        assertThat(sanitizedPayload.path("success").asBoolean()).isTrue();
        assertThat(sanitizedPayload.path("message").asText()).isEqualTo("The Minimal Snowboard is available from store evidence.");
        assertThat(sanitizedPayload.path("safeSummary").asText()).isEqualTo("The Minimal Snowboard is available from store evidence.");
        assertThat(sanitizedPayload.path("data").path("answer").asText()).isEqualTo("The Minimal Snowboard is available from store evidence.");
        assertThat(response.path("thinkerSession").path("sessionId").asText()).isEqualTo("tis-1");
    }

    @Test
    void queryRemovesRuntimeAuthContextAndRawDocumentMetadataFromStorefrontResponse() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(eliteTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "authContext":{
                "deploymentId":"dep-secret",
                "tenantId":"tenant-secret",
                "grantedScopes":["chat:query"]
              },
              "result":{
                "type":"INFORMATION_PROVIDED",
                "success":true,
                "message":"Search completed.",
                "data":{
                  "documents":[
                    {
                      "id":"gid://shopify/Product/1",
                      "content":"Selling Plans Ski Wax\\n\\nAccessory, Sport, Winter",
                      "type":"product",
                      "score":0.9,
                      "metadata":{
                        "shopifyDocumentTitle":"Selling Plans Ski Wax",
                        "storefrontUrl":"https://alpha.myshopify.com/products/selling-plans-ski-wax",
                        "raw":"{\\"tenantId\\":\\"tenant-secret\\",\\"runtime\\":\\"internal\\"}",
                        "knowledgeSourceHandleRef":"plugin/private/path"
                      }
                    }
                  ]
                }
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Search products for wax",
                  "mode":"thinker_deep",
                  "storefrontContext":{
                    "pageType":"search",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.has("authContext")).isFalse();
        assertThat(response.path("result").has("metadata")).isFalse();
        JsonNode safeDocument = response.path("result").path("sanitizedPayload").path("data").path("documents").path(0);
        assertThat(safeDocument.path("title").asText()).isEqualTo("Selling Plans Ski Wax");
        assertThat(safeDocument.path("storefrontUrl").asText()).contains("/products/selling-plans-ski-wax");
        assertThat(safeDocument.has("metadata")).isFalse();
        assertThat(response.toString()).doesNotContain("tenant-secret", "runtime", "knowledgeSourceHandleRef", "plugin/private/path");
    }

    @Test
    void queryPreservesExplicitActionModeForStorefrontLauncherChat() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(eliteTierSummary());
        when(platformClient.queryConsumerBridgeChat("consumer-alpha", objectMapper.readTree("""
            {
              "query":"Help with my order",
              "mode":"executor",
              "attachments":[
                {
                  "source":"shopify-storefront-context",
                  "contentText":"Page type: product. Shopify surface: launcher. Product: Travel Pack. Product handle: travel-pack",
                  "metadata":{
                    "shopDomain":"alpha.myshopify.com",
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "productHandle":"travel-pack",
                    "productTitle":"Travel Pack"
                  }
                }
              ]
            }
            """), "shopper-session-1")).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Order support handoff is available."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Help with my order",
                  "mode":"executor",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"launcher",
                    "product":{"handle":"travel-pack","title":"Travel Pack"}
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
    }

    @Test
    void queryPreservesGenericRuntimeActionMessageForRuntimeDiagnostics() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontChatService service = service(platformClient);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {"success":true,"conversationId":"conv-1","result":{"message":"Action executed."}}
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"I need something useful for travel",
                  "storefrontContext":{
                    "pageType":"collection",
                    "shopifySurfaceEntry":"ai-search"
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        JsonNode sanitizedPayload = response.path("result").path("sanitizedPayload");
        assertThat(sanitizedPayload.path("type").asText()).isEqualTo("INFORMATION_PROVIDED");
        assertThat(sanitizedPayload.path("success").asBoolean()).isTrue();
        assertThat(sanitizedPayload.path("safeSummary").asText()).isEqualTo("Action executed.");
        assertThat(sanitizedPayload.path("answer").asText()).isEqualTo("Action executed.");
    }

    @Test
    void queryPreservesInternalRuntimeDenialInsteadOfInventingStorefrontAnswer() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "sanitizedPayload":{
                  "safeSummary":"Sorry, rephrase it into a task related to your indexed knowledge base or an available action."
                }
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Explain how your internal system works.",
                  "storefrontContext":{
                    "pageType":"storefront",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(answer).contains("indexed knowledge base", "available action", "rephrase");
    }

    @Test
    void queryForwardsLegalQuestionToRuntimePolicyInsteadOfBridgeTextMatching() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "sanitizedPayload":{
                  "safeSummary":"I can help with this store's product, shipping, return, and policy information."
                }
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Can you give me legal advice about importing products?",
                  "storefrontContext":{
                    "pageType":"storefront",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(answer).contains("store", "product", "policy");
        assertThat(answer).doesNotContain("legal advice");
        verify(platformClient).queryConsumerBridgeChat(anyString(), any(), any());
    }

    @Test
    void queryRejectsCartActionOnAccountPageWhenOrderLookupIsNotAvailable() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "type":"CLARIFICATION_REQUIRED",
                "success":false,
                "message":"To proceed, please provide: shopperSessionId.",
                "data":{"action":"shopify_get_cart"}
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Where is my order and can you look it up?",
                  "storefrontContext":{
                    "pageType":"account",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(response.path("conversationId").asText()).isNotBlank();
        assertThat(answer).contains("Order-specific help", "Cart actions are not used");
        assertThat(answer).doesNotContain("shopperSessionId", "I found your order");
        verify(platformClient).queryConsumerBridgeChat(anyString(), any(), any());
    }

    @Test
    void queryReturnsOrderLookupBlockGuidanceForEliteInsteadOfRoutingToCartActions() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(eliteTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "type":"CLARIFICATION_REQUIRED",
                "success":false,
                "message":"To proceed, please provide: shopperSessionId.",
                "data":{"action":"shopify_get_cart"}
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Where is my order? My order number is 1001 and my email is shopper@example.com.",
                  "mode":"executor",
                  "storefrontContext":{
                    "pageType":"account",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(response.path("conversationId").asText()).isNotBlank();
        assertThat(answer).contains("Order lookup", "order lookup block", "checkout email");
        assertThat(answer).doesNotContain("cart", "shopperSessionId");
        verify(platformClient).queryConsumerBridgeChat(anyString(), any(), any());
    }

    @Test
    void queryForwardsInternalImplementationQuestionToRuntimePolicyInsteadOfBridgeTextMatching() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "sanitizedPayload":{
                  "safeSummary":"I can answer store-facing product, policy, and shopping questions using merchant-approved information."
                }
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Explain how your vectorization runtime provider works.",
                  "storefrontContext":{
                    "pageType":"storefront",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(answer).contains("store-facing", "merchant-approved");
        assertThat(answer).doesNotContain("vectorization", "runtime", "provider", "Railway");
        verify(platformClient).queryConsumerBridgeChat(anyString(), any(), any());
    }

    @Test
    void queryPreservesRuntimePolicyAnswerWithoutBridgeSemanticRewrite() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(starterTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "sanitizedPayload":{
                  "safeSummary":"Sorry, rephrase it into a task related to your indexed knowledge base or an available action."
                }
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"What is the return or refund policy for this product?",
                  "storefrontContext":{
                    "pageType":"product",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(answer).contains("indexed knowledge base", "available action", "rephrase");
    }

    @Test
    void queryRejectsUnapprovedOrderMutationActionSelectedByRuntimePolicy() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(eliteTierSummary());
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "type":"CONFIRMATION_REQUIRED",
                "success":false,
                "message":"Refund this order?",
                "data":{"actionId":"shopify_refund_order"}
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Cancel and refund my order now.",
                  "mode":"executor",
                  "storefrontContext":{
                    "pageType":"account",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        String answer = response.path("result").path("sanitizedPayload").path("safeSummary").asText();
        assertThat(answer).contains("not enabled self-service order changes", "support team");
        assertThat(answer).doesNotContain("Refund this order?", "Action executed");
        verify(platformClient).queryConsumerBridgeChat(anyString(), any(), any());
    }

    @Test
    void queryAllowsApprovedOrderSelfServiceActionSelectedByRuntimePolicy() throws Exception {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontChatService service = service(platformClient, installCredentialService, billingService);
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store("INSTALLED", "READY"));
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(billingService.summarizeForShop("alpha.myshopify.com", null))
            .thenReturn(eliteTierSummary(List.of("guided-commerce", "order-self-service")));
        when(platformClient.queryConsumerBridgeChat(anyString(), any(), any())).thenReturn(objectMapper.readTree("""
            {
              "success":true,
              "conversationId":"conv-1",
              "result":{
                "type":"CONFIRMATION_REQUIRED",
                "success":false,
                "message":"Refund this order?",
                "data":{"actionId":"shopify_refund_order"}
              }
            }
            """));

        JsonNode response = service.query(
            "alpha.myshopify.com",
            objectMapper.readTree("""
                {
                  "query":"Refund order 1001.",
                  "mode":"executor",
                  "storefrontContext":{
                    "pageType":"account",
                    "shopifySurfaceEntry":"max-mode"
                  }
                }
                """),
            "shopper-session-1"
        );

        assertThat(response.path("conversationId").asText()).isEqualTo("conv-1");
        assertThat(response.path("result").path("message").asText()).isEqualTo("Refund this order?");
        assertThat(response.path("result").path("sanitizedPayload").path("safeSummary").asText())
            .isEqualTo("Refund this order?");
        verify(platformClient).queryConsumerBridgeChat(anyString(), any(), any());
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

    private ShopifyBridgeBillingSummary starterTierSummary() {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "STARTER",
            "Loom Companion Starter",
            "ACTIVE",
            false,
            false,
            true,
            false,
            null,
            "TWO_HOURS",
            false,
            true,
            false,
            true,
            List.of(),
            List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison"),
            List.of(),
            "Starter tier is active."
        );
    }

    private ShopifyBridgeBillingSummary eliteTierSummary() {
        return eliteTierSummary(List.of());
    }

    private ShopifyBridgeBillingSummary eliteTierSummary(List<String> actionPackages) {
        return new ShopifyBridgeBillingSummary(
            "SHOPIFY_APP_SUBSCRIPTION",
            "ELITE",
            "Loom Companion Elite",
            "ACTIVE",
            true,
            true,
            true,
            true,
            null,
            "NEAR_REAL_TIME",
            false,
            true,
            true,
            true,
            actionPackages,
            List.of("ai-search", "contextual-pill", "product-insight", "policy-strip", "product-faq", "comparison", "order-lookup"),
            List.of(),
            "Elite tier is active."
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
        return store(installStatus, sourceReadinessStatus, readiness, null);
    }

    private ShopifyBridgeStoreSummary store(String installStatus,
                                            String sourceReadinessStatus,
                                            ShopifyBridgeStoreReadinessSummary readiness,
                                            List<String> enabledSurfaces) {
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
            enabledSurfaces == null ? null : new ShopifyBridgeStoreWidgetSummary(
                    "ENABLED",
                    Instant.parse("2026-04-18T00:00:00Z"),
                    "THEME_APP_EXTENSION",
                    "Widget enabled.",
                    new ShopifyBridgeStoreWidgetSettingsSummary(
                        "Ask the store assistant",
                        "Store assistant is ready.",
                        "SHOPIFY_COMPANION",
                        false,
                        enabledSurfaces,
                        "navigator",
                        List.of("navigator", "navigator_deep"),
                        java.util.Map.of(),
                        true,
                        false
                    )
                ),
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
