package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ShopifyStorefrontPreviewService {

    private static final String EXTENSION_HANDLE = "companion-app-embed";
    private static final String DEFAULT_LAUNCHER_LABEL = "Ask the store assistant";

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
            DEFAULT_LAUNCHER_LABEL,
            List.of(
                "Open Shopify Admin > Online Store > Themes > Customize.",
                "Enable the Companion launcher app embed.",
                "Set Bridge base URL to " + (bridgeBaseUrl == null ? "<bridge-public-base-url>" : bridgeBaseUrl) + ".",
                "Keep the launcher label as-is or customize it for the storefront tone.",
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
}
