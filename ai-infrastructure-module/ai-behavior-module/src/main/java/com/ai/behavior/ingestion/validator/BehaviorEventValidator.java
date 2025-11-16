package com.ai.behavior.ingestion.validator;

import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;

@Component
public class BehaviorEventValidator {

    private static final EnumSet<EventType> TEXTUAL_EVENTS = EnumSet.of(EventType.FEEDBACK, EventType.REVIEW, EventType.SEARCH);
    private final Clock clock;

    public BehaviorEventValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(BehaviorEvent event) {
        if (event == null) {
            throw new BehaviorValidationException("Behavior event cannot be null");
        }
        if (event.getEventType() == null) {
            throw new BehaviorValidationException("eventType is required");
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now(clock));
        }
        if (event.getTimestamp().isAfter(LocalDateTime.now(clock).plusMinutes(5))) {
            throw new BehaviorValidationException("event timestamp cannot be in the future");
        }
        if (event.isAnonymous() && event.getSessionId() == null) {
            throw new BehaviorValidationException("anonymous events must include sessionId");
        }

        if (TEXTUAL_EVENTS.contains(event.getEventType())) {
            ensureMetadataField(event, "feedback_text", "review_text", "query");
        }
    }

    private void ensureMetadataField(BehaviorEvent event, String... keys) {
        if (event.getMetadata() == null) {
            throw new BehaviorValidationException("textual events require metadata payload");
        }
        boolean hasField = false;
        for (String key : keys) {
            if (event.getMetadata().containsKey(key)) {
                hasField = true;
                break;
            }
        }
        if (!hasField) {
            throw new BehaviorValidationException("textual events must include one of " + String.join(",", keys));
        }
    }
}
