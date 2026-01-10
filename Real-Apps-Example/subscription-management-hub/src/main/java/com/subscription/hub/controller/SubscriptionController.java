package com.subscription.hub.controller;

import com.subscription.hub.entity.Subscription;
import com.subscription.hub.entity.SubscriptionPlan;
import com.subscription.hub.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
@Slf4j
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    @PostMapping("/subscribe")
    public ResponseEntity<Subscription> subscribe(
            @RequestParam UUID userId,
            @RequestParam UUID planId,
            @RequestParam(defaultValue = "MONTHLY") Subscription.BillingCycle billingCycle) {
        Subscription subscription = subscriptionService.subscribe(userId, planId, billingCycle);
        return ResponseEntity.ok(subscription);
    }
    
    @PostMapping("/{id}/unsubscribe")
    public ResponseEntity<Subscription> unsubscribe(
            @PathVariable UUID id,
            @RequestParam(required = false) String reason) {
        Subscription subscription = subscriptionService.unsubscribe(id, reason);
        return ResponseEntity.ok(subscription);
    }
    
    @PostMapping("/{id}/upgrade")
    public ResponseEntity<Subscription> upgrade(
            @PathVariable UUID id,
            @RequestParam UUID newPlanId) {
        Subscription subscription = subscriptionService.upgrade(id, newPlanId);
        return ResponseEntity.ok(subscription);
    }
    
    @PostMapping("/{id}/downgrade")
    public ResponseEntity<Subscription> downgrade(
            @PathVariable UUID id,
            @RequestParam UUID newPlanId) {
        Subscription subscription = subscriptionService.downgrade(id, newPlanId);
        return ResponseEntity.ok(subscription);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<Subscription> getSubscription(@PathVariable UUID id) {
        Subscription subscription = subscriptionService.findById(id);
        return ResponseEntity.ok(subscription);
    }
    
    @GetMapping("/user/{userId}/active")
    public ResponseEntity<Subscription> getActiveSubscription(@PathVariable UUID userId) {
        return subscriptionService.getActiveSubscription(userId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
