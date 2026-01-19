package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.config.ProgressiveIntentExtractionProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentExtractionPostProcessor;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Progressive intent extraction with a bounded fallback ladder.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ai.intent-extraction.progressive",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class ProgressiveIntentExtractionEngine {

    private static final String METADATA_KEY_ATTEMPTS = "attempts";

    private final ProgressiveIntentExtractionProperties properties;
    private final CompoundIntentExtractionStrategy compoundStrategy;
    private final RepairIntentExtractionStrategy repairStrategy;
    private final MultiStepIntentExtractionStrategy multiStepStrategy;
    private final IntentExtractionPostProcessor postProcessor;

    public record ExtractionOutput(MultiIntentResponse response, Map<String, Object> diagnostics) {}

    public ExtractionOutput extract(String query, OrchestrationContext context) {
        if (!StringUtils.hasText(query)) {
            return new ExtractionOutput(safeDefault("Blank query"), Map.of("extractionPath", "fallback"));
        }

        OrchestrationContext safeContext = context != null ? context : OrchestrationContext.anonymous();
        safeContext.validate();

        List<Map<String, Object>> attemptEvents = new ArrayList<>();
        int totalLlmCalls = 0;

        String forceMode = normalizeMode(properties != null ? properties.getForceMode() : null);
        int maxCalls = properties != null ? properties.getMaxTotalLlmCalls() : 5;

        ExtractionAttempt compoundAttempt = null;
        if (!"multi_step".equals(forceMode)) {
            compoundAttempt = compoundStrategy.attemptExtract(query, safeContext);
            totalLlmCalls += compoundAttempt.getLlmCalls();
            attemptEvents.add(toAttemptEvent(compoundAttempt));

            if (compoundAttempt.isSuccess()) {
                MultiIntentResponse finalized = finalizeResponse(compoundAttempt.getResponse(), query);
                Map<String, Object> diagnostics = diagnostics("compound", attemptEvents, totalLlmCalls);
                return new ExtractionOutput(finalized, diagnostics);
            }
        }

        if ("compound".equals(forceMode)) {
            MultiIntentResponse fallback = safeDefault("Forced compound mode failed");
            return new ExtractionOutput(fallback, diagnostics("fallback", attemptEvents, totalLlmCalls));
        }

        boolean repairEnabled = properties != null && properties.isRepairEnabled();
        boolean shouldAttemptRepair = (forceMode.isEmpty() || "auto".equals(forceMode) || "repair".equals(forceMode));
        if (shouldAttemptRepair
            && (repairEnabled || "repair".equals(forceMode))
            && compoundAttempt != null
            && compoundAttempt.isStructuralFailure()
            && totalLlmCalls < maxCalls) {

            int configuredAttempts = properties != null ? properties.getRepairMaxAttempts() : 1;
            int maxRepairAttempts = Math.max("repair".equals(forceMode) ? 1 : 0, configuredAttempts);
            for (int attempt = 0; attempt < maxRepairAttempts && totalLlmCalls < maxCalls; attempt++) {
                ExtractionAttempt repairAttempt = repairStrategy.attemptRepair(query, safeContext, compoundAttempt);
                totalLlmCalls += repairAttempt.getLlmCalls();
                attemptEvents.add(toAttemptEvent(repairAttempt));

                if (repairAttempt.isSuccess()) {
                    MultiIntentResponse finalized = finalizeResponse(repairAttempt.getResponse(), query);
                    Map<String, Object> diagnostics = diagnostics("repair", attemptEvents, totalLlmCalls);
                    return new ExtractionOutput(finalized, diagnostics);
                }
            }
        }

        if ("repair".equals(forceMode)) {
            MultiIntentResponse fallback = safeDefault("Forced repair mode failed");
            return new ExtractionOutput(fallback, diagnostics("fallback", attemptEvents, totalLlmCalls));
        }

        boolean multiStepEnabled = "multi_step".equals(forceMode) || properties == null || properties.isMultiStepEnabled();
        int remainingCalls = maxCalls - totalLlmCalls;
        if (multiStepEnabled && remainingCalls >= 2) {
            ExtractionAttempt multiStepAttempt = multiStepStrategy.attemptExtract(query, safeContext);
            totalLlmCalls += multiStepAttempt.getLlmCalls();
            attemptEvents.add(toAttemptEvent(multiStepAttempt));

            if (multiStepAttempt.isSuccess()) {
                MultiIntentResponse finalized = finalizeResponse(multiStepAttempt.getResponse(), query);
                Map<String, Object> diagnostics = diagnostics("multi_step", attemptEvents, totalLlmCalls);
                return new ExtractionOutput(finalized, diagnostics);
            }
        }

        MultiIntentResponse fallback = safeDefault("Intent extraction failed after bounded attempts");
        return new ExtractionOutput(fallback, diagnostics("fallback", attemptEvents, totalLlmCalls));
    }

    private MultiIntentResponse finalizeResponse(MultiIntentResponse response, String originalQuery) {
        try {
            MultiIntentResponse processed = postProcessor.postProcess(response, originalQuery);
            if (processed == null || !processed.hasIntents()) {
                return safeDefault("Extraction produced empty intents");
            }
            return processed;
        } catch (Exception ex) {
            log.warn("Post-processing failed, returning fallback intent: {}", ex.getMessage());
            return safeDefault("Post-processing failed: " + ex.getMessage());
        }
    }

    private MultiIntentResponse safeDefault(String reason) {
        Intent fallbackIntent = Intent.builder()
            .type(IntentType.OUT_OF_SCOPE)
            .intent("out_of_scope")
            .confidence(0.0d)
            .requiresRetrieval(false)
            .requiresGeneration(false)
            .actionParams(Map.of("reason", reason))
            .build();

        return MultiIntentResponse.builder()
            .intents(List.of(fallbackIntent))
            .compound(false)
            .orchestrationStrategy("ADMIT_UNKNOWN")
            .metadata(Map.of("fallback", true))
            .build();
    }

    private String normalizeMode(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> diagnostics(String path, List<Map<String, Object>> attempts, int llmCalls) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("extractionPath", path);
        out.put("extractionAttempts", attempts != null ? attempts.size() : 0);
        out.put("llmCalls", llmCalls);
        if (attempts != null && !attempts.isEmpty()) {
            out.put(METADATA_KEY_ATTEMPTS, Collections.unmodifiableList(attempts));
        }
        return Collections.unmodifiableMap(out);
    }

    private Map<String, Object> toAttemptEvent(ExtractionAttempt attempt) {
        Map<String, Object> event = new LinkedHashMap<>();
        if (attempt == null) {
            event.put("strategy", null);
            event.put("success", false);
            event.put("llmCalls", 0);
            return Collections.unmodifiableMap(event);
        }
        event.put("strategy", attempt.getStrategyName());
        event.put("success", attempt.isSuccess());
        event.put("llmCalls", attempt.getLlmCalls());
        if (attempt.getValidationResult() != null) {
            IntentExtractionValidator.ValidationResult validation = attempt.getValidationResult();
            event.put("errorCategory", validation.errorCategory() != null ? validation.errorCategory().name() : null);
            if (validation.errors() != null && !validation.errors().isEmpty()) {
                event.put("errors", List.copyOf(validation.errors()));
            }
            if (validation.warnings() != null && !validation.warnings().isEmpty()) {
                event.put("warnings", List.copyOf(validation.warnings()));
            }
        }
        if (attempt.getErrorMessage() != null) {
            event.put("errorMessage", attempt.getErrorMessage());
        }
        return Collections.unmodifiableMap(event);
    }
}
