package com.ai.fabric.realapps.chat.orders.action;

import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.service.PurchaseOrderService;
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
    name = "cancel_purchase_order",
    description = "Cancel an existing purchase order by order number (PO-...) or by order id",
    category = "commerce",
    requiresConfirmation = true
)
@RequiredArgsConstructor
@Slf4j
public class CancelPurchaseOrderActionHandler {

    private final PurchaseOrderService purchaseOrderService;

    @ActionConfirmation
    public String confirm(
        @Param(value = "orderNumber", description = "Order number (PO-...)", required = true) String orderNumber,
        @Param(value = "orderId", description = "Order id (numeric)") Long orderId
    ) {
        if (orderId != null) {
            return "Cancel order " + orderId + "?";
        }
        if (StringUtils.hasText(orderNumber)) {
            return "Cancel order " + orderNumber.trim() + "?";
        }
        return "Cancel order?";
    }

    @ActionExecute
    public ActionResult execute(
        @Param(value = "orderNumber", description = "Order number (PO-...)", required = true) String orderNumber,
        @Param(value = "orderId", description = "Order id (numeric)") Long orderId,
        ActionContext context
    ) {
        String userId = context != null ? context.userId() : null;
        try {
            String orderRef = orderId != null ? String.valueOf(orderId) : orderNumber;
            PurchaseOrder cancelled = purchaseOrderService.cancelForUser(orderRef, userId);
            return ActionResult.builder()
                .success(true)
                .message("Order cancelled")
                .data(Map.of(
                    "orderId", cancelled.getId(),
                    "orderNumber", cancelled.getOrderNumber(),
                    "status", cancelled.getStatus() != null ? cancelled.getStatus().name() : null
                ))
                .build();
        } catch (Exception e) {
            log.error("Cancel purchase order failed for user {}", userId, e);
            return ActionResult.builder()
                .success(false)
                .message("Failed to cancel purchase order: " + e.getMessage())
                .errorCode("CANCEL_PURCHASE_ORDER_FAILED")
                .build();
        }
    }
}
