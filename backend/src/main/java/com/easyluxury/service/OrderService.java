package com.easyluxury.service;

import com.ai.infrastructure.annotation.AICapable;
import com.ai.infrastructure.annotation.AIProcess;
import com.easyluxury.entity.Order;
import com.easyluxury.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Order Service
 * 
 * Domain service with AI capabilities enabled via annotations.
 * AI processing happens automatically via AOP aspects.
 * 
 * @author Easy Luxury Team
 * @version 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {
    
    private final OrderRepository orderRepository;
    
    /**
     * Create a new order
     * AI processing: Automatic embedding generation, search indexing
     */
    @AIProcess(entityType = "order", processType = "create")
    @Transactional
    public Order createOrder(Order order) {
        log.info("Creating order: {}", order.getId());
        return orderRepository.save(order);
    }
    
    /**
     * Update an existing order
     * AI processing: Automatic embedding regeneration, search re-indexing, analysis
     */
    @AIProcess(entityType = "order", processType = "update")
    @Transactional
    public Order updateOrder(UUID id, Order order) {
        log.info("Updating order: {}", id);
        
        Order existingOrder = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        
        existingOrder.setUserId(order.getUserId());
        existingOrder.setTotalAmount(order.getTotalAmount());
        existingOrder.setStatus(order.getStatus());
        existingOrder.setNotes(order.getNotes());
        existingOrder.setShippingAddress(order.getShippingAddress());
        existingOrder.setBillingAddress(order.getBillingAddress());
        existingOrder.setPaymentMethod(order.getPaymentMethod());
        
        return orderRepository.save(existingOrder);
    }
    
    /**
     * Delete an order
     * AI processing: Automatic removal from search index, cleanup
     */
    @AIProcess(entityType = "order", processType = "delete")
    @Transactional
    public void deleteOrder(UUID id) {
        log.info("Deleting order: {}", id);
        
        Order order = orderRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Order not found: " + id));
        
        orderRepository.delete(order);
    }
    
    /**
     * Find order by ID
     */
    public Optional<Order> findById(UUID id) {
        return orderRepository.findById(id);
    }
    
    /**
     * Find orders by user ID
     */
    public List<Order> findByUserId(UUID userId) {
        return orderRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    /**
     * Find all orders
     */
    public List<Order> findAll() {
        return orderRepository.findAll();
    }
}
