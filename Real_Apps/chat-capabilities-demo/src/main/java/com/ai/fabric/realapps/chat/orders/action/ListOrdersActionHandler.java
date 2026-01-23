package com.ai.fabric.realapps.chat.orders.action;

import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.service.PurchaseOrderService;
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
public class ListOrdersActionHandler implements ActionHandler {

    private final PurchaseOrderService purchaseOrderService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("list_orders")
            .description("List my purchase orders")
            .category("commerce")
            .parameters(Map.of(
                "limit", "Max number of orders to return (optional; default 50)"
            ))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Fetch your orders?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        int limit = intParam(params, "limit", 50);
        List<PurchaseOrder> orders = purchaseOrderService.listForUser(userId, limit);

        List<Map<String, Object>> payload = orders.stream()
            .map(o -> Map.<String, Object>of(
                "orderId", o.getId(),
                "orderNumber", o.getOrderNumber(),
                "status", o.getStatus() != null ? o.getStatus().name() : null,
                "sku", o.getSku(),
                "productName", o.getProductName(),
                "quantity", o.getQuantity(),
                "totalPrice", o.getTotalPrice(),
                "currency", o.getCurrency(),
                "createdAt", o.getCreatedAt()
            ))
            .toList();

        return ActionResult.builder()
            .success(true)
            .message(payload.isEmpty() ? "No orders found" : "Orders")
            .data(Map.of("count", payload.size(), "orders", payload))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("List orders failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to list orders: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("LIST_ORDERS_FAILED")
            .build();
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
