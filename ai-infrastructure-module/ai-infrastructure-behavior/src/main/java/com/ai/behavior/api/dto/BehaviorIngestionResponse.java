package com.ai.behavior.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class BehaviorIngestionResponse {
    UUID eventId;
    String status;

    public static BehaviorIngestionResponse accepted(UUID id) {
        return BehaviorIngestionResponse.builder()
            .eventId(id)
            .status("accepted")
            .build();
    }
}
