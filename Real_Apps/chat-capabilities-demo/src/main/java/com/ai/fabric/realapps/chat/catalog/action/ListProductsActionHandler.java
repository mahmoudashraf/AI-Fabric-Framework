package com.ai.fabric.realapps.chat.catalog.action;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class ListProductsActionHandler implements ActionHandler {

    private final ProductService productService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("list_products")
            .description("List products from the catalog")
            .category("commerce")
            .parameters(Map.of(
                "limit", "Max number of products to return (optional; default 50)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Fetch products?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        int limit = intParam(params, "limit", 50);
        List<Product> products = productService.list(limit);

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
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("List products failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to list products: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("LIST_PRODUCTS_FAILED")
            .build();
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
