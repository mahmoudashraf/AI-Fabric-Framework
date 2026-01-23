package com.ai.fabric.realapps.chat.cart.action;

import com.ai.fabric.realapps.chat.cart.domain.Cart;
import com.ai.fabric.realapps.chat.cart.domain.CartItem;
import com.ai.fabric.realapps.chat.cart.service.CartService;
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
public class ViewCartActionHandler implements ActionHandler {

    private final CartService cartService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("view_cart")
            .description("View my current active cart and its items")
            .category("commerce")
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Fetch your current cart?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        Cart cart = cartService.getOrCreateActiveCart(userId);
        List<CartItem> items = cart.getItems() != null ? cart.getItems() : List.of();

        List<Map<String, Object>> outItems = items.stream()
            .filter(i -> i != null)
            .map(i -> Map.<String, Object>of(
                "id", i.getId(),
                "sku", i.getSku(),
                "productName", i.getProductName(),
                "quantity", i.getQuantity(),
                "unitPrice", i.getUnitPrice(),
                "totalPrice", i.getTotalPrice()
            ))
            .toList();

        return ActionResult.builder()
            .success(true)
            .message(outItems.isEmpty() ? "Your cart is empty" : "Your active cart")
            .data(Map.of(
                "cartId", cart.getId(),
                "status", cart.getStatus() != null ? cart.getStatus().name() : null,
                "currency", cart.getCurrency(),
                "couponCode", cart.getCouponCode(),
                "subtotal", cart.getSubtotal(),
                "discount", cart.getDiscount(),
                "total", cart.getTotal(),
                "items", outItems
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("View cart failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to fetch cart: " + e.getMessage())
            .errorCode("VIEW_CART_FAILED")
            .build();
    }
}

