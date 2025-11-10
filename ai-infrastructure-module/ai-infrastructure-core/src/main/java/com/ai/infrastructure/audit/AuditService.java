package com.ai.infrastructure.audit;

import com.ai.infrastructure.dto.AIAuditRequest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Lightweight infrastructure audit helper that delegates to {@link AIAuditService} and provides
 * defensive defaults for infrastructure components.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private static final DateTimeFormatter ISO_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    private final AIAuditService aiAuditService;
    private final Clock clock;

    public void logOperation(String requestId,
                             String userId,
                             String eventType,
                             Iterable<String> eventDetails,
                             String timestampIso) {
        LocalDateTime timestamp = parseTimestamp(timestampIso).orElseGet(() -> LocalDateTime.now(clock));
        logOperation(requestId, userId, eventType, eventDetails, timestamp);
    }

    public void logOperation(String requestId,
                             String userId,
                             String eventType,
                             Iterable<String> eventDetails) {
        logOperation(requestId, userId, eventType, eventDetails, LocalDateTime.now(clock));
    }

    public void logOperation(String requestId,
                             String userId,
                             String eventType,
                             Iterable<String> eventDetails,
                             LocalDateTime timestamp) {
        Objects.requireNonNull(aiAuditService, "AIAuditService must be available");

        String auditRequestId = requestId != null ? requestId : "audit-" + timestamp.toEpochSecond(clock.getZone().getRules().getOffset(timestamp));
        String detailString = StreamSupport.stream(eventDetails != null ? eventDetails.spliterator() : Collections.<String>emptyList().spliterator(), false)
            .filter(Objects::nonNull)
            .collect(Collectors.joining(" | "));

        try {
            AIAuditRequest auditRequest = AIAuditRequest.builder()
                .requestId(auditRequestId)
                .userId(userId)
                .operationType(eventType)
                .timestamp(timestamp)
                .details(detailString)
                .metadata(Collections.emptyMap())
                .complianceRequirements(Collections.emptyList())
                .build();
            aiAuditService.logAuditEvent(auditRequest);
        } catch (Exception ex) {
            log.warn("Failed to log audit event [{}] - {}", eventType, ex.getMessage());
        }
    }

    private Optional<LocalDateTime> parseTimestamp(String timestampIso) {
        if (timestampIso == null || timestampIso.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDateTime.parse(timestampIso, ISO_FORMAT));
        } catch (Exception ex) {
            log.debug("Unable to parse timestamp '{}': {}", timestampIso, ex.getMessage());
            return Optional.empty();
        }
    }
}
