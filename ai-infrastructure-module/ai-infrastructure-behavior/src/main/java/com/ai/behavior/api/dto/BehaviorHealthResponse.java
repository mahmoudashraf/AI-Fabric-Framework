package com.ai.behavior.api.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class BehaviorHealthResponse {
    long totalEvents;
    long recentEvents;
    String sinkType;
    boolean healthy;
    String message;
}
