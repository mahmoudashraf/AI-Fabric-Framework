package com.ai.infrastructure.intent.orchestration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Normalize {@link OrchestrationResult} into a provider-agnostic, deterministic contract.
 *
 * <p>This component is intentionally conservative: it only normalizes outcomes where the
 * system has deterministic facts (e.g., missing action handler, any child ERROR).</p>
 */
@Component
@RequiredArgsConstructor
public class OrchestrationResultNormalizer {

    // Canonical error codes (kept as strings to avoid leaking new public enums)
    public static final String ERROR_CODE_ACTION_NOT_FOUND = "ACTION_NOT_FOUND";
    public static final String ERROR_CODE_CHILD_ERROR = "CHILD_ERROR";
    public static final String ERROR_CODE_GENERATION_FAILED = "GENERATION_FAILED";

    /**
     * Normalize the given result. If no normalization is applicable, returns the original result.
     */
    public OrchestrationResult normalize(OrchestrationResult raw) {
        if (raw == null) {
            return null;
        }

        // Rule: normalize compound wrappers into a stable top-level type.
        // IMPORTANT: a compound request may contain a successful primary child plus a non-primary child ERROR
        // (commonly ACTION_NOT_FOUND from a follow-up "summarize/explain" misclassified as ACTION).
        // We must not sink the entire request to ERROR in that case.
        if (raw.getType() == OrchestrationResultType.COMPOUND_HANDLED) {
            OrchestrationResult primary = choosePrimaryChild(raw.getChildren());
            if (primary != null && primary.getType() != null && primary.getType() != OrchestrationResultType.ERROR) {
                OrchestrationResult childError = firstChildError(raw.getChildren());
                if (childError != null) {
                    String errorCode = StringUtils.hasText(childError.getErrorCode())
                        ? childError.getErrorCode()
                        : deriveErrorCodeFromMessage(childError.getMessage());
                    if (!StringUtils.hasText(errorCode)) {
                        errorCode = ERROR_CODE_CHILD_ERROR;
                    }

                    // Soft child error: do not sink the compound when primary succeeded.
                    if (ERROR_CODE_ACTION_NOT_FOUND.equals(errorCode) || isGenerationFailure(childError)) {
                        String softCode = ERROR_CODE_ACTION_NOT_FOUND.equals(errorCode)
                            ? errorCode
                            : ERROR_CODE_GENERATION_FAILED;
                        Map<String, Object> metadata = withSoftChildErrorMetadata(raw.getMetadata(), softCode);
                        return promoteCompoundPrimary(raw, primary, metadata);
                    }

                    // Hard child error: bubble deterministically.
                    return bubbleChildError(raw, childError, errorCode);
                }

                return promoteCompoundPrimary(raw, primary, raw.getMetadata());
            }
        }

        // Rule: bubble any remaining child error to a top-level ERROR deterministically.
        OrchestrationResult childError = firstChildError(raw.getChildren());
        if (childError != null) {
            String errorCode = StringUtils.hasText(childError.getErrorCode())
                ? childError.getErrorCode()
                : deriveErrorCodeFromMessage(childError.getMessage());

            if (!StringUtils.hasText(errorCode)) {
                errorCode = ERROR_CODE_CHILD_ERROR;
            }

            return bubbleChildError(raw, childError, errorCode);
        }

        // Rule: if the result is already an ERROR but lacks errorCode, try to derive.
        if (raw.getType() == OrchestrationResultType.ERROR && !StringUtils.hasText(raw.getErrorCode())) {
            String derived = deriveErrorCodeFromDataOrMessage(raw.getData(), raw.getMessage());
            if (StringUtils.hasText(derived)) {
                raw.setErrorCode(derived);
                raw.setMetadata(withErrorCodeMetadata(raw.getMetadata(), derived));
            }
        }

        return raw;
    }

    private OrchestrationResult bubbleChildError(OrchestrationResult raw, OrchestrationResult childError, String errorCode) {
        String message = StringUtils.hasText(childError.getMessage())
            ? childError.getMessage()
            : (StringUtils.hasText(raw.getMessage()) ? raw.getMessage() : "An error occurred.");

        Map<String, Object> metadata = withErrorCodeMetadata(raw.getMetadata(), errorCode);
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.ERROR)
            .success(false)
            .message(message)
            .errorCode(errorCode)
            // preserve details for debugging/auditing
            .data(raw.getData())
            .children(raw.getChildren())
            .nextSteps(raw.getNextSteps())
            .metadata(metadata)
            .smartSuggestion(raw.getSmartSuggestion())
            .build();
    }

    private OrchestrationResult promoteCompoundPrimary(OrchestrationResult raw,
                                                       OrchestrationResult primary,
                                                       Map<String, Object> metadata) {
        OrchestrationResultType normalizedType = primary.getType();
        boolean normalizedSuccess = switch (normalizedType) {
            case ACTION_EXECUTED, INFORMATION_PROVIDED -> primary.isSuccess();
            case OUT_OF_SCOPE -> true;
            case ACTION_DENIED -> false;
            default -> raw.isSuccess();
        };

        String message = StringUtils.hasText(primary.getMessage()) ? primary.getMessage() : raw.getMessage();
        Map<String, Object> data = (primary.getData() != null && !primary.getData().isEmpty())
            ? primary.getData()
            : raw.getData();

        return OrchestrationResult.builder()
            .type(normalizedType)
            .success(normalizedSuccess)
            .message(message)
            .errorCode(raw.getErrorCode())
            .data(data)
            .children(raw.getChildren())
            .nextSteps(raw.getNextSteps())
            .metadata(metadata)
            .smartSuggestion(raw.getSmartSuggestion())
            .build();
    }

    private OrchestrationResult firstChildError(List<OrchestrationResult> children) {
        if (children == null || children.isEmpty()) {
            return null;
        }
        return children.stream()
            .filter(child -> child != null && child.getType() == OrchestrationResultType.ERROR)
            .findFirst()
            .orElse(null);
    }

    private OrchestrationResult choosePrimaryChild(List<OrchestrationResult> children) {
        if (children == null || children.isEmpty()) {
            return null;
        }

        // Priority order: actions > information > scope (provider-agnostic and stable).
        return firstByType(children, OrchestrationResultType.ACTION_EXECUTED,
            OrchestrationResultType.ACTION_DENIED,
            OrchestrationResultType.INFORMATION_PROVIDED,
            OrchestrationResultType.OUT_OF_SCOPE,
            OrchestrationResultType.COMPOUND_HANDLED);
    }

    private OrchestrationResult firstByType(List<OrchestrationResult> children, OrchestrationResultType... types) {
        for (OrchestrationResultType type : types) {
            for (OrchestrationResult child : children) {
                if (child != null && child.getType() == type) {
                    return child;
                }
            }
        }
        return null;
    }

    private String deriveErrorCodeFromDataOrMessage(Map<String, Object> data, String message) {
        String fromActionResult = extractActionResultErrorCode(data);
        if (StringUtils.hasText(fromActionResult)) {
            return fromActionResult;
        }
        return deriveErrorCodeFromMessage(message);
    }

    private String extractActionResultErrorCode(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        Object actionResult = data.get("actionResult");
        if (actionResult instanceof com.ai.infrastructure.intent.action.ActionResult ar) {
            return ar.getErrorCode();
        }
        if (actionResult instanceof Map<?, ?> map) {
            Object code = map.get("errorCode");
            return code != null ? String.valueOf(code) : null;
        }
        return null;
    }

    private String deriveErrorCodeFromMessage(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        String m = message.toLowerCase();
        if (m.contains("no action handler registered")) {
            return ERROR_CODE_ACTION_NOT_FOUND;
        }
        return null;
    }

    private Map<String, Object> withErrorCodeMetadata(Map<String, Object> existing, String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return existing;
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing != null ? existing : Map.of());
        merged.put("errorCode", errorCode);
        return Map.copyOf(merged);
    }

    private Map<String, Object> withSoftChildErrorMetadata(Map<String, Object> existing, String errorCode) {
        if (!StringUtils.hasText(errorCode)) {
            return existing;
        }
        Map<String, Object> merged = new LinkedHashMap<>(existing != null ? existing : Map.of());
        merged.put("softChildErrorCode", errorCode);
        return Map.copyOf(merged);
    }

    private boolean isGenerationFailure(OrchestrationResult childError) {
        if (childError == null || childError.getType() != OrchestrationResultType.ERROR) {
            return false;
        }
        // Signal emitted by IntentHandlingStep.handleInformationBasic() when LLM generation fails.
        Map<String, Object> data = childError.getData();
        return data != null && data.containsKey("generationError");
    }
}

