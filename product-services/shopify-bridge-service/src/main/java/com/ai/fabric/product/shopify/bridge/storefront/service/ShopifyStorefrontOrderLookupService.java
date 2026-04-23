package com.ai.fabric.product.shopify.bridge.storefront.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyInstallRecordSummary;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyInstallRecordService;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyScopeSupport;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupLineItemSummary;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupRequest;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupResponse;
import com.ai.fabric.product.shopify.bridge.storefront.model.ShopifyStorefrontOrderLookupSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ShopifyStorefrontOrderLookupService {

    private static final String LOOKUP_QUERY = """
        query ShopifyBridgeOrderLookup($query: String!) {
          orders(first: 5, sortKey: PROCESSED_AT, reverse: true, query: $query) {
            nodes {
              id
              name
              confirmationNumber
              createdAt
              displayFinancialStatus
              displayFulfillmentStatus
              statusPageUrl
              currentTotalPriceSet {
                shopMoney {
                  amount
                  currencyCode
                }
              }
              lineItems(first: 10) {
                nodes {
                  title
                  variantTitle
                  quantity
                }
              }
              fulfillments {
                status
                trackingInfo(first: 5) {
                  company
                  number
                  url
                }
              }
            }
          }
        }
        """;

    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyInstallRecordService installRecordService;
    private final ShopifyBridgeBillingService billingService;
    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;

    public ShopifyStorefrontOrderLookupService(PlatformShopifyStoreClient platformShopifyStoreClient,
                                               ShopifyBridgeInstallCredentialService installCredentialService,
                                               ShopifyInstallRecordService installRecordService,
                                               ShopifyBridgeBillingService billingService,
                                               ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.installCredentialService = installCredentialService;
        this.installRecordService = installRecordService;
        this.billingService = billingService;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
    }

    public ShopifyStorefrontOrderLookupResponse lookup(String shopDomain,
                                                       ShopifyStorefrontOrderLookupRequest request) {
        String normalizedShopDomain = normalizeShopDomain(shopDomain);
        if (normalizedShopDomain == null) {
            return unavailable("INVALID_REQUEST", "Shop domain is required.");
        }
        String orderReference = normalizeOrderReference(request == null ? null : request.orderNumber());
        String email = normalizeEmail(request == null ? null : request.email());
        if (orderReference == null || email == null) {
            return unavailable(
                "INVALID_REQUEST",
                "Enter the order number and the same email address used at checkout."
            );
        }

        ShopifyBridgeStoreSummary store = platformShopifyStoreClient.getStore(normalizedShopDomain);
        if (store.readiness() == null || !store.readiness().storefrontReady()) {
            return unavailable("STOREFRONT_NOT_READY", firstStorefrontBlockingReason(store));
        }

        ShopifyBridgeCredentialAcquisition acquisition = installCredentialService.resolvePersistedMaterial(normalizedShopDomain).orElse(null);
        if (acquisition == null || acquisition.tokenExchangeMaterial().accessToken() == null
            || acquisition.tokenExchangeMaterial().accessToken().isBlank()) {
            return unavailable(
                "ORDER_LOOKUP_UNAVAILABLE",
                "Order lookup is not available right now. Contact the store directly for help."
            );
        }

        List<String> configuredSurfaces = store.widgetDetail() != null
            && store.widgetDetail().settings() != null
            && store.widgetDetail().settings().enabledSurfaces() != null
            ? store.widgetDetail().settings().enabledSurfaces()
            : List.of();
        List<String> effectiveSurfaces = billingService.effectiveAllowedSurfaces(
            normalizedShopDomain,
            acquisition.tokenExchangeMaterial().accessToken(),
            configuredSurfaces
        );
        if (!effectiveSurfaces.contains("order-lookup")) {
            return unavailable(
                "SURFACE_NOT_ENABLED",
                "Order lookup is not enabled for this store's current Companion configuration."
            );
        }

        ShopifyInstallRecordSummary installRecord = installRecordService.findByShopDomain(normalizedShopDomain).orElse(null);
        String scopesText = optionalText(acquisition.tokenExchangeMaterial().scopesText());
        if (scopesText == null && installRecord != null) {
            scopesText = installRecord.scopesText();
        }
        if (!ShopifyScopeSupport.hasScope(scopesText, "read_orders")) {
            return unavailable(
                "SCOPE_REQUIRED",
                "Order lookup is not available right now. Contact the store directly for help."
            );
        }

        ShopifyStorefrontOrderLookupSummary summary = resolveOrderSummary(
            normalizedShopDomain,
            acquisition.tokenExchangeMaterial().accessToken(),
            orderReference,
            email
        );
        if (summary == null) {
            return new ShopifyStorefrontOrderLookupResponse(
                true,
                false,
                "NO_MATCH",
                "We could not verify an order with those details. Check the order number and checkout email, or contact the store for help.",
                "Use the original checkout email and the exact order number shown on the confirmation email.",
                null
            );
        }
        return new ShopifyStorefrontOrderLookupResponse(
            true,
            true,
            "MATCHED",
            "Order details verified.",
            "This lookup is read-only. For refunds, edits, or address changes, contact the store directly.",
            summary
        );
    }

    private ShopifyStorefrontOrderLookupSummary resolveOrderSummary(String shopDomain,
                                                                    String accessToken,
                                                                    String orderReference,
                                                                    String email) {
        for (String query : buildQueries(orderReference, email)) {
            Map<String, Object> response = shopifyAdminGraphqlClient.execute(
                shopDomain,
                accessToken,
                LOOKUP_QUERY,
                Map.of("query", query)
            );
            List<Map<String, Object>> orders = extractOrders(response);
            Map<String, Object> bestMatch = orders.stream()
                .filter(order -> exactOrderReferenceMatch(order, orderReference))
                .max(Comparator.comparing(order -> optionalText(order.get("createdAt")), Comparator.nullsLast(String::compareTo)))
                .orElse(null);
            if (bestMatch != null) {
                return toSummary(bestMatch);
            }
        }
        return null;
    }

    private List<String> buildQueries(String orderReference, String email) {
        Set<String> queries = new LinkedHashSet<>();
        if (looksLikeOrderName(orderReference)) {
            String orderName = normalizeOrderName(orderReference);
            queries.add("name:" + orderName + " email:" + email.toLowerCase(Locale.ROOT));
            queries.add("name:\"" + orderName + "\" email:" + email.toLowerCase(Locale.ROOT));
        }
        String confirmationNumber = normalizeConfirmationNumber(orderReference);
        if (confirmationNumber != null) {
            queries.add("confirmation_number:" + confirmationNumber + " email:" + email.toLowerCase(Locale.ROOT));
            queries.add("confirmation_number:\"" + confirmationNumber + "\" email:" + email.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(queries);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractOrders(Map<String, Object> response) {
        failOnGraphQlErrors(response, "Shopify order lookup failed.");
        Object dataObject = response.get("data");
        if (!(dataObject instanceof Map<?, ?> data)) {
            return List.of();
        }
        Object ordersObject = data.get("orders");
        if (!(ordersObject instanceof Map<?, ?> orders)) {
            return List.of();
        }
        Object nodesObject = orders.get("nodes");
        if (!(nodesObject instanceof List<?> nodes)) {
            return List.of();
        }
        List<Map<String, Object>> ordersList = new ArrayList<>();
        for (Object node : nodes) {
            if (node instanceof Map<?, ?> map) {
                ordersList.add((Map<String, Object>) map);
            }
        }
        return List.copyOf(ordersList);
    }

    private boolean exactOrderReferenceMatch(Map<String, Object> order, String orderReference) {
        String normalizedReference = orderReference.trim();
        String orderName = optionalText(order.get("name"));
        if (orderName != null && normalizeOrderName(orderName).equalsIgnoreCase(normalizeOrderName(normalizedReference))) {
            return true;
        }
        String confirmationNumber = optionalText(order.get("confirmationNumber"));
        return confirmationNumber != null && confirmationNumber.trim().equalsIgnoreCase(normalizeConfirmationNumber(normalizedReference));
    }

    @SuppressWarnings("unchecked")
    private ShopifyStorefrontOrderLookupSummary toSummary(Map<String, Object> order) {
        String trackingCompany = null;
        String trackingNumberMasked = null;
        String trackingUrl = null;
        Object fulfillmentsObject = order.get("fulfillments");
        if (fulfillmentsObject instanceof List<?> fulfillments) {
            for (Object fulfillment : fulfillments) {
                if (!(fulfillment instanceof Map<?, ?> map)) {
                    continue;
                }
                Object trackingInfoObject = map.get("trackingInfo");
                if (!(trackingInfoObject instanceof List<?> trackingItems) || trackingItems.isEmpty()) {
                    continue;
                }
                Object firstTrackingItem = trackingItems.getFirst();
                if (!(firstTrackingItem instanceof Map<?, ?> tracking)) {
                    continue;
                }
                trackingCompany = optionalText(tracking.get("company"));
                trackingNumberMasked = maskTrackingNumber(optionalText(tracking.get("number")));
                trackingUrl = optionalText(tracking.get("url"));
                break;
            }
        }

        List<ShopifyStorefrontOrderLookupLineItemSummary> lineItems = List.of();
        Object lineItemsObject = order.get("lineItems");
        if (lineItemsObject instanceof Map<?, ?> lineItemsMap && lineItemsMap.get("nodes") instanceof List<?> nodes) {
            List<ShopifyStorefrontOrderLookupLineItemSummary> items = new ArrayList<>();
            for (Object node : nodes) {
                if (!(node instanceof Map<?, ?> item)) {
                    continue;
                }
                items.add(new ShopifyStorefrontOrderLookupLineItemSummary(
                    optionalText(item.get("title")),
                    optionalText(item.get("variantTitle")),
                    intValue(item.get("quantity"))
                ));
            }
            lineItems = List.copyOf(items);
        }

        String totalAmount = null;
        String currencyCode = null;
        Object totalPriceObject = order.get("currentTotalPriceSet");
        if (totalPriceObject instanceof Map<?, ?> totalPriceMap
            && totalPriceMap.get("shopMoney") instanceof Map<?, ?> shopMoney) {
            totalAmount = optionalText(shopMoney.get("amount"));
            currencyCode = optionalText(shopMoney.get("currencyCode"));
        }

        return new ShopifyStorefrontOrderLookupSummary(
            optionalText(order.get("name")),
            optionalText(order.get("confirmationNumber")),
            optionalText(order.get("displayFinancialStatus")),
            optionalText(order.get("displayFulfillmentStatus")),
            optionalText(order.get("createdAt")),
            totalAmount,
            currencyCode,
            trackingCompany,
            trackingNumberMasked,
            trackingUrl,
            optionalText(order.get("statusPageUrl")),
            lineItems
        );
    }

    private ShopifyStorefrontOrderLookupResponse unavailable(String status, String message) {
        return new ShopifyStorefrontOrderLookupResponse(
            false,
            false,
            status,
            message,
            "For refunds, address changes, or account-specific help, contact the store directly.",
            null
        );
    }

    private void failOnGraphQlErrors(Map<String, Object> response, String fallbackMessage) {
        Object errors = response.get("errors");
        if (!(errors instanceof List<?> errorList) || errorList.isEmpty()) {
            return;
        }
        List<String> messages = new ArrayList<>();
        for (Object error : errorList) {
            if (!(error instanceof Map<?, ?> map)) {
                continue;
            }
            String message = optionalText(map.get("message"));
            if (message != null) {
                messages.add(message);
            }
        }
        throw new ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_GATEWAY,
            messages.isEmpty() ? fallbackMessage : String.join(" ", messages)
        );
    }

    private String firstStorefrontBlockingReason(ShopifyBridgeStoreSummary store) {
        if (store.readiness() != null
            && store.readiness().storefrontBlockingReasons() != null
            && !store.readiness().storefrontBlockingReasons().isEmpty()) {
            return store.readiness().storefrontBlockingReasons().getFirst();
        }
        return "Order lookup is not available for this store yet.";
    }

    private String normalizeShopDomain(String value) {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeOrderReference(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 120) {
            return null;
        }
        return normalized;
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 254 || !normalized.contains("@") || normalized.startsWith("@") || normalized.endsWith("@")) {
            return null;
        }
        return normalized;
    }

    private boolean looksLikeOrderName(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim();
        return normalized.startsWith("#") || normalized.chars().allMatch(Character::isDigit);
    }

    private String normalizeOrderName(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }
        if (normalized.startsWith("#")) {
            return normalized;
        }
        return "#" + normalized;
    }

    private String normalizeConfirmationNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("#") || normalized.chars().allMatch(Character::isDigit)) {
            return null;
        }
        return normalized;
    }

    private int intValue(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private String maskTrackingNumber(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() <= 4) {
            return normalized;
        }
        return "..." + normalized.substring(normalized.length() - 4);
    }

    private String optionalText(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
