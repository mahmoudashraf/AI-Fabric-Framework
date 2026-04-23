package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingPlanSummary;
import com.ai.fabric.product.shopify.bridge.billing.model.ShopifyBridgeBillingSummary;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicDeploymentIntegrationSummary;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicRuntimeEndpointsSummary;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicRuntimePostureSummary;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentVersionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSourcePreflightCategorySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSourcePreflightSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSettingsSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreWidgetSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontBootstrapServiceTest {

    @Test
    void bootstrapResolvesConsumerCredentialsAndMarksWidgetEnabled() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyBridgeInstallCredentialService installCredentialService = mock(ShopifyBridgeInstallCredentialService.class);
        ShopifyBridgeBillingService billingService = mock(ShopifyBridgeBillingService.class);
        ShopifyStorefrontBootstrapService service = new ShopifyStorefrontBootstrapService(
            platformClient,
            installCredentialService,
            mock(ShopifyInstallRecordService.class),
            billingService,
            mock(ShopifyStorefrontGovernedActionService.class),
            properties("https://bridge.example.com")
        );
        ShopifyBridgeStoreSummary store = store("INSTALLED", "READY", "NOT_ENABLED", "consumer-alpha", "dep-1");
        ShopifyBridgeStoreSummary updated = store("INSTALLED", "READY", "ENABLED", "consumer-alpha", "dep-1");
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store);
        when(installCredentialService.resolvePersistedMaterial("alpha.myshopify.com")).thenReturn(Optional.empty());
        when(platformClient.getConsumerCredentials("consumer-alpha")).thenReturn(new PlatformPublicConsumerDeploymentCredentialsResponse(
            "consumer-alpha",
            "dep-1",
            "https://runtime.example.com",
            new PlatformPublicDeploymentIntegrationSummary(
                "PRIVATE_RUNTIME_BACKEND_MEDIATED",
                new PlatformPublicRuntimePostureSummary("SIGNED_PRIVATE_RUNTIME", true, true, false),
                new PlatformPublicRuntimeEndpointsSummary(
                    "https://runtime.example.com/api/chat/me",
                    "https://runtime.example.com/api/chat/me/query",
                    "https://runtime.example.com/api/chat/me/suggestions",
                    "https://runtime.example.com/api/chat/me/conversations",
                    "https://runtime.example.com/api/chat/me/auth-context"
                ),
                "Route storefront traffic through the Shopify Bridge backend."
            )
        ));
        when(platformClient.recordWidgetStatus(eq("alpha.myshopify.com"), eq(new ShopifyBridgeRecordWidgetStatusRequest(
            "ENABLED",
            "THEME_APP_EXTENSION",
            "Theme app extension resolved storefront bootstrap."
        )))).thenReturn(updated);
        when(billingService.summarizeForShop("alpha.myshopify.com", null)).thenReturn(freeTierSummary());
        when(billingService.effectiveAllowedSurfaces("alpha.myshopify.com", null, List.of("ai-search", "comparison")))
            .thenReturn(List.of("ai-search"));

        ShopifyStorefrontBootstrapResponse response = service.bootstrap("alpha.myshopify.com");

        assertThat(response.available()).isTrue();
        assertThat(response.consumerId()).isEqualTo("consumer-alpha");
        assertThat(response.billingTier()).isEqualTo("FREE");
        assertThat(response.billingStatus()).isEqualTo("ACTIVE");
        assertThat(response.catalogProductCap()).isEqualTo(50);
        assertThat(response.poweredByBadgeRequired()).isTrue();
        assertThat(response.chatFallbackEnabled()).isFalse();
        assertThat(response.launcherLabel()).isEqualTo("Need help?");
        assertThat(response.welcomeMessage()).isEqualTo("Ask me about products and store policies.");
        assertThat(response.enabledSurfaces()).containsExactly("ai-search");
        assertThat(response.groundingSignals())
            .contains("Catalog product grounding", "Review-aware product grounding", "Collection grounding", "Store page grounding", "Policy grounding");
        assertThat(response.supportedReviewProviders())
            .containsExactly("Judge.me", "Loox");
        assertThat(response.bridgeQueryUrl()).isEqualTo("https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/chat/query");
        assertThat(response.bridgeEventUrl()).isEqualTo("https://bridge.example.com/api/storefront/shops/alpha.myshopify.com/events");
        assertThat(response.preferredIntegrationMode()).isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        verify(platformClient).getConsumerCredentials("consumer-alpha");
    }

    @Test
    void bootstrapReturnsUnavailableWhenStoreNotReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontBootstrapService service = new ShopifyStorefrontBootstrapService(
            platformClient,
            mock(ShopifyBridgeInstallCredentialService.class),
            mock(ShopifyInstallRecordService.class),
            mock(ShopifyBridgeBillingService.class),
            mock(ShopifyStorefrontGovernedActionService.class),
            properties("https://bridge.example.com")
        );
        when(platformClient.getStore("alpha.myshopify.com"))
            .thenReturn(store("INSTALLED", "NOT_RUN", "NOT_ENABLED", "consumer-alpha", "dep-1"));

        ShopifyStorefrontBootstrapResponse response = service.bootstrap("alpha.myshopify.com");

        assertThat(response.available()).isFalse();
        assertThat(response.message()).contains("Store data is not ready yet");
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
            List.of(
                new ShopifyBridgeBillingPlanSummary(
                    "FREE",
                    "Loom Companion Free",
                    null,
                    null,
                    null,
                    true,
                    true,
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
                    "Free tier is always available."
                )
            ),
            "Free tier is active."
        );
    }

    private ShopifyBridgeProperties properties(String publicBaseUrl) {
        return new ShopifyBridgeProperties(
            "Shopify Bridge Service",
            "shopify-bridge-prod",
            "SHOPIFY",
            "SHOPIFY_BRIDGE_SERVICE",
            "production",
            publicBaseUrl,
            "2026-04",
            "shopify-api-key",
            "shopify-api-secret",
            "https://platform.example.com",
            "platform-api-key",
            "X-PLATFORM-API-KEY",
            "webhook-secret",
            "bridge-admin-key",
            "X-BRIDGE-API-KEY"
        );
    }

    private ShopifyBridgeStoreSummary store(String installStatus,
                                            String sourceReadinessStatus,
                                            String widgetStatus,
                                            String consumerId,
                                            String deploymentId) {
        return new ShopifyBridgeStoreSummary(
            "shp-1",
            "alpha.myshopify.com",
            "Alpha",
            "shopify-bridge-prod",
            "Shopify Bridge Prod",
            "cust-1",
            "Alpha Customer",
            deploymentId,
            "Alpha Deployment",
            "APPLIED_VERIFIED",
            consumerId,
            "Alpha Storefront",
            installStatus,
            "SYNCED",
            sourceReadinessStatus,
            widgetStatus,
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
            new ShopifyBridgeStoreSourcePreflightSummary(
                "READY",
                Instant.parse("2026-04-18T00:00:00Z"),
                List.of(
                    new ShopifyBridgeStoreSourcePreflightCategorySummary(
                        "products",
                        true,
                        "READY",
                        12,
                        "Products reachable",
                        List.of("Judge.me", "Loox")
                    )
                )
            ),
            null,
            null,
            new ShopifyBridgeStoreWidgetSummary(
                widgetStatus,
                Instant.parse("2026-04-18T00:00:00Z"),
                "THEME_APP_EXTENSION",
                "Theme extension status recorded.",
                new ShopifyBridgeStoreWidgetSettingsSummary(
                    "Need help?",
                    "Ask me about products and store policies.",
                    "GUIDED_COMMERCE",
                    List.of("ai-search", "comparison")
                )
            ),
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
                java.util.List.of("Enable the Shopify theme app extension and load the storefront once to finish widget activation.")
            );
        }
        return new ShopifyBridgeStoreReadinessSummary(
            "BLOCKED",
            false,
            false,
            java.util.List.of("Shopify source readiness is not READY yet."),
            java.util.List.of("Store data is not ready yet. Run source preflight and complete publish/apply/verify before enabling the widget."),
            java.util.List.of("Run source preflight and resolve any blocked Shopify source categories.")
        );
    }
}
