package com.ai.infrastructure.intent.orchestration.policy;

import com.ai.infrastructure.config.IntentExtractionPromptProperties;
import com.ai.infrastructure.config.OrchestrationProperties;
import org.springframework.util.StringUtils;

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
    IntentExtractionPromptProperties.PromptMode promptMode
) {

    public OrchestrationPolicy {
        profile = profile != null ? profile : OrchestrationProfile.DEFAULT;
        mode = normalize(mode);
        position = normalize(position);
        informationMode = informationMode != null ? informationMode : profile.defaultInformationMode();
        promptMode = promptMode != null ? promptMode : profile.defaultPromptMode();
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
