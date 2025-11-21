package com.ai.behavior.service;

import com.ai.behavior.model.BehaviorInsights;
import com.ai.behavior.repository.BehaviorInsightsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BehaviorSearchService {

    private final BehaviorInsightsRepository insightsRepository;

    public List<BehaviorInsights> search(SearchParameters parameters) {
        if (parameters == null) {
            return List.of();
        }

        List<BehaviorInsights> candidates = resolveCandidates(parameters);

        return candidates.stream()
            .filter(insight -> matchesSegment(insight, parameters.segment()))
            .filter(insight -> matchesPattern(insight, parameters.pattern()))
            .filter(insight -> meetsConfidence(insight, parameters.minConfidence()))
            .limit(parameters.limit())
            .collect(Collectors.toList());
    }

    private List<BehaviorInsights> resolveCandidates(SearchParameters parameters) {
        if (!CollectionUtils.isEmpty(parameters.userIds())) {
            return insightsRepository.findByUserIdIn(parameters.userIds());
        }
        if (StringUtils.hasText(parameters.segment())) {
            return insightsRepository.findBySegmentOrderByAnalyzedAtDesc(parameters.segment())
                .stream()
                .limit(parameters.limit())
                .toList();
        }
        return insightsRepository.findAll(PageRequest.of(0, parameters.limit())).getContent();
    }

    private boolean matchesSegment(BehaviorInsights insights, String segment) {
        if (!StringUtils.hasText(segment)) {
            return true;
        }
        return segment.equalsIgnoreCase(insights.getSegment());
    }

    private boolean matchesPattern(BehaviorInsights insights, String pattern) {
        if (!StringUtils.hasText(pattern)) {
            return true;
        }
        if (insights.getPatterns() == null) {
            return false;
        }
        String normalized = pattern.toLowerCase(Locale.ROOT);
        return insights.getPatterns().stream()
            .filter(StringUtils::hasText)
            .map(value -> value.toLowerCase(Locale.ROOT))
            .anyMatch(value -> value.contains(normalized));
    }

    private boolean meetsConfidence(BehaviorInsights insights, Double minConfidence) {
        if (minConfidence == null) {
            return true;
        }
        if (insights.getScores() == null) {
            return false;
        }
        Double confidence = insights.getScores().getOrDefault("confidenceScore", 0.0);
        return confidence != null && confidence >= minConfidence;
    }

    public record SearchParameters(
        String segment,
        String pattern,
        Double minConfidence,
        List<UUID> userIds,
        int limit
    ) {
        public SearchParameters {
            limit = limit <= 0 ? 25 : Math.min(limit, 500);
            userIds = userIds == null ? Collections.emptyList() : List.copyOf(userIds);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder {
            private String segment;
            private String pattern;
            private Double minConfidence;
            private List<UUID> userIds;
            private int limit = 25;

            public Builder segment(String segment) {
                this.segment = segment;
                return this;
            }

            public Builder pattern(String pattern) {
                this.pattern = pattern;
                return this;
            }

            public Builder minConfidence(Double minConfidence) {
                this.minConfidence = minConfidence;
                return this;
            }

            public Builder userIds(List<UUID> userIds) {
                this.userIds = userIds;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public SearchParameters build() {
                return new SearchParameters(segment, pattern, minConfidence, userIds, limit);
            }
        }
    }
}
