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
}

