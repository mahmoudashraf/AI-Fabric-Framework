package com.subscription.hub.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for tracking behavior events for BehaviorAnalysisService
 * This service integrates with AI Fabric Framework's BehaviorAnalysisService
 * via the SubscriptionExternalEventProvider
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BehaviorEventService {
    
    private final SubscriptionExternalEventProvider eventProvider;
    
    /**
     * Track a subscription-related event for behavior analysis
     */
    public void trackEvent(UUID userId, String eventType, Map<String, Object> eventData) {
        log.info("Tracking event: userId={}, eventType={}, eventData={}", userId, eventType, eventData);
        
        // Convert Map<String, Object> to Map<String, String> for event provider
        Map<String, String> stringEventData = eventData.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() != null ? entry.getValue().toString() : ""
            ));
        
        eventProvider.publishEvent(userId.toString(), eventType, stringEventData);
    }
}
