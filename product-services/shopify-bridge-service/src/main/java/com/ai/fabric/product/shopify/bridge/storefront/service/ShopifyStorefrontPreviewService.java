package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPlacementSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontPreviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class ShopifyStorefrontPreviewService {

    private static final String EXTENSION_HANDLE = "companion-app-embed";
    private static final String AI_SEARCH_BLOCK_HANDLE = "companion-ai-search";
    private static final String CONTEXTUAL_PILL_BLOCK_HANDLE = "companion-contextual-pill";
    private static final String PRODUCT_INSIGHT_BLOCK_HANDLE = "companion-product-insight";
    private static final String POLICY_STRIP_BLOCK_HANDLE = "companion-policy-strip";
    private static final String PRODUCT_FAQ_BLOCK_HANDLE = "companion-product-faq";
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
            buildSurfacePlacements(store.shopDomain()),
            List.of(
                "Open Shopify Admin > Online Store > Themes > Customize.",
                "Enable the Companion launcher app embed.",
                "Optionally place the AI search block on a homepage or collection-oriented template.",
                "Optionally place the contextual pill block on collection or product templates to surface quick prompts inline.",
                "Optionally place the product insight, policy strip, and product FAQ blocks on the product template.",
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

    private List<ShopifyStorefrontPlacementSummary> buildSurfacePlacements(String shopDomain) {
        return List.of(
            buildAppBlockPlacement(
                shopDomain,
                "ai-search",
                "AI search block",
                AI_SEARCH_BLOCK_HANDLE,
                "index",
                "newAppsSection",
                "FREE",
                "Use this as the merchant-placeable Free-tier entry point on a homepage or landing template."
            ),
            buildAppBlockPlacement(
                shopDomain,
                "contextual-pill",
                "Contextual pill block",
                CONTEXTUAL_PILL_BLOCK_HANDLE,
                "collection",
                "newAppsSection",
                "STARTER",
                "Place this on a collection or product template to keep guided prompts visible inline before shoppers open chat."
            ),
            buildAppBlockPlacement(
                shopDomain,
                "product-insight",
                "Product insight block",
                PRODUCT_INSIGHT_BLOCK_HANDLE,
                "product",
                "mainSection",
                "STARTER",
                "Place this inside the main product section so shoppers see grounded product guidance without opening chat."
            ),
            buildAppBlockPlacement(
                shopDomain,
                "policy-strip",
                "Policy strip block",
                POLICY_STRIP_BLOCK_HANDLE,
                "product",
                "mainSection",
                "STARTER",
                "Place this near price or add-to-cart so shipping and return guidance appears at the decision point."
            ),
            buildAppBlockPlacement(
                shopDomain,
                "product-faq",
                "Product FAQ block",
                PRODUCT_FAQ_BLOCK_HANDLE,
                "product",
                "mainSection",
                "STARTER",
                "Place this lower on the product template to answer common shopper questions without switching to a full chat flow."
            )
        );
    }

    private ShopifyStorefrontPlacementSummary buildAppBlockPlacement(String shopDomain,
                                                                     String surfaceId,
                                                                     String label,
                                                                     String blockHandle,
                                                                     String template,
                                                                     String target,
                                                                     String requiredTierKey,
                                                                     String guidance) {
        return new ShopifyStorefrontPlacementSummary(
            surfaceId,
            label,
            "APP_BLOCK",
            blockHandle,
            template,
            target,
            buildAppBlockThemeEditorUrl(shopDomain, blockHandle, template, target),
            requiredTierKey,
            guidance
        );
    }

    private String buildAppBlockThemeEditorUrl(String shopDomain, String blockHandle, String template, String target) {
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
            .queryParam("template", template)
            .queryParam("addAppBlockId", apiKey + "/" + blockHandle)
            .queryParam("target", target)
            .buildAndExpand(storeHandle)
            .toUriString();
    }
}
