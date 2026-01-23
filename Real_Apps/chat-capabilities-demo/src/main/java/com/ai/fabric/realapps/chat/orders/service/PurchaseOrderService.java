package com.ai.fabric.realapps.chat.orders.service;

import com.ai.fabric.realapps.chat.catalog.domain.Product;
import com.ai.fabric.realapps.chat.catalog.service.ProductService;
import com.ai.fabric.realapps.chat.orders.domain.PurchaseOrder;
import com.ai.fabric.realapps.chat.orders.repo.PurchaseOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final ProductService productService;

    @Transactional
    public PurchaseOrder createPurchaseOrder(String userId,
                                             String sku,
                                             int quantity,
                                             String shippingAddress,
                                             String email) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(sku)) {
            throw new IllegalArgumentException("sku is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (!StringUtils.hasText(shippingAddress)) {
            throw new IllegalArgumentException("shippingAddress is required");
        }
        if (!StringUtils.hasText(email)) {
            throw new IllegalArgumentException("email is required");
        }

        Product product = productService.findBySku(sku.trim())
            .orElseThrow(() -> new EntityNotFoundException("Product not found for sku: " + sku));

        Integer inStock = product.getInStockQty();
        if (inStock == null) {
            inStock = 0;
        }
        if (inStock < quantity) {
            throw new IllegalArgumentException("Insufficient stock for sku '" + product.getSku()
                + "'. Available=" + inStock + " requested=" + quantity);
        }

        BigDecimal unitPrice = product.getPrice();
        if (unitPrice == null) {
            throw new IllegalArgumentException("Product price is not set for sku: " + product.getSku());
        }

        String currency = StringUtils.hasText(product.getCurrency())
            ? product.getCurrency().trim().toUpperCase(Locale.ROOT)
            : "USD";

        BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));

        // Update inventory (async indexing per Product @AICapable config).
        productService.updateProductStock(product.getSku(), inStock - quantity);

        PurchaseOrder order = new PurchaseOrder();
        order.setOrderNumber("PO-" + UUID.randomUUID());
        order.setUserId(userId.trim());
        order.setSku(product.getSku());
        order.setProductId(product.getId());
        order.setProductName(product.getName());
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice);
        order.setCurrency(currency);
        order.setTotalPrice(totalPrice);
        order.setShippingAddress(shippingAddress.trim());
        order.setEmail(email.trim());
        order.setStatus(PurchaseOrder.Status.CREATED);

        return purchaseOrderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> listForUser(String userId, int limit) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        int effectiveLimit = limit <= 0 ? 50 : Math.min(limit, 200);
        return purchaseOrderRepository.findByUserIdOrderByCreatedAtDesc(userId.trim()).stream()
            .limit(effectiveLimit)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> listActiveForUser(String userId, int limit) {
        if (!StringUtils.hasText(userId)) {
            return List.of();
        }
        int effectiveLimit = limit <= 0 ? 25 : Math.min(limit, 200);
        return purchaseOrderRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId.trim(), PurchaseOrder.Status.CREATED).stream()
            .limit(effectiveLimit)
            .toList();
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getForUser(long id, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        PurchaseOrder order = purchaseOrderRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + id));
        if (!userId.trim().equals(order.getUserId())) {
            throw new EntityNotFoundException("Order not found: " + id);
        }
        return order;
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getForUserByReference(String userId, String orderNumberOrId) {
        return getByOrderNumberOrIdForUser(orderNumberOrId, userId);
    }

    @Transactional
    public PurchaseOrder cancelForUser(String orderNumber, String userId) {
        PurchaseOrder order = getByOrderNumberOrIdForUser(orderNumber, userId);

        if (order.getStatus() == PurchaseOrder.Status.CANCELLED) {
            throw new IllegalStateException("Order is already cancelled: " + order.getOrderNumber());
        }
        if (order.getStatus() == PurchaseOrder.Status.FULFILLED) {
            throw new IllegalStateException("Order is already fulfilled and cannot be cancelled: " + order.getOrderNumber());
        }

        order.setStatus(PurchaseOrder.Status.CANCELLED);

        productService.findBySku(order.getSku()).ifPresent(product -> {
            Integer inStock = product.getInStockQty();
            if (inStock == null) {
                inStock = 0;
            }
            Integer qty = order.getQuantity();
            int restoreQty = qty != null ? qty : 0;
            productService.updateProductStock(product.getSku(), inStock + restoreQty);
        });

        return purchaseOrderRepository.save(order);
    }

    @Transactional
    public PurchaseOrder updateDeliveryAddressForUser(String userId, String orderNumber, String newShippingAddress) {
        if (!StringUtils.hasText(newShippingAddress)) {
            throw new IllegalArgumentException("shippingAddress is required");
        }

        PurchaseOrder order = StringUtils.hasText(orderNumber)
            ? getByOrderNumberOrIdForUser(orderNumber, userId)
            : getCurrentCreatedOrderForUser(userId);

        if (order.getStatus() == PurchaseOrder.Status.CANCELLED) {
            throw new IllegalStateException("Order is cancelled: " + order.getOrderNumber());
        }
        if (order.getStatus() == PurchaseOrder.Status.FULFILLED) {
            throw new IllegalStateException("Order is already fulfilled and cannot be changed: " + order.getOrderNumber());
        }

        order.setShippingAddress(newShippingAddress.trim());
        return purchaseOrderRepository.save(order);
    }

    private PurchaseOrder getByOrderNumberOrIdForUser(String orderNumberOrId, String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        if (!StringUtils.hasText(orderNumberOrId)) {
            throw new IllegalArgumentException("orderNumber is required");
        }

        String normalized = orderNumberOrId.trim();
        Long asId = parseLongIfDigitsOnly(normalized);
        if (asId != null) {
            return getForUser(asId, userId);
        }

        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(normalized)
            .orElseThrow(() -> new EntityNotFoundException("Order not found: " + normalized));

        if (!userId.trim().equals(order.getUserId())) {
            throw new EntityNotFoundException("Order not found: " + normalized);
        }

        return order;
    }

    private PurchaseOrder getCurrentCreatedOrderForUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            throw new IllegalArgumentException("userId is required");
        }
        return purchaseOrderRepository.findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId.trim(), PurchaseOrder.Status.CREATED)
            .orElseThrow(() -> new EntityNotFoundException("No current order found for user: " + userId.trim()));
    }

    private Long parseLongIfDigitsOnly(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        for (int i = 0; i < trimmed.length(); i++) {
            if (!Character.isDigit(trimmed.charAt(i))) {
                return null;
            }
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
