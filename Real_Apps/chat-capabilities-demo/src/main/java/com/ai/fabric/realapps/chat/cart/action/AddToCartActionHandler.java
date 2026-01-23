package com.ai.fabric.realapps.chat.cart.action;

import com.ai.fabric.realapps.chat.cart.domain.Cart;
import com.ai.fabric.realapps.chat.cart.service.CartService;
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
public class AddToCartActionHandler implements ActionHandler {

    private final CartService cartService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("add_to_cart")
            .description("Add a product SKU to my active cart")
            .category("commerce")
            .parameters(Map.of(
                "sku", "Product SKU (required)",
                "quantity", "Quantity (required)"
            ))
            .requiredParameters(Set.of("sku", "quantity"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String sku = stringParam(params, "sku");
        Integer qty = intParam(params, "quantity");
        if (StringUtils.hasText(sku) && qty != null) {
            return "Add " + qty + " × " + sku.trim() + " to your cart?";
        }
        return "Add item to cart?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String sku = requiredString(params, "sku");
        int quantity = requiredInt(params, "quantity");

        Cart cart = cartService.addItem(userId, sku, quantity);

        return ActionResult.builder()
            .success(true)
            .message("Added to cart")
            .data(Map.of(
                "cartId", cart.getId(),
                "total", cart.getTotal(),
                "currency", cart.getCurrency(),
                "itemsCount", cart.getItems() != null ? cart.getItems().size() : 0
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Add to cart failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to add to cart: " + e.getMessage())
            .errorCode("ADD_TO_CART_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        String value = stringParam(params, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private int requiredInt(Map<String, Object> params, String key) {
        Integer value = intParam(params, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
    }

    private Integer intParam(Map<String, Object> params, String key) {
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
        return null;
    }
}

