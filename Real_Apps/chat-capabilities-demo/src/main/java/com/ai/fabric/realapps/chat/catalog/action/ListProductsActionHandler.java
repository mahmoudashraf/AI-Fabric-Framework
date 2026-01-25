package com.ai.fabric.realapps.chat.catalog.action;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@AIAction(
    name = "list_products",
    description = "List products from the catalog",
    category = "commerce",
    requiresConfirmation = false
)
@RequiredArgsConstructor
@Slf4j
public class ListProductsActionHandler {

    private final ProductService productService;

    @ActionExecute
    public ActionResult execute(@Param(value = "limit", description = "Max number of products to return") Integer limit,
                                ActionContext context) {
        try {
            int effectiveLimit = limit != null ? limit : 50;
            List<Product> products = productService.list(effectiveLimit);

            List<Map<String, Object>> payload = products.stream()
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
                .message(payload.isEmpty() ? "No products found" : "Products")
                .data(Map.of("count", payload.size(), "products", payload))
                .build();
        } catch (Exception e) {
            String userId = context != null ? context.userId() : null;
            log.error("List products failed for user {}", userId, e);
            return ActionResult.builder()
                .success(false)
                .message("Failed to list products: " + (e != null ? e.getMessage() : "unknown"))
                .errorCode("LIST_PRODUCTS_FAILED")
                .build();
        }
    }
}
