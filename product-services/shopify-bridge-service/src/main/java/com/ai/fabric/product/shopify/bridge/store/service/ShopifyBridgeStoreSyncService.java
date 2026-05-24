package com.ai.fabric.product.shopify.bridge.store.service;

import com.ai.fabric.product.shopify.bridge.billing.service.ShopifyBridgeBillingService;
import com.ai.fabric.product.shopify.bridge.client.platform.PlatformShopifyStoreClient;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeRecordSyncStatusRequest;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSummary;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeStoreSyncDocument;
import com.ai.fabric.product.shopify.bridge.store.model.ShopifyBridgeSyncStoreDocumentsRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class ShopifyBridgeStoreSyncService {

    private static final int MAX_POLICY_BODY_CHARS = 5_500;

    private static final String PRODUCTS_QUERY = """
        query ShopifyCompanionProductsSync($cursor: String) {
          products(first: 50, after: $cursor) {
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
                tags
                featuredImage {
                  url
                  altText
                }
                totalInventory
                priceRangeV2 {
                  minVariantPrice {
                    amount
                    currencyCode
                  }
                  maxVariantPrice {
                    amount
                    currencyCode
                  }
                }
                variants(first: 20) {
                  edges {
                    node {
                      id
                      title
                      sku
                      availableForSale
                      price
                      compareAtPrice
                      selectedOptions {
                        name
                        value
                      }
                    }
                  }
                }
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

    private static final String COLLECTIONS_QUERY = """
        query ShopifyCompanionCollectionsSync($cursor: String) {
          collections(first: 50, after: $cursor) {
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
        query ShopifyCompanionPagesSync($cursor: String) {
          pages(first: 50, after: $cursor) {
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
        query ShopifyCompanionPoliciesSync {
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

    private static final String ARTICLES_QUERY = """
        query ShopifyCompanionArticlesSync($cursor: String) {
          articles(first: 50, after: $cursor) {
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

    private final ShopifyAdminGraphqlClient shopifyAdminGraphqlClient;
    private final PlatformShopifyStoreClient platformShopifyStoreClient;
    private final ShopifyBridgeBillingService billingService;

    public ShopifyBridgeStoreSyncService(ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                         PlatformShopifyStoreClient platformShopifyStoreClient,
                                         ShopifyBridgeBillingService billingService) {
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.platformShopifyStoreClient = platformShopifyStoreClient;
        this.billingService = billingService;
    }

    public ShopifyBridgeStoreSummary sync(ShopifyBridgeCredentialAcquisition acquisition) {
        return sync(acquisition, "FULL", "Shopify bridge synced %d documents into the platform runtime.");
    }

    public ShopifyBridgeStoreSummary syncFromWebhook(ShopifyBridgeCredentialAcquisition acquisition,
                                                     String topic) {
        String normalizedTopic = topic == null || topic.isBlank() ? "shopify content change" : topic.trim();
        return sync(acquisition, "INCREMENTAL", "Shopify bridge completed a webhook-triggered refresh for " + normalizedTopic + " and synced %d documents into the platform runtime.");
    }

    private ShopifyBridgeStoreSummary sync(ShopifyBridgeCredentialAcquisition acquisition,
                                           String mode,
                                           String successMessageTemplate) {
        ShopifyBridgeStoreSummary store = acquisition.store();
        String shopDomain = store.shopDomain();
        String accessToken = acquisition.tokenExchangeMaterial().accessToken();
        Integer catalogProductCap = billingService.catalogProductCap(shopDomain, accessToken);
        try {
            List<ShopifyBridgeStoreSyncDocument> documents = new ArrayList<>();
            if (store.productsEnabled()) {
                documents.addAll(loadProducts(shopDomain, accessToken, catalogProductCap));
            }
            if (store.collectionsEnabled()) {
                documents.addAll(loadCollections(shopDomain, accessToken));
            }
            if (store.pagesEnabled()) {
                documents.addAll(loadPages(shopDomain, accessToken));
            }
            if (store.policiesEnabled()) {
                documents.addAll(loadPolicies(shopDomain, accessToken));
            }
            if (store.articlesEnabled()) {
                documents.addAll(loadArticles(shopDomain, accessToken));
            }
            if (store.metaobjectsEnabled()) {
                documents.addAll(loadMetaobjects(shopDomain, accessToken));
            }
            ShopifyBridgeStoreSummary synced = platformShopifyStoreClient.syncDocuments(
                shopDomain,
                new ShopifyBridgeSyncStoreDocumentsRequest(mode, documents)
            );
            return platformShopifyStoreClient.recordSyncStatus(
                shopDomain,
                new ShopifyBridgeRecordSyncStatusRequest(
                    "SYNCED",
                    mode,
                    documents.size(),
                    successMessageTemplate.formatted(documents.size())
                )
            );
        } catch (RuntimeException ex) {
            String message = syncFailureMessage(ex);
            platformShopifyStoreClient.recordSyncStatus(
                shopDomain,
                new ShopifyBridgeRecordSyncStatusRequest("FAILED", mode, 0, message)
            );
            throw new ResponseStatusException(BAD_GATEWAY, message, ex);
        }
    }

    private List<ShopifyBridgeStoreSyncDocument> loadProducts(String shopDomain, String accessToken, Integer catalogProductCap) {
        return paginate(shopDomain, accessToken, PRODUCTS_QUERY, "products", catalogProductCap).stream()
            .map(node -> {
                ShopifyProductReviewSignals.ReviewSignalSummary reviewSignals = ShopifyProductReviewSignals.summary(node);
                LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(metadata(
                    "handle", text(node, "handle"),
                    "vendor", text(node, "vendor"),
                    "productType", text(node, "productType"),
                    "metafieldKeys", ShopifyKeyProductMetafields.keys(node),
                    "updatedAt", text(node, "updatedAt"),
                    "storefrontUrl", storefrontUrl(shopDomain, "/products/" + safePath(text(node, "handle"))),
                    "imageUrl", productImageUrl(node),
                    "imageAltText", productImageAltText(node)
                ));
                metadata.putAll(ShopifyProductCommerceEvidence.metadata(node));
                metadata.putAll(reviewSignals.metadata());
                return new ShopifyBridgeStoreSyncDocument(
                    requiredText(node, "id"),
                    "products",
                    "product",
                    text(node, "title"),
                    joinContent(
                        text(node, "title"),
                        text(node, "vendor"),
                        text(node, "productType"),
                        joinTags(node.get("tags")),
                        ShopifyProductCommerceEvidence.content(node),
                        sanitizeRichText(text(node, "descriptionHtml")),
                        ShopifyProductReviewSignals.content(node),
                        ShopifyKeyProductMetafields.content(node)
                    ),
                    Map.copyOf(metadata)
                );
            })
            .toList();
    }

    private List<ShopifyBridgeStoreSyncDocument> loadCollections(String shopDomain, String accessToken) {
        return paginate(shopDomain, accessToken, COLLECTIONS_QUERY, "collections", null).stream()
            .map(node -> new ShopifyBridgeStoreSyncDocument(
                requiredText(node, "id"),
                "collections",
                "product",
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    "Collection",
                    sanitizeRichText(text(node, "descriptionHtml"))
                ),
                metadata(
                    "handle", text(node, "handle"),
                    "updatedAt", text(node, "updatedAt"),
                    "documentType", "collection",
                    "storefrontUrl", storefrontUrl(shopDomain, "/collections/" + safePath(text(node, "handle")))
                )
            ))
            .toList();
    }

    private List<ShopifyBridgeStoreSyncDocument> loadPages(String shopDomain, String accessToken) {
        return paginate(shopDomain, accessToken, PAGES_QUERY, "pages", null).stream()
            .map(node -> new ShopifyBridgeStoreSyncDocument(
                requiredText(node, "id"),
                "pages",
                "support-policy",
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    sanitizeRichText(text(node, "body"))
                ),
                metadata(
                    "handle", text(node, "handle"),
                    "updatedAt", text(node, "updatedAt"),
                    "documentType", "page",
                    "storefrontUrl", storefrontUrl(shopDomain, "/pages/" + safePath(text(node, "handle")))
                )
            ))
            .toList();
    }

    private List<ShopifyBridgeStoreSyncDocument> loadPolicies(String shopDomain, String accessToken) {
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
            .map(node -> new ShopifyBridgeStoreSyncDocument(
                requiredText(node, "id"),
                "policies",
                "support-policy",
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    text(node, "type"),
                    sanitizePolicyBody(text(node, "body"))
                ),
                metadata(
                    "policyType", text(node, "type"),
                    "updatedAt", text(node, "updatedAt"),
                    "documentType", "policy",
                    "storefrontUrl", text(node, "url")
                )
            ))
            .toList();
    }

    private List<ShopifyBridgeStoreSyncDocument> loadArticles(String shopDomain, String accessToken) {
        return paginate(shopDomain, accessToken, ARTICLES_QUERY, "articles", null).stream()
            .filter(node -> text(node, "publishedAt") != null)
            .map(node -> new ShopifyBridgeStoreSyncDocument(
                requiredText(node, "id"),
                "articles",
                "support-policy",
                text(node, "title"),
                joinContent(
                    text(node, "title"),
                    nestedText(node, "blog", "title"),
                    nestedText(node, "author", "name"),
                    sanitizeRichText(text(node, "summary")),
                    sanitizeRichText(text(node, "body"))
                ),
                metadata(
                    "handle", text(node, "handle"),
                    "updatedAt", text(node, "updatedAt"),
                    "documentType", "article",
                    "storefrontUrl", articleStorefrontUrl(shopDomain, node)
                )
            ))
            .toList();
    }

    private List<ShopifyBridgeStoreSyncDocument> loadMetaobjects(String shopDomain, String accessToken) {
        return ShopifyMetaobjectSupport.loadAllEntries(shopDomain, accessToken, shopifyAdminGraphqlClient).stream()
            .map(entry -> new ShopifyBridgeStoreSyncDocument(
                entry.id(),
                "metaobjects",
                "support-policy",
                entry.title(),
                entry.content(),
                metadata(
                    "handle", entry.handle(),
                    "updatedAt", entry.updatedAt(),
                    "documentType", "metaobject",
                    "storefrontUrl", entry.storefrontUrl(),
                    "metaobjectType", entry.type(),
                    "definitionName", entry.definitionName()
                )
            ))
            .toList();
    }

    private List<Map<String, Object>> paginate(String shopDomain,
                                               String accessToken,
                                               String query,
                                               String connectionField,
                                               Integer itemCap) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        String cursor = null;
        while (true) {
            Map<String, Object> response = shopifyAdminGraphqlClient.execute(
                shopDomain,
                accessToken,
                query,
                variablesWithCursor(cursor)
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
                if (itemCap != null && itemCap > 0 && nodes.size() >= itemCap) {
                    return nodes;
                }
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

    private Map<String, Object> variablesWithCursor(String cursor) {
        LinkedHashMap<String, Object> variables = new LinkedHashMap<>();
        variables.put("cursor", cursor);
        return variables;
    }

    private Map<String, Object> metadata(Object... entries) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        for (int index = 0; index + 1 < entries.length; index += 2) {
            Object key = entries[index];
            Object value = entries[index + 1];
            if (key == null || value == null) {
                continue;
            }
            String normalizedKey = key.toString().trim();
            if (normalizedKey.isEmpty()) {
                continue;
            }
            if (value instanceof String text && text.isBlank()) {
                continue;
            }
            metadata.put(normalizedKey, value);
        }
        return Map.copyOf(metadata);
    }

    private String joinContent(String... parts) {
        StringBuilder content = new StringBuilder();
        for (String part : parts) {
            if (part == null || part.isBlank()) {
                continue;
            }
            if (!content.isEmpty()) {
                content.append("\n\n");
            }
            content.append(part.trim());
        }
        return content.toString();
    }

    private String joinTags(Object value) {
        if (!(value instanceof List<?> tags) || tags.isEmpty()) {
            return null;
        }
        return tags.stream()
            .map(tag -> tag == null ? null : tag.toString().trim())
            .filter(tag -> tag != null && !tag.isBlank())
            .reduce((left, right) -> left + ", " + right)
            .orElse(null);
    }

    private String sanitizeRichText(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String stripped = html
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p\\s*>", "\n")
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ");
        String collapsed = stripped.replaceAll("[\\t\\x0B\\f\\r ]+", " ").replaceAll("\\n{3,}", "\n\n");
        String normalized = collapsed.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String sanitizePolicyBody(String html) {
        String sanitized = sanitizeRichText(html);
        if (sanitized == null) {
            return null;
        }
        String withoutLiquidArtifacts = sanitized
            .replaceAll("\\{\\{[^}]+}}", " ")
            .replaceAll("\\{%[^%]+%}", " ")
            .replaceAll("\\s{2,}", " ")
            .trim();
        if (withoutLiquidArtifacts.isEmpty()) {
            return null;
        }
        return truncateForKnowledge(withoutLiquidArtifacts, MAX_POLICY_BODY_CHARS);
    }

    private String truncateForKnowledge(String value, int maxChars) {
        if (value == null || value.isBlank() || maxChars <= 0 || value.length() <= maxChars) {
            return value;
        }
        int cutoff = value.lastIndexOf(' ', maxChars - 1);
        if (cutoff < Math.max(1, maxChars / 2)) {
            cutoff = maxChars;
        }
        return value.substring(0, cutoff).trim();
    }

    private String storefrontUrl(String shopDomain, String path) {
        if (shopDomain == null || shopDomain.isBlank() || path == null || path.isBlank()) {
            return null;
        }
        return URI.create("https://" + shopDomain).resolve(path.startsWith("/") ? path : "/" + path).toString();
    }

    private String safePath(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String requiredText(Map<String, Object> map, String field) {
        String value = text(map, field);
        if (value == null) {
            throw new ResponseStatusException(BAD_GATEWAY, "Shopify Admin API response is missing " + field + ".");
        }
        return value;
    }

    private String text(Map<String, Object> map, String field) {
        if (map == null || field == null) {
            return null;
        }
        Object value = map.get(field);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String nestedText(Map<String, Object> map, String field, String nestedField) {
        Object value = map == null ? null : map.get(field);
        if (!(value instanceof Map<?, ?> nested)) {
            return null;
        }
        Object nestedValue = nested.get(nestedField);
        if (nestedValue == null) {
            return null;
        }
        String text = nestedValue.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private String productImageUrl(Map<String, Object> node) {
        return nestedText(node, "featuredImage", "url");
    }

    private String productImageAltText(Map<String, Object> node) {
        String altText = nestedText(node, "featuredImage", "altText");
        return altText == null ? text(node, "title") : altText;
    }

    private String articleStorefrontUrl(String shopDomain, Map<String, Object> node) {
        String blogHandle = nestedText(node, "blog", "handle");
        String handle = text(node, "handle");
        if (blogHandle == null || handle == null) {
            return null;
        }
        return storefrontUrl(shopDomain, "/blogs/" + safePath(blogHandle) + "/" + safePath(handle));
    }

    private String syncFailureMessage(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException && responseStatusException.getReason() != null) {
            return responseStatusException.getReason();
        }
        if (ex instanceof RestClientResponseException restClientResponseException) {
            return "Platform sync API failed with HTTP " + restClientResponseException.getStatusCode().value() + ".";
        }
        return ex.getMessage() == null || ex.getMessage().isBlank()
            ? "Shopify bridge sync failed."
            : ex.getMessage().trim();
    }
}
