package com.ai.fabric.product.shopify.bridge.billing.service;

import com.ai.fabric.product.shopify.bridge.billing.config.ShopifyBridgeBillingProperties;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordedBillingStateSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSupportSubscriptionSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyBridgeBillingServiceTest {

    @Test
    void freeModeIsActiveAndNotBlocked() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("FREE", "", "", "", 0, false, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            mock(ShopifyAdminGraphqlClient.class),
            mock(ShopifyInstallRecordService.class),
            null
        );

        var summary = service.summarize();

        assertThat(summary.mode()).isEqualTo("FREE");
        assertThat(summary.tierKey()).isEqualTo("FREE");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isFalse();
        assertThat(summary.catalogProductCap()).isEqualTo(50);
        assertThat(summary.allowedSurfaces()).containsExactly("ai-search");
        assertThat(summary.availablePlans())
            .anySatisfy(plan -> {
                if ("FREE".equals(plan.tierKey())) {
                    assertThat(plan.allowedSurfaces()).containsExactly("ai-search");
                    assertThat(plan.chatFallbackEnabled()).isFalse();
                }
            });
    }

    @Test
    void paidModeWithoutRecurringPricingConfigKeepsFreeTierActive() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "", "", "", 0, false, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            mock(ShopifyAdminGraphqlClient.class),
            mock(ShopifyInstallRecordService.class),
            null
        );

        var summary = service.summarize();

        assertThat(summary.mode()).isEqualTo("SHOPIFY_APP_SUBSCRIPTION");
        assertThat(summary.tierKey()).isEqualTo("FREE");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isFalse();
        assertThat(summary.availablePlans())
            .anySatisfy(plan -> {
                assertThat(plan.tierKey()).isEqualTo("STARTER");
                assertThat(plan.commerciallyAvailable()).isFalse();
            });
    }

    @Test
    void paidModeWithRecurringPricingConfigAdvertisesStarterUpgrade() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(activeSubscriptionsQuery())))
            .thenReturn(Map.of(
                "data", Map.of(
                    "currentAppInstallation", Map.of(
                        "activeSubscriptions", List.of()
                    )
                )
            ));
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "29.00", "USD", "EVERY_30_DAYS", 7, true, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            client,
            mock(ShopifyInstallRecordService.class),
            null
        );

        var summary = service.summarizeForShop("alpha.myshopify.com", "token");

        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.tierKey()).isEqualTo("FREE");
        assertThat(summary.merchantApprovalRequired()).isFalse();
        assertThat(summary.availablePlans())
            .anySatisfy(plan -> {
                assertThat(plan.tierKey()).isEqualTo("STARTER");
                assertThat(plan.commerciallyAvailable()).isTrue();
                assertThat(plan.merchantApprovalSupported()).isTrue();
                assertThat(plan.allowedSurfaces()).contains("comparison");
                assertThat(plan.chatFallbackEnabled()).isTrue();
            });
    }

    @Test
    void summarizeForShopReturnsActiveWhenShopifyHasActiveSubscription() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(activeSubscriptionsQuery())))
            .thenReturn(Map.of(
                "data", Map.of(
                    "currentAppInstallation", Map.of(
                        "activeSubscriptions", List.of(
                            Map.of(
                                "id", "gid://shopify/AppSubscription/1",
                                "name", "Loom Companion Starter",
                                "status", "ACTIVE"
                            )
                        )
                    )
                )
            ));
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "29.00", "USD", "EVERY_30_DAYS", 0, false, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            client,
            mock(ShopifyInstallRecordService.class),
            null
        );

        var summary = service.summarizeForShop("alpha.myshopify.com", "token");

        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.tierKey()).isEqualTo("STARTER");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isFalse();
        assertThat(summary.paidTier()).isTrue();
    }

    @Test
    void createApprovalReturnsConfirmationUrl() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(activeSubscriptionsQuery())))
            .thenReturn(Map.of(
                "data", Map.of(
                    "currentAppInstallation", Map.of(
                        "activeSubscriptions", List.of()
                    )
                )
            ));
        when(client.execute(eq("alpha.myshopify.com"), eq("token"), eq(createSubscriptionMutation()), anyMap()))
            .thenReturn(Map.of(
                "data", Map.of(
                    "appSubscriptionCreate", Map.of(
                        "confirmationUrl", "https://alpha.myshopify.com/admin/charges/confirm",
                        "appSubscription", Map.of("id", "gid://shopify/AppSubscription/2"),
                        "userErrors", List.of()
                    )
                )
            ));
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "29.00", "USD", "EVERY_30_DAYS", 7, true, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            client,
            mock(ShopifyInstallRecordService.class),
            null
        );

        var response = service.createApproval("alpha.myshopify.com", "token", "STARTER");

        assertThat(response.status()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(response.confirmationUrl()).isEqualTo("https://alpha.myshopify.com/admin/charges/confirm");
    }

    @Test
    void freeModeUsesRecordedEliteBillingStateForStoreEntitlements() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.of(new ShopifyInstallRecordSummary(
            "alpha.myshopify.com",
            "INSTALLED",
            "https://alpha.myshopify.com",
            null,
            null,
            null,
            null,
            "read_products,read_orders",
            null,
            null,
            true,
            Instant.parse("2026-04-26T00:00:00Z"),
            "ELITE",
            "ACTIVE",
            List.of(new ShopifyBridgeSupportSubscriptionSummary(
                "recorded-shopify-billing-elite",
                "Loom Companion Elite",
                "ACTIVE",
                "ELITE",
                true
            )),
            Instant.parse("2026-04-26T00:00:00Z"),
            Instant.parse("2026-04-26T00:00:00Z"),
            Instant.parse("2026-04-26T00:00:00Z"),
            null
        )));
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("FREE", "", "", "", 0, false, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            client,
            installRecordService,
            null
        );

        var summary = service.summarizeForShop("alpha.myshopify.com", null);

        assertThat(summary.mode()).isEqualTo("FREE");
        assertThat(summary.tierKey()).isEqualTo("ELITE");
        assertThat(summary.chatFallbackEnabled()).isTrue();
        assertThat(summary.actionCapable()).isTrue();
        assertThat(summary.requiresExplicitConfirmation()).isTrue();
        assertThat(summary.allowedSurfaces()).contains("comparison", "order-lookup");
        verify(client, never()).execute(eq("alpha.myshopify.com"), eq("token"), eq(activeSubscriptionsQuery()));
    }

    @Test
    void freeModeUsesPlatformRecordedEliteBillingStateAfterBridgeRestart() {
        ShopifyAdminGraphqlClient client = mock(ShopifyAdminGraphqlClient.class);
        ShopifyInstallRecordService installRecordService = mock(ShopifyInstallRecordService.class);
        PlatformShopifyStoreClient platformShopifyStoreClient = mock(PlatformShopifyStoreClient.class);
        when(installRecordService.findByShopDomain("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(platformShopifyStoreClient.getBillingState("alpha.myshopify.com"))
            .thenReturn(new ShopifyBridgeRecordedBillingStateSummary(
                "alpha.myshopify.com",
                "ELITE",
                "ACTIVE",
                "sub-1",
                "Loom Companion Elite",
                Instant.parse("2026-04-26T00:00:00Z"),
                "live verification"
            ));
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("FREE", "", "", "", 0, false, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            client,
            installRecordService,
            platformShopifyStoreClient
        );

        var summary = service.summarizeForShop("alpha.myshopify.com", null);

        assertThat(summary.mode()).isEqualTo("FREE");
        assertThat(summary.tierKey()).isEqualTo("ELITE");
        assertThat(summary.chatFallbackEnabled()).isTrue();
        assertThat(summary.actionCapable()).isTrue();
        assertThat(summary.message()).contains("Platform-recorded Shopify billing state");
        verify(client, never()).execute(eq("alpha.myshopify.com"), eq("token"), eq(activeSubscriptionsQuery()));
    }

    private ShopifyBridgeBillingProperties billingProperties(String mode,
                                                             String amount,
                                                             String currencyCode,
                                                             String interval,
                                                             int trialDays,
                                                             boolean test,
                                                             boolean eliteEnabled) {
        return new ShopifyBridgeBillingProperties(
            mode,
            "Companion Free",
            "",
            "",
            currencyCode,
            interval,
            0,
            false,
            amount != null && !amount.isBlank(),
            "Loom Companion Starter",
            "loom-companion-starter",
            amount,
            currencyCode,
            interval,
            trialDays,
            test,
            eliteEnabled,
            "Loom Companion Elite",
            "loom-companion-elite",
            eliteEnabled ? "179.00" : "",
            "USD",
            "EVERY_30_DAYS",
            0,
            false
        );
    }

    private ShopifyBridgeProperties properties(String publicBaseUrl, String shopifyApiKey) {
        return new ShopifyBridgeProperties(
            "Bridge",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "prod",
            publicBaseUrl,
            "2026-04",
            shopifyApiKey,
            "shopify-secret",
            "https://platform.example.com",
            "platform-admin-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private String activeSubscriptionsQuery() {
        return """
        query ShopifyBridgeActiveSubscriptions {
          currentAppInstallation {
            activeSubscriptions {
              id
              name
              status
            }
          }
        }
        """;
    }

    private String createSubscriptionMutation() {
        return """
        mutation ShopifyBridgeCreateAppSubscription($name: String!, $returnUrl: URL!, $lineItems: [AppSubscriptionLineItemInput!]!, $trialDays: Int, $test: Boolean) {
          appSubscriptionCreate(name: $name, returnUrl: $returnUrl, lineItems: $lineItems, trialDays: $trialDays, test: $test) {
            confirmationUrl
            appSubscription {
              id
            }
            userErrors {
              field
              message
            }
          }
        }
        """;
    }
}
