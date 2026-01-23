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
public class RemoveFromCartActionHandler implements ActionHandler {

    private final CartService cartService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("remove_from_cart")
            .description("Remove a product SKU from my active cart")
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
        String sku = stringParam(params, "sku");
        if (StringUtils.hasText(sku)) {
            return "Remove " + sku.trim() + " from your cart?";
        }
        return "Remove item from cart?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String sku = requiredString(params, "sku");
        Cart cart = cartService.removeItem(userId, sku);

        return ActionResult.builder()
            .success(true)
            .message("Removed from cart")
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
        log.error("Remove from cart failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to remove from cart: " + e.getMessage())
            .errorCode("REMOVE_FROM_CART_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        String value = stringParam(params, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
    }
}

