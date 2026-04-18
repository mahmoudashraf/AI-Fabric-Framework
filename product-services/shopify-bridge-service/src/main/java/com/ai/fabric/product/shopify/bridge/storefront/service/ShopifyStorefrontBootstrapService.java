package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@Service
public class ShopifyStorefrontBootstrapService {

    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";

    private final PlatformShopifyStoreClient platformShopifyStoreClient;

    public ShopifyStorefrontBootstrapService(PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public ShopifyStorefrontBootstrapResponse bootstrap(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (store.readiness() == null || !store.readiness().storefrontReady()) {
            return unavailable(store, firstStorefrontBlockingReason(store));
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
        String bridgeEventUrl = "/api/storefront/shops/" + encodePathSegment(updated.shopDomain()) + "/events";
        String preferredIntegrationMode = credentials.integration() == null ? null : credentials.integration().preferredIntegrationMode();
        String runtimeAuthMode = credentials.integration() == null || credentials.integration().posture() == null
            ? null
            : credentials.integration().posture().runtimeAuthMode();
        String guidance = credentials.integration() == null ? null : credentials.integration().guidance();
        String launcherLabel = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().launcherLabel() != null && !updated.widgetDetail().settings().launcherLabel().isBlank()
            ? updated.widgetDetail().settings().launcherLabel().trim()
            : DEFAULT_LAUNCHER_LABEL;
        String welcomeMessage = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().welcomeMessage() != null && !updated.widgetDetail().settings().welcomeMessage().isBlank()
            ? updated.widgetDetail().settings().welcomeMessage().trim()
            : DEFAULT_WELCOME_MESSAGE;

        return new ShopifyStorefrontBootstrapResponse(
            true,
            updated.shopDomain(),
            updated.consumerId(),
            updated.deploymentId(),
            updated.widgetStatus(),
            updated.sourceReadinessStatus(),
            launcherLabel,
            welcomeMessage,
            preferredIntegrationMode,
            runtimeAuthMode,
            bridgeQueryUrl,
            bridgeSuggestionsUrl,
            bridgeEventUrl,
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
            DEFAULT_LAUNCHER_LABEL,
            DEFAULT_WELCOME_MESSAGE,
            null,
            null,
            null,
            null,
            null,
            null,
            message
        );
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        if (store.readiness() != null && !store.readiness().storefrontBlockingReasons().isEmpty()) {
            return store.readiness().storefrontBlockingReasons().get(0);
        }
        return "Storefront bootstrap is not available for this store yet.";
    }

    private String encodePathSegment(String value) {
        return UriUtils.encodePathSegment(value == null ? "" : value.trim(), StandardCharsets.UTF_8);
    }
}
