package com.subscription.hub.action.handler;

import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.subscription.hub.entity.Subscription;
import com.subscription.hub.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class SubscribeActionHandler implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("subscribe")
            .description("Subscribe to a subscription plan")
            .category("subscription")
            .parameters(Map.of(
                "planId", "UUID of the plan to subscribe to",
                "billingCycle", "MONTHLY or ANNUAL"
            ))
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        if (userId == null) {
            return false;
        }
        // User can subscribe if they don't have an active subscription
        return !subscriptionService.hasActiveSubscription(UUID.fromString(userId));
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String planId = (String) params.get("planId");
        String billingCycle = (String) params.getOrDefault("billingCycle", "MONTHLY");
        
        return String.format(
            "Are you sure you want to subscribe to this plan with %s billing?",
            billingCycle
        );
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            String planId = (String) params.get("planId");
            if (planId == null) {
                return ActionResult.builder()
                    .success(false)
                    .message("Plan ID is required")
                    .build();
            }
            
            String billingCycleStr = (String) params.getOrDefault("billingCycle", "MONTHLY");
            Subscription.BillingCycle billingCycle = Subscription.BillingCycle.valueOf(billingCycleStr.toUpperCase());
            
            Subscription subscription = subscriptionService.subscribe(
                UUID.fromString(userId),
                UUID.fromString(planId),
                billingCycle
            );
            
            return ActionResult.builder()
                .success(true)
                .message("You have successfully subscribed!")
                .data(Map.of(
                    "subscriptionId", subscription.getId().toString(),
                    "planId", planId,
                    "status", subscription.getStatus().toString()
                ))
                .build();
        } catch (Exception e) {
            log.error("Error subscribing to plan", e);
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Error subscribing for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to subscribe. " + e.getMessage())
            .errorCode("SUBSCRIBE_FAILED")
            .build();
    }
}
