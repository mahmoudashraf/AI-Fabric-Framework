package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourcePageResponse;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeVectorizationSourceRecord;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ShopifyBridgeVectorizationSourceService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 250;
    private static final int MAX_POLICY_BODY_CHARS = 5_500;

    private static final String PRODUCTS_COUNT_QUERY = """
        query ShopifyCompanionProductsVectorizationCount {
          productsCount(limit: null) {
            count
          }
        }
        """;

    private static final String COLLECTIONS_COUNT_QUERY = """
        query ShopifyCompanionCollectionsVectorizationCount {
          collectionsCount(limit: null) {
            count
          }
        }
        """;

    private static final String PAGES_COUNT_QUERY = """
        query ShopifyCompanionPagesVectorizationCount {
          pagesCount(limit: null) {
            count
          }
        }
        """;

    private static final String PRODUCTS_QUERY = """
        query ShopifyCompanionProductsVectorizationPage($cursor: String, $limit: Int!) {
          products(first: $limit, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                title
                handle
                descriptionHtml
                vendor
                productType
                updatedAt
              }
            }
          }
        }
        """;

    private static final String COLLECTIONS_QUERY = """
        query ShopifyCompanionCollectionsVectorizationPage($cursor: String, $limit: Int!) {
          collections(first: $limit, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                title
                handle
                descriptionHtml
                updatedAt
              }
            }
          }
        }
        """;

    private static final String PAGES_QUERY = """
        query ShopifyCompanionPagesVectorizationPage($cursor: String, $limit: Int!) {
          pages(first: $limit, after: $cursor) {
            pageInfo {
              hasNextPage
              endCursor
            }
            edges {
              node {
                id
                title
                handle
                body
                updatedAt
              }
            }
          }
        }
        """;

    private static final String POLICIES_QUERY = """
        query ShopifyCompanionPoliciesVectorizationPage {
          shop {
            shopPolicies {
              id
              title
              type
              body
              url
              updatedAt
            }
          }
        }
        """;

    private final ShopifyBridgeInstallCredentialService installCredentialService;
    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;

    public ShopifyBridgeVectorizationSourceService(ShopifyBridgeInstallCredentialService installCredentialService,
                                                   ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        this.installCredentialService = installCredentialService;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
    }

    public ShopifyBridgeVectorizationSourcePageResponse page(String shopDomain,
                                                             String entityType,
                                                             String cursor,
                                                             Integer limit) {
        ShopifyBridgeCredentialAcquisition acquisition = installCredentialService.resolvePersistedMaterial(shopDomain)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify vectorization source requires persisted store credentials. Install or reconnect the app first."
            ));
        ShopifyBridgeStoreSummary store = acquisition.store();
        int effectiveLimit = normalizeLimit(limit);
        String normalizedEntityType = normalizeEntityType(entityType);
        List<String> categories = categoriesFor(normalizedEntityType, store);
        if (categories.isEmpty()) {
            return new ShopifyBridgeVectorizationSourcePageResponse(
                store.shopDomain(),
                normalizedEntityType,
                0,
                false,
                null,
                List.of()
            );
        }

        ParsedCursor parsedCursor = parseCursor(cursor, categories.getFirst());
        if (!categories.contains(parsedCursor.category())) {
            throw new ResponseStatusException(CONFLICT, "Unsupported Shopify vectorization cursor category: " + parsedCursor.category());
        }
        String accessToken = acquisition.tokenExchangeMaterial().accessToken();
        int totalCount = totalCount(store, accessToken, categories);
        PageResult page = switch (parsedCursor.category()) {
            case "products" -> loadProductsPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "collections" -> loadCollectionsPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "pages" -> loadPagesPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "policies" -> loadPoliciesPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported Shopify vectorization source category: " + parsedCursor.category());
        };

        String nextCursor = null;
        boolean hasMore = false;
        if (page.hasMore() && page.nextCursor() != null) {
            nextCursor = encodeCursor(parsedCursor.category(), page.nextCursor());
            hasMore = true;
        } else {
            int currentIndex = categories.indexOf(parsedCursor.category());
            if (currentIndex >= 0 && currentIndex + 1 < categories.size()) {
                nextCursor = encodeCursor(categories.get(currentIndex + 1), null);
                hasMore = true;
            }
        }

        return new ShopifyBridgeVectorizationSourcePageResponse(
            store.shopDomain(),
            normalizedEntityType,
            totalCount,
            hasMore,
            nextCursor,
            page.items()
        );
    }

    private int totalCount(ShopifyBridgeStoreSummary store, String accessToken, List<String> categories) {
        int total = 0;
        for (String category : categories) {
            total += switch (category) {
                case "products" -> countFromConnection(
                    store.shopDomain(),
                    accessToken,
                    PRODUCTS_COUNT_QUERY,
                    "productsCount"
                );
                case "collections" -> countFromConnection(
                    store.shopDomain(),
                    accessToken,
                    COLLECTIONS_COUNT_QUERY,
                    "collectionsCount"
                );
                case "pages" -> countFromConnection(
                    store.shopDomain(),
                    accessToken,
                    PAGES_COUNT_QUERY,
                    "pagesCount"
                );
                case "policies" -> loadPolicies(store.shopDomain(), accessToken).size();
                default -> 0;
            };
        }
        return total;
    }

    private int countFromConnection(String shopDomain,
                                    String accessToken,
                                    String query,
                                    String rootField) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(shopDomain, accessToken, query);
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> count = requireMap(data.get(rootField), "Shopify Admin API response is missing " + rootField + ".");
        Object value = count.get("count");
        if (value instanceof Number number) {
            return Math.max(number.intValue(), 0);
        }
        return 0;
    }

    private PageResult loadProductsPage(String shopDomain,
                                        String accessToken,
                                        String cursor,
                                        int limit) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            PRODUCTS_QUERY,
            variablesWithCursor(cursor, limit)
        );
        return pageFromConnection(
            response,
            "products",
            node -> new ShopifyBridgeVectorizationSourceRecord(
                requiredText(node, "id"),
                text(node, "updatedAt"),
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    text(node, "vendor"),
                    text(node, "productType"),
                    sanitizeRichText(text(node, "descriptionHtml"))
                ),
                "products",
                "product",
                storefrontUrl(shopDomain, "/products/" + safePath(text(node, "handle"))),
                text(node, "handle"),
                text(node, "vendor"),
                text(node, "productType"),
                null
            )
        );
    }

    private PageResult loadCollectionsPage(String shopDomain,
                                           String accessToken,
                                           String cursor,
                                           int limit) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            COLLECTIONS_QUERY,
            variablesWithCursor(cursor, limit)
        );
        return pageFromConnection(
            response,
            "collections",
            node -> new ShopifyBridgeVectorizationSourceRecord(
                requiredText(node, "id"),
                text(node, "updatedAt"),
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    "Collection",
                    sanitizeRichText(text(node, "descriptionHtml"))
                ),
                "collections",
                "collection",
                storefrontUrl(shopDomain, "/collections/" + safePath(text(node, "handle"))),
                text(node, "handle"),
                null,
                null,
                null
            )
        );
    }

    private PageResult loadPagesPage(String shopDomain,
                                     String accessToken,
                                     String cursor,
                                     int limit) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            PAGES_QUERY,
            variablesWithCursor(cursor, limit)
        );
        return pageFromConnection(
            response,
            "pages",
            node -> new ShopifyBridgeVectorizationSourceRecord(
                requiredText(node, "id"),
                text(node, "updatedAt"),
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    sanitizeRichText(text(node, "body"))
                ),
                "pages",
                "page",
                storefrontUrl(shopDomain, "/pages/" + safePath(text(node, "handle"))),
                text(node, "handle"),
                null,
                null,
                null
            )
        );
    }

    private PageResult loadPoliciesPage(String shopDomain,
                                        String accessToken,
                                        String cursor,
                                        int limit) {
        List<ShopifyBridgeVectorizationSourceRecord> allPolicies = loadPolicies(shopDomain, accessToken);
        int offset = parseOffset(cursor);
        if (offset >= allPolicies.size()) {
            return new PageResult(List.of(), null, false);
        }
        int toIndex = Math.min(offset + limit, allPolicies.size());
        List<ShopifyBridgeVectorizationSourceRecord> items = allPolicies.subList(offset, toIndex);
        boolean hasMore = toIndex < allPolicies.size();
        String nextCursor = hasMore ? Integer.toString(toIndex) : null;
        return new PageResult(items, nextCursor, hasMore);
    }

    private List<ShopifyBridgeVectorizationSourceRecord> loadPolicies(String shopDomain, String accessToken) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(shopDomain, accessToken, POLICIES_QUERY);
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> shop = requireMap(data.get("shop"), "Shopify Admin API response is missing shop.");
        List<?> policies = requireList(shop.get("shopPolicies"), "Shopify Admin API response is missing shop policies.");
        return policies.stream()
            .map(this::requireMapFromListItem)
            .map(node -> new ShopifyBridgeVectorizationSourceRecord(
                requiredText(node, "id"),
                text(node, "updatedAt"),
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    text(node, "type"),
                    sanitizePolicyBody(text(node, "body"))
                ),
                "policies",
                "policy",
                text(node, "url"),
                null,
                null,
                null,
                text(node, "type")
            ))
            .toList();
    }

    private PageResult pageFromConnection(Map<String, Object> response,
                                          String connectionField,
                                          RecordFactory recordFactory) {
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> connection = requireMap(
            data.get(connectionField),
            "Shopify Admin API response is missing " + connectionField + "."
        );
        List<?> edges = requireList(connection.get("edges"), "Shopify Admin API response is missing edges for " + connectionField + ".");
        List<ShopifyBridgeVectorizationSourceRecord> items = new ArrayList<>(edges.size());
        for (Object edge : edges) {
            Map<String, Object> edgeMap = requireMapFromListItem(edge);
            Map<String, Object> node = requireMap(edgeMap.get("node"), "Shopify Admin API response is missing node for " + connectionField + ".");
            items.add(recordFactory.create(node));
        }
        Map<String, Object> pageInfo = requireMap(
            connection.get("pageInfo"),
            "Shopify Admin API response is missing pageInfo for " + connectionField + "."
        );
        boolean hasNextPage = Boolean.TRUE.equals(pageInfo.get("hasNextPage"));
        String endCursor = text(pageInfo, "endCursor");
        return new PageResult(items, hasNextPage ? endCursor : null, hasNextPage && endCursor != null);
    }

    private ParsedCursor parseCursor(String cursor, String defaultCategory) {
        if (cursor == null || cursor.isBlank()) {
            return new ParsedCursor(defaultCategory, null);
        }
        String[] parts = cursor.split("\\|", 2);
        String category = parts[0].trim().toLowerCase(Locale.ROOT);
        String nativeCursor = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
        return new ParsedCursor(category, nativeCursor);
    }

    private String encodeCursor(String category, String nativeCursor) {
        return category + "|" + (nativeCursor == null ? "" : nativeCursor);
    }

    private int parseOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor.trim()), 0);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(CONFLICT, "Unsupported Shopify vectorization policy cursor: " + cursor);
        }
    }

    private List<String> categoriesFor(String entityType, ShopifyBridgeStoreSummary store) {
        if ("product".equals(entityType)) {
            List<String> categories = new ArrayList<>(2);
            if (store.productsEnabled()) {
                categories.add("products");
            }
            if (store.collectionsEnabled()) {
                categories.add("collections");
            }
            return categories;
        }
        if ("support-policy".equals(entityType)) {
            List<String> categories = new ArrayList<>(2);
            if (store.pagesEnabled()) {
                categories.add("pages");
            }
            if (store.policiesEnabled()) {
                categories.add("policies");
            }
            return categories;
        }
        throw new ResponseStatusException(CONFLICT, "Unsupported Shopify vectorization entity type: " + entityType);
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizeEntityType(String entityType) {
        if (entityType == null || entityType.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "entityType is required.");
        }
        return entityType.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> variablesWithCursor(String cursor, int limit) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("cursor", cursor);
        variables.put("limit", limit);
        return variables;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value, String message) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMapFromListItem(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API returned an invalid object payload.");
    }

    private List<?> requireList(Object value, String message) {
        if (value instanceof List<?> list) {
            return list;
        }
        throw new ResponseStatusException(BAD_GATEWAY, message);
    }

    private List<String> errorMessages(Map<String, Object> response) {
        Object value = response.get("errors");
        if (!(value instanceof List<?> errors)) {
            return List.of();
        }
        return errors.stream()
            .map(this::requireMapFromListItem)
            .map(error -> text(error, "message"))
            .filter(message -> message != null && !message.isBlank())
            .toList();
    }

    private String requiredText(Map<String, Object> source, String fieldName) {
        String value = text(source, fieldName);
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API response is missing " + fieldName + ".");
        }
        return value;
    }

    private String text(Map<String, Object> source, String fieldName) {
        Object value = source.get(fieldName);
        return value == null ? null : value.toString();
    }

    private String joinContent(String... parts) {
        List<String> values = new ArrayList<>();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                values.add(part.trim());
            }
        }
        return String.join("\n\n", values);
    }

    private String sanitizeRichText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replaceAll("(?is)<script.*?>.*?</script>", " ")
            .replaceAll("(?is)<style.*?>.*?</style>", " ")
            .replaceAll("(?is)<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String sanitizePolicyBody(String value) {
        String sanitized = sanitizeRichText(value);
        if (sanitized.length() <= MAX_POLICY_BODY_CHARS) {
            return sanitized;
        }
        return sanitized.substring(0, MAX_POLICY_BODY_CHARS) + "...";
    }

    private String storefrontUrl(String shopDomain, String path) {
        if (path == null || path.isBlank()) {
            return "https://" + shopDomain;
        }
        return "https://" + shopDomain + (path.startsWith("/") ? path : "/" + path);
    }

    private String safePath(String value) {
        return value == null ? "" : value.trim();
    }

    private interface RecordFactory {
        ShopifyBridgeVectorizationSourceRecord create(Map<String, Object> node);
    }

    private record ParsedCursor(String category, String nativeCursor) {
    }

    private record PageResult(List<ShopifyBridgeVectorizationSourceRecord> items, String nextCursor, boolean hasMore) {
    }
}
