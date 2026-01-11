package com.subscription.hub.service;

import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ExternalEventProvider implementation for subscription events
 * This allows BehaviorAnalysisService to analyze subscription-related user behavior
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SubscriptionExternalEventProvider implements ExternalEventProvider {
    
    // In-memory event store for demo purposes
    // In production, this would be backed by a database or event stream
    private final Map<String, List<ExternalEvent>> eventStore = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> lastProcessedTime = new ConcurrentHashMap<>();
    
    /**
     * Publish an event for behavior analysis
     */
    public void publishEvent(String userId, String eventType, Map<String, String> eventData) {
        // Convert Map<String, String> to Map<String, Object> for ExternalEvent
        Map<String, Object> objectEventData = eventData != null 
            ? new java.util.HashMap<>(eventData)
            : new java.util.HashMap<>();
        
        ExternalEvent event = ExternalEvent.builder()
            .eventType(eventType)
            .timestamp(LocalDateTime.now())
            .eventData(objectEventData)
            .source("subscription-management-hub")
            .build();
        
        eventStore.computeIfAbsent(userId, k -> new ArrayList<>()).add(event);
        log.debug("Published event for user {}: {}", userId, eventType);
    }
    
    @Override
    public List<ExternalEvent> getEventsForUser(String userId, LocalDateTime since, LocalDateTime until) {
        List<ExternalEvent> userEvents = eventStore.getOrDefault(userId, new ArrayList<>());
        
        if (since == null && until == null) {
            return new ArrayList<>(userEvents);
        }
        
        return userEvents.stream()
            .filter(event -> {
                if (since != null && event.getTimestamp().isBefore(since)) {
                    return false;
                }
                if (until != null && event.getTimestamp().isAfter(until)) {
                    return false;
                }
                return true;
            })
            .toList();
    }
    
    @Override
    public UserEventBatch getNextUserEvents() {
        // Find a user with unprocessed events
        for (Map.Entry<String, List<ExternalEvent>> entry : eventStore.entrySet()) {
            String userId = entry.getKey();
            List<ExternalEvent> events = entry.getValue();
            
            LocalDateTime lastProcessed = lastProcessedTime.get(userId);
            if (lastProcessed == null) {
                // First time processing this user
                lastProcessedTime.put(userId, LocalDateTime.now());
                return UserEventBatch.builder()
                    .userId(userId)
                    .events(events)
                    .totalEventCount(events.size())
                    .build();
            }
            
            // Get events after last processed time
            List<ExternalEvent> newEvents = events.stream()
                .filter(event -> event.getTimestamp().isAfter(lastProcessed))
                .toList();
            
            if (!newEvents.isEmpty()) {
                lastProcessedTime.put(userId, LocalDateTime.now());
                return UserEventBatch.builder()
                    .userId(userId)
                    .events(newEvents)
                    .totalEventCount(newEvents.size())
                    .build();
            }
        }
        
        return null; // No users with unprocessed events
    }
}
