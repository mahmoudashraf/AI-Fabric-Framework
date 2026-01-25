package com.ai.fabric.realapps.chat.cart.action;

import com.ai.fabric.realapps.chat.cart.domain.Cart;
import com.ai.fabric.realapps.chat.cart.service.CartService;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.annotation.AIAction;
import com.ai.infrastructure.intent.action.annotation.ActionConfirmation;
import com.ai.infrastructure.intent.action.annotation.ActionExecute;
import com.ai.infrastructure.intent.action.annotation.Param;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@AIAction(
    name = "add_to_cart",
    description = "Add a product SKU to my active cart",
    category = "commerce",
    requiresConfirmation = true
)
@RequiredArgsConstructor
@Slf4j
public class AddToCartActionHandler {

    private final CartService cartService;

    @ActionConfirmation
    public String confirm(
        @Param(value = "sku", description = "Product SKU", required = true) String sku,
        @Param(value = "quantity", description = "Quantity", required = true, min = 1) Integer quantity
    ) {
        Integer qty = quantity;
        if (StringUtils.hasText(sku) && qty != null) {
            return "Add " + qty + " × " + sku.trim() + " to your cart?";
        }
        return "Add item to cart?";
    }

    @ActionExecute
    public ActionResult execute(
        @Param(value = "sku", description = "Product SKU", required = true) String sku,
        @Param(value = "quantity", description = "Quantity", required = true, min = 1) Integer quantity,
        ActionContext context
    ) {
        try {
            String userId = context != null ? context.userId() : null;
            Cart cart = cartService.addItem(userId, sku, quantity != null ? quantity : 1);
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
        } catch (Exception e) {
            String userId = context != null ? context.userId() : null;
            log.error("Add to cart failed for user {}", userId, e);
            return ActionResult.builder()
                .success(false)
                .message("Failed to add to cart: " + e.getMessage())
                .errorCode("ADD_TO_CART_FAILED")
                .build();
        }
    }
}
