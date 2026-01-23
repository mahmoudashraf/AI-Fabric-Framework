package com.ai.fabric.realapps.chat.orders.action;

import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.service.PurchaseOrderService;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
@Slf4j
public class GetOrderDetailsActionHandler implements ActionHandler {

    private final PurchaseOrderService purchaseOrderService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("get_order_details")
            .description("Get purchase order details by order number (PO-...) or numeric id")
            .category("commerce")
            .parameters(Map.of(
                "orderNumberOrId", "Order number (PO-...) or numeric id (required)"
            ))
            .requiredParameters(Set.of("orderNumberOrId"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String ref = params != null && params.get("orderNumberOrId") != null ? params.get("orderNumberOrId").toString() : null;
        if (StringUtils.hasText(ref)) {
            return "Fetch order details for " + ref.trim() + "?";
        }
        return "Fetch order details?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String ref = requiredString(params, "orderNumberOrId");
        PurchaseOrder order = purchaseOrderService.getForUserByReference(userId, ref);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNumber", order.getOrderNumber());
        payload.put("status", order.getStatus() != null ? order.getStatus().name() : null);
        payload.put("sku", order.getSku());
        payload.put("productName", order.getProductName());
        payload.put("quantity", order.getQuantity());
        payload.put("unitPrice", order.getUnitPrice());
        payload.put("totalPrice", order.getTotalPrice());
        payload.put("currency", order.getCurrency());
        payload.put("shippingAddress", order.getShippingAddress());
        payload.put("createdAt", order.getCreatedAt());

        return ActionResult.builder()
            .success(true)
            .message("Order details")
            .data(payload)
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Get order details failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to fetch order details: " + (e != null ? e.getMessage() : "unknown"))
            .errorCode("GET_ORDER_DETAILS_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        String value = raw != null ? raw.toString() : null;
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }
}
