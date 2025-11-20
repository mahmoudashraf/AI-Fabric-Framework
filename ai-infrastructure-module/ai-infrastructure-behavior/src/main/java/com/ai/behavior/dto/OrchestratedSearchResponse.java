package com.ai.behavior.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrchestratedSearchResponse {

    private String query;
    private Instant executedAt;
    private boolean piiDetected;
    private String error;
    private SearchResults results;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SearchResults {
        @Builder.Default
        private List<UUID> matchedUsers = Collections.emptyList();
        private long totalMatches;
        private String searchStrategy;
        private String aiExplanation;
        @Builder.Default
        private List<BehaviorInsightView> insights = Collections.emptyList();
    }
}
