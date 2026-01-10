package com.subscription.hub.action.handler;

import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.subscription.hub.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DowngradeSubscriptionActionHandler implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("downgrade_subscription")
            .description("Downgrade subscription to a lower tier plan")
            .category("subscription")
            .parameters(Map.of(
                "subscriptionId", "UUID of the subscription to downgrade",
                "newPlanId", "UUID of the new plan to downgrade to"
            ))
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        if (userId == null) {
            return false;
        }
        return subscriptionService.hasActiveSubscription(UUID.fromString(userId));
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        return "Are you sure you want to downgrade? You may lose access to some features. This change will take effect on your next billing cycle.";
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            String subscriptionId = (String) params.get("subscriptionId");
            String newPlanId = (String) params.get("newPlanId");
            
            if (subscriptionId == null || newPlanId == null) {
                return ActionResult.builder()
                    .success(false)
                    .message("Subscription ID and new plan ID are required")
                    .build();
            }
            
            var subscription = subscriptionService.downgrade(
                UUID.fromString(subscriptionId),
                UUID.fromString(newPlanId)
            );
            
            return ActionResult.builder()
                .success(true)
                .message("Your subscription has been downgraded successfully")
                .data(Map.of(
                    "subscriptionId", subscriptionId,
                    "newPlanId", newPlanId,
                    "status", subscription.getStatus().toString()
                ))
                .build();
        } catch (Exception e) {
            log.error("Error downgrading subscription", e);
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Error downgrading subscription for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to downgrade subscription. " + e.getMessage())
            .errorCode("DOWNGRADE_FAILED")
            .build();
    }
}
