package com.ai.fabric.product.shopify.bridge.action.service;

import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionExecuteRequest;
import com.ai.fabric.product.shopify.bridge.action.model.ShopifyBridgeActionResult;
import com.ai.fabric.product.shopify.bridge.client.shopify.ShopifyAdminGraphqlClient;
import com.ai.fabric.product.shopify.bridge.install.model.ShopifyBridgeCredentialAcquisition;
import com.ai.fabric.product.shopify.bridge.install.service.ShopifyBridgeInstallCredentialService;
import com.ai.fabric.product.shopify.bridge.store.service.ShopifyProductReviewSignals;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

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

    public ShopifyBridgeActionExecutionService(ShopifyBridgeInstallCredentialService installCredentialService,
                                               ShopifyAdminGraphqlClient shopifyAdminGraphqlClient) {
        this.installCredentialService = installCredentialService;
        this.shopifyAdminGraphqlClient = shopifyAdminGraphqlClient;
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
            default -> ShopifyBridgeActionResult.failure("ACTION_NOT_SUPPORTED", "Action is not supported.");
        };
    }

    private ShopifyBridgeActionResult listProducts(ShopifyBridgeCredentialAcquisition acquisition,
                                                   ShopifyBridgeActionExecuteRequest request) {
        List<Map<String, Object>> items = fetchProducts(acquisition, textParam(request, "query"));
        return ShopifyBridgeActionResult.ok(
            items.isEmpty() ? "No products found." : "Products",
            Map.of("items", items, "count", items.size())
        );
    }

    private ShopifyBridgeActionResult searchProducts(ShopifyBridgeCredentialAcquisition acquisition,
                                                     ShopifyBridgeActionExecuteRequest request) {
        String query = textParam(request, "query");
        List<Map<String, Object>> items = fetchProducts(acquisition, query);
        return ShopifyBridgeActionResult.ok(
            items.isEmpty() ? "No matching products found." : "Products",
            Map.of("items", items, "count", items.size(), "query", query)
        );
    }

    private ShopifyBridgeActionResult getProductDetails(ShopifyBridgeCredentialAcquisition acquisition,
                                                        ShopifyBridgeActionExecuteRequest request) {
        String sku = normalize(textParam(request, "sku"));
        if (sku == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.sku is required.");
        }
        Map<String, Object> product = findProductBySku(acquisition, sku);
        if (product == null) {
            return ShopifyBridgeActionResult.failure("NOT_FOUND", "No product was found for SKU " + sku + ".");
        }
        return ShopifyBridgeActionResult.ok("Product details", Map.of("product", product, "sku", sku));
    }

    private ShopifyBridgeActionResult checkAvailability(ShopifyBridgeCredentialAcquisition acquisition,
                                                        ShopifyBridgeActionExecuteRequest request) {
        String sku = normalize(textParam(request, "sku"));
        if (sku == null) {
            return ShopifyBridgeActionResult.failure("INVALID_REQUEST", "params.sku is required.");
        }
        Map<String, Object> product = findProductBySku(acquisition, sku);
        if (product == null) {
            return ShopifyBridgeActionResult.failure("NOT_FOUND", "No product was found for SKU " + sku + ".");
        }
        Map<String, Object> variant = firstMatchingVariant(product, sku);
        if (variant == null) {
            return ShopifyBridgeActionResult.failure("NOT_FOUND", "No variant was found for SKU " + sku + ".");
        }
        LinkedHashMap<String, Object> availability = new LinkedHashMap<>();
        availability.put("sku", sku);
        availability.put("productTitle", product.get("title"));
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
        String query = normalize(textParam(request, "query"));
        int limit = integerParam(request, "limit", 5, 1, 10);
        List<Map<String, Object>> policies = fetchPolicies(acquisition);
        List<Map<String, Object>> result = policies.stream().limit(limit).toList();
        return ShopifyBridgeActionResult.ok(
            result.isEmpty() ? "No policies found." : "Policies",
            Map.of("items", result, "count", result.size(), "query", query)
        );
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

    private Map<String, Object> findProductBySku(ShopifyBridgeCredentialAcquisition acquisition, String sku) {
        String query = "sku:" + sku;
        return fetchProducts(acquisition, query).stream()
            .filter(item -> firstMatchingVariant(item, sku) != null)
            .findFirst()
            .orElse(null);
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

    private String text(Map<String, Object> node, String field) {
        Object value = node == null ? null : node.get(field);
        return value == null ? "" : value.toString().trim();
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

}
