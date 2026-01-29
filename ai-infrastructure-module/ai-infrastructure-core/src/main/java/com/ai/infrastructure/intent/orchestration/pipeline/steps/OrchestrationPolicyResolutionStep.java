package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationPolicy;
import com.ai.infrastructure.intent.orchestration.policy.OrchestrationProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the server-authoritative {@link OrchestrationPolicy} for the current request.
 *
 * <p><strong>Order:</strong> 22 (after AccessControlStep (20), before ConversationEnrichmentStep (25)).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestrationPolicyResolutionStep implements PipelineStep {

    private static final String STEP_NAME = "OrchestrationPolicyResolution";
    private static final int STEP_ORDER = 22;

    private static final String METADATA_KEY_POLICY = "orchestrationPolicy";

    private static final String ERROR_UNSUPPORTED_POSITION_PREFIX = "Unsupported position: ";
    private static final String ERROR_UNSUPPORTED_MODE_PREFIX = "Unsupported mode: ";

    private final OrchestrationProperties orchestrationProperties;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public boolean shouldSkip(PipelineContext context) {
        return context == null || context.isShouldTerminate();
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        Objects.requireNonNull(context, "context must not be null");
        OrchestrationContext orchestrationContext = context.getOrchestrationContext();

        OrchestrationProfile profile = orchestrationProperties != null && orchestrationProperties.getProfile() != null
            ? orchestrationProperties.getProfile()
            : OrchestrationProfile.DEFAULT;

        String requestedPosition = orchestrationContext != null ? orchestrationContext.getPosition() : null;
        String requestedMode = orchestrationContext != null ? orchestrationContext.getMode() : null;
        String normalizedPosition = normalizeKey(requestedPosition);
        String normalizedRequestedMode = normalizeKey(requestedMode);

        String modeSource = null;
        String effectiveMode = null;

        String routedMode = normalizedPosition != null
            ? lookupCaseInsensitive(orchestrationProperties != null ? orchestrationProperties.getPositionRouting() : null, normalizedPosition)
            : null;

        if (routedMode != null) {
            String normalizedRoutedMode = normalizeKey(routedMode);
            OrchestrationProperties.ModeOverrides routedOverrides = normalizedRoutedMode != null
                ? lookupCaseInsensitive(orchestrationProperties != null ? orchestrationProperties.getModes() : null, normalizedRoutedMode)
                : null;

            if (routedOverrides != null) {
                effectiveMode = normalizedRoutedMode;
                modeSource = "POSITION";
            } else {
                return context.terminate(OrchestrationResult.error(ERROR_UNSUPPORTED_MODE_PREFIX + normalizedRoutedMode));
            }
        } else if (normalizedPosition != null && orchestrationProperties != null && orchestrationProperties.isStrictPositionRouting()) {
            return context.terminate(OrchestrationResult.error(ERROR_UNSUPPORTED_POSITION_PREFIX + normalizedPosition));
        }

        if (effectiveMode == null && normalizedRequestedMode != null) {
            OrchestrationProperties.ModeOverrides modeOverrides = lookupCaseInsensitive(
                orchestrationProperties != null ? orchestrationProperties.getModes() : null,
                normalizedRequestedMode
            );
            if (modeOverrides != null) {
                effectiveMode = normalizedRequestedMode;
                modeSource = "REQUEST_MODE";
            } else if (orchestrationProperties != null && orchestrationProperties.isStrictModeRouting()) {
                return context.terminate(OrchestrationResult.error(ERROR_UNSUPPORTED_MODE_PREFIX + normalizedRequestedMode));
            }
        }

        OrchestrationProperties.ModeOverrides effectiveModeOverrides = effectiveMode != null
            ? lookupCaseInsensitive(orchestrationProperties != null ? orchestrationProperties.getModes() : null, effectiveMode)
            : null;

        OrchestrationProperties.InformationMode informationModeEffective = profile.defaultInformationMode();

        if (effectiveModeOverrides != null) {
            if (effectiveModeOverrides.getInformationMode() != null) {
                informationModeEffective = effectiveModeOverrides.getInformationMode();
            }
        }

        if (orchestrationProperties != null && orchestrationProperties.getInformationMode() != null) {
            informationModeEffective = orchestrationProperties.getInformationMode();
        }

        OrchestrationPolicy policy = new OrchestrationPolicy(
            profile,
            effectiveMode,
            normalizedPosition,
            informationModeEffective
        );

        OrchestrationContext updatedOrchestrationContext = orchestrationContext != null
            ? orchestrationContext.toBuilder().orchestrationPolicy(policy).build()
            : null;

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("profile", policy.profile().name());
        debug.put("mode", policy.mode());
        debug.put("position", policy.position());
        debug.put("informationModeEffective", policy.informationMode().name());
        debug.put("modeSource", modeSource);

        PipelineContext updated = context.toBuilder()
            .orchestrationContext(updatedOrchestrationContext)
            .orchestrationPolicy(policy)
            .build()
            .withMetadata(METADATA_KEY_POLICY, Collections.unmodifiableMap(debug));

        log.debug("Resolved orchestration policy profile={}, mode={}, position={}, informationMode={} for request {}",
            policy.profile(),
            policy.mode(),
            policy.position(),
            policy.informationMode(),
            context.getRequestId());

        return updated;
    }

    private static String normalizeKey(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase();
    }

    private static <T> T lookupCaseInsensitive(Map<String, T> map, String normalizedKey) {
        if (map == null || map.isEmpty() || normalizedKey == null) {
            return null;
        }
        for (Map.Entry<String, T> entry : map.entrySet()) {
            String key = normalizeKey(entry.getKey());
            if (normalizedKey.equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
