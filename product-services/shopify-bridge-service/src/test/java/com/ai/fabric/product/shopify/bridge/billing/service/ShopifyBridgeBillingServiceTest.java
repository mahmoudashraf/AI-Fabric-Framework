package com.ai.fabric.product.shopify.bridge.billing.service;

import com.ai.fabric.product.shopify.bridge.billing.config.ShopifyBridgeBillingProperties;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShopifyBridgeBillingServiceTest {

    @Test
    void freeModeIsActiveAndNotBlocked() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("FREE", "", "", "", 0, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            mock(ShopifyAdminGraphqlClient.class)
        );

        var summary = service.summarize();

        assertThat(summary.mode()).isEqualTo("FREE");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isFalse();
    }

    @Test
    void paidModeWithoutRecurringPricingConfigIsBlocked() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "", "", "", 0, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            mock(ShopifyAdminGraphqlClient.class)
        );

        var summary = service.summarize();

        assertThat(summary.mode()).isEqualTo("SHOPIFY_APP_SUBSCRIPTION");
        assertThat(summary.status()).isEqualTo("SETUP_REQUIRED");
        assertThat(summary.launchBlocked()).isTrue();
        assertThat(summary.merchantApprovalRequired()).isTrue();
    }

    @Test
    void paidModeWithRecurringPricingConfigIsReadyForApproval() {
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "29.00", "USD", "EVERY_30_DAYS", 7, true),
            properties("https://bridge.example.com", "shopify-api-key"),
            mock(ShopifyAdminGraphqlClient.class)
        );

        var summary = service.summarize();

        assertThat(summary.status()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(summary.launchBlocked()).isTrue();
        assertThat(summary.merchantApprovalRequired()).isTrue();
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
                                "name", "Companion Pro",
                                "status", "ACTIVE"
                            )
                        )
                    )
                )
            ));
        ShopifyBridgeBillingService service = new ShopifyBridgeBillingService(
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "29.00", "USD", "EVERY_30_DAYS", 0, false),
            properties("https://bridge.example.com", "shopify-api-key"),
            client
        );

        var summary = service.summarizeForShop("alpha.myshopify.com", "token");

        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.launchBlocked()).isFalse();
        assertThat(summary.merchantApprovalRequired()).isFalse();
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
            billingProperties("SHOPIFY_APP_SUBSCRIPTION", "29.00", "USD", "EVERY_30_DAYS", 7, true),
            properties("https://bridge.example.com", "shopify-api-key"),
            client
        );

        var response = service.createApproval("alpha.myshopify.com", "token");

        assertThat(response.status()).isEqualTo("READY_FOR_APPROVAL");
        assertThat(response.confirmationUrl()).isEqualTo("https://alpha.myshopify.com/admin/charges/confirm");
    }

    private ShopifyBridgeBillingProperties billingProperties(String mode,
                                                             String amount,
                                                             String currencyCode,
                                                             String interval,
                                                             int trialDays,
                                                             boolean test) {
        return new ShopifyBridgeBillingProperties(
            mode,
            "Companion Pro",
            "companion-pro",
            amount,
            currencyCode,
            interval,
            trialDays,
            test
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
