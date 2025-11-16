package com.ai.behavior.api.dto;

import com.ai.behavior.model.BehaviorAlert;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Value
@Builder
public class BehaviorAlertResponse {
    UUID id;
    UUID userId;
    UUID behaviorEventId;
    String alertType;
    String severity;
    String message;
    Map<String, Object> context;
    LocalDateTime detectedAt;

    public static BehaviorAlertResponse from(BehaviorAlert alert) {
        return BehaviorAlertResponse.builder()
            .id(alert.getId())
            .userId(alert.getUserId())
            .behaviorEventId(alert.getBehaviorEventId())
            .alertType(alert.getAlertType())
            .severity(alert.getSeverity())
            .message(alert.getMessage())
            .context(alert.getContext())
            .detectedAt(alert.getDetectedAt())
            .build();
    }
}
