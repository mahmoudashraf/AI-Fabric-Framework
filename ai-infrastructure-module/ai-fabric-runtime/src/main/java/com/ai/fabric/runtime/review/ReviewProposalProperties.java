package com.ai.fabric.runtime.review;

import ai.fabric.intent.action.AIActionNames;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties("app.human-review.proposals")
public record ReviewProposalProperties(boolean enabled, Set<String> allowedActions) {
    public ReviewProposalProperties {
        allowedActions = allowedActions == null ? Set.of() : allowedActions.stream()
            .map(AIActionNames::normalize).collect(Collectors.toUnmodifiableSet());
        if (enabled && allowedActions.isEmpty()) {
            throw new IllegalArgumentException("Review proposals require explicitly configured actions.");
        }
    }
}
