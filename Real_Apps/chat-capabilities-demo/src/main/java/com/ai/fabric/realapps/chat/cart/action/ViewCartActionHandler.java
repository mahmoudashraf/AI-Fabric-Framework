package com.ai.fabric.realapps.chat.cart.action;

import com.ai.fabric.realapps.chat.cart.domain.Cart;
import com.ai.fabric.realapps.chat.cart.domain.CartItem;
import com.ai.fabric.realapps.chat.cart.service.CartService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.List;
import java.util.LinkedHashMap;
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
            .map(i -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", i.getId());
                row.put("sku", i.getSku());
                row.put("productName", i.getProductName());
                row.put("quantity", i.getQuantity());
                row.put("unitPrice", i.getUnitPrice());
                row.put("totalPrice", i.getTotalPrice());
                return row;
            })
            .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cartId", cart.getId());
        data.put("status", cart.getStatus() != null ? cart.getStatus().name() : null);
        data.put("currency", cart.getCurrency());
        data.put("couponCode", cart.getCouponCode());
        data.put("subtotal", cart.getSubtotal());
        data.put("discount", cart.getDiscount());
        data.put("total", cart.getTotal());
        data.put("items", outItems);

        return ActionResult.builder()
            .success(true)
            .message(outItems.isEmpty() ? "Your cart is empty" : "Your active cart")
            .data(data)
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("View cart failed for user {}", userId, e);
        if (e instanceof jakarta.persistence.EntityNotFoundException) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("cartId", null);
            data.put("status", "ACTIVE");
            data.put("currency", "USD");
            data.put("couponCode", null);
            data.put("subtotal", 0);
            data.put("discount", 0);
            data.put("total", 0);
            data.put("items", List.of());

            return ActionResult.builder()
                .success(true)
                .message("Your cart is empty")
                .data(data)
                .build();
        }
        return ActionResult.builder()
            .success(false)
            .message("Failed to fetch cart: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()))
            .errorCode("VIEW_CART_FAILED")
            .build();
    }
}
