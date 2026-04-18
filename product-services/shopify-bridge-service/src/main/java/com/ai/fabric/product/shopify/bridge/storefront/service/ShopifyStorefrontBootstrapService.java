package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

import static org.springframework.util.StringUtils.hasText;

@Service
public class ShopifyStorefrontBootstrapService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;

    public ShopifyStorefrontBootstrapService(PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public ShopifyStorefrontBootstrapResponse bootstrap(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (!"INSTALLED".equalsIgnoreCase(store.installStatus())) {
            return unavailable(store, "Shopify Companion is not currently installed for this store.");
        }
        if (!hasText(store.consumerId()) || !hasText(store.deploymentId())) {
            return unavailable(store, "Shopify Companion provisioning is incomplete. Finish merchant bootstrap first.");
        }
        if (!"READY".equalsIgnoreCase(store.sourceReadinessStatus())) {
            return unavailable(store, "Store data is not ready yet. Run source preflight and complete publish/apply/verify before enabling the widget.");
        }

        PlatformPublicConsumerDeploymentCredentialsResponse credentials =
            platformShopifyStoreClient.getConsumerCredentials(store.consumerId());
        ShopifyBridgeStoreSummary updated = platformShopifyStoreClient.recordWidgetStatus(
            store.shopDomain(),
            new ShopifyBridgeRecordWidgetStatusRequest(
                "ENABLED",
                "THEME_APP_EXTENSION",
                "Theme app extension resolved storefront bootstrap."
            )
        );

        String bridgeQueryUrl = "/api/storefront/shops/" + encodePathSegment(updated.shopDomain()) + "/chat/query";
        String bridgeSuggestionsUrl = "/api/storefront/shops/" + encodePathSegment(updated.shopDomain()) + "/chat/suggestions";
        String preferredIntegrationMode = credentials.integration() == null ? null : credentials.integration().preferredIntegrationMode();
        String runtimeAuthMode = credentials.integration() == null || credentials.integration().posture() == null
            ? null
            : credentials.integration().posture().runtimeAuthMode();
        String guidance = credentials.integration() == null ? null : credentials.integration().guidance();

        return new ShopifyStorefrontBootstrapResponse(
            true,
            updated.shopDomain(),
            updated.consumerId(),
            updated.deploymentId(),
            updated.widgetStatus(),
            updated.sourceReadinessStatus(),
            preferredIntegrationMode,
            runtimeAuthMode,
            bridgeQueryUrl,
            bridgeSuggestionsUrl,
            guidance,
            "Storefront bootstrap resolved. Theme app extension can now call the bridge-backed shopper endpoints."
        );
    }

    private ShopifyStorefrontBootstrapResponse unavailable(ShopifyBridgeStoreSummary store, String message) {
        return new ShopifyStorefrontBootstrapResponse(
            false,
            store.shopDomain(),
            store.consumerId(),
            store.deploymentId(),
            store.widgetStatus(),
            store.sourceReadinessStatus(),
            null,
            null,
            null,
            null,
            null,
            message
        );
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }
}
