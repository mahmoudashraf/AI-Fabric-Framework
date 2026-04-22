package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.platform.model.PlatformPublicConsumerDeploymentCredentialsResponse;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordWidgetStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontBootstrapResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class ShopifyStorefrontBootstrapService {

    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";
    private static final String DEFAULT_SHELL_MODE_PROFILE = "SHOPIFY_COMPANION";
    private static final List<String> DEFAULT_ENABLED_SURFACES = List.of(
        "ai-search",
        "contextual-pill",
        "product-insight",
        "policy-strip",
        "product-faq",
        "comparison"
    );

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeProperties properties;

    public ShopifyStorefrontBootstrapService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                             ShopifyBridgeProperties properties) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.properties = properties;
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

        String bridgeQueryUrl = storefrontUrl(updated.shopDomain(), "/chat/query");
        String bridgeSuggestionsUrl = storefrontUrl(updated.shopDomain(), "/chat/suggestions");
        String bridgeEventUrl = storefrontUrl(updated.shopDomain(), "/events");
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
        String shellModeProfile = updated.widgetDetail() != null && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().shellModeProfile() != null && !updated.widgetDetail().settings().shellModeProfile().isBlank()
            ? updated.widgetDetail().settings().shellModeProfile().trim()
            : DEFAULT_SHELL_MODE_PROFILE;
        List<String> enabledSurfaces = updated.widgetDetail() != null
            && updated.widgetDetail().settings() != null
            && updated.widgetDetail().settings().enabledSurfaces() != null
            && !updated.widgetDetail().settings().enabledSurfaces().isEmpty()
            ? List.copyOf(updated.widgetDetail().settings().enabledSurfaces())
            : DEFAULT_ENABLED_SURFACES;

        return new ShopifyStorefrontBootstrapResponse(
            true,
            updated.shopDomain(),
            updated.consumerId(),
            updated.deploymentId(),
            updated.widgetStatus(),
            updated.sourceReadinessStatus(),
            launcherLabel,
            welcomeMessage,
            shellModeProfile,
            enabledSurfaces,
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
            DEFAULT_SHELL_MODE_PROFILE,
            DEFAULT_ENABLED_SURFACES,
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

    private String storefrontUrl(String shopDomain, String suffix) {
        String path = "/api/storefront/shops/" + encodePathSegment(shopDomain) + suffix;
        if (properties.publicBaseUrl().isBlank()) {
            return path;
        }
        String base = properties.publicBaseUrl().endsWith("/")
            ? properties.publicBaseUrl().substring(0, properties.publicBaseUrl().length() - 1)
            : properties.publicBaseUrl();
        return base + path;
    }
}
