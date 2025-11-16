package com.ai.behavior.ingestion.validator;

import com.ai.behavior.exception.BehaviorValidationException;
import com.ai.behavior.model.BehaviorEvent;
import com.ai.behavior.model.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BehaviorEventValidatorTest {

    private BehaviorEventValidator validator;

    @BeforeEach
    void setUp() {
        validator = new BehaviorEventValidator(Clock.fixed(Instant.parse("2025-11-16T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldValidateMinimalEvent() {
        BehaviorEvent event = BehaviorEvent.builder()
            .userId(java.util.UUID.randomUUID())
            .eventType(EventType.VIEW)
            .timestamp(LocalDateTime.of(2025, 11, 15, 10, 0))
            .build();

        assertThatCode(() -> validator.validate(event)).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectFutureEvent() {
        BehaviorEvent event = BehaviorEvent.builder()
            .userId(java.util.UUID.randomUUID())
            .eventType(EventType.VIEW)
            .timestamp(LocalDateTime.of(2025, 11, 17, 10, 0))
            .build();

        assertThatThrownBy(() -> validator.validate(event))
            .isInstanceOf(BehaviorValidationException.class);
    }

    @Test
    void shouldRequireSessionForAnonymousEvent() {
        BehaviorEvent event = BehaviorEvent.builder()
            .eventType(EventType.CLICK)
            .timestamp(LocalDateTime.of(2025, 11, 15, 10, 0))
            .build();

        assertThatThrownBy(() -> validator.validate(event))
            .isInstanceOf(BehaviorValidationException.class);
    }
}
