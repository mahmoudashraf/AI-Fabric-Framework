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
public class ApplyCouponToCartActionHandler implements ActionHandler {

    private final CartService cartService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("apply_coupon_to_cart")
            .description("Apply a coupon code to my active cart")
            .category("commerce")
            .parameters(Map.of(
                "code", "Coupon code (required)"
            ))
            .requiredParameters(Set.of("code"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String code = stringParam(params, "code");
        if (StringUtils.hasText(code)) {
            return "Apply coupon " + code.trim() + " to your cart?";
        }
        return "Apply coupon to cart?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String code = requiredString(params, "code");
        Cart cart = cartService.applyCoupon(userId, code);

        return ActionResult.builder()
            .success(true)
            .message("Coupon applied")
            .data(Map.of(
                "cartId", cart.getId(),
                "couponCode", cart.getCouponCode(),
                "subtotal", cart.getSubtotal(),
                "discount", cart.getDiscount(),
                "total", cart.getTotal(),
                "currency", cart.getCurrency()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Apply coupon failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to apply coupon: " + e.getMessage())
            .errorCode("APPLY_COUPON_FAILED")
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

