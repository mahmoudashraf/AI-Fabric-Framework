package com.ai.infrastructure.behavior.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
public class UserEventBatch {
    private UUID userId;
    private List<ExternalEvent> events;
    private int totalEventCount;
    private Map<String, Object> userContext;
}
