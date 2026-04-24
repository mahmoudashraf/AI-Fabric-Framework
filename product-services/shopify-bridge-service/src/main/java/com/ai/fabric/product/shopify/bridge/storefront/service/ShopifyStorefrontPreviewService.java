package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.config.ShopifyBridgeProperties;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyProductReviewSignals;
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
    private static final String COMPARISON_BLOCK_HANDLE = "companion-comparison";
    private static final String ORDER_LOOKUP_BLOCK_HANDLE = "companion-order-lookup";
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
        List<String> blockingReasons = new ArrayList<>(ShopifyStorefrontInteractionReadinessSupport.blockingReasons(store));
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
        List<String> groundingSignals = groundingSignals(store);
        List<String> supportedReviewProviders = supportedReviewProviders(store);

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
            groundingSignals,
            supportedReviewProviders,
            buildSurfacePlacements(store.shopDomain()),
            List.of(
                "Open Shopify Admin > Online Store > Themes > Customize.",
                "Enable the Companion launcher app embed.",
                "Optionally place the AI search block on a homepage or collection-oriented template.",
                "Optionally place the contextual pill block on collection or product templates to surface quick prompts inline.",
                "Optionally place the product insight, policy strip, product FAQ, and comparison blocks on the product template.",
                "Place the order lookup block only for Elite stores after order-read scope and support readiness are verified.",
                "Set Bridge base URL to " + (bridgeBaseUrl == null ? "<bridge-public-base-url>" : bridgeBaseUrl) + ".",
                "Keep the launcher label as-is or set it from the Companion app widget settings.",
                "Use the Companion app welcome message as the first assistant response in the launcher.",
                "Open the storefront once after enabling the embed to let the bridge record widget activation."
            ),
            List.copyOf(blockingReasons),
            message
        );
    }

    private List<String> groundingSignals(ShopifyBridgeStoreSummary store) {
        List<String> signals = new ArrayList<>();
        if (store.productsEnabled()) {
            signals.add("Catalog product grounding");
            signals.add("Review-aware product grounding");
        }
        if (store.collectionsEnabled()) {
            signals.add("Collection grounding");
        }
        if (store.pagesEnabled()) {
            signals.add("Store page grounding");
        }
        if (store.policiesEnabled()) {
            signals.add("Policy grounding");
        }
        if (store.articlesEnabled()) {
            signals.add("Published article grounding");
        }
        if (store.metaobjectsEnabled()) {
            signals.add("Metaobject grounding");
        }
        return List.copyOf(signals);
    }

    private List<String> supportedReviewProviders(ShopifyBridgeStoreSummary store) {
        if (!store.productsEnabled()) {
            return List.of();
        }
        if (store.sourcePreflight() != null && store.sourcePreflight().categories() != null) {
            List<String> detected = store.sourcePreflight().categories().stream()
                .filter(category -> "products".equalsIgnoreCase(category.category()))
                .flatMap(category -> category.signals() == null ? java.util.stream.Stream.empty() : category.signals().stream())
                .filter(signal -> signal != null && !signal.isBlank())
                .toList();
            if (!detected.isEmpty()) {
                return List.copyOf(detected);
            }
        }
        return ShopifyProductReviewSignals.supportedProviderLabels();
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
            ),
            buildAppBlockPlacement(
                shopDomain,
                "comparison",
                "Comparison block",
                COMPARISON_BLOCK_HANDLE,
                "product",
                "mainSection",
                "STARTER",
                "Place this beside product insight or below key buying details so shoppers can compare alternatives without opening chat."
            ),
            buildAppBlockPlacement(
                shopDomain,
                "order-lookup",
                "Elite order lookup block",
                ORDER_LOOKUP_BLOCK_HANDLE,
                "page",
                "newAppsSection",
                "ELITE",
                "Place this on a support, contact, or order-help page only for Elite stores with verified order-read scope and customer-safe support posture."
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
