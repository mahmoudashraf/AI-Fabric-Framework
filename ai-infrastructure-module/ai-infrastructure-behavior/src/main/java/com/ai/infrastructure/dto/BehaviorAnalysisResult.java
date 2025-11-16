package com.ai.infrastructure.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.List;

@Value
@Builder
public class BehaviorAnalysisResult {
    String analysisId;
    String userId;
    String analysisType;
    String summary;
    List<String> insights;
    List<String> patterns;
    List<String> recommendations;
    Double confidenceScore;
    Double significanceScore;
    LocalDateTime analyzedAt;
}
