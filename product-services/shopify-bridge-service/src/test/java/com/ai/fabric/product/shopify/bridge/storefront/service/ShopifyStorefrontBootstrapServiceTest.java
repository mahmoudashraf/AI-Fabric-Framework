package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicDeploymentIntegrationSummary;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicRuntimeEndpointsSummary;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicRuntimePostureSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentReleaseSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreDeploymentVersionSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreCredentialSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreReadinessSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShopifyStorefrontBootstrapServiceTest {

    @Test
    void bootstrapResolvesConsumerCredentialsAndMarksWidgetEnabled() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontBootstrapService service = new ShopifyStorefrontBootstrapService(platformClient);
        ShopifyBridgeStoreSummary store = store("INSTALLED", "READY", "NOT_ENABLED", "consumer-alpha", "dep-1");
        ShopifyBridgeStoreSummary updated = store("INSTALLED", "READY", "ENABLED", "consumer-alpha", "dep-1");
        when(platformClient.getStore("alpha.myshopify.com")).thenReturn(store);
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

        ShopifyStorefrontBootstrapResponse response = service.bootstrap("alpha.myshopify.com");

        assertThat(response.available()).isTrue();
        assertThat(response.consumerId()).isEqualTo("consumer-alpha");
        assertThat(response.bridgeQueryUrl()).isEqualTo("/api/storefront/shops/alpha.myshopify.com/chat/query");
        assertThat(response.preferredIntegrationMode()).isEqualTo("PRIVATE_RUNTIME_BACKEND_MEDIATED");
        verify(platformClient).getConsumerCredentials("consumer-alpha");
    }

    @Test
    void bootstrapReturnsUnavailableWhenStoreNotReady() {
        PlatformShopifyStoreClient platformClient = mock(PlatformShopifyStoreClient.class);
        ShopifyStorefrontBootstrapService service = new ShopifyStorefrontBootstrapService(platformClient);
        when(platformClient.getStore("alpha.myshopify.com"))
            .thenReturn(store("INSTALLED", "NOT_RUN", "NOT_ENABLED", "consumer-alpha", "dep-1"));

        ShopifyStorefrontBootstrapResponse response = service.bootstrap("alpha.myshopify.com");

        assertThat(response.available()).isFalse();
        assertThat(response.message()).contains("Store data is not ready yet");
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
            new ShopifyBridgeStoreCredentialSummary(
                "READY",
                true,
                true,
                "MANAGED_SHOPIFY_ACCESS_TOKEN_ALPHA_AAAAAA",
                "MANAGED_SHOPIFY_REFRESH_TOKEN_ALPHA_BBBBBB",
                Instant.parse("2026-04-18T00:00:00Z"),
                Instant.parse("2026-04-18T01:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z"),
                "read_products,read_content",
                true
            ),
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
