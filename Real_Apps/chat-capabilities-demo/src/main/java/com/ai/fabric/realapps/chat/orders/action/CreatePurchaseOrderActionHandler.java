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
public class CreatePurchaseOrderActionHandler implements ActionHandler {

    private final PurchaseOrderService purchaseOrderService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("create_purchase_order")
            .description("Create a purchase order for a product SKU")
            .category("commerce")
            .parameters(Map.of(
                "sku", "Product SKU (required)",
                "quantity", "Quantity (required)",
                "shippingAddress", "Shipping address (required)",
                "email", "Customer email (required)"
            ))
            .requiredParameters(Set.of("sku", "quantity", "shippingAddress", "email"))
            .build();
    }

    @Override
    public boolean validateActionAllowed(String userId) {
        return StringUtils.hasText(userId);
    }

    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String sku = stringParam(params, "sku");
        Integer quantity = intParam(params, "quantity");
        if (StringUtils.hasText(sku) && quantity != null) {
            return "Create purchase order for " + quantity + " × " + sku + "?";
        }
        return "Create purchase order?";
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String sku = requiredString(params, "sku");
        int quantity = requiredInt(params, "quantity");
        String shippingAddress = requiredString(params, "shippingAddress");
        String email = requiredString(params, "email");

        PurchaseOrder created = purchaseOrderService.createPurchaseOrder(
            userId,
            sku,
            quantity,
            shippingAddress,
            email
        );

        return ActionResult.builder()
            .success(true)
            .message("Purchase order created")
            .data(Map.of(
                "orderId", created.getId(),
                "orderNumber", created.getOrderNumber(),
                "sku", created.getSku(),
                "quantity", created.getQuantity(),
                "totalPrice", created.getTotalPrice(),
                "currency", created.getCurrency(),
                "status", created.getStatus() != null ? created.getStatus().name() : null
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Create purchase order failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to create purchase order: " + e.getMessage())
            .errorCode("CREATE_PURCHASE_ORDER_FAILED")
            .build();
    }

    private String requiredString(Map<String, Object> params, String key) {
        String value = stringParam(params, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value.trim();
    }

    private int requiredInt(Map<String, Object> params, String key) {
        Integer value = intParam(params, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private String stringParam(Map<String, Object> params, String key) {
        Object raw = params != null ? params.get(key) : null;
        return raw != null ? raw.toString() : null;
    }

    private Integer intParam(Map<String, Object> params, String key) {
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
        return null;
    }
}

