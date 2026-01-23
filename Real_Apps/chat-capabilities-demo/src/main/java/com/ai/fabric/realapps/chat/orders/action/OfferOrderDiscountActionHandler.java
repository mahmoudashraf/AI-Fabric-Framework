package com.ai.fabric.realapps.chat.orders.action;

import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class OfferOrderDiscountActionHandler implements ActionHandler {

    public static final String ACTION_NAME = "offer_order_discount";

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Offer a retention discount to keep an order active instead of cancelling it")
            .category("commerce")
            .parameters(Map.of(
                "orderNumber", "Order number (PO-...) (optional)",
                "orderId", "Order id (numeric) (optional)",
                "discountPercent", "Discount percent (optional; default 10)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        int percent = intParam(params, "discountPercent", 10);
        return "Apply a " + percent + "% discount to keep your order instead of cancelling?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        int percent = intParam(params, "discountPercent", 10);
        String orderNumber = stringParam(params, "orderNumber");
        String orderId = stringParam(params, "orderId");

        String ref = StringUtils.hasText(orderNumber) ? orderNumber.trim() : (StringUtils.hasText(orderId) ? orderId.trim() : "your order");
        String coupon = percent >= 15 ? "SAVE15" : "SAVE10";

        log.info("Issued retention offer {}% (coupon={}) for user={} orderRef={}", percent, coupon, userId, ref);

        return ActionResult.builder()
            .success(true)
            .message("Discount offer applied")
            .data(Map.of(
                "orderRef", ref,
                "discountPercent", percent,
                "couponCode", coupon,
                "note", "This demo does not persist discounts to the order record."
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Offer discount failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to apply discount offer: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("OFFER_ORDER_DISCOUNT_FAILED")
            .build();
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
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
