package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.action.service.ShopifyBridgeActionExecutionService;
import com.ai.fabric.product.shopify.bridge.analytics.service.ShopifyBridgeUsageService;
import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontReadActionRequest;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ShopifyStorefrontReadActionService {

    private static final String COMPARISON_SURFACE_ID = "comparison";
    private static final List<String> COMPARISON_ACTION_IDS = List.of("find_similar_products", "compare_products");

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyBridgeActionExecutionService actionExecutionService;
    private final ShopifyBridgeUsageService usageService;

    public ShopifyStorefrontReadActionService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                              ShopifyBridgeInstallCredentialService installCredentialService,
                                              ShopifyBridgeBillingService billingService,
                                              ShopifyBridgeActionExecutionService actionExecutionService,
                                              ShopifyBridgeUsageService usageService) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.billingService = billingService;
        this.actionExecutionService = actionExecutionService;
        this.usageService = usageService;
    }

    public ShopifyBridgeActionResult execute(String shopDomain,
                                             ShopifyStorefrontReadActionRequest request,
                                             String shopperSessionId) {
        String normalizedShopDomain = normalizeShopDomain(shopDomain);
        String actionId = normalizeActionId(request == null ? null : request.actionId());
        String surfaceId = normalizeSurfaceId(request == null ? null : request.surfaceId());
        if (normalizedShopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (actionId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "actionId is required.");
        }
        if (surfaceId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "surfaceId is required.");
        }
        if (!COMPARISON_SURFACE_ID.equals(surfaceId)) {
            return ShopifyBridgeActionResult.failure(
                "ACTION_NOT_ALLOWED",
                "Storefront read actions are only available for the comparison surface."
            );
        }
        if (!COMPARISON_ACTION_IDS.contains(actionId)) {
            return ShopifyBridgeActionResult.failure(
                "ACTION_NOT_ALLOWED",
                "Only shopper-safe comparison actions are available on the storefront."
            );
        }

        Map<String, Object> sanitizedParams = sanitizeParams(actionId, request.params());
        if (sanitizedParams == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", invalidRequestReason(actionId));
        }

        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(normalizedShopDomain);
        if (store.readiness() == null || !store.readiness().storefrontReady()) {
            return ShopifyBridgeActionResult.failure(
                "STOREFRONT_NOT_READY",
                firstStorefrontBlockingReason(store)
            );
        }
        if (!effectiveEnabledSurfaces(store).contains(surfaceId)) {
            return ShopifyBridgeActionResult.failure(
                "SURFACE_NOT_ENABLED",
                "The comparison surface is not enabled for this store's current tier or widget configuration."
            );
        }

        ShopifyBridgeActionResult result = actionExecutionService.execute(
            normalizedShopDomain,
            new ShopifyBridgeActionExecuteRequest(
                actionId,
                sanitizedParams,
                null,
                buildTrace(surfaceId, shopperSessionId)
            )
        );
        usageService.recordEvent(normalizedShopDomain, "STOREFRONT_READ_ACTION");
        return result;
    }

    private Map<String, Object> sanitizeParams(String actionId, Map<String, Object> params) {
        if ("find_similar_products".equals(actionId)) {
            String sku = sanitizeSku(textParam(params, "sku"));
            if (sku == null) {
                return null;
            }
            int limit = sanitizeLimit(textParam(params, "limit"));
            return Map.of(
                "sku", sku,
                "limit", limit
            );
        }
        if ("compare_products".equals(actionId)) {
            String referenceSku = sanitizeSku(textParam(params, "referenceSku"));
            String comparisonSku = sanitizeSku(textParam(params, "comparisonSku"));
            if (referenceSku == null || comparisonSku == null || referenceSku.equalsIgnoreCase(comparisonSku)) {
                return null;
            }
            return Map.of(
                "referenceSku", referenceSku,
                "comparisonSku", comparisonSku
            );
        }
        return null;
    }

    private String invalidRequestReason(String actionId) {
        if ("find_similar_products".equals(actionId)) {
            return "find_similar_products requires params.sku and accepts an optional bounded params.limit.";
        }
        if ("compare_products".equals(actionId)) {
            return "compare_products requires different params.referenceSku and params.comparisonSku values.";
        }
        return "Invalid storefront read action request.";
    }

    private Map<String, Object> buildTrace(String surfaceId, String shopperSessionId) {
        LinkedHashMap<String, Object> trace = new LinkedHashMap<>();
        trace.put("channel", "storefront-read-action");
        trace.put("surfaceId", surfaceId);
        if (shopperSessionId != null && !shopperSessionId.isBlank()) {
            trace.put("shopperSessionId", shopperSessionId.trim());
        }
        return trace;
    }

    private List<String> effectiveEnabledSurfaces(ShopifyBridgeStoreSummary store) {
        List<String> configuredSurfaces = store.widgetDetail() != null
            && store.widgetDetail().settings() != null
            && store.widgetDetail().settings().enabledSurfaces() != null
            ? store.widgetDetail().settings().enabledSurfaces()
            : List.of();
        String accessToken = installCredentialService.resolvePersistedMaterial(store.shopDomain())
            .map(acquisition -> acquisition.tokenExchangeMaterial().accessToken())
            .orElse(null);
        return billingService.effectiveAllowedSurfaces(store.shopDomain(), accessToken, configuredSurfaces);
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        if (store.readiness() != null
            && store.readiness().storefrontBlockingReasons() != null
            && !store.readiness().storefrontBlockingReasons().isEmpty()) {
            return store.readiness().storefrontBlockingReasons().getFirst();
        }
        return "Storefront comparison actions are not available for this store yet.";
    }

    private String normalizeShopDomain(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeActionId(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeSurfaceId(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String textParam(Map<String, Object> params, String key) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return null;
        }
        return params.get(key).toString();
    }

    private String sanitizeSku(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            return null;
        }
        return normalized;
    }

    private int sanitizeLimit(String value) {
        if (value == null || value.isBlank()) {
            return 3;
        }
        try {
            return Math.max(1, Math.min(4, Integer.parseInt(value.trim())));
        } catch (NumberFormatException ignored) {
            return 3;
        }
    }
}
