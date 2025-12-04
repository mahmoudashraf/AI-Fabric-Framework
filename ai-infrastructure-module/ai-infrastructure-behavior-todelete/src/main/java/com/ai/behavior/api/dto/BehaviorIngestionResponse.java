package com.ai.behavior.api.dto;

import lombok.Builder;
import lombok.Value;

import java.util.UUID;

@Value
@Builder
public class BehaviorIngestionResponse {
    UUID signalId;
    String status;

    public static BehaviorIngestionResponse accepted(UUID id) {
        return BehaviorIngestionResponse.builder()
            .signalId(id)
            .status("accepted")
            .build();
    }
}
