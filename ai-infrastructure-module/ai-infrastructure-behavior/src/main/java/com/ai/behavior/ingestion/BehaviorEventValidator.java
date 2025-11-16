package com.ai.behavior.ingestion;

import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
public class BehaviorEventValidator {

    public void validate(BehaviorEvent event) {
        if (event == null) {
            throw new BehaviorValidationException("Behavior event cannot be null");
        }
        if (event.getEventType() == null) {
            throw new BehaviorValidationException("eventType is required");
        }
        if (event.getUserId() == null && (event.getSessionId() == null || event.getSessionId().isBlank())) {
            throw new BehaviorValidationException("Either userId or sessionId must be provided");
        }
        if (event.getTimestamp() == null) {
            event.setTimestamp(LocalDateTime.now());
        }
        if (event.getMetadata() != null && event.getMetadata().size() > 100) {
            throw new BehaviorValidationException("metadata contains more than 100 keys");
        }
    }
}
