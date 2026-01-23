package com.ai.fabric.realapps.chat.orders.action;

import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.service.PurchaseOrderService;
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
public class ChangeDeliveryAddressActionHandler implements ActionHandler {

    private final PurchaseOrderService purchaseOrderService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("change_delivery_address")
            .description("Change the delivery (shipping) address for the current order (or a specific order number)")
            .category("commerce")
            .parameters(Map.of(
                "shippingAddress", "New shipping address (required)",
                "orderNumber", "Order number (PO-...) (optional if orderId is provided; defaults to current order)",
                "orderId", "Order id (numeric) (optional if orderNumber is provided; defaults to current order)"
            ))
            .requiredParameters(Set.of("shippingAddress"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String orderId = stringParam(params, "orderId");
        if (StringUtils.hasText(orderId)) {
            return "Change delivery address for order " + orderId.trim() + "?";
        }
        String orderNumber = stringParam(params, "orderNumber");
        if (StringUtils.hasText(orderNumber)) {
            return "Change delivery address for order " + orderNumber.trim() + "?";
        }
        return "Change delivery address for your current order?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String shippingAddress = requiredString(params, "shippingAddress");
        String orderId = stringParam(params, "orderId");
        String orderNumber = StringUtils.hasText(orderId) ? orderId : stringParam(params, "orderNumber");

        PurchaseOrder updated = purchaseOrderService.updateDeliveryAddressForUser(userId, orderNumber, shippingAddress);

        return ActionResult.builder()
            .success(true)
            .message("Delivery address updated")
            .data(Map.of(
                "orderId", updated.getId(),
                "orderNumber", updated.getOrderNumber(),
                "shippingAddress", updated.getShippingAddress(),
                "status", updated.getStatus() != null ? updated.getStatus().name() : null
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Change delivery address failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to change delivery address: " + e.getMessage())
            .errorCode("CHANGE_DELIVERY_ADDRESS_FAILED")
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
