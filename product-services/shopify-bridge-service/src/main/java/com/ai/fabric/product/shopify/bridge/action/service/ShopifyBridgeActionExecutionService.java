package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantRequest;
import com.ai.fabric.product.shopify.bridge.governedaction.model.ShopifyStorefrontGovernedActionGrantResponse;
import com.ai.fabric.product.shopify.bridge.governedaction.service.ShopifyStorefrontGovernedActionService;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyProductReviewSignals;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ShopifyBridgeActionExecutionService {

    private static final String PRODUCTS_QUERY = """
        query ShopifyBridgeProductsAction($query: String) {
          products(first: 10, query: $query, sortKey: UPDATED_AT, reverse: true) {
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
                variants(first: 25) {
                  nodes {
                    id
                    title
                    sku
                    price
                    availableForSale
                    inventoryQuantity
                  }
                }
              }
            }
          }
        }
        """;

    private static final String POLICIES_QUERY = """
        query ShopifyBridgePoliciesAction {
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
    private final ShopifyStorefrontGovernedActionService governedActionService;

    public ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                               ShopifyAdminGraphqlClient shopifyAdminGraphqlClient,
                                               ShopifyStorefrontGovernedActionService governedActionService) {
        this.installCredentialService = installCredentialService;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
        this.governedActionService = governedActionService;
    }

    public ShopifyBridgeActionResult execute(String shopDomain, ShopifyBridgeActionExecuteRequest request) {
        String normalizedShopDomain = normalize(shopDomain);
        String actionId = normalize(request == null ? null : request.actionId());
        if (normalizedShopDomain == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "shopDomain is required.");
        }
        if (actionId == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "actionId is required.");
        }
        Optional<ShopifyBridgeCredentialAcquisition> acquisition =
            installCredentialService.resolvePersistedMaterial(normalizedShopDomain);
        if (acquisition.isEmpty()) {
            return ShopifyBridgeActionResult.failure(
                "NOT_CONNECTED",
                "Shopify store credentials are not available for this bridge."
            );
        }

        return switch (actionId) {
            case "list_products" -> listProducts(acquisition.get(), request);
            case "search_products" -> searchProducts(acquisition.get(), request);
            case "get_product_details" -> getProductDetails(acquisition.get(), request);
            case "check_availability" -> checkAvailability(acquisition.get(), request);
            case "get_policy" -> getPolicy(acquisition.get(), request);
            case "relationship_query" -> relationshipQuery(acquisition.get(), request);
            case "add_product_to_cart", "add_to_cart" -> addProductToCart(acquisition.get(), request);
            case "update_cart_quantity" -> updateCartQuantity(acquisition.get(), request);
            default -> ShopifyBridgeActionResult.failure("ACTION_NOT_SUPPORTED", "Action is not supported.");
        };
    }

    private ShopifyBridgeActionResult listProducts(ShopifyBridgeCredentialAcquisition acquisition,
                                                   ShopifyBridgeActionExecuteRequest request) {
        String query = textParam(request, "query");
        ProductFilters filters = productFilters(request);
        ProductRelationshipSearch search = fetchRelationshipProducts(acquisition, query, 10);
        List<Map<String, Object>> items = filterProducts(search.items(), filters);
        return ShopifyBridgeActionResult.ok(
            items.isEmpty() ? "No products found." : "Products",
            productSearchData(items, query, search, filters)
        );
    }

    private ShopifyBridgeActionResult searchProducts(ShopifyBridgeCredentialAcquisition acquisition,
                                                     ShopifyBridgeActionExecuteRequest request) {
        String query = textParam(request, "query");
        ProductFilters filters = productFilters(request);
        ProductRelationshipSearch search = fetchRelationshipProducts(acquisition, query, 10);
        List<Map<String, Object>> items = filterProducts(search.items(), filters);
        return ShopifyBridgeActionResult.ok(
            items.isEmpty() ? "No matching products found." : "Products",
            productSearchData(items, query, search, filters)
        );
    }

    private ShopifyBridgeActionResult getProductDetails(ShopifyBridgeCredentialAcquisition acquisition,
                                                        ShopifyBridgeActionExecuteRequest request) {
        String lookupValue = productLookupValue(request);
        if (lookupValue == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.sku, params.handle, or params.query is required.");
        }
        ProductLookup lookup = findProduct(acquisition, lookupValue);
        if (lookup == null) {
            return ShopifyBridgeActionResult.failure("NOT_FOUND", "No product was found for " + lookupValue + ".");
        }
        return ShopifyBridgeActionResult.ok(
            "Product details",
            Map.of("product", lookup.product(), "lookup", lookupValue, "lookupMethod", lookup.lookupMethod())
        );
    }

    private ShopifyBridgeActionResult checkAvailability(ShopifyBridgeCredentialAcquisition acquisition,
                                                        ShopifyBridgeActionExecuteRequest request) {
        String lookupValue = productLookupValue(request);
        if (lookupValue == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.sku, params.handle, or params.query is required.");
        }
        ProductLookup lookup = findProduct(acquisition, lookupValue);
        if (lookup == null) {
            return ShopifyBridgeActionResult.failure("NOT_FOUND", "No product was found for " + lookupValue + ".");
        }
        Map<String, Object> product = lookup.product();
        Map<String, Object> variant = lookup.variant() == null ? primaryAvailabilityVariant(product) : lookup.variant();
        if (variant == null) {
            return ShopifyBridgeActionResult.failure("NOT_FOUND", "No sellable variant was found for " + lookupValue + ".");
        }
        LinkedHashMap<String, Object> availability = new LinkedHashMap<>();
        availability.put("lookup", lookupValue);
        availability.put("lookupMethod", lookup.lookupMethod());
        availability.put("sku", text(variant, "sku"));
        availability.put("productTitle", product.get("title"));
        availability.put("productHandle", product.get("handle"));
        availability.put("variantTitle", variant.get("title"));
        availability.put("available", Boolean.TRUE.equals(variant.get("availableForSale")));
        availability.put("inventoryQuantity", variant.get("inventoryQuantity"));
        availability.put("storefrontUrl", product.get("storefrontUrl"));
        return ShopifyBridgeActionResult.ok(
            "Availability",
            availability
        );
    }

    private ShopifyBridgeActionResult getPolicy(ShopifyBridgeCredentialAcquisition acquisition,
                                                ShopifyBridgeActionExecuteRequest request) {
        String query = firstNonBlank(textParam(request, "query"), textParam(request, "policyType"));
        int limit = integerParam(request, "limit", 5, 1, 10);
        List<Map<String, Object>> policies = fetchPolicies(acquisition);
        List<Map<String, Object>> result = policies.stream()
            .filter(policy -> policyMatches(policy, query))
            .limit(limit)
            .toList();
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("items", result);
        data.put("count", result.size());
        if (query != null) {
            data.put("query", query);
        }
        return ShopifyBridgeActionResult.ok(
            result.isEmpty() ? "No policies found." : "Policies",
            data
        );
    }

    private ShopifyBridgeActionResult relationshipQuery(ShopifyBridgeCredentialAcquisition acquisition,
                                                        ShopifyBridgeActionExecuteRequest request) {
        String query = firstNonBlank(textParam(request, "query"), textParam(request, "semanticQuery"));
        if (query == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.query is required.");
        }
        int limit = integerParam(request, "limit", 8, 1, 20);
        List<String> entityTypes = listParam(request, "entityTypes");
        boolean explicitEntityTypes = !entityTypes.isEmpty();
        boolean wantsPolicies = entityTypesContain(entityTypes, "policy")
            || containsAny(query, "policy", "policies", "return", "refund", "shipping", "privacy", "terms");
        boolean wantsProducts = !explicitEntityTypes
            || entityTypesContain(entityTypes, "product")
            || entityTypesContain(entityTypes, "catalog")
            || containsAny(query, "product", "products", "snowboard", "item", "items", "buy", "compare", "similar");

        List<Map<String, Object>> documents = new ArrayList<>();
        List<Map<String, Object>> productItems = List.of();
        List<Map<String, Object>> policyItems = List.of();
        List<String> productSearchQueries = List.of();
        boolean productFallbackToRecentCatalog = false;
        ProductFilters filters = productFilters(request);
        if (wantsProducts) {
            ProductRelationshipSearch search = fetchRelationshipProducts(acquisition, query, limit);
            productItems = filterProducts(search.items(), filters);
            productSearchQueries = search.queries();
            productFallbackToRecentCatalog = search.fallbackToRecentCatalog();
            productItems.stream()
                .map(this::productRelationshipDocument)
                .forEach(documents::add);
        }
        if (wantsPolicies && documents.size() < limit) {
            int remaining = Math.max(1, limit - documents.size());
            policyItems = fetchPolicies(acquisition).stream()
                .filter(policy -> policyMatches(policy, query))
                .limit(remaining)
                .toList();
            policyItems.stream()
                .map(this::policyRelationshipDocument)
                .forEach(documents::add);
        }

        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("query", query);
        data.put("entityTypes", entityTypes);
        data.put("documents", documents);
        data.put("items", productItems);
        data.put("policies", policyItems);
        data.put("totalResults", documents.size());
        data.put("returnedResults", documents.size());
        data.put("hybridSearchUsed", false);
        data.put("filtersApplied", filters.toMap());
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "shopify-admin-api");
        metadata.put("shopDomain", acquisition.store().shopDomain());
        metadata.put("relationshipMode", "SHOPIFY_COMPANION_LIVE_CATALOG");
        metadata.put("productSearchQueries", productSearchQueries);
        metadata.put("productFallbackToRecentCatalog", productFallbackToRecentCatalog);
        data.put("metadata", metadata);
        return ShopifyBridgeActionResult.ok(
            documents.isEmpty() ? "No relationship query results found." : "Relationship query results",
            data
        );
    }

    private Map<String, Object> productSearchData(List<Map<String, Object>> items,
                                                  String query,
                                                  ProductRelationshipSearch search,
                                                  ProductFilters filters) {
        LinkedHashMap<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("count", items.size());
        if (query != null) {
            data.put("query", query);
        }
        data.put("productSearchQueries", search.queries());
        data.put("productFallbackToRecentCatalog", search.fallbackToRecentCatalog());
        data.put("filtersApplied", filters.toMap());
        return data;
    }

    private ShopifyBridgeActionResult addProductToCart(ShopifyBridgeCredentialAcquisition acquisition,
                                                       ShopifyBridgeActionExecuteRequest request) {
        ProductLookup lookup = resolveGuidedCommerceProduct(acquisition, request);
        if (lookup == null) {
            return ShopifyBridgeActionResult.failure(
                "PRODUCT_SELECTION_REQUIRED",
                "Choose a specific product or variant before adding it to the cart."
            );
        }
        Map<String, Object> variant = lookup.variant();
        if (variant == null || numericShopifyId(text(variant, "id")) == null) {
            return ShopifyBridgeActionResult.failure(
                "VARIANT_SELECTION_REQUIRED",
                "Choose a Shopify variant before adding it to the cart."
            );
        }
        return grantGuidedCommerce(
            acquisition,
            request,
            "ADD_TO_CART",
            lookup.product(),
            variant,
            integerParam(request, "quantity", 1, 1, 10),
            null,
            null
        );
    }

    private ShopifyBridgeActionResult updateCartQuantity(ShopifyBridgeCredentialAcquisition acquisition,
                                                         ShopifyBridgeActionExecuteRequest request) {
        String cartLineKey = firstNonBlank(textParam(request, "cartLineKey"), textParam(request, "lineKey"));
        if (cartLineKey == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.cartLineKey is required.");
        }
        Integer targetQuantity = requiredIntegerParam(request, "targetQuantity", 0, 25);
        if (targetQuantity == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.targetQuantity must be a number between 0 and 25.");
        }
        ProductLookup lookup = resolveGuidedCommerceProduct(acquisition, request);
        if (lookup == null || lookup.variant() == null || numericShopifyId(text(lookup.variant(), "id")) == null) {
            return ShopifyBridgeActionResult.failure("VARIANT_SELECTION_REQUIRED", "params.variantId or a resolvable product query is required.");
        }
        return grantGuidedCommerce(
            acquisition,
            request,
            "UPDATE_CART_QUANTITY",
            lookup.product(),
            lookup.variant(),
            null,
            targetQuantity,
            cartLineKey
        );
    }

    private ShopifyBridgeActionResult grantGuidedCommerce(ShopifyBridgeCredentialAcquisition acquisition,
                                                          ShopifyBridgeActionExecuteRequest request,
                                                          String actionType,
                                                          Map<String, Object> product,
                                                          Map<String, Object> variant,
                                                          Integer requestedQuantity,
                                                          Integer targetQuantity,
                                                          String cartLineKey) {
        String shopperSessionId = shopperSessionId(request);
        if (shopperSessionId == null) {
            return ShopifyBridgeActionResult.failure(
                "SHOPPER_SESSION_REQUIRED",
                "A shopper session identifier is required for governed commerce actions."
            );
        }
        String productId = numericShopifyId(text(product, "id"));
        String variantId = numericShopifyId(text(variant, "id"));
        try {
            ShopifyStorefrontGovernedActionGrantResponse grant = governedActionService.grant(
                acquisition.store().shopDomain(),
                new ShopifyStorefrontGovernedActionGrantRequest(
                    actionType,
                    firstNonBlank(textParam(request, "surfaceId"), "max-mode"),
                    firstNonBlank(textParam(request, "pageType"), "product"),
                    productId,
                    firstNonBlank(textParam(request, "productHandle"), text(product, "handle")),
                    firstNonBlank(textParam(request, "productTitle"), text(product, "title")),
                    variantId,
                    requestedQuantity,
                    targetQuantity,
                    cartLineKey,
                    true
                ),
                shopperSessionId
            );
            LinkedHashMap<String, Object> data = new LinkedHashMap<>();
            data.put("actionType", grant.actionType());
            data.put("actionPackage", grant.actionPackage());
            data.put("operationKind", grant.operationKind());
            data.put("auditId", grant.auditId());
            data.put("token", grant.token());
            data.put("variantId", grant.variantId());
            data.put("requestedQuantity", grant.requestedQuantity());
            data.put("targetQuantity", grant.targetQuantity());
            data.put("cartLineKey", grant.cartLineKey());
            data.put("confirmationRequired", grant.confirmationRequired());
            data.put("confirmationAccepted", true);
            data.put("expiresAt", grant.expiresAt());
            data.put("productTitle", text(product, "title"));
            data.put("productHandle", text(product, "handle"));
            data.put("productId", productId);
            data.put("storefrontUrl", product.get("storefrontUrl"));
            data.put("clientCompletionRequired", true);
            data.put("completePath", "/api/storefront/shops/" + acquisition.store().shopDomain() + "/actions/complete");
            data.put("message", grant.message());
            return ShopifyBridgeActionResult.ok(
                "ADD_TO_CART".equals(actionType)
                    ? "Guided add-to-cart grant created."
                    : "Guided cart update grant created.",
                data
            );
        } catch (ResponseStatusException ex) {
            String message = ex.getReason() == null || ex.getReason().isBlank()
                ? "Governed commerce action could not be granted."
                : ex.getReason();
            return ShopifyBridgeActionResult.failure("GOVERNED_ACTION_REJECTED", message);
        }
    }

    private List<Map<String, Object>> fetchProducts(ShopifyBridgeCredentialAcquisition acquisition,
                                                    String query) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("query", normalize(query));
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            acquisition.store().shopDomain(),
            acquisition.tokenExchangeMaterial().accessToken(),
            PRODUCTS_QUERY,
            variables
        );
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"));
        Map<String, Object> products = requireMap(data.get("products"));
        List<?> edges = requireList(products.get("edges"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object edge : edges) {
            Map<String, Object> edgeMap = requireMap(edge);
            Map<String, Object> node = requireMap(edgeMap.get("node"));
            items.add(toProductItem(acquisition.store().shopDomain(), node));
        }
        return items;
    }

    private ProductRelationshipSearch fetchRelationshipProducts(ShopifyBridgeCredentialAcquisition acquisition,
                                                                String query,
                                                                int limit) {
        List<Map<String, Object>> items = new ArrayList<>();
        List<String> attemptedQueries = new ArrayList<>();
        for (String searchQuery : relationshipProductSearchQueries(query)) {
            attemptedQueries.add(searchQuery);
            addUniqueProducts(items, fetchProducts(acquisition, searchQuery), limit);
            if (items.size() >= limit) {
                return new ProductRelationshipSearch(items, attemptedQueries, false);
            }
        }
        if (items.isEmpty()) {
            attemptedQueries.add("<recent-catalog>");
            addUniqueProducts(items, fetchProducts(acquisition, null), limit);
            return new ProductRelationshipSearch(items, attemptedQueries, true);
        }
        return new ProductRelationshipSearch(items, attemptedQueries, false);
    }

    private List<String> relationshipProductSearchQueries(String query) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        String normalized = normalize(query);
        if (normalized != null) {
            queries.add(normalized);
        }
        List<String> tokens = relationshipProductSearchTokens(query);
        if (!tokens.isEmpty()) {
            queries.add(String.join(" ", tokens));
            tokens.forEach(queries::add);
        }
        return new ArrayList<>(queries);
    }

    private List<String> relationshipProductSearchTokens(String query) {
        String normalized = normalize(query);
        if (normalized == null) {
            return List.of();
        }
        Set<String> stopWords = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "before", "best", "better", "buy", "can",
            "catalog", "compare", "comparison", "find", "for", "from", "good", "help", "i", "in",
            "is", "item", "items", "me", "my", "of", "on", "option", "options", "or", "please",
            "product", "products", "recommend", "recommendation", "recommendations", "safe", "safest",
            "shop", "shopping", "should", "similar", "store", "tell", "that", "the", "these", "this",
            "those", "to", "which", "with", "you"
        );
        return java.util.Arrays.stream(normalized.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s-]", " ").split("\\s+"))
            .map(String::trim)
            .filter(token -> token.length() >= 3)
            .map(this::singularCatalogToken)
            .filter(token -> !stopWords.contains(token))
            .distinct()
            .toList();
    }

    private String singularCatalogToken(String token) {
        if (token.length() > 4 && token.endsWith("ies")) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() > 4 && token.endsWith("s") && !token.endsWith("ss")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private void addUniqueProducts(List<Map<String, Object>> target,
                                   List<Map<String, Object>> candidates,
                                   int limit) {
        for (Map<String, Object> candidate : candidates) {
            if (target.size() >= limit) {
                return;
            }
            String candidateKey = firstNonBlank(text(candidate, "id"), text(candidate, "handle"), text(candidate, "title"));
            boolean alreadyPresent = target.stream()
                .map(item -> firstNonBlank(text(item, "id"), text(item, "handle"), text(item, "title")))
                .anyMatch(existingKey -> existingKey != null && existingKey.equals(candidateKey));
            if (!alreadyPresent) {
                target.add(candidate);
            }
        }
    }

    private List<Map<String, Object>> filterProducts(List<Map<String, Object>> products, ProductFilters filters) {
        if (products == null || products.isEmpty() || filters == null || !filters.hasAny()) {
            return products == null ? List.of() : List.copyOf(products);
        }
        return products.stream()
            .filter(product -> !Boolean.TRUE.equals(filters.availableOnly()) || Boolean.TRUE.equals(product.get("available")))
            .filter(product -> {
                if (filters.maxPrice() == null) {
                    return true;
                }
                Double price = numericValue(product.get("price"));
                return price != null && price <= filters.maxPrice();
            })
            .toList();
    }

    private List<Map<String, Object>> fetchPolicies(ShopifyBridgeCredentialAcquisition acquisition) {
        Map<String, Object> response = shopifyAdminGraphqlClient.execute(
            acquisition.store().shopDomain(),
            acquisition.tokenExchangeMaterial().accessToken(),
            POLICIES_QUERY
        );
        List<String> errors = errorMessages(response);
        if (!errors.isEmpty()) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY, String.join(" ", errors));
        }
        Map<String, Object> data = requireMap(response.get("data"));
        Map<String, Object> shop = requireMap(data.get("shop"));
        List<?> policies = requireList(shop.get("shopPolicies"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object policy : policies) {
            Map<String, Object> node = requireMap(policy);
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("id", text(node, "id"));
            item.put("title", text(node, "title"));
            item.put("type", text(node, "type"));
            item.put("body", sanitizeRichText(text(node, "body")));
            item.put("url", text(node, "url"));
            item.put("updatedAt", text(node, "updatedAt"));
            items.add(item);
        }
        return items;
    }

    private ProductLookup findProduct(ShopifyBridgeCredentialAcquisition acquisition, String lookupValue) {
        if (!looksLikeSku(lookupValue)) {
            return findProductByText(acquisition, lookupValue);
        }
        ProductLookup skuLookup = findProductBySku(acquisition, lookupValue);
        if (skuLookup != null) {
            return skuLookup;
        }
        return findProductByText(acquisition, lookupValue);
    }

    private ProductLookup findProductBySku(ShopifyBridgeCredentialAcquisition acquisition, String sku) {
        String query = "sku:" + sku;
        return fetchProducts(acquisition, query).stream()
            .map(item -> {
                Map<String, Object> variant = firstMatchingVariant(item, sku);
                return variant == null ? null : new ProductLookup(item, variant, "SKU");
            })
            .filter(java.util.Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

    private ProductLookup findProductByText(ShopifyBridgeCredentialAcquisition acquisition, String lookupValue) {
        List<Map<String, Object>> matches = fetchProducts(acquisition, lookupValue);
        if (matches.isEmpty()) {
            return null;
        }
        String normalizedLookup = normalizeComparable(lookupValue);
        Map<String, Object> exact = matches.stream()
            .filter(item -> normalizedLookup.equals(normalizeComparable(text(item, "title")))
                || normalizedLookup.equals(normalizeComparable(text(item, "handle"))))
            .findFirst()
            .orElse(null);
        Map<String, Object> product = exact != null
            ? exact
            : matches.size() == 1 ? matches.getFirst() : null;
        if (product == null) {
            return null;
        }
        return new ProductLookup(product, primaryAvailabilityVariant(product), "TITLE_OR_HANDLE");
    }

    private ProductLookup resolveGuidedCommerceProduct(ShopifyBridgeCredentialAcquisition acquisition,
                                                       ShopifyBridgeActionExecuteRequest request) {
        String variantId = numericShopifyId(firstNonBlank(textParam(request, "variantId"), textParam(request, "shopifyVariantId")));
        if (variantId != null) {
            LinkedHashMap<String, Object> product = new LinkedHashMap<>();
            product.put("id", firstNonBlank(textParam(request, "productId"), textParam(request, "shopifyProductId"), ""));
            product.put("title", firstNonBlank(textParam(request, "productTitle"), textParam(request, "title"), ""));
            product.put("handle", firstNonBlank(textParam(request, "productHandle"), textParam(request, "handle"), ""));
            product.put("storefrontUrl", storefrontUrl(acquisition.store().shopDomain(), text(product, "handle")));
            LinkedHashMap<String, Object> variant = new LinkedHashMap<>();
            variant.put("id", variantId);
            variant.put("title", firstNonBlank(textParam(request, "variantTitle"), ""));
            return new ProductLookup(product, variant, "VARIANT_ID");
        }
        String lookupValue = productLookupValue(request);
        if (lookupValue == null) {
            return null;
        }
        return findProduct(acquisition, lookupValue);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstMatchingVariant(Map<String, Object> product, String sku) {
        Object rawVariants = product.get("variants");
        if (!(rawVariants instanceof List<?> variants)) {
            return null;
        }
        String normalizedSku = sku.trim().toLowerCase(Locale.ROOT);
        return variants.stream()
            .filter(Map.class::isInstance)
            .map(Map.class::cast)
            .filter(variant -> normalizedSku.equals(text(variant, "sku").toLowerCase(Locale.ROOT)))
            .findFirst()
            .orElse(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> primaryAvailabilityVariant(Map<String, Object> product) {
        Object rawVariants = product.get("variants");
        if (!(rawVariants instanceof List<?> variants)) {
            return null;
        }
        Map<String, Object> first = null;
        for (Object rawVariant : variants) {
            if (!(rawVariant instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> variant = (Map<String, Object>) map;
            if (first == null) {
                first = variant;
            }
            if (Boolean.TRUE.equals(variant.get("availableForSale"))) {
                return variant;
            }
        }
        return first;
    }

    private Map<String, Object> toProductItem(String shopDomain, Map<String, Object> node) {
        List<Map<String, Object>> variants = extractVariants(node);
        Map<String, Object> primaryVariant = variants.stream().findFirst().orElse(Map.of());
        ShopifyProductReviewSignals.ReviewSignalSummary reviewSignals = ShopifyProductReviewSignals.summary(node);
        LinkedHashMap<String, Object> item = new LinkedHashMap<>();
        item.put("id", text(node, "id"));
        item.put("title", text(node, "title"));
        item.put("handle", text(node, "handle"));
        item.put("vendor", text(node, "vendor"));
        item.put("productType", text(node, "productType"));
        item.put("description", sanitizeRichText(text(node, "descriptionHtml")));
        item.put("updatedAt", text(node, "updatedAt"));
        item.put("storefrontUrl", storefrontUrl(shopDomain, text(node, "handle")));
        item.put("available", variants.stream().anyMatch(variant -> Boolean.TRUE.equals(variant.get("availableForSale"))));
        item.put("primarySku", text(primaryVariant, "sku"));
        item.put("price", primaryVariant.get("price"));
        item.put("inventoryQuantity", primaryVariant.get("inventoryQuantity"));
        item.put("reviewSignalsPresent", reviewSignals.present());
        if (reviewSignals.provider() != null) {
            item.put("reviewProvider", reviewSignals.provider());
        }
        if (reviewSignals.averageRatingText() != null) {
            item.put("reviewAverage", reviewSignals.averageRatingText());
        }
        if (reviewSignals.reviewCount() != null) {
            item.put("reviewCount", reviewSignals.reviewCount());
        }
        item.put("variants", variants);
        return item;
    }

    private Map<String, Object> productRelationshipDocument(Map<String, Object> product) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handle", product.get("handle"));
        metadata.put("vendor", product.get("vendor"));
        metadata.put("productType", product.get("productType"));
        metadata.put("available", product.get("available"));
        metadata.put("price", product.get("price"));
        metadata.put("primarySku", product.get("primarySku"));
        metadata.put("inventoryQuantity", product.get("inventoryQuantity"));
        metadata.put("storefrontUrl", product.get("storefrontUrl"));
        metadata.put("reviewSignalsPresent", product.get("reviewSignalsPresent"));
        if (product.containsKey("reviewProvider")) {
            metadata.put("reviewProvider", product.get("reviewProvider"));
        }
        if (product.containsKey("reviewAverage")) {
            metadata.put("reviewAverage", product.get("reviewAverage"));
        }
        if (product.containsKey("reviewCount")) {
            metadata.put("reviewCount", product.get("reviewCount"));
        }
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("id", product.get("id"));
        document.put("entityType", "product");
        document.put("title", product.get("title"));
        document.put("content", product.get("description"));
        document.put("metadata", metadata);
        document.put("sourceUrl", product.get("storefrontUrl"));
        return document;
    }

    private Map<String, Object> policyRelationshipDocument(Map<String, Object> policy) {
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("type", policy.get("type"));
        metadata.put("updatedAt", policy.get("updatedAt"));
        LinkedHashMap<String, Object> document = new LinkedHashMap<>();
        document.put("id", policy.get("id"));
        document.put("entityType", "policy");
        document.put("title", policy.get("title"));
        document.put("content", policy.get("body"));
        document.put("metadata", metadata);
        document.put("sourceUrl", policy.get("url"));
        return document;
    }

    private List<Map<String, Object>> extractVariants(Map<String, Object> node) {
        Map<String, Object> variants = requireMap(node.get("variants"));
        List<?> rawNodes = requireList(variants.get("nodes"));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object rawNode : rawNodes) {
            Map<String, Object> variant = requireMap(rawNode);
            LinkedHashMap<String, Object> item = new LinkedHashMap<>();
            item.put("id", text(variant, "id"));
            item.put("title", text(variant, "title"));
            item.put("sku", text(variant, "sku"));
            item.put("price", text(variant, "price"));
            item.put("availableForSale", Boolean.TRUE.equals(variant.get("availableForSale")));
            item.put("inventoryQuantity", variant.get("inventoryQuantity"));
            items.add(item);
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requireMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<?> requireList(Object value) {
        return value instanceof List<?> list ? list : List.of();
    }

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

    private String textParam(ShopifyBridgeActionExecuteRequest request, String key) {
        if (request == null || request.params() == null) {
            return null;
        }
        Object value = request.params().get(key);
        return value == null ? null : value.toString().trim();
    }

    private ProductFilters productFilters(ShopifyBridgeActionExecuteRequest request) {
        return new ProductFilters(
            decimalParam(request, "maxPrice"),
            booleanParam(request, "availableOnly")
        );
    }

    private Double decimalParam(ShopifyBridgeActionExecuteRequest request, String key) {
        if (request == null || request.params() == null) {
            return null;
        }
        return numericValue(request.params().get(key));
    }

    private Boolean booleanParam(ShopifyBridgeActionExecuteRequest request, String key) {
        if (request == null || request.params() == null) {
            return null;
        }
        Object value = request.params().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim();
        if (raw.isBlank()) {
            return null;
        }
        if ("true".equalsIgnoreCase(raw) || "yes".equalsIgnoreCase(raw)) {
            return true;
        }
        if ("false".equalsIgnoreCase(raw) || "no".equalsIgnoreCase(raw)) {
            return false;
        }
        return null;
    }

    private List<String> listParam(ShopifyBridgeActionExecuteRequest request, String key) {
        if (request == null || request.params() == null) {
            return List.of();
        }
        Object value = request.params().get(key);
        if (value instanceof List<?> list) {
            return list.stream()
                .map(raw -> raw == null ? null : raw.toString().trim())
                .filter(raw -> raw != null && !raw.isBlank())
                .map(raw -> raw.toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        }
        if (value == null) {
            return List.of();
        }
        String raw = value.toString().trim();
        if (raw.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(raw.split(","))
            .map(String::trim)
            .filter(part -> !part.isBlank())
            .map(part -> part.toLowerCase(Locale.ROOT))
            .distinct()
            .toList();
    }

    private int integerParam(ShopifyBridgeActionExecuteRequest request, String key, int defaultValue, int min, int max) {
        String raw = textParam(request, key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Math.max(min, Math.min(max, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return defaultValue;
        }
    }

    private Integer requiredIntegerParam(ShopifyBridgeActionExecuteRequest request, String key, int min, int max) {
        String raw = textParam(request, key);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            return value < min || value > max ? null : value;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String text(Map<String, Object> node, String field) {
        Object value = node == null ? null : node.get(field);
        return value == null ? "" : value.toString().trim();
    }

    private Double numericValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        String raw = value.toString().trim();
        if (raw.isBlank()) {
            return null;
        }
        String normalized = raw.replaceAll("[^0-9.\\-]", "");
        if (normalized.isBlank() || ".".equals(normalized) || "-".equals(normalized)) {
            return null;
        }
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String sanitizeRichText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
            .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
            .replaceAll("(?is)<script[^>]*>.*?</script>", " ")
            .replaceAll("(?is)<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String storefrontUrl(String shopDomain, String handle) {
        if (handle == null || handle.isBlank()) {
            return "https://" + shopDomain;
        }
        return "https://" + shopDomain + "/products/" + handle.trim();
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String productLookupValue(ShopifyBridgeActionExecuteRequest request) {
        return normalize(firstNonBlank(
            textParam(request, "sku"),
            textParam(request, "handle"),
            textParam(request, "productHandle"),
            textParam(request, "title"),
            textParam(request, "productTitle"),
            textParam(request, "query")
        ));
    }

    private String normalizeComparable(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "";
        }
        return normalized
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("(^-+|-+$)", "");
    }

    private boolean looksLikeSku(String value) {
        String normalized = normalize(value);
        return normalized != null && !normalized.matches(".*\\s+.*");
    }

    private boolean entityTypesContain(List<String> entityTypes, String expected) {
        return entityTypes.stream().anyMatch(type -> expected.equals(type) || (expected + "s").equals(type));
    }

    private boolean containsAny(String value, String... tokens) {
        String normalized = value == null ? "" : value.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (normalized.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String shopperSessionId(ShopifyBridgeActionExecuteRequest request) {
        if (request == null) {
            return null;
        }
        if (request.trace() != null) {
            Object direct = request.trace().get("sessionId");
            if (direct != null && !direct.toString().isBlank()) {
                return direct.toString().trim();
            }
            Object authContext = request.trace().get("authContext");
            if (authContext instanceof Map<?, ?> map) {
                Object session = map.get("sessionId");
                if (session != null && !session.toString().isBlank()) {
                    return session.toString().trim();
                }
            }
        }
        return firstNonBlank(textParam(request, "shopperSessionId"), request.idempotencyKey());
    }

    private String numericShopifyId(String value) {
        String normalized = normalize(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.chars().allMatch(Character::isDigit)) {
            return normalized;
        }
        int slash = normalized.lastIndexOf('/');
        if (slash >= 0 && slash + 1 < normalized.length()) {
            String tail = normalized.substring(slash + 1);
            if (!tail.isBlank() && tail.chars().allMatch(Character::isDigit)) {
                return tail;
            }
        }
        return null;
    }

    private boolean policyMatches(Map<String, Object> policy, String query) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery == null) {
            return true;
        }
        boolean knownIntent = policyQueryHasKnownIntent(normalizedQuery);
        for (String needle : policySearchNeedles(normalizedQuery)) {
            if (policyTypeOrTitleMatches(policy, needle)
                || (!knownIntent && policyFieldMatches(policy, "body", needle))) {
                return true;
            }
        }
        return false;
    }

    private boolean policyQueryHasKnownIntent(String query) {
        return containsAny(
            query,
            "refund",
            "return",
            "shipping",
            "ship",
            "delivery",
            "privacy",
            "personal data",
            "data policy",
            "terms",
            "service",
            "legal"
        );
    }

    private List<String> policySearchNeedles(String query) {
        String normalized = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> needles = new LinkedHashSet<>();
        needles.add(normalized);
        if (containsAny(normalized, "refund", "return")) {
            needles.add("refund policy");
            needles.add("refund");
            needles.add("return policy");
            needles.add("return");
        }
        if (containsAny(normalized, "shipping", "ship", "delivery")) {
            needles.add("shipping policy");
            needles.add("shipping");
            needles.add("delivery");
        }
        if (containsAny(normalized, "privacy", "personal data", "data policy")) {
            needles.add("privacy policy");
            needles.add("privacy");
        }
        if (containsAny(normalized, "terms", "service", "legal")) {
            needles.add("terms of service");
            needles.add("terms");
        }
        return List.copyOf(needles);
    }

    private boolean policyTypeOrTitleMatches(Map<String, Object> policy, String needle) {
        String typedNeedle = needle.replace('-', '_').replace(' ', '_');
        String compactNeedle = typedNeedle.replace("_policy", "");
        return policyFieldMatches(policy, "type", typedNeedle)
            || policyFieldMatches(policy, "type", compactNeedle)
            || policyFieldMatches(policy, "title", needle);
    }

    private boolean policyFieldMatches(Map<String, Object> policy, String field, String needle) {
        if (needle == null || needle.isBlank()) {
            return false;
        }
        String value = text(policy, field);
        return !value.isBlank() && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private record ProductLookup(Map<String, Object> product, Map<String, Object> variant, String lookupMethod) {
    }

    private record ProductRelationshipSearch(List<Map<String, Object>> items,
                                             List<String> queries,
                                             boolean fallbackToRecentCatalog) {
    }

    private record ProductFilters(Double maxPrice, Boolean availableOnly) {

        private boolean hasAny() {
            return maxPrice != null || availableOnly != null;
        }

        private Map<String, Object> toMap() {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            if (maxPrice != null) {
                out.put("maxPrice", maxPrice);
            }
            if (availableOnly != null) {
                out.put("availableOnly", availableOnly);
            }
            return Map.copyOf(out);
        }
    }

}
