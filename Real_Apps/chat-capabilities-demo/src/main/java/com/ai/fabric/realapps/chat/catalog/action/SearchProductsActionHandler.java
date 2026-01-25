package com.ai.fabric.realapps.chat.catalog.action;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@AIAction(
    name = "search_products",
    description = "Search products by keyword (sku/name/description/category/tags)",
    category = "commerce",
    requiresConfirmation = false
)
@RequiredArgsConstructor
@Slf4j
public class SearchProductsActionHandler {

    private final ProductService productService;

    @ActionExecute
    public ActionResult execute(
        @Param(value = "query", description = "Search query", required = true) String query,
        @Param(value = "limit", description = "Max number of products to return") Integer limit,
        ActionContext context
    ) {
        try {
            int effectiveLimit = limit != null ? limit : 10;

            // Prefer deterministic repository search without embeddings to keep this action reliable
            // even when embedding providers are disabled.
            List<Product> results = productService.list(500).stream()
                .filter(p -> matches(query, p))
                .limit(Math.max(1, Math.min(effectiveLimit, 50)))
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
        } catch (Exception e) {
            String userId = context != null ? context.userId() : null;
            log.error("Search products failed for user {}", userId, e);
            return ActionResult.builder()
                .success(false)
                .message("Failed to search products: " + (e != null ? e.getMessage() : "unknown"))
                .errorCode("SEARCH_PRODUCTS_FAILED")
                .build();
        }
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

    // Uses deterministic matching; no embeddings required.
}
