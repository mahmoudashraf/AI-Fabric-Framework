package com.subscription.hub.action.handler;

import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.subscription.hub.service.SubscriptionService;
import com.subscription.hub.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class CancelSubscriptionActionHandler extends BaseActionHandler implements ActionHandler {
    
    private final SubscriptionService subscriptionService;
    
    public CancelSubscriptionActionHandler(SubscriptionService subscriptionService, UserService userService) {
        super(userService);
        this.subscriptionService = subscriptionService;
    }
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("cancel_subscription")
            .description("Cancel an active subscription")
            .category("subscription")
            .parameters(Map.of(
                "subscriptionId", "UUID of the subscription to cancel",
                "reason", "Optional reason for cancellation"
            ))
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        if (userId == null) {
            return false;
        }
        try {
            UUID userUuid = parseUserId(userId);
            return subscriptionService.hasActiveSubscription(userUuid);
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String subscriptionId = (String) params.get("subscriptionId");
        if (subscriptionId == null) {
            return "Are you sure you want to cancel your subscription? This action cannot be undone.";
        }
        
        try {
            var subscription = subscriptionService.findById(UUID.fromString(subscriptionId));
            return String.format(
                "Are you sure you want to cancel your subscription? " +
                "Your access will end on %s. This action cannot be undone.",
                subscription.getEndDate() != null ? subscription.getEndDate().toString() : "immediately"
            );
        } catch (Exception e) {
            return "Are you sure you want to cancel your subscription? This action cannot be undone.";
        }
    }

    @Override
    public boolean requiresConfirmation() {
        return true;
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            String subscriptionId = (String) params.get("subscriptionId");
            if (subscriptionId == null) {
                return ActionResult.builder()
                    .success(false)
                    .message("Subscription ID is required")
                    .build();
            }
            
            String reason = (String) params.getOrDefault("reason", "User requested");
            
            var subscription = subscriptionService.unsubscribe(
                UUID.fromString(subscriptionId),
                reason
            );
            
            return ActionResult.builder()
                .success(true)
                .message("Your subscription has been cancelled successfully")
                .data(Map.of(
                    "subscriptionId", subscriptionId,
                    "status", subscription.getStatus().toString(),
                    "endDate", subscription.getEndDate() != null ? subscription.getEndDate().toString() : ""
                ))
                .build();
        } catch (Exception e) {
            log.error("Error cancelling subscription", e);
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Error cancelling subscription for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to cancel subscription. Please contact support.")
            .errorCode("CANCEL_FAILED")
            .build();
    }
}
