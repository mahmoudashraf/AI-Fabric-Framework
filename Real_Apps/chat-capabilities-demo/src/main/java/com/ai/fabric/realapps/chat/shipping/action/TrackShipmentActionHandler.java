package com.ai.fabric.realapps.chat.shipping.action;

import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.service.PurchaseOrderService;
import com.ai.fabric.realapps.chat.shipping.domain.Shipment;
import com.ai.fabric.realapps.chat.shipping.service.ShipmentService;
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
public class TrackShipmentActionHandler implements ActionHandler {

    private final PurchaseOrderService purchaseOrderService;
    private final ShipmentService shipmentService;

    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("track_shipment")
            .description("Track the latest shipment for an order (by order number or id)")
            .category("commerce")
            .parameters(Map.of(
                "orderNumber", "Order number (PO-...) or order id (required)"
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
        return "Fetch shipment tracking details?";
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        String orderNumberOrId = requiredString(params, "orderNumber");
        PurchaseOrder order = purchaseOrderService.resolveForUser(orderNumberOrId, userId);

        Shipment shipment = shipmentService.findLatestForOrder(order.getId())
            .orElse(null);

        if (shipment == null) {
            return ActionResult.builder()
                .success(true)
                .message("No shipment found for this order yet")
                .data(Map.of(
                    "orderId", order.getId(),
                    "orderNumber", order.getOrderNumber()
                ))
                .build();
        }

        return ActionResult.builder()
            .success(true)
            .message("Shipment status")
            .data(Map.of(
                "orderId", order.getId(),
                "orderNumber", order.getOrderNumber(),
                "shipmentId", shipment.getId(),
                "carrier", shipment.getCarrier(),
                "trackingNumber", shipment.getTrackingNumber(),
                "status", shipment.getStatus() != null ? shipment.getStatus().name() : null,
                "eta", shipment.getEta()
            ))
            .build();
    }

    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Track shipment failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Failed to track shipment: " + e.getMessage())
            .errorCode("TRACK_SHIPMENT_FAILED")
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

