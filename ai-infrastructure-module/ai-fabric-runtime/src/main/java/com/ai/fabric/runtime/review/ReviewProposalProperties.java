package com.ai.fabric.runtime.review;

import ai.fabric.intent.action.AIActionNames;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Set;
import java.util.Collections;
import java.util.TreeSet;

@ConfigurationProperties("app.human-review.proposals")
public record ReviewProposalProperties(boolean enabled, Set<String> allowedActions, TargetLookup targetLookup) {
    @ConstructorBinding
    public ReviewProposalProperties {
        Set<String> normalizedActions = new TreeSet<>();
        if (allowedActions != null) allowedActions.forEach(action -> normalizedActions.add(AIActionNames.normalize(action)));
        allowedActions = Collections.unmodifiableSet(normalizedActions);
        if (enabled && allowedActions.isEmpty()) {
            throw new IllegalArgumentException("Review proposals require explicitly configured actions.");
        }
    }

    public ReviewProposalProperties(boolean enabled, Set<String> allowedActions) {
        this(enabled, allowedActions, null);
    }

    public record TargetLookup(String action, String parameter, String vectorSpace) {
        public TargetLookup {
            action = AIActionNames.normalize(action);
            if (action == null || action.isBlank() || parameter == null
                || !parameter.matches("[A-Za-z][A-Za-z0-9_]{0,79}")) {
                throw new IllegalArgumentException("A target lookup requires a registered READ action and parameter.");
            }
            vectorSpace = vectorSpace == null || vectorSpace.isBlank() ? null : vectorSpace.trim().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
