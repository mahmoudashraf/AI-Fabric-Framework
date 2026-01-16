package com.ai.infrastructure.intent;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic validator for intent extraction responses.
 *
 * <p>Validation is system-fact driven and must not involve additional LLM calls.</p>
 */
@Component
@RequiredArgsConstructor
public class IntentExtractionValidator {

    private final ActionHandlerRegistry actionHandlerRegistry;

    public ValidationResult validate(MultiIntentResponse response) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (response == null) {
            errors.add("Response is null");
            return new ValidationResult(false, ErrorCategory.STRUCTURAL, errors, warnings);
        }

        if (response.getIntents() == null || response.getIntents().isEmpty()) {
            warnings.add("Response contains no intents");
            return new ValidationResult(true, ErrorCategory.NONE, errors, warnings);
        }

        for (int i = 0; i < response.getIntents().size(); i++) {
            validateIntent(response.getIntents().get(i), i, errors, warnings);
        }

        if (!errors.isEmpty()) {
            return new ValidationResult(false, categorizeErrors(errors), errors, warnings);
        }

        return new ValidationResult(true, ErrorCategory.NONE, errors, warnings);
    }

    private void validateIntent(Intent intent, int index, List<String> errors, List<String> warnings) {
        String prefix = "Intent[" + index + "]: ";

        if (intent == null) {
            errors.add(prefix + "Intent is null");
            return;
        }

        if (!intent.hasValidType()) {
            errors.add(prefix + "Missing or invalid type");
        }

        if (!intent.hasMeaningfulName()) {
            errors.add(prefix + "Missing intent name (intent or action field)");
        }

        if (intent.getType() == IntentType.ACTION) {
            validateActionIntent(intent, prefix, errors, warnings);
        } else if (intent.getType() == IntentType.INFORMATION) {
            validateInformationIntent(intent, prefix, warnings);
        }
    }

    private void validateActionIntent(Intent intent, String prefix, List<String> errors, List<String> warnings) {
        String actionName = StringUtils.hasText(intent.getAction())
            ? intent.getAction()
            : intent.getIntent();

        if (!StringUtils.hasText(actionName)) {
            errors.add(prefix + "ACTION intent missing action name");
            return;
        }

        if (actionHandlerRegistry != null && actionHandlerRegistry.findHandler(actionName).isEmpty()) {
            warnings.add(prefix + "No handler registered for action '" + actionName + "'");
        }
    }

    private void validateInformationIntent(Intent intent, String prefix, List<String> warnings) {
        if (Boolean.TRUE.equals(intent.getRequiresRetrieval()) && !StringUtils.hasText(intent.getVectorSpace())) {
            warnings.add(prefix + "INFORMATION intent requires retrieval but vectorSpace is missing");
        }
    }

    private ErrorCategory categorizeErrors(List<String> errors) {
        if (errors.stream().anyMatch(e -> e.toLowerCase().contains("null") || e.toLowerCase().contains("missing"))) {
            return ErrorCategory.STRUCTURAL;
        }
        if (errors.stream().anyMatch(e -> e.toLowerCase().contains("handler") || e.toLowerCase().contains("action"))) {
            return ErrorCategory.UNSAFE;
        }
        return ErrorCategory.OTHER;
    }

    public enum ErrorCategory {
        NONE,
        STRUCTURAL,
        UNSAFE,
        OTHER
    }

    public record ValidationResult(
        boolean valid,
        ErrorCategory errorCategory,
        List<String> errors,
        List<String> warnings
    ) {
        public boolean isStructuralFailure() {
            return !valid && errorCategory == ErrorCategory.STRUCTURAL;
        }
    }
}

