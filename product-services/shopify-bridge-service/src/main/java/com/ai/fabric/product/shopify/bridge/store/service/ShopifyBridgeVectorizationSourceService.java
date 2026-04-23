package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
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

    private static final String ARTICLES_QUERY = """
        query ShopifyCompanionArticlesVectorizationPage($cursor: String, $limit: Int!) {
          articles(first: $limit, after: $cursor) {
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
                summary
                updatedAt
                publishedAt
                blog {
                  title
                  handle
                }
                author {
                  name
                }
              }
            }
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
                metafields(first: 12) {
                  edges {
                    node {
                      namespace
                      key
                      type
                      value
                    }
                  }
                }
                updatedAt
              }
            }
          }
        }
        """;

    private static final String NODE_RECORD_QUERY = """
        query ShopifyCompanionVectorizationRecord($id: ID!) {
          node(id: $id) {
            __typename
            ... on Product {
              id
              title
              handle
              descriptionHtml
              vendor
              productType
              metafields(first: 12) {
                edges {
                  node {
                    namespace
                    key
                    type
                    value
                  }
                }
              }
              updatedAt
            }
            ... on Collection {
              id
              title
              handle
              descriptionHtml
              updatedAt
            }
            ... on Page {
              id
              title
              handle
              body
              updatedAt
            }
            ... on Article {
              id
              title
              handle
              body
              summary
              updatedAt
              publishedAt
              blog {
                title
                handle
              }
              author {
                name
              }
            }
            ... on Metaobject {
              id
              type
              handle
              displayName
              updatedAt
              onlineStoreUrl
              fields {
                key
                type
                value
              }
              definition {
                id
                type
                name
                description
                displayNameKey
                fieldDefinitions {
                  key
                  name
                }
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
    private final ShopifyBridgeBillingService billingService;

    public ShopifyBridgeVectorizationSourceService(ShopifyBridgeInstallCredentialService installCredentialService,
                                                   ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                                   ShopifyBridgeBillingService billingService) {
        this.installCredentialService = installCredentialService;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.billingService = billingService;
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
        Integer catalogProductCap = billingService.catalogProductCap(store.shopDomain(), accessToken);
        int totalCount = totalCount(store, accessToken, categories, catalogProductCap);
        int remainingProductBudget = remainingProductBudget(parsedCursor.category(), parsedCursor.seenCount(), catalogProductCap, effectiveLimit);
        if ("products".equals(parsedCursor.category()) && remainingProductBudget <= 0) {
            int currentIndex = categories.indexOf(parsedCursor.category());
            String nextCursor = currentIndex >= 0 && currentIndex + 1 < categories.size()
                ? encodeCursor(categories.get(currentIndex + 1), null, 0)
                : null;
            return new ShopifyBridgeVectorizationSourcePageResponse(
                store.shopDomain(),
                normalizedEntityType,
                totalCount,
                nextCursor != null,
                nextCursor,
                List.of()
            );
        }
        PageResult page = switch (parsedCursor.category()) {
            case "products" -> loadProductsPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), remainingProductBudget, catalogProductCap);
            case "collections" -> loadCollectionsPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "pages" -> loadPagesPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "articles" -> loadArticlesPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "policies" -> loadPoliciesPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            case "metaobjects" -> loadMetaobjectsPage(store.shopDomain(), accessToken, parsedCursor.nativeCursor(), effectiveLimit);
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported Shopify vectorization source category: " + parsedCursor.category());
        };

        String nextCursor = null;
        boolean hasMore = false;
        boolean productCapReached = "products".equals(parsedCursor.category())
            && catalogProductCap != null
            && catalogProductCap > 0
            && parsedCursor.seenCount() + page.items().size() >= catalogProductCap;
        if (!productCapReached && page.hasMore() && page.nextCursor() != null) {
            nextCursor = encodeCursor(parsedCursor.category(), page.nextCursor(), parsedCursor.seenCount() + page.items().size());
            hasMore = true;
        } else {
            int currentIndex = categories.indexOf(parsedCursor.category());
            if (currentIndex >= 0 && currentIndex + 1 < categories.size()) {
                nextCursor = encodeCursor(categories.get(currentIndex + 1), null, 0);
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

    public ShopifyBridgeVectorizationSourceRecord record(String shopDomain,
                                                         String sourceCategory,
                                                         String sourceObjectId) {
        ShopifyBridgeCredentialAcquisition acquisition = installCredentialService.resolvePersistedMaterial(shopDomain)
            .orElseThrow(() -> new ResponseStatusException(
                CONFLICT,
                "Shopify vectorization source requires persisted store credentials. Install or reconnect the app first."
            ));
        ShopifyBridgeStoreSummary store = acquisition.store();
        String normalizedCategory = normalizeSourceCategory(sourceCategory);
        if (sourceObjectId == null || sourceObjectId.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "sourceObjectId is required.");
        }
        String accessToken = acquisition.tokenExchangeMaterial().accessToken();
        Integer catalogProductCap = billingService.catalogProductCap(store.shopDomain(), accessToken);
        return switch (normalizedCategory) {
            case "products" -> requireProductWithinTierCap(store.shopDomain(), accessToken, sourceObjectId, catalogProductCap, loadNodeRecord(
                store.shopDomain(),
                accessToken,
                sourceObjectId,
                "Product",
                node -> new ShopifyBridgeVectorizationSourceRecord(
                    requiredText(node, "id"),
                    text(node, "updatedAt"),
                    text(node, "title"),
                    joinContent(
                        text(node, "title"),
                        text(node, "vendor"),
                        text(node, "productType"),
                        sanitizeRichText(text(node, "descriptionHtml")),
                        ShopifyProductReviewSignals.content(node),
                        ShopifyKeyProductMetafields.content(node)
                    ),
                    "products",
                    "product",
                    storefrontUrl(store.shopDomain(), "/products/" + safePath(text(node, "handle"))),
                    text(node, "handle"),
                    text(node, "vendor"),
                    text(node, "productType"),
                    null,
                    null,
                    null
                )
            ));
            case "collections" -> loadNodeRecord(
                store.shopDomain(),
                accessToken,
                sourceObjectId,
                "Collection",
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
                    storefrontUrl(store.shopDomain(), "/collections/" + safePath(text(node, "handle"))),
                    text(node, "handle"),
                    null,
                    null,
                    null,
                    null,
                    null
                )
            );
            case "pages" -> loadNodeRecord(
                store.shopDomain(),
                accessToken,
                sourceObjectId,
                "Page",
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
                    storefrontUrl(store.shopDomain(), "/pages/" + safePath(text(node, "handle"))),
                    text(node, "handle"),
                    null,
                    null,
                    null,
                    null,
                    null
                )
            );
            case "articles" -> requirePublishedArticle(
                sourceObjectId,
                loadNodeRecord(
                    store.shopDomain(),
                    accessToken,
                    sourceObjectId,
                    "Article",
                    node -> text(node, "publishedAt") == null ? null : articleRecord(store.shopDomain(), node)
                )
            );
            case "policies" -> loadPolicies(store.shopDomain(), accessToken).stream()
                .filter(item -> sourceObjectId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Shopify policy record not found: " + sourceObjectId));
            case "metaobjects" -> loadRecordFromMetaobject(store.shopDomain(), accessToken, sourceObjectId);
            default -> throw new ResponseStatusException(CONFLICT, "Unsupported Shopify vectorization source category: " + sourceCategory);
        };
    }

    private int totalCount(ShopifyBridgeStoreSummary store, String accessToken, List<String> categories, Integer catalogProductCap) {
        int total = 0;
        for (String category : categories) {
            total += switch (category) {
                case "products" -> applyProductCap(countFromConnection(
                    store.shopDomain(),
                    accessToken,
                    PRODUCTS_COUNT_QUERY,
                    "productsCount"
                ), catalogProductCap);
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
                case "articles" -> countPublishedArticles(store.shopDomain(), accessToken);
                case "policies" -> loadPolicies(store.shopDomain(), accessToken).size();
                case "metaobjects" -> ShopifyMetaobjectSupport.totalCount(store.shopDomain(), accessToken, shopifyAdminGraphqlClient);
                default -> 0;
            };
        }
        return total;
    }

    private ShopifyBridgeVectorizationSourceRecord loadNodeRecord(String shopDomain,
                                                                  String accessToken,
                                                                  String sourceObjectId,
                                                                  String expectedType,
                                                                  RecordFactory recordFactory) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            NODE_RECORD_QUERY,
            Map.of("id", sourceObjectId)
        );
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"), "Shopify Admin API response is missing data.");
        Map<String, Object> node = requireMap(data.get("node"), "Shopify Admin API response is missing node.");
        String typename = text(node, "__typename");
        if (typename != null && !expectedType.equalsIgnoreCase(typename)) {
            throw new ResponseStatusException(CONFLICT, "Shopify source record type mismatch. Expected " + expectedType + " but found " + typename + ".");
        }
        return recordFactory.create(node);
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
                                        int limit,
                                        Integer catalogProductCap) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            PRODUCTS_QUERY,
            variablesWithCursor(cursor, limit)
        );
        return applyProductCap(pageFromConnection(
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
                    sanitizeRichText(text(node, "descriptionHtml")),
                    ShopifyProductReviewSignals.content(node),
                    ShopifyKeyProductMetafields.content(node)
                ),
                "products",
                "product",
                storefrontUrl(shopDomain, "/products/" + safePath(text(node, "handle"))),
                text(node, "handle"),
                text(node, "vendor"),
                text(node, "productType"),
                null,
                null,
                null
            )
        ), catalogProductCap);
    }

    private ShopifyBridgeVectorizationSourceRecord requireProductWithinTierCap(String shopDomain,
                                                                               String accessToken,
                                                                               String sourceObjectId,
                                                                               Integer catalogProductCap,
                                                                               ShopifyBridgeVectorizationSourceRecord record) {
        if (catalogProductCap == null || catalogProductCap <= 0) {
            return record;
        }
        List<String> accessibleIds = loadProductsPage(shopDomain, accessToken, null, catalogProductCap, catalogProductCap).items().stream()
            .map(ShopifyBridgeVectorizationSourceRecord::id)
            .toList();
        if (accessibleIds.contains(sourceObjectId)) {
            return record;
        }
        throw new ResponseStatusException(CONFLICT, "Requested product is outside the current tier product cap.");
    }

    private int applyProductCap(int count, Integer catalogProductCap) {
        if (catalogProductCap == null || catalogProductCap <= 0) {
            return count;
        }
        return Math.min(count, catalogProductCap);
    }

    private PageResult applyProductCap(PageResult page, Integer catalogProductCap) {
        if (catalogProductCap == null || catalogProductCap <= 0 || page.items().size() <= catalogProductCap) {
            return page;
        }
        return new PageResult(
            List.copyOf(page.items().subList(0, catalogProductCap)),
            page.nextCursor(),
            page.hasMore()
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
                null,
                null,
                null
            )
        );
    }

    private PageResult loadArticlesPage(String shopDomain,
                                        String accessToken,
                                        String cursor,
                                        int limit) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            shopDomain,
            accessToken,
            ARTICLES_QUERY,
            variablesWithCursor(cursor, limit)
        );
        return pageFromConnection(
            response,
            "articles",
            node -> text(node, "publishedAt") == null ? null : articleRecord(shopDomain, node)
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

    private PageResult loadMetaobjectsPage(String shopDomain,
                                           String accessToken,
                                           String cursor,
                                           int limit) {
        ShopifyMetaobjectSupport.MetaobjectPage page = ShopifyMetaobjectSupport.pageEntries(
            shopDomain,
            accessToken,
            shopifyAdminGraphqlClient,
            cursor,
            limit
        );
        return new PageResult(
            page.items().stream()
                .map(this::metaobjectRecord)
                .toList(),
            page.nextCursor(),
            page.hasMore()
        );
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
                text(node, "type"),
                null,
                null
            ))
            .toList();
    }

    private int countPublishedArticles(String shopDomain, String accessToken) {
        int count = 0;
        String cursor = null;
        while (true) {
            PageResult page = loadArticlesPage(shopDomain, accessToken, cursor, MAX_LIMIT);
            count += page.items().size();
            if (!page.hasMore()) {
                return count;
            }
            cursor = page.nextCursor();
        }
    }

    private ShopifyBridgeVectorizationSourceRecord articleRecord(String shopDomain, Map<String, Object> node) {
        return new ShopifyBridgeVectorizationSourceRecord(
            requiredText(node, "id"),
            text(node, "updatedAt"),
            text(node, "title"),
            joinContent(
                text(node, "title"),
                nestedText(node, "blog", "title"),
                nestedText(node, "author", "name"),
                sanitizeRichText(text(node, "summary")),
                sanitizeRichText(text(node, "body"))
            ),
            "articles",
            "article",
            articleStorefrontUrl(shopDomain, node),
            text(node, "handle"),
            null,
            null,
            null,
            null,
            null
        );
    }

    private ShopifyBridgeVectorizationSourceRecord loadRecordFromMetaobject(String shopDomain,
                                                                            String accessToken,
                                                                            String sourceObjectId) {
        return metaobjectRecord(ShopifyMetaobjectSupport.loadRecord(
            shopDomain,
            accessToken,
            shopifyAdminGraphqlClient,
            sourceObjectId
        ));
    }

    private ShopifyBridgeVectorizationSourceRecord metaobjectRecord(ShopifyMetaobjectSupport.MetaobjectEntrySummary entry) {
        return new ShopifyBridgeVectorizationSourceRecord(
            entry.id(),
            entry.updatedAt(),
            entry.title(),
            entry.content(),
            "metaobjects",
            "support-policy",
            entry.storefrontUrl(),
            entry.handle(),
            null,
            null,
            null,
            entry.type(),
            entry.definitionName()
        );
    }

    private ShopifyBridgeVectorizationSourceRecord requirePublishedArticle(String sourceObjectId,
                                                                           ShopifyBridgeVectorizationSourceRecord record) {
        if (record != null) {
            return record;
        }
        throw new ResponseStatusException(CONFLICT, "Shopify article is not published: " + sourceObjectId);
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
            ShopifyBridgeVectorizationSourceRecord record = recordFactory.create(node);
            if (record != null) {
                items.add(record);
            }
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
            return new ParsedCursor(defaultCategory, null, 0);
        }
        String[] parts = cursor.split("\\|", 3);
        String category = parts[0].trim().toLowerCase(Locale.ROOT);
        String nativeCursor = parts.length > 1 && !parts[1].isBlank() ? parts[1].trim() : null;
        int seenCount = parts.length > 2 ? parseOffset(parts[2]) : 0;
        return new ParsedCursor(category, nativeCursor, seenCount);
    }

    private String encodeCursor(String category, String nativeCursor, int seenCount) {
        return category + "|" + (nativeCursor == null ? "" : nativeCursor) + "|" + Math.max(seenCount, 0);
    }

    private int remainingProductBudget(String category, int seenCount, Integer catalogProductCap, int requestedLimit) {
        if (!"products".equals(category) || catalogProductCap == null || catalogProductCap <= 0) {
            return requestedLimit;
        }
        return Math.max(Math.min(requestedLimit, catalogProductCap - Math.max(seenCount, 0)), 0);
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
            List<String> categories = new ArrayList<>(4);
            if (store.pagesEnabled()) {
                categories.add("pages");
            }
            if (store.policiesEnabled()) {
                categories.add("policies");
            }
            if (store.articlesEnabled()) {
                categories.add("articles");
            }
            if (store.metaobjectsEnabled()) {
                categories.add("metaobjects");
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

    private String normalizeSourceCategory(String sourceCategory) {
        if (sourceCategory == null || sourceCategory.isBlank()) {
            throw new ResponseStatusException(CONFLICT, "sourceCategory is required.");
        }
        return sourceCategory.trim().toLowerCase(Locale.ROOT);
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

    private String nestedText(Map<String, Object> source, String fieldName, String nestedFieldName) {
        Object value = source.get(fieldName);
        if (!(value instanceof Map<?, ?> nested)) {
            return null;
        }
        Object nestedValue = nested.get(nestedFieldName);
        return nestedValue == null ? null : nestedValue.toString();
    }

    private String articleStorefrontUrl(String shopDomain, Map<String, Object> node) {
        String blogHandle = nestedText(node, "blog", "handle");
        String handle = text(node, "handle");
        if (blogHandle == null || blogHandle.isBlank() || handle == null || handle.isBlank()) {
            return null;
        }
        return storefrontUrl(shopDomain, "/blogs/" + safePath(blogHandle) + "/" + safePath(handle));
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

    private record ParsedCursor(String category, String nativeCursor, int seenCount) {
    }

    private record PageResult(List<ShopifyBridgeVectorizationSourceRecord> items, String nextCursor, boolean hasMore) {
    }
}
