package com.ai.fabric.realapps.chat.cart.action;

import com.ai.fabric.realapps.chat.cart.service.CartService;
import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.payments.domain.Payment;
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
public class CheckoutCartActionHandler implements ActionHandler {

    private final CartService cartService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("checkout_cart")
            .description("Checkout my active cart and create an order")
            .category("commerce")
            .parameters(Map.of(
                "shippingAddress", "Shipping address (required)",
                "email", "Customer email (required)",
                "paymentMethod", "Payment method (optional: CARD, PAYPAL, COD)"
            ))
            .requiredParameters(Set.of("shippingAddress", "email"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Checkout your cart and place the order?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String shippingAddress = requiredString(params, "shippingAddress");
        String email = requiredString(params, "email");
        String paymentMethodRaw = stringParam(params, "paymentMethod");
        Payment.Method method = parsePaymentMethod(paymentMethodRaw);

        PurchaseOrder order = cartService.checkout(userId, shippingAddress, email, method);

        return ActionResult.builder()
            .success(true)
            .message("Checkout complete")
            .data(Map.of(
                "orderId", order.getId(),
                "orderNumber", order.getOrderNumber(),
                "totalPrice", order.getTotalPrice(),
                "currency", order.getCurrency(),
                "status", order.getStatus() != null ? order.getStatus().name() : null
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Checkout cart failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to checkout cart: " + e.getMessage())
            .errorCode("CHECKOUT_CART_FAILED")
            .build();
    }

    private Payment.Method parsePaymentMethod(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Payment.Method.CARD;
        }
        try {
            return Payment.Method.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Payment.Method.CARD;
        }
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

