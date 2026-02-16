package com.ai.fabric.realapps.chat.connector.service;

import com.ai.fabric.realapps.chat.accounts.domain.Account;
import com.ai.fabric.realapps.chat.accounts.service.AccountService;
import com.ai.fabric.realapps.chat.addresses.domain.Address;
import com.ai.fabric.realapps.chat.addresses.service.AddressService;
import com.ai.fabric.realapps.chat.cart.domain.Cart;
import com.ai.fabric.realapps.chat.cart.domain.CartItem;
import com.ai.fabric.realapps.chat.cart.service.CartService;
import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.service.PurchaseOrderService;
import com.ai.fabric.realapps.chat.payments.domain.Payment;
import com.ai.fabric.realapps.chat.reviews.domain.Review;
import com.ai.fabric.realapps.chat.reviews.service.ReviewService;
import com.ai.fabric.realapps.chat.returns.domain.ReturnRequest;
import com.ai.fabric.realapps.chat.returns.service.ReturnRequestService;
import com.ai.fabric.realapps.chat.shipping.domain.Shipment;
import com.ai.fabric.realapps.chat.shipping.service.ShipmentService;
import com.ai.fabric.realapps.chat.support.domain.SupportTicket;
import com.ai.fabric.realapps.chat.support.service.SupportTicketService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectorActionDispatcher {

    private static final String KEY_ITEMS = "_items";
    private static final String KEY_COUNT = "_count";

    private static final TypeReference<List<CartItemInput>> CART_ITEM_INPUTS = new TypeReference<>() { };

    private final ObjectMapper objectMapper;

    private final ProductService productService;
    private final CartService cartService;
    private final PurchaseOrderService purchaseOrderService;
    private final AddressService addressService;
    private final AccountService accountService;
    private final SupportTicketService supportTicketService;
    private final ReviewService reviewService;
    private final ShipmentService shipmentService;
    private final ReturnRequestService returnRequestService;

    public Map<String, Object> dispatch(String actionId, Map<String, Object> params, Trace trace) {
        if (!StringUtils.hasText(actionId)) {
            return failure("INVALID_REQUEST", "actionId is required");
        }
        String name = actionId.trim();
        Map<String, Object> safeParams = params != null ? params : Map.of();

        try {
            return switch (name) {
                case "list_products" -> listProducts(safeParams);
                case "search_products" -> searchProducts(safeParams);
                case "get_product_details" -> getProductDetails(safeParams);

                case "view_cart" -> viewCart(trace);
                case "add_to_cart" -> addToCart(safeParams, trace);
                case "remove_from_cart" -> removeFromCart(safeParams, trace);
                case "apply_coupon_to_cart" -> applyCouponToCart(safeParams, trace);
                case "checkout_cart" -> checkoutCart(safeParams, trace);

                case "create_purchase_order" -> createPurchaseOrder(safeParams, trace);
                case "cancel_purchase_order" -> cancelPurchaseOrder(safeParams, trace);
                case "list_orders" -> listOrders(safeParams, trace);
                case "get_active_orders" -> getActiveOrders(safeParams, trace);
                case "get_order_details" -> getOrderDetails(safeParams, trace);
                case "change_delivery_address" -> changeDeliveryAddress(safeParams, trace);
                case "offer_order_discount" -> offerOrderDiscount(safeParams, trace);

                case "list_my_addresses" -> listMyAddresses(safeParams, trace);
                case "get_my_account" -> getMyAccount(trace);
                case "create_support_ticket" -> createSupportTicket(safeParams, trace);
                case "add_review" -> addReview(safeParams, trace);
                case "track_shipment" -> trackShipment(safeParams, trace);
                case "create_return_request" -> createReturnRequest(safeParams, trace);

                default -> failure("UNKNOWN_ACTION", "Unknown actionId: " + name);
            };
        } catch (Exception ex) {
            String requestId = trace != null ? trace.requestId() : null;
            log.error("Connector action '{}' failed (requestId={}): {}", name, requestId, ex.getMessage(), ex);
            return failure("ACTION_EXECUTION_FAILED", "Action execution failed: " + safeMessage(ex));
        }
    }

    private Map<String, Object> listProducts(Map<String, Object> params) {
        String query = requireString(params, "query");
        List<Product> products = productService.searchByNameAndCategory(query, 10);
        boolean matched = !products.isEmpty();
        if (!matched) {
            products = productService.trending(10);
        }
        List<Map<String, Object>> payload = products.stream().map(this::toProductPayload).toList();
        return success(payload.isEmpty()
            ? "No products found"
            : (matched ? "Matching products" : "Trending products"), listPayload(payload));
    }

    private Map<String, Object> searchProducts(Map<String, Object> params) {
        String query = requireString(params, "query");
        List<Product> products = productService.searchByNameAndCategory(query, 10);
        boolean matched = !products.isEmpty();
        if (!matched) {
            products = productService.trending(10);
        }
        List<Map<String, Object>> payload = products.stream().map(this::toProductPayload).toList();
        return success(payload.isEmpty()
            ? "No products found"
            : (matched ? "Matching products" : "Trending products"), listPayload(payload));
    }

    private Map<String, Object> getProductDetails(Map<String, Object> params) {
        String sku = requireString(params, "sku");
        Product product = productService.findBySku(sku)
            .orElseThrow(() -> new IllegalArgumentException("Product not found for sku: " + sku));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", product.getId());
        data.put("sku", product.getSku());
        data.put("name", product.getName());
        data.put("description", product.getDescription());
        data.put("category", product.getCategory());
        data.put("tags", product.getTags());
        data.put("imageUrl", product.getImageUrl());
        data.put("price", product.getPrice());
        data.put("currency", product.getCurrency());
        data.put("inStockQty", product.getInStockQty());

        return success("Product details", data);
    }

    private Map<String, Object> viewCart(Trace trace) {
        String userId = requireUserId(trace);
        Cart cart = cartService.getOrCreateActiveCart(userId);

        List<CartItem> items = cart.getItems() != null ? cart.getItems() : List.of();
        List<Map<String, Object>> outItems = items.stream()
            .filter(i -> i != null)
            .map(i -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", i.getId());
                row.put("sku", i.getSku());
                row.put("productName", i.getProductName());
                row.put("quantity", i.getQuantity());
                row.put("unitPrice", i.getUnitPrice());
                row.put("totalPrice", i.getTotalPrice());
                return row;
            })
            .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("cartId", cart.getId());
        data.put("status", cart.getStatus() != null ? cart.getStatus().name() : null);
        data.put("currency", cart.getCurrency());
        data.put("couponCode", cart.getCouponCode());
        data.put("subtotal", cart.getSubtotal());
        data.put("discount", cart.getDiscount());
        data.put("total", cart.getTotal());
        data.put("items", outItems);

        return success(outItems.isEmpty() ? "Your cart is empty" : "Your active cart", data);
    }

    private Map<String, Object> addToCart(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        List<CartItemInput> items = readCartItems(params.get("items"));
        if (items == null || items.isEmpty()) {
            return failure("CART_ITEMS_REQUIRED", "No items provided");
        }

        Cart cart = null;
        for (CartItemInput item : items) {
            if (item == null || !StringUtils.hasText(item.sku())) {
                continue;
            }
            int qty = item.quantity() != null ? item.quantity() : 1;
            cart = cartService.addItem(userId, item.sku(), qty);
        }
        if (cart == null) {
            return failure("CART_ITEMS_INVALID", "No valid items provided");
        }

        String cartId = cart.getId() != null ? String.valueOf(cart.getId()) : null;
        List<Map<String, Object>> pinned = cartId != null
            ? List.of(pinnedTarget(cartId, "cart", "active cart", Map.of("cartId", cartId)))
            : List.of();

        return success("Added to cart", Map.of(
            "cartId", cart.getId(),
            "total", cart.getTotal(),
            "currency", cart.getCurrency(),
            "itemsCount", cart.getItems() != null ? cart.getItems().size() : 0
        ), pinned);
    }

    private Map<String, Object> removeFromCart(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String sku = requireString(params, "sku");
        Cart cart = cartService.removeItem(userId, sku);

        String cartId = cart != null && cart.getId() != null ? String.valueOf(cart.getId()) : null;
        List<Map<String, Object>> pinned = cartId != null
            ? List.of(pinnedTarget(cartId, "cart", "active cart", Map.of("cartId", cartId)))
            : List.of();

        return success("Removed from cart", Map.of(
            "cartId", cart.getId(),
            "total", cart.getTotal(),
            "currency", cart.getCurrency(),
            "itemsCount", cart.getItems() != null ? cart.getItems().size() : 0
        ), pinned);
    }

    private Map<String, Object> applyCouponToCart(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String code = requireString(params, "code");
        Cart cart = cartService.applyCoupon(userId, code);

        String cartId = cart != null && cart.getId() != null ? String.valueOf(cart.getId()) : null;
        List<Map<String, Object>> pinned = cartId != null
            ? List.of(pinnedTarget(cartId, "cart", "active cart", Map.of("cartId", cartId)))
            : List.of();

        return success("Coupon applied", Map.of(
            "cartId", cart.getId(),
            "couponCode", cart.getCouponCode(),
            "subtotal", cart.getSubtotal(),
            "discount", cart.getDiscount(),
            "total", cart.getTotal(),
            "currency", cart.getCurrency()
        ), pinned);
    }

    private Map<String, Object> checkoutCart(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String shippingAddress = requireString(params, "shippingAddress");
        String email = requireString(params, "email");
        Payment.Method method = parsePaymentMethod(params.get("paymentMethod")).orElse(Payment.Method.CARD);

        PurchaseOrder order = cartService.checkout(userId, shippingAddress, email, method);

        String orderNumber = order != null ? order.getOrderNumber() : null;
        List<Map<String, Object>> pinned = StringUtils.hasText(orderNumber)
            ? List.of(pinnedTarget(orderNumber, "order", "purchase order", Map.of(
                "orderNumber", orderNumber,
                "orderId", order.getId() != null ? String.valueOf(order.getId()) : ""
            )))
            : List.of();

        return success("Checkout complete", Map.of(
            "orderId", order.getId(),
            "orderNumber", order.getOrderNumber(),
            "totalPrice", order.getTotalPrice(),
            "currency", order.getCurrency(),
            "status", order.getStatus() != null ? order.getStatus().name() : null
        ), pinned);
    }

    private Map<String, Object> createPurchaseOrder(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String sku = requireString(params, "sku");
        int quantity = requireInt(params, "quantity", 1);
        String shippingAddress = requireString(params, "shippingAddress");
        String email = requireString(params, "email");

        PurchaseOrder created = purchaseOrderService.createPurchaseOrder(userId, sku, quantity, shippingAddress, email);

        String orderNumber = created != null ? created.getOrderNumber() : null;
        List<Map<String, Object>> pinned = StringUtils.hasText(orderNumber)
            ? List.of(pinnedTarget(orderNumber, "order", "purchase order", Map.of(
                "orderNumber", orderNumber.trim(),
                "orderId", created.getId() != null ? String.valueOf(created.getId()) : "",
                "sku", created.getSku() != null ? created.getSku() : ""
            )))
            : List.of();

        return success("Purchase order created", Map.of(
            "orderId", created.getId(),
            "orderNumber", created.getOrderNumber(),
            "sku", created.getSku(),
            "quantity", created.getQuantity(),
            "totalPrice", created.getTotalPrice(),
            "currency", created.getCurrency(),
            "status", created.getStatus() != null ? created.getStatus().name() : null
        ), pinned);
    }

    private Map<String, Object> cancelPurchaseOrder(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String orderNumber = readString(params, "orderNumber");
        Long orderId = readLong(params, "orderId");
        if (!StringUtils.hasText(orderNumber) && orderId == null) {
            return failure("ORDER_REF_REQUIRED", "orderNumber or orderId is required");
        }
        String orderRef = orderId != null ? String.valueOf(orderId) : orderNumber;

        PurchaseOrder cancelled = purchaseOrderService.cancelForUser(orderRef, userId);

        String cancelledOrderNumber = cancelled != null ? cancelled.getOrderNumber() : null;
        List<Map<String, Object>> pinned = StringUtils.hasText(cancelledOrderNumber)
            ? List.of(pinnedTarget(cancelledOrderNumber.trim(), "order", "purchase order", Map.of(
                "orderNumber", cancelledOrderNumber.trim(),
                "orderId", cancelled.getId() != null ? String.valueOf(cancelled.getId()) : ""
            )))
            : List.of();

        return success("Order cancelled", Map.of(
            "orderId", cancelled.getId(),
            "orderNumber", cancelled.getOrderNumber(),
            "status", cancelled.getStatus() != null ? cancelled.getStatus().name() : null
        ), pinned);
    }

    private Map<String, Object> listOrders(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        int limit = readInt(params, "limit", 50);
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

        return success(payload.isEmpty() ? "No orders found" : "Orders", listPayload(payload));
    }

    private Map<String, Object> getActiveOrders(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        int limit = readInt(params, "limit", 25);
        List<PurchaseOrder> orders = purchaseOrderService.listActiveForUser(userId, limit);

        List<Map<String, Object>> payload = orders.stream()
            .map(o -> Map.<String, Object>of(
                "orderId", o.getId(),
                "orderNumber", o.getOrderNumber(),
                "status", o.getStatus() != null ? o.getStatus().name() : null,
                "sku", o.getSku(),
                "quantity", o.getQuantity(),
                "totalPrice", o.getTotalPrice(),
                "currency", o.getCurrency(),
                "shippingAddress", o.getShippingAddress(),
                "createdAt", o.getCreatedAt()
            ))
            .toList();

        return success(payload.isEmpty() ? "No active orders found" : "Active orders", listPayload(payload));
    }

    private Map<String, Object> getOrderDetails(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String orderNumberOrId = requireString(params, "orderNumberOrId");

        PurchaseOrder order = purchaseOrderService.getForUserByReference(userId, orderNumberOrId);

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

        return success("Order details", payload);
    }

    private Map<String, Object> changeDeliveryAddress(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String shippingAddress = requireString(params, "shippingAddress");
        String orderNumber = readString(params, "orderNumber");
        Long orderId = readLong(params, "orderId");
        String orderRef = orderId != null ? String.valueOf(orderId) : orderNumber;

        PurchaseOrder updated = purchaseOrderService.updateDeliveryAddressForUser(userId, orderRef, shippingAddress);
        String updatedOrderNumber = updated != null ? updated.getOrderNumber() : null;

        List<Map<String, Object>> pinned = StringUtils.hasText(updatedOrderNumber)
            ? List.of(pinnedTarget(updatedOrderNumber.trim(), "order", "purchase order", Map.of(
                "orderNumber", updatedOrderNumber.trim(),
                "orderId", updated.getId() != null ? String.valueOf(updated.getId()) : ""
            )))
            : List.of();

        return success("Delivery address updated", Map.of(
            "orderId", updated.getId(),
            "orderNumber", updated.getOrderNumber(),
            "shippingAddress", updated.getShippingAddress(),
            "status", updated.getStatus() != null ? updated.getStatus().name() : null
        ), pinned);
    }

    private Map<String, Object> offerOrderDiscount(Map<String, Object> params, Trace trace) {
        String userId = trace != null ? trace.userId() : null;
        int percent = readInt(params, "discountPercent", 10);

        String orderNumber = readString(params, "orderNumber");
        Long orderId = readLong(params, "orderId");
        String ref = StringUtils.hasText(orderNumber) ? orderNumber.trim() : (orderId != null ? String.valueOf(orderId) : "your order");
        String coupon = percent >= 15 ? "SAVE15" : "SAVE10";

        log.info("Issued retention offer {}% (coupon={}) for user={} orderRef={}", percent, coupon, userId, ref);

        List<Map<String, Object>> pinned = !"your order".equals(ref)
            ? List.of(pinnedTarget(ref, "order", "purchase order", Map.of("orderRef", ref)))
            : List.of();

        return success("Discount offer applied", Map.of(
            "orderRef", ref,
            "discountPercent", percent,
            "couponCode", coupon,
            "note", "This demo does not persist discounts to the order record."
        ), pinned);
    }

    private Map<String, Object> listMyAddresses(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        int limit = readInt(params, "limit", 20);
        List<Address> addresses = addressService.listForUser(userId, limit);

        List<Map<String, Object>> payload = addresses.stream()
            .filter(a -> a != null)
            .map(a -> Map.<String, Object>of(
                "addressId", a.getId(),
                "type", a.getType() != null ? a.getType().name() : null,
                "line1", a.getLine1(),
                "line2", a.getLine2(),
                "city", a.getCity(),
                "state", a.getState(),
                "postalCode", a.getPostalCode(),
                "country", a.getCountry(),
                "isDefault", a.isDefault()
            ))
            .toList();

        return success(payload.isEmpty() ? "No saved addresses found" : "Saved addresses", listPayload(payload));
    }

    private Map<String, Object> getMyAccount(Trace trace) {
        String userId = requireUserId(trace);
        Account account = accountService.findByUserId(userId).orElse(null);
        if (account == null) {
            return success("No account profile found", Map.of("userId", userId));
        }
        return success("Account details", Map.of(
            "accountId", account.getId(),
            "userId", account.getUserId(),
            "name", account.getName(),
            "email", account.getEmail(),
            "tier", account.getTier(),
            "segment", account.getSegment()
        ));
    }

    private Map<String, Object> createSupportTicket(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String issueType = requireString(params, "issueType");
        String description = requireString(params, "description");
        String orderNumber = readString(params, "orderNumber");

        SupportTicket created = supportTicketService.create(userId, issueType, description, orderNumber);

        String ticketId = created != null && created.getId() != null ? String.valueOf(created.getId()) : null;
        List<Map<String, Object>> pinned = ticketId != null
            ? List.of(pinnedTarget(ticketId, "support_ticket", "support ticket", Map.of("ticketId", ticketId)))
            : List.of();

        return success("Support ticket created", Map.of(
            "ticketId", created.getId(),
            "status", created.getStatus() != null ? created.getStatus().name() : null,
            "issueType", created.getIssueType()
        ), pinned);
    }

    private Map<String, Object> addReview(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        Long productId = readLong(params, "productId");
        String sku = readString(params, "sku");
        int rating = requireInt(params, "rating", -1);
        if (rating < 1 || rating > 5) {
            return failure("INVALID_RATING", "rating must be between 1 and 5");
        }
        String text = requireString(params, "text");

        Review created = reviewService.create(userId, productId, sku, rating, text);

        String reviewId = created != null && created.getId() != null ? String.valueOf(created.getId()) : null;
        List<Map<String, Object>> pinned = reviewId != null
            ? List.of(pinnedTarget(reviewId, "review", "review", Map.of("reviewId", reviewId)))
            : List.of();

        return success("Review submitted", Map.of(
            "reviewId", created.getId(),
            "rating", created.getRating(),
            "productId", created.getProductId(),
            "sku", created.getSku()
        ), pinned);
    }

    private Map<String, Object> trackShipment(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String orderNumber = requireString(params, "orderNumber");

        PurchaseOrder order = purchaseOrderService.resolveForUser(orderNumber, userId);
        Shipment shipment = shipmentService.findLatestForOrder(order.getId()).orElse(null);
        if (shipment == null) {
            return success("No shipment found for this order yet", Map.of(
                "orderId", order.getId(),
                "orderNumber", order.getOrderNumber()
            ));
        }

        return success("Shipment status", Map.of(
            "orderId", order.getId(),
            "orderNumber", order.getOrderNumber(),
            "shipmentId", shipment.getId(),
            "carrier", shipment.getCarrier(),
            "trackingNumber", shipment.getTrackingNumber(),
            "status", shipment.getStatus() != null ? shipment.getStatus().name() : null,
            "eta", shipment.getEta()
        ));
    }

    private Map<String, Object> createReturnRequest(Map<String, Object> params, Trace trace) {
        String userId = requireUserId(trace);
        String orderNumber = requireString(params, "orderNumber");
        String reason = requireString(params, "reason");

        ReturnRequest created = returnRequestService.create(userId, orderNumber, reason);

        String returnId = created != null && created.getId() != null ? String.valueOf(created.getId()) : null;
        List<Map<String, Object>> pinned = returnId != null
            ? List.of(pinnedTarget(returnId, "return_request", "return request", Map.of("returnRequestId", returnId)))
            : List.of();

        return success(created.isEligible() ? "Return request created" : "Return request created (not eligible)", Map.of(
            "returnRequestId", created.getId(),
            "status", created.getStatus() != null ? created.getStatus().name() : null,
            "eligible", created.isEligible()
        ), pinned);
    }

    private List<CartItemInput> readCartItems(Object raw) {
        if (raw == null) {
            return List.of();
        }
        try {
            return objectMapper.convertValue(raw, CART_ITEM_INPUTS);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Optional<Payment.Method> parsePaymentMethod(Object raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return Optional.empty();
        }
        String normalized = s.trim().toUpperCase(Locale.ROOT);
        try {
            return Optional.of(Payment.Method.valueOf(normalized));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Map<String, Object> toProductPayload(Product product) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", product != null ? product.getId() : null);
        out.put("sku", product != null ? product.getSku() : null);
        out.put("name", product != null ? product.getName() : null);
        out.put("description", product != null ? product.getDescription() : null);
        out.put("category", product != null ? product.getCategory() : null);
        out.put("tags", product != null ? product.getTags() : null);
        out.put("imageUrl", product != null ? product.getImageUrl() : null);
        out.put("price", product != null ? product.getPrice() : null);
        out.put("currency", product != null ? product.getCurrency() : null);
        out.put("inStockQty", product != null ? product.getInStockQty() : null);
        out.put("createdAt", product != null ? product.getCreatedAt() : null);
        out.put("updatedAt", product != null ? product.getUpdatedAt() : null);
        return out;
    }

    private Map<String, Object> success(String message, Map<String, Object> data) {
        return success(message, data, List.of());
    }

    private Map<String, Object> success(String message, Map<String, Object> data, List<Map<String, Object>> pinnedTargets) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        if (StringUtils.hasText(message)) {
            out.put("message", message);
        }
        if (data != null) {
            out.put("data", data);
        }
        if (pinnedTargets != null && !pinnedTargets.isEmpty()) {
            out.put("pinnedTargets", pinnedTargets);
        }
        return out;
    }

    private Map<String, Object> failure(String errorCode, String message) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", false);
        if (StringUtils.hasText(message)) {
            out.put("message", message);
        }
        if (StringUtils.hasText(errorCode)) {
            out.put("errorCode", errorCode);
        }
        return out;
    }

    private Map<String, Object> pinnedTarget(String id, String vectorSpace, String contentText, Map<String, String> metadata) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", id);
        if (StringUtils.hasText(vectorSpace)) {
            out.put("vectorSpace", vectorSpace);
        }
        if (StringUtils.hasText(contentText)) {
            out.put("contentText", contentText);
        }
        if (metadata != null && !metadata.isEmpty()) {
            out.put("metadata", metadata);
        }
        return out;
    }

    private Map<String, Object> listPayload(List<Map<String, Object>> items) {
        List<Map<String, Object>> safe = items != null ? items : List.of();
        return Map.of(
            KEY_ITEMS, safe,
            KEY_COUNT, safe.size()
        );
    }

    private String requireUserId(Trace trace) {
        String userId = trace != null ? trace.userId() : null;
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("trace.userId is required");
        }
        return userId.trim();
    }

    private String requireString(Map<String, Object> params, String key) {
        String value = readString(params, key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value.trim();
    }

    private String readString(Map<String, Object> params, String key) {
        if (params == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        String s = raw.toString();
        return StringUtils.hasText(s) ? s : null;
    }

    private int requireInt(Map<String, Object> params, String key, int fallback) {
        Integer value = readIntObject(params, key);
        if (value == null) {
            if (fallback >= 0) {
                return fallback;
            }
            throw new IllegalArgumentException("Missing required parameter: " + key);
        }
        return value;
    }

    private int readInt(Map<String, Object> params, String key, int fallback) {
        Integer value = readIntObject(params, key);
        return value != null ? value : fallback;
    }

    private Integer readIntObject(Map<String, Object> params, String key) {
        if (params == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Long readLong(Map<String, Object> params, String key) {
        if (params == null || !StringUtils.hasText(key)) {
            return null;
        }
        Object raw = params.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = raw.toString();
        if (!StringUtils.hasText(s)) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String safeMessage(Exception ex) {
        if (ex == null) {
            return "unknown";
        }
        if (StringUtils.hasText(ex.getMessage())) {
            return ex.getMessage();
        }
        return ex.getClass().getSimpleName();
    }

    public record Trace(
        String requestId,
        String conversationId,
        String userId,
        String sessionId
    ) {
    }

    public record CartItemInput(
        String sku,
        Integer quantity
    ) {
    }
}
