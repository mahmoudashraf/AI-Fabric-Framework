package com.ai.infrastructure.intent.orchestration.policy;

import com.ai.infrastructure.config.OrchestrationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Server-authoritative orchestration policy resolved per request.
 *
 * <p>This is an internal contract used by pipeline steps to avoid scattered flag reads and
 * to enable position/mode driven orchestration over time.</p>
 */
public record OrchestrationPolicy(
    OrchestrationProfile profile,
    String mode,
    String position,
    OrchestrationProperties.InformationMode informationMode,
    OrchestrationCapabilities capabilities,
    RagBudgets ragBudgets
) {

    public OrchestrationPolicy {
        profile = profile != null ? profile : OrchestrationProfile.DEFAULT;
        mode = normalize(mode);
        position = normalize(position);
        informationMode = informationMode != null ? informationMode : profile.defaultInformationMode();
        capabilities = capabilities != null ? capabilities : OrchestrationCapabilities.defaults();
        ragBudgets = ragBudgets != null ? ragBudgets : RagBudgets.defaults();
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record OrchestrationCapabilities(
        boolean actionsEnabled,
        boolean retrievalEnabled,
        boolean deepRetrievalEnabled,
        boolean suggestionsEnabled,
        boolean exposeReadProbeFallbackAttempt,
        boolean actionsPreferred,
        boolean knowledgeBaseOverviewEnabled,
        boolean retrievalAllowlistRequired,
        boolean vectorSpaceSelectionRequired,
        boolean minimizeRagWhenPinnedTargetsCoverRequest,
        boolean forceRetrievalWhenTargetsPresent,
        boolean forceRetrievalConsiderStoredTargets
    ) {
        public static OrchestrationCapabilities defaults() {
            return new OrchestrationCapabilities(true, true, false, true, false, false, true, false, false, true, false, false);
        }
    }

    public record RagBudgets(
        Boolean fanoutEnabled,
        Integer maxSpaces,
        Integer topKPerSpace,
        Integer maxDocumentsReturnedToClient,
        Integer maxDocumentsUsedForContext,
        Integer maxContextChars,
        List<String> retrievalVectorSpacesAllowlist,
        Double similarityThreshold
    ) {
        public RagBudgets(Boolean fanoutEnabled,
                          Integer maxSpaces,
                          Integer topKPerSpace,
                          Integer maxDocumentsReturnedToClient,
                          Integer maxDocumentsUsedForContext,
                          Integer maxContextChars,
                          List<String> retrievalVectorSpacesAllowlist) {
            this(
                fanoutEnabled,
                maxSpaces,
                topKPerSpace,
                maxDocumentsReturnedToClient,
                maxDocumentsUsedForContext,
                maxContextChars,
                retrievalVectorSpacesAllowlist,
                null
            );
        }

        public RagBudgets {
            retrievalVectorSpacesAllowlist = normalizeAllowlist(retrievalVectorSpacesAllowlist);
            similarityThreshold = normalizeSimilarityThreshold(similarityThreshold);
        }

        public static RagBudgets defaults() {
            return new RagBudgets(null, null, null, null, null, null, List.of(), null);
        }

        public boolean hasVectorSpaceAllowlist() {
            return retrievalVectorSpacesAllowlist != null && !retrievalVectorSpacesAllowlist.isEmpty();
        }

        private static List<String> normalizeAllowlist(List<String> input) {
            if (input == null || input.isEmpty()) {
                return List.of();
            }
            List<String> out = new ArrayList<>();
            for (String value : input) {
                if (!StringUtils.hasText(value)) {
                    continue;
                }
                String normalized = value.trim().toLowerCase(Locale.ROOT);
                if (normalized.isEmpty()) {
                    continue;
                }
                if (!out.contains(normalized)) {
                    out.add(normalized);
                }
            }
            return out.isEmpty() ? List.of() : Collections.unmodifiableList(out);
        }

        private static Double normalizeSimilarityThreshold(Double input) {
            if (input == null || !Double.isFinite(input) || input < 0.0d || input > 1.0d) {
                return null;
            }
            return input;
        }
    }
}
