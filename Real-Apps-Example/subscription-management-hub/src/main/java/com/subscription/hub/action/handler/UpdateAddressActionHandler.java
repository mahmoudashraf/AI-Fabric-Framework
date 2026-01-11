package com.subscription.hub.action.handler;

import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import com.subscription.hub.entity.Address;
import com.subscription.hub.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class UpdateAddressActionHandler implements ActionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(UpdateAddressActionHandler.class);
    private final SubscriptionService subscriptionService;
    private final PIIDetectionService piiDetectionService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("update_address")
            .description("Update billing or shipping address")
            .category("subscription")
            .parameters(Map.of(
                "subscriptionId", "UUID of the subscription",
                "addressType", "BILLING or SHIPPING",
                "streetAddress", "Street address",
                "city", "City",
                "state", "State/Province",
                "postalCode", "Postal/ZIP code",
                "country", "Country"
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
        String addressType = (String) params.getOrDefault("addressType", "BILLING");
        return String.format(
            "Are you sure you want to update your %s address?",
            addressType.toLowerCase()
        );
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
            
            String addressTypeStr = (String) params.getOrDefault("addressType", "BILLING");
            Address.AddressType addressType = Address.AddressType.valueOf(addressTypeStr.toUpperCase());
            
            // Build address from parameters
            Address address = Address.builder()
                .streetAddress((String) params.get("streetAddress"))
                .city((String) params.get("city"))
                .state((String) params.get("state"))
                .postalCode((String) params.get("postalCode"))
                .country((String) params.get("country"))
                .type(addressType)
                .build();
            
            // Validate address using PII detection service
            String addressString = String.format("%s, %s, %s %s, %s",
                address.getStreetAddress(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry()
            );
            
            var piiResult = piiDetectionService.detectAndProcess(addressString);
            // Address is valid if no PII issues detected (addresses are expected to contain location data)
            // In DETECT_ONLY mode, we just check if the format is reasonable
            // Note: Addresses may contain PII-like patterns (street numbers, postal codes) which is normal
            boolean isValid = !piiResult.isPiiDetected() || !piiResult.hasDetections();
            address.setIsValidated(isValid);
            address.setValidationScore(isValid ? 1.0 : 0.7); // Slightly lower if PII detected, but still acceptable for addresses
            
            var subscription = subscriptionService.updateAddress(
                UUID.fromString(subscriptionId),
                addressType,
                address
            );
            
            return ActionResult.builder()
                .success(true)
                .message("Your address has been updated successfully")
                .data(Map.of(
                    "subscriptionId", subscriptionId,
                    "addressType", addressType.toString(),
                    "isValidated", address.getIsValidated().toString()
                ))
                .build();
        } catch (Exception e) {
            log.error("Error updating address", e);
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Error updating address for user: {}", userId, e);
        
        return ActionResult.builder()
            .success(false)
            .message("Failed to update address. " + e.getMessage())
            .errorCode("UPDATE_ADDRESS_FAILED")
            .build();
    }
}
