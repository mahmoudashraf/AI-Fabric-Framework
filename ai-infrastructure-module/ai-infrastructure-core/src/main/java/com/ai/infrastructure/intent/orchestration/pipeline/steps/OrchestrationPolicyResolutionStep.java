package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
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

    private static final String ERROR_UNSUPPORTED_MODE_PREFIX = "Unsupported mode: ";
    private static final String ERROR_UNSUPPORTED_DEFAULT_MODE_PREFIX = "Unsupported default mode: ";

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

        if (effectiveMode == null) {
            String normalizedDefaultMode = normalizeKey(orchestrationProperties != null ? orchestrationProperties.getDefaultMode() : null);
            if (normalizedDefaultMode != null) {
                OrchestrationProperties.ModeOverrides defaultOverrides = lookupCaseInsensitive(
                    orchestrationProperties != null ? orchestrationProperties.getModes() : null,
                    normalizedDefaultMode
                );
                if (defaultOverrides != null) {
                    effectiveMode = normalizedDefaultMode;
                    modeSource = "DEFAULT_MODE";
                } else {
                    return context.terminate(OrchestrationResult.error(ERROR_UNSUPPORTED_DEFAULT_MODE_PREFIX + normalizedDefaultMode));
                }
            }
        }

        OrchestrationProperties.ModeOverrides effectiveModeOverrides = effectiveMode != null
            ? lookupCaseInsensitive(orchestrationProperties != null ? orchestrationProperties.getModes() : null, effectiveMode)
            : null;

        OrchestrationProperties.InformationMode informationModeEffective = profile.defaultInformationMode();
        Boolean advancedRagOverride = null;

        if (effectiveModeOverrides != null) {
            // Capability bundle (mode-level gating).
            // Defaults preserve existing behavior when unset (greenfield-compatible, but non-breaking).
            if (effectiveModeOverrides.getInformationMode() != null) {
                informationModeEffective = effectiveModeOverrides.getInformationMode();
            }
            if (effectiveModeOverrides.getUseAdvancedRag() != null) {
                advancedRagOverride = effectiveModeOverrides.getUseAdvancedRag();
            }
        }

        if (orchestrationProperties != null && orchestrationProperties.getInformationMode() != null) {
            informationModeEffective = orchestrationProperties.getInformationMode();
        }

        boolean actionsEnabled = effectiveModeOverrides != null && effectiveModeOverrides.getActionsEnabled() != null
            ? effectiveModeOverrides.getActionsEnabled()
            : true;
        boolean retrievalEnabled = effectiveModeOverrides != null && effectiveModeOverrides.getRetrievalEnabled() != null
            ? effectiveModeOverrides.getRetrievalEnabled()
            : true;
        boolean deepRetrievalEnabled = effectiveModeOverrides != null && effectiveModeOverrides.getDeepRetrievalEnabled() != null
            ? effectiveModeOverrides.getDeepRetrievalEnabled()
            : false;
        boolean suggestionsEnabled = effectiveModeOverrides != null && effectiveModeOverrides.getSuggestionsEnabled() != null
            ? effectiveModeOverrides.getSuggestionsEnabled()
            : true;

        boolean actionsPreferred = effectiveModeOverrides != null && effectiveModeOverrides.getActionsPreferred() != null
            ? effectiveModeOverrides.getActionsPreferred()
            : false;

        boolean knowledgeBaseOverviewEnabled = effectiveModeOverrides != null && effectiveModeOverrides.getKnowledgeBaseOverviewEnabled() != null
            ? effectiveModeOverrides.getKnowledgeBaseOverviewEnabled()
            : true;

        boolean retrievalAllowlistRequired = effectiveModeOverrides != null && effectiveModeOverrides.getRetrievalAllowlistRequired() != null
            ? effectiveModeOverrides.getRetrievalAllowlistRequired()
            : false;

        boolean vectorSpaceSelectionRequired = effectiveModeOverrides != null && effectiveModeOverrides.getVectorSpaceSelectionRequired() != null
            ? effectiveModeOverrides.getVectorSpaceSelectionRequired()
            : false;

        boolean exposeReadProbeFallbackAttempt = orchestrationProperties != null
            && orchestrationProperties.isExposeReadProbeFallbackAttempt();
        if (effectiveModeOverrides != null && effectiveModeOverrides.getExposeReadProbeFallbackAttempt() != null) {
            exposeReadProbeFallbackAttempt = effectiveModeOverrides.getExposeReadProbeFallbackAttempt();
        }

        OrchestrationPolicy.RagBudgets ragBudgets = null;
        if (effectiveModeOverrides != null && effectiveModeOverrides.getRag() != null) {
            OrchestrationProperties.RagModeOverrides rag = effectiveModeOverrides.getRag();
            ragBudgets = new OrchestrationPolicy.RagBudgets(
                rag.getFanoutEnabled(),
                rag.getMaxSpaces(),
                rag.getTopKPerSpace(),
                rag.getMaxDocumentsReturnedToClient(),
                rag.getMaxDocumentsUsedForContext(),
                rag.getMaxContextChars(),
                rag.getRetrievalVectorSpacesAllowlist()
            );
        }

        OrchestrationPolicy policy = new OrchestrationPolicy(
            profile,
            effectiveMode,
            normalizedPosition,
            informationModeEffective,
            new OrchestrationPolicy.OrchestrationCapabilities(
                actionsEnabled,
                retrievalEnabled,
                deepRetrievalEnabled,
                suggestionsEnabled,
                exposeReadProbeFallbackAttempt,
                actionsPreferred,
                knowledgeBaseOverviewEnabled,
                retrievalAllowlistRequired,
                vectorSpaceSelectionRequired
            ),
            ragBudgets
        );

        OrchestrationContext updatedOrchestrationContext = orchestrationContext;
        if (orchestrationContext != null) {
            if (advancedRagOverride != null) {
                Map<String, Object> meta = new LinkedHashMap<>(orchestrationContext.getMetadata() != null
                    ? orchestrationContext.getMetadata()
                    : Map.of());
                meta.put(OrchestrationContextMetadataKeys.USE_ADVANCED_RAG, advancedRagOverride);
                updatedOrchestrationContext = orchestrationContext.toBuilder()
                    .metadata(Collections.unmodifiableMap(meta))
                    .orchestrationPolicy(policy)
                    .build();
            } else {
                updatedOrchestrationContext = orchestrationContext.toBuilder()
                    .orchestrationPolicy(policy)
                    .build();
            }
        }

        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("profile", policy.profile().name());
        debug.put("mode", policy.mode());
        debug.put("position", policy.position());
        debug.put("informationModeEffective", policy.informationMode().name());
        debug.put("modeSource", modeSource);
        debug.put("actionsEnabled", policy.capabilities().actionsEnabled());
        debug.put("retrievalEnabled", policy.capabilities().retrievalEnabled());
        debug.put("deepRetrievalEnabled", policy.capabilities().deepRetrievalEnabled());
        debug.put("suggestionsEnabled", policy.capabilities().suggestionsEnabled());
        debug.put("exposeReadProbeFallbackAttempt", policy.capabilities().exposeReadProbeFallbackAttempt());
        debug.put("actionsPreferred", policy.capabilities().actionsPreferred());
        debug.put("knowledgeBaseOverviewEnabled", policy.capabilities().knowledgeBaseOverviewEnabled());
        debug.put("retrievalAllowlistRequired", policy.capabilities().retrievalAllowlistRequired());
        debug.put("vectorSpaceSelectionRequired", policy.capabilities().vectorSpaceSelectionRequired());
        if (advancedRagOverride != null) {
            debug.put("advancedRagOverride", advancedRagOverride);
            debug.put("advancedRagOverrideSource", "MODE");
        }
        if (policy.ragBudgets() != null) {
            OrchestrationPolicy.RagBudgets b = policy.ragBudgets();
            if (b.fanoutEnabled() != null) {
                debug.put("ragFanoutEnabled", b.fanoutEnabled());
            }
            if (b.maxSpaces() != null) {
                debug.put("ragMaxSpaces", b.maxSpaces());
            }
            if (b.topKPerSpace() != null) {
                debug.put("ragTopKPerSpace", b.topKPerSpace());
            }
            if (b.maxDocumentsReturnedToClient() != null) {
                debug.put("ragMaxDocumentsReturnedToClient", b.maxDocumentsReturnedToClient());
            }
            if (b.maxDocumentsUsedForContext() != null) {
                debug.put("ragMaxDocumentsUsedForContext", b.maxDocumentsUsedForContext());
            }
            if (b.maxContextChars() != null) {
                debug.put("ragMaxContextChars", b.maxContextChars());
            }
            if (b.hasVectorSpaceAllowlist()) {
                debug.put("ragRetrievalVectorSpacesAllowlist", b.retrievalVectorSpacesAllowlist());
            }
        }

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
