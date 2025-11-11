package com.ai.infrastructure.dto;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Value;

/**
 * Summary of a behavior retention cleanup execution.
 */
@Value
@Builder
public class BehaviorRetentionResult {
    LocalDateTime timestamp;
    int evaluatedCount;
    int deletedCount;
    int skippedIndefiniteCount;
}
