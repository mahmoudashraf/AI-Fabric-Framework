package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSourcePreflightRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSourcePreflightCategorySummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class ShopifyBridgeSourcePreflightService {

    private static final String PRODUCTS_QUERY = """
        query ShopifyCompanionProductsPreflight {
          productsCount(limit: null) {
            count
          }
        }
        """;

    private static final String COLLECTIONS_QUERY = """
        query ShopifyCompanionCollectionsPreflight {
          collectionsCount(limit: null) {
            count
          }
        }
        """;

    private static final String PAGES_QUERY = """
        query ShopifyCompanionPagesPreflight {
          pagesCount(limit: null) {
            count
          }
        }
        """;

    private static final String POLICIES_QUERY = """
        query ShopifyCompanionPoliciesPreflight {
          shop {
            shopPolicies {
              id
            }
          }
        }
        """;

    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;

    public ShopifyBridgeSourcePreflightService(ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                               PlatformShopifyStoreClient platformShopifyStoreClient) {
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
    }

    public ShopifyBridgeStoreSummary run(ShopifyBridgeCredentialAcquisition acquisition) {
        ShopifyBridgeStoreSummary store = acquisition.store();
        String accessToken = acquisition.tokenExchangeMaterial().accessToken();
        List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories = new ArrayList<>();
        categories.add(evaluateCountCategory(
            store.shopDomain(),
            accessToken,
            "products",
            store.productsEnabled(),
            PRODUCTS_QUERY,
            "productsCount",
            "Products"
        ));
        categories.add(evaluateCountCategory(
            store.shopDomain(),
            accessToken,
            "collections",
            store.collectionsEnabled(),
            COLLECTIONS_QUERY,
            "collectionsCount",
            "Collections"
        ));
        categories.add(evaluateCountCategory(
            store.shopDomain(),
            accessToken,
            "pages",
            store.pagesEnabled(),
            PAGES_QUERY,
            "pagesCount",
            "Pages"
        ));
        categories.add(evaluateCategory(
            "policies",
            store.policiesEnabled(),
            () -> fetchPolicies(store.shopDomain(), accessToken)
        ));
        return platformShopifyStoreClient.recordSourcePreflight(
            store.shopDomain(),
            new ShopifyBridgeRecordSourcePreflightRequest(categories)
        );
    }

    private ShopifyBridgeStoreSourcePreflightCategorySummary evaluateCountCategory(String shopDomain,
                                                                                   String accessToken,
                                                                                   String category,
                                                                                   boolean enabled,
                                                                                   String query,
                                                                                   String rootField,
                                                                                   String label) {
        return evaluateCategory(category, enabled, () -> {
            int count = extractCount(shopifyAdminGraphqlClient.execute(shopDomain, accessToken, query), rootField);
            return new CategoryResult(
                "READY",
                count,
                count > 0 ? label + " reachable (" + count + " items)." : "No " + category + " found in the store."
            );
        });
    }

    private CategoryResult fetchPolicies(String shopDomain, String accessToken) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(shopDomain, accessToken, POLICIES_QUERY);
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> shop = requireMap(data.get("shop"), "Shopify Admin API response is missing shop.");
        List<?> policies = requireList(shop.get("shopPolicies"), "Shopify Admin API response is missing shop policies.");
        int count = policies.size();
        return new CategoryResult(
            "READY",
            count,
            count > 0 ? "Policies reachable (" + count + " documents)." : "No shop policies configured."
        );
    }

    private ShopifyBridgeStoreSourcePreflightCategorySummary evaluateCategory(String category,
                                                                              boolean enabled,
                                                                              Supplier<CategoryResult> supplier) {
        if (!enabled) {
            return new ShopifyBridgeStoreSourcePreflightCategorySummary(
                category,
                false,
                "PENDING",
                0,
                "Disabled for this store."
            );
        }
        try {
            CategoryResult result = supplier.get();
            return new ShopifyBridgeStoreSourcePreflightCategorySummary(
                category,
                true,
                result.status(),
                Math.max(result.itemCount(), 0),
                result.message()
            );
        } catch (RestClientResponseException ex) {
            String status = ex.getStatusCode().is4xxClientError() ? "BLOCKED" : "FAILED";
            String message = ex.getStatusCode().is4xxClientError()
                ? "Shopify denied access to " + category + ". Verify app scopes and store install."
                : "Shopify Admin API failed while checking " + category + ".";
            return new ShopifyBridgeStoreSourcePreflightCategorySummary(category, true, status, 0, message);
        } catch (ResponseStatusException ex) {
            String message = ex.getReason() == null ? "Shopify preflight failed for " + category + "." : ex.getReason();
            String normalized = message.toLowerCase(Locale.ROOT);
            String status = normalized.contains("access") || normalized.contains("scope") || normalized.contains("denied")
                ? "BLOCKED"
                : "FAILED";
            return new ShopifyBridgeStoreSourcePreflightCategorySummary(category, true, status, 0, message);
        }
    }

    private int extractCount(Map<String, Object> response, String rootField) {
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> count = requireMap(data.get(rootField), "Shopify Admin API response is missing " + rootField + ".");
        Object countValue = count.get("count");
        if (!(countValue instanceof Number number)) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API returned an invalid count for " + rootField + ".");
        }
        return number.intValue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    private List<?> requireList(Object value, String message) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    @SuppressWarnings("unchecked")
    private List<String> errorMessages(Map<String, Object> response) {
        Object value = response.get("errors");
        if (!(value instanceof List<?> errors)) {
            return List.of();
        }
        return errors.stream()
            .map(error -> {
                if (error instanceof Map<?, ?> map) {
                    Object message = map.get("message");
                    return message == null ? null : message.toString().trim();
                }
                return error == null ? null : error.toString().trim();
            })
            .filter(message -> message != null && !message.isBlank())
            .toList();
    }

    private record CategoryResult(
        String status,
        int itemCount,
        String message
    ) {
    }
}
