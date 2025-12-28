package com.ai.infrastructure.behavior.it.support;

import com.ai.infrastructure.behavior.model.ExternalEvent;
import com.ai.infrastructure.behavior.model.UserEventBatch;
import com.ai.infrastructure.behavior.spi.ExternalEventProvider;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class TestEventProvider implements ExternalEventProvider {

    private final AtomicReference<List<ExternalEvent>> targetedEvents = new AtomicReference<>(Collections.emptyList());
    private final AtomicReference<UserEventBatch> nextBatch = new AtomicReference<>(null);

    @Override
    public List<ExternalEvent> getEventsForUser(UUID userId, LocalDateTime since, LocalDateTime until) {
        return targetedEvents.get();
    }

    @Override
    public UserEventBatch getNextUserEvents() {
        return nextBatch.get();
    }

    public void setTargetedEvents(List<ExternalEvent> events) {
        targetedEvents.set(events);
    }

    public void setNextBatch(UUID userId, List<ExternalEvent> events, Map<String, Object> userContext) {
        nextBatch.set(UserEventBatch.builder()
            .userId(userId)
            .events(events)
            .totalEventCount(events != null ? events.size() : 0)
            .userContext(userContext)
            .build());
    }

    public void clear() {
        targetedEvents.set(Collections.emptyList());
        nextBatch.set(null);
    }
}
