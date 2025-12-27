package com.ai.infrastructure.behavior.spi;

import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.UserEventBatch;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * User-implemented interface to provide behavioral events from external sources.
 * The module never stores raw events; it pulls them on-demand via this SPI.
 */
public interface ExternalEventProvider {
    
    /**
     * CASE 1: Targeted Fetch with Time Window.
     *
     * @param userId user to analyze
     * @param since  optional lower bound timestamp (null = default window)
     * @param until  optional upper bound timestamp (null = now)
     * @return list of events within the window
     */
    List<ExternalEvent> getEventsForUser(UUID userId, LocalDateTime since, LocalDateTime until);
    
    /**
     * CASE 2: Discovery Fetch for next user needing analysis.
     *
     * @return batch containing the userId, events, and optional context; null if none
     */
    UserEventBatch getNextUserEvents();
}
