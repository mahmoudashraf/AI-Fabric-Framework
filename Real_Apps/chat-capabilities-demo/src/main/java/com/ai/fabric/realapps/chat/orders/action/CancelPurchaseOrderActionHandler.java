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
public class CancelPurchaseOrderActionHandler implements ActionHandler {

    private final PurchaseOrderService purchaseOrderService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_purchase_order")
            .description("Cancel an existing purchase order by order number (PO-...) or by order id")
            .category("commerce")
            .parameters(Map.of(
                "orderNumber", "Order number (PO-...) (optional if orderId is provided)",
                "orderId", "Order id (numeric) (optional if orderNumber is provided)"
            ))
            .requiredParameters(Set.of("orderNumber"))
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
            return "Cancel order " + orderId.trim() + "?";
        }
        String orderNumber = stringParam(params, "orderNumber");
        if (StringUtils.hasText(orderNumber)) {
            return "Cancel order " + orderNumber.trim() + "?";
        }
        return "Cancel order?";
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String orderId = stringParam(params, "orderId");
        String orderNumber = stringParam(params, "orderNumber");
        String orderNumberOrId = StringUtils.hasText(orderId) ? orderId : requiredString(params, "orderNumber");

        PurchaseOrder cancelled = purchaseOrderService.cancelForUser(orderNumberOrId, userId);

        return ActionResult.builder()
            .success(true)
            .message("Order cancelled")
            .data(Map.of(
                "orderId", cancelled.getId(),
                "orderNumber", cancelled.getOrderNumber(),
                "status", cancelled.getStatus() != null ? cancelled.getStatus().name() : null
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Cancel purchase order failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to cancel purchase order: " + e.getMessage())
            .errorCode("CANCEL_PURCHASE_ORDER_FAILED")
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
