package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ShopifyStorefrontPreviewService {

    private static final String EXTENSION_HANDLE = "companion-app-embed";
    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";
    private static final String DEFAULT_WELCOME_MESSAGE =
        "Store assistant is ready. Ask about products, policies, or collections.";

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeProperties properties;

    public ShopifyStorefrontPreviewService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                           ShopifyBridgeProperties properties) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.properties = properties;
    }

    public ShopifyStorefrontPreviewResponse preview(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        String bridgeBaseUrl = blankToNull(properties.publicBaseUrl());
        List<String> blockingReasons = new ArrayList<>();
        if (store.readiness() != null && store.readiness().storefrontBlockingReasons() != null) {
            blockingReasons.addAll(store.readiness().storefrontBlockingReasons());
        }
        if (bridgeBaseUrl == null) {
            blockingReasons.add("Shopify Bridge public base URL is not configured yet.");
        }

        boolean ready = blockingReasons.isEmpty();
        String launcherLabel = store.widgetDetail() != null && store.widgetDetail().settings() != null
            ? blankToNull(store.widgetDetail().settings().launcherLabel())
            : null;
        String welcomeMessage = store.widgetDetail() != null && store.widgetDetail().settings() != null
            ? blankToNull(store.widgetDetail().settings().welcomeMessage())
            : null;
        String message = ready
            ? "Storefront theme app extension can be enabled now."
            : blockingReasons.get(0);

        return new ShopifyStorefrontPreviewResponse(
            ready,
            store.shopDomain(),
            storefrontBaseUrl(store.shopDomain()),
            bridgeBaseUrl,
            store.widgetStatus(),
            store.onboardingStatus(),
            store.consumerId(),
            store.deploymentId(),
            EXTENSION_HANDLE,
            launcherLabel == null ? DEFAULT_LAUNCHER_LABEL : launcherLabel,
            welcomeMessage == null ? DEFAULT_WELCOME_MESSAGE : welcomeMessage,
            buildThemeEditorActivationUrl(store.shopDomain()),
            List.of(
                "Open Shopify Admin > Online Store > Themes > Customize.",
                "Enable the Companion launcher app embed.",
                "Set Bridge base URL to " + (bridgeBaseUrl == null ? "<bridge-public-base-url>" : bridgeBaseUrl) + ".",
                "Keep the launcher label as-is or set it from the Companion app widget settings.",
                "Use the Companion app welcome message as the first assistant response in the launcher.",
                "Open the storefront once after enabling the embed to let the bridge record widget activation."
            ),
            List.copyOf(blockingReasons),
            message
        );
    }

    private String storefrontBaseUrl(String shopDomain) {
        if (shopDomain == null || shopDomain.isBlank()) {
            return null;
        }
        return "https://" + shopDomain.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String buildThemeEditorActivationUrl(String shopDomain) {
        String apiKey = blankToNull(properties.shopifyApiKey());
        String normalizedShopDomain = blankToNull(shopDomain);
        if (apiKey == null || normalizedShopDomain == null || !normalizedShopDomain.endsWith(".myshopify.com")) {
            return null;
        }
        String storeHandle = normalizedShopDomain.substring(0, normalizedShopDomain.indexOf(".myshopify.com"))
            .toLowerCase(Locale.ROOT);
        return UriComponentsBuilder.newInstance()
            .scheme("https")
            .host("admin.shopify.com")
            .path("/store/{storeHandle}/themes/current/editor")
            .queryParam("context", "apps")
            .queryParam("activateAppId", apiKey + "/" + EXTENSION_HANDLE)
            .buildAndExpand(storeHandle)
            .toUriString();
    }
}
