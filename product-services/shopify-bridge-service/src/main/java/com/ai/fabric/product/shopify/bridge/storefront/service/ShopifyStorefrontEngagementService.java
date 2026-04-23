package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontEngagementEventRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class ShopifyStorefrontEngagementService {

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeUsageService usageService;

    public ShopifyStorefrontEngagementService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                              ShopifyBridgeUsageService usageService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.usageService = usageService;
    }

    public void record(String shopDomain, ShopifyStorefrontEngagementEventRequest request, String shopperSessionId) {
        ShopifyBridgeStoreSummary store = requireReadyStore(shopDomain);
        if (shopperSessionId != null && shopperSessionId.isBlank()) {
            throw badRequest("shopperSessionId must not be blank when provided.");
        }
        usageService.recordEvent(store.shopDomain(), toUsageEventType(request));
    }

    private ShopifyBridgeStoreSummary requireReadyStore(String shopDomain) {
        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(shopDomain);
        if (store.readiness() == null || !store.readiness().storefrontReady()) {
            throw unavailable(firstStorefrontBlockingReason(store));
        }
        return store;
    }

    private String toUsageEventType(ShopifyStorefrontEngagementEventRequest request) {
        if (request == null || request.eventType() == null || request.eventType().isBlank()) {
            throw badRequest("eventType is required.");
        }
        String normalized = request.eventType().trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "WIDGET_OPENED" -> "STOREFRONT_WIDGET_OPENED_" + pageBucket(request.pageType());
            case "SUGGESTION_CLICKED" -> "STOREFRONT_SUGGESTION_CLICKED";
            case "SEARCH_SUBMITTED" -> "STOREFRONT_SEARCH_SUBMITTED_" + pageBucket(request.pageType());
            case "CONTEXTUAL_PROMPT_CLICKED" -> "STOREFRONT_CONTEXTUAL_PROMPT_CLICKED_" + pageBucket(request.pageType());
            case "PRODUCT_FAQ_CLICKED" -> "STOREFRONT_PRODUCT_FAQ_CLICKED_" + pageBucket(request.pageType());
            case "COMPARISON_CLICKED" -> "STOREFRONT_COMPARISON_CLICKED_" + pageBucket(request.pageType());
            case "CHAT_RESET" -> "STOREFRONT_CHAT_RESET";
            default -> throw badRequest("Unsupported storefront event type: " + request.eventType());
        };
    }

    private String pageBucket(String pageType) {
        if (pageType == null || pageType.isBlank()) {
            return "GENERIC_PAGE";
        }
        return switch (pageType.trim().toLowerCase(Locale.ROOT)) {
            case "product" -> "PRODUCT_PAGE";
            case "collection", "collections" -> "COLLECTION_PAGE";
            case "index", "home" -> "HOME_PAGE";
            case "page", "article", "blog" -> "CONTENT_PAGE";
            default -> "GENERIC_PAGE";
        };
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        if (store.readiness() != null && !store.readiness().storefrontBlockingReasons().isEmpty()) {
            return store.readiness().storefrontBlockingReasons().get(0);
        }
        return "Store assistant is not live yet for " + store.shopDomain() + ".";
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
