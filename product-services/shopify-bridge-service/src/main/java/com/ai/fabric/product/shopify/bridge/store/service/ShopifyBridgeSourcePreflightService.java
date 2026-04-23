package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
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

    private static final String ARTICLES_QUERY = """
        query ShopifyCompanionArticlesPreflight($cursor: String) {
          articles(first: 50, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                publishedAt
              }
            }
          }
        }
        """;

    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeBillingService billingService;

    public ShopifyBridgeSourcePreflightService(ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                               PlatformShopifyStoreClient platformShopifyStoreClient,
                                               ShopifyBridgeBillingService billingService) {
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.billingService = billingService;
    }

    public ShopifyBridgeStoreSummary run(ShopifyBridgeCredentialAcquisition acquisition) {
        ShopifyBridgeStoreSummary store = acquisition.store();
        String accessToken = acquisition.tokenExchangeMaterial().accessToken();
        Integer catalogProductCap = billingService.catalogProductCap(store.shopDomain(), accessToken);
        List<ShopifyBridgeStoreSourcePreflightCategorySummary> categories = new ArrayList<>();
        categories.add(evaluateCountCategory(
            store.shopDomain(),
            accessToken,
            "products",
            store.productsEnabled(),
            PRODUCTS_QUERY,
            "productsCount",
            "Products",
            catalogProductCap
        ));
        categories.add(evaluateCountCategory(
            store.shopDomain(),
            accessToken,
            "collections",
            store.collectionsEnabled(),
            COLLECTIONS_QUERY,
            "collectionsCount",
            "Collections",
            null
        ));
        categories.add(evaluateCountCategory(
            store.shopDomain(),
            accessToken,
            "pages",
            store.pagesEnabled(),
            PAGES_QUERY,
            "pagesCount",
            "Pages",
            null
        ));
        categories.add(evaluateCategory(
            "policies",
            store.policiesEnabled(),
            () -> fetchPolicies(store.shopDomain(), accessToken)
        ));
        categories.add(evaluateCategory(
            "articles",
            store.articlesEnabled(),
            () -> fetchArticles(store.shopDomain(), accessToken)
        ));
        categories.add(evaluateCategory(
            "metaobjects",
            store.metaobjectsEnabled(),
            () -> fetchMetaobjects(store.shopDomain(), accessToken)
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
                                                                                   String label,
                                                                                   Integer cap) {
        return evaluateCategory(category, enabled, () -> {
            int count = extractCount(shopifyAdminGraphqlClient.execute(shopDomain, accessToken, query), rootField);
            int effectiveCount = applyProductCap(category, count, cap);
            return new CategoryResult(
                "READY",
                effectiveCount,
                count > 0
                    ? describeCountMessage(label, count, effectiveCount, cap)
                    : "No " + category + " found in the store."
            );
        });
    }

    private int applyProductCap(String category, int count, Integer cap) {
        if (!"products".equalsIgnoreCase(category) || cap == null || cap <= 0) {
            return count;
        }
        return Math.min(count, cap);
    }

    private String describeCountMessage(String label, int actualCount, int effectiveCount, Integer cap) {
        if (cap != null && cap > 0 && effectiveCount < actualCount) {
            return label + " reachable (" + actualCount + " total, " + effectiveCount + " accessible at the current tier cap).";
        }
        return label + " reachable (" + actualCount + " items).";
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

    private CategoryResult fetchArticles(String shopDomain, String accessToken) {
        int count = paginate(shopDomain, accessToken, ARTICLES_QUERY, "articles").stream()
            .map(node -> text(node, "publishedAt"))
            .filter(value -> value != null && !value.isBlank())
            .toList()
            .size();
        return new CategoryResult(
            "READY",
            count,
            count > 0 ? "Articles reachable (" + count + " published posts)." : "No published articles found in the store."
        );
    }

    private CategoryResult fetchMetaobjects(String shopDomain, String accessToken) {
        int count = ShopifyMetaobjectSupport.totalCount(shopDomain, accessToken, shopifyAdminGraphqlClient);
        return new CategoryResult(
            "READY",
            count,
            count > 0 ? "Metaobjects reachable (" + count + " records)." : "No eligible metaobjects found in the store."
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

    private List<Map<String, Object>> paginate(String shopDomain,
                                               String accessToken,
                                               String query,
                                               String connectionField) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        String cursor = null;
        while (true) {
            Map<String, Object> response = shopifyAdminGraphqlClient.execute(
                shopDomain,
                accessToken,
                query,
                cursorVariables(cursor)
            );
            List<String> errors = errorMessages(response);
            if (!errors.isEmpty()) {
                throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
            }
            Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
            Map<String, Object> connection = requireMap(data.get(connectionField), "Shopify Admin API response is missing " + connectionField + ".");
            List<?> edges = requireList(connection.get("edges"), "Shopify Admin API response is missing edges for " + connectionField + ".");
            for (Object edge : edges) {
                Map<String, Object> edgeMap = requireMapFromListItem(edge);
                nodes.add(requireMap(edgeMap.get("node"), "Shopify Admin API response is missing node for " + connectionField + "."));
            }
            Map<String, Object> pageInfo = requireMap(connection.get("pageInfo"), "Shopify Admin API response is missing pageInfo for " + connectionField + ".");
            boolean hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
            String endCursor = text(pageInfo, "endCursor");
            if (!hasNextPage || endCursor == null) {
                break;
            }
            cursor = endCursor;
        }
        return nodes;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMapFromListItem(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API returned an invalid object payload.");
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

    private String text(Map<String, Object> source, String field) {
        Object value = source.get(field);
        return value == null ? null : value.toString().trim();
    }

    private Map<String, Object> cursorVariables(String cursor) {
        Map<String, Object> variables = new java.util.LinkedHashMap<>();
        variables.put("cursor", cursor);
        return variables;
    }

    private record CategoryResult(
        String status,
        int itemCount,
        String message
    ) {
    }
}
