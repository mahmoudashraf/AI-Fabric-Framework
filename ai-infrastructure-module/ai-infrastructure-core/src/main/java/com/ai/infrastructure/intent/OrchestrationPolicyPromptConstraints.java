package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.orchestration.policy.OrchestrationPolicy;
import org.springframework.util.StringUtils;

import java.util.stream.Collectors;

/**
 * Builds minimal, server-authoritative prompting constraints derived from the resolved orchestration policy.
 *
 * <p>This is intentionally short and deterministic. It is used to reduce LLM confusion by explicitly stating which
 * capabilities are enabled for the current request (actions vs retrieval), while still enforcing the same rules in
 * the pipeline (fail-closed).</p>
 */
public final class OrchestrationPolicyPromptConstraints {

    private OrchestrationPolicyPromptConstraints() {}

    public static String buildSystemAddon(OrchestrationPolicy policy) {
        if (policy == null || policy.capabilities() == null) {
            return "";
        }

        OrchestrationPolicy.OrchestrationCapabilities capabilities = policy.capabilities();
        OrchestrationPolicy.RagBudgets budgets = policy.ragBudgets();

        boolean isDefaultCapabilities =
            capabilities.actionsEnabled()
                && capabilities.retrievalEnabled()
                && !capabilities.deepRetrievalEnabled()
                && capabilities.suggestionsEnabled();
        boolean hasAllowlist = budgets != null && budgets.hasVectorSpaceAllowlist();
        if (isDefaultCapabilities && !hasAllowlist) {
            return "";
        }

        StringBuilder out = new StringBuilder(256);
        out.append("MODE CAPABILITIES (server-enforced):\n");
        out.append("- actionsEnabled=").append(capabilities.actionsEnabled()).append("\n");
        out.append("- retrievalEnabled=").append(capabilities.retrievalEnabled()).append("\n");
        out.append("- deepRetrievalEnabled=").append(capabilities.deepRetrievalEnabled()).append("\n");

        if (!capabilities.actionsEnabled()) {
            out.append("- Do NOT output ACTION intents in this mode.\n");
        }
        if (!capabilities.retrievalEnabled()) {
            out.append("- Do NOT request retrieval (requiresRetrieval=false).\n");
        }

        if (budgets != null && budgets.hasVectorSpaceAllowlist()) {
            String allowed = budgets.retrievalVectorSpacesAllowlist().stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));
            if (StringUtils.hasText(allowed)) {
                out.append("- Retrieval vectorSpace allowlist: ").append(allowed).append("\n");
                out.append("  If you set vectorSpace, it MUST be one of these values.\n");
            }
        }

        return out.toString().trim();
    }
}
