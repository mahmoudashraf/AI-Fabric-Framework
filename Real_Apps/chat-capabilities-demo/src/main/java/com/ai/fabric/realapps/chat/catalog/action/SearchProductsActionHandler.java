package com.ai.fabric.realapps.chat.catalog.action;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class SearchProductsActionHandler implements ActionHandler {

    private final ProductService productService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("search_products")
            .description("Search products by keyword (sku/name/description/category/tags)")
            .category("commerce")
            .parameters(Map.of(
                "query", "Search query (required)",
                "limit", "Max number of products to return (optional; default 10)"
            ))
            .requiredParameters(Set.of("query"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String query = params != null && params.get("query") != null ? params.get("query").toString() : null;
        if (StringUtils.hasText(query)) {
            return "Search products for '" + query.trim() + "'?";
        }
        return "Search products?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String query = requiredString(params, "query");
        int limit = intParam(params, "limit", 10);

        // Prefer deterministic repository search without embeddings to keep this action reliable
        // even when embedding providers are disabled.
        List<Product> results = productService.list(500).stream()
            .filter(p -> matches(query, p))
            .limit(Math.max(1, Math.min(limit, 50)))
            .toList();

        List<Map<String, Object>> payload = results.stream()
            .map(p -> Map.<String, Object>of(
                "id", p.getId(),
                "sku", p.getSku(),
                "name", p.getName(),
                "category", p.getCategory(),
                "tags", p.getTags(),
                "price", p.getPrice(),
                "currency", p.getCurrency(),
                "inStockQty", p.getInStockQty()
            ))
            .toList();

        return ActionResult.builder()
            .success(true)
            .message(payload.isEmpty() ? "No matching products found" : "Matching products")
            .data(Map.of("count", payload.size(), "products", payload))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Search products failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to search products: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("SEARCH_PRODUCTS_FAILED")
            .build();
    }

    private boolean matches(String query, Product product) {
        if (product == null) {
            return false;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        return contains(product.getSku(), q)
            || contains(product.getName(), q)
            || contains(product.getDescription(), q)
            || contains(product.getCategory(), q)
            || contains(product.getTags(), q);
    }

    private boolean contains(String value, String lowerQuery) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(lowerQuery)) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(lowerQuery);
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        String value = raw != null ? raw.toString() : null;
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object raw = params != null ? params.get(key) : null;
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw != null) {
            try {
                return Integer.parseInt(raw.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return defaultValue;
    }
}
