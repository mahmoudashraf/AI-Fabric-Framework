package com.ai.fabric.realapps.chat.catalog.action;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetProductDetailsActionHandler implements ActionHandler {

    private final ProductService productService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("get_product_details")
            .description("Get full product details by SKU")
            .category("commerce")
            .parameters(Map.of(
                "sku", "Product SKU (required)"
            ))
            .requiredParameters(Set.of("sku"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String sku = params != null && params.get("sku") != null ? params.get("sku").toString() : null;
        if (StringUtils.hasText(sku)) {
            return "Fetch product details for " + sku.trim() + "?";
        }
        return "Fetch product details?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String sku = requiredString(params, "sku");
        Product product = productService.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("Product not found for sku: " + sku));

        return ActionResult.builder()
            .success(true)
            .message("Product details")
            .data(Map.of(
                "id", product.getId(),
                "sku", product.getSku(),
                "name", product.getName(),
                "description", product.getDescription(),
                "category", product.getCategory(),
                "tags", product.getTags(),
                "price", product.getPrice(),
                "currency", product.getCurrency(),
                "inStockQty", product.getInStockQty()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Get product details failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to fetch product details: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("GET_PRODUCT_DETAILS_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        String value = raw != null ? raw.toString() : null;
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }
}
