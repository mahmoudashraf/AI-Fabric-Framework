package com.ai.infrastructure.intent;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
        List<ValidationIssue> issues = new ArrayList<>();

        if (response == null) {
            issues.add(ValidationIssue.error(IssueCode.RESPONSE_NULL, -1, null, "Response is null"));
            return toResult(issues);
        }

        if (response.getIntents() == null || response.getIntents().isEmpty()) {
            issues.add(ValidationIssue.warn(IssueCode.RESPONSE_EMPTY_INTENTS, -1, "intents", "Response contains no intents"));
            return toResult(issues);
        }

        for (int i = 0; i < response.getIntents().size(); i++) {
            validateIntent(response.getIntents().get(i), i, issues);
        }

        return toResult(issues);
    }

    private void validateIntent(Intent intent, int index, List<ValidationIssue> issues) {
        if (intent == null) {
            issues.add(ValidationIssue.error(IssueCode.INTENT_NULL, index, null, "Intent is null"));
            return;
        }

        if (!intent.hasValidType()) {
            issues.add(ValidationIssue.error(IssueCode.INTENT_MISSING_TYPE, index, "type", "Missing or invalid type"));
        }

        // Provider-agnostic tolerance: OUT_OF_SCOPE intents may omit intent/action names.
        if (!intent.hasMeaningfulName() && intent.getType() != IntentType.OUT_OF_SCOPE) {
            issues.add(ValidationIssue.error(IssueCode.INTENT_MISSING_NAME, index, "intent", "Missing intent name (intent or action field)"));
        }

        if (intent.getType() == IntentType.ACTION) {
            validateActionIntent(intent, index, issues);
        } else if (intent.getType() == IntentType.INFORMATION) {
            validateInformationIntent(intent, index, issues);
        }
    }

    private void validateActionIntent(Intent intent, int index, List<ValidationIssue> issues) {
        String actionName = StringUtils.hasText(intent.getAction())
            ? intent.getAction()
            : intent.getIntent();

        if (!StringUtils.hasText(actionName)) {
            issues.add(ValidationIssue.error(IssueCode.ACTION_MISSING_NAME, index, "action", "ACTION intent missing action name"));
            return;
        }

        if (actionHandlerRegistry == null) {
            issues.add(ValidationIssue.warn(IssueCode.ACTION_REGISTRY_UNAVAILABLE, index, "action", "Action handler registry unavailable; skipping action validation"));
            return;
        }

        // Canonicalize action aliases ("relationship query" etc.) via registry metadata.
        AIActionMetaData meta = actionHandlerRegistry.findMetadata(actionName).orElse(null);
        String canonical = meta != null && StringUtils.hasText(meta.getName()) ? meta.getName() : actionName;

        if (actionHandlerRegistry.findHandler(canonical).isEmpty()) {
            // Unknown action names are unsafe: they will fail later in action execution.
            issues.add(ValidationIssue.error(IssueCode.ACTION_UNREGISTERED, index, "action", "No handler registered for action '" + canonical + "'"));
            return;
        }

        Set<String> required = meta != null ? meta.getRequiredParameters() : Collections.emptySet();
        if (required == null || required.isEmpty()) {
            return;
        }

        Map<String, Object> params = intent.getActionParams();
        for (String param : required) {
            if (!StringUtils.hasText(param)) {
                continue;
            }
            Object value = params != null ? params.get(param) : null;
            if (value == null || value.toString().isBlank()) {
                issues.add(ValidationIssue.error(
                    IssueCode.ACTION_REQUIRED_PARAM_MISSING,
                    index,
                    "actionParams." + param,
                    "Missing required action parameter '" + param + "' for action '" + canonical + "'"
                ));
            }
        }
    }

    private void validateInformationIntent(Intent intent, int index, List<ValidationIssue> issues) {
        if (Boolean.TRUE.equals(intent.getRequiresRetrieval()) && !StringUtils.hasText(intent.getVectorSpace())) {
            issues.add(ValidationIssue.warn(
                IssueCode.INFORMATION_MISSING_VECTOR_SPACE,
                index,
                "vectorSpace",
                "INFORMATION intent requires retrieval but vectorSpace is missing"
            ));
        }
    }

    public enum ErrorCategory {
        NONE,
        STRUCTURAL,
        UNSAFE,
        INCOMPLETE,
        OTHER
    }

    public enum Severity {
        ERROR,
        WARNING
    }

    public enum IssueCode {
        RESPONSE_NULL,
        RESPONSE_EMPTY_INTENTS,
        INTENT_NULL,
        INTENT_MISSING_TYPE,
        INTENT_MISSING_NAME,
        ACTION_MISSING_NAME,
        ACTION_REGISTRY_UNAVAILABLE,
        ACTION_UNREGISTERED,
        ACTION_REQUIRED_PARAM_MISSING,
        INFORMATION_MISSING_VECTOR_SPACE
    }

    public record ValidationIssue(
        IssueCode code,
        Severity severity,
        int intentIndex,
        String field,
        String message
    ) {
        static ValidationIssue error(IssueCode code, int index, String field, String message) {
            return new ValidationIssue(code, Severity.ERROR, index, field, message);
        }

        static ValidationIssue warn(IssueCode code, int index, String field, String message) {
            return new ValidationIssue(code, Severity.WARNING, index, field, message);
        }
    }

    public record ValidationResult(
        boolean valid,
        ErrorCategory errorCategory,
        List<ValidationIssue> issues,
        List<String> errors,
        List<String> warnings
    ) {
        public ValidationResult(boolean valid, ErrorCategory errorCategory, List<String> errors, List<String> warnings) {
            this(valid, errorCategory, List.of(), errors, warnings);
        }

        public boolean isStructuralFailure() {
            return !valid && errorCategory == ErrorCategory.STRUCTURAL;
        }
    }

    private ValidationResult toResult(List<ValidationIssue> issues) {
        List<ValidationIssue> safeIssues = issues == null ? List.of() : List.copyOf(issues);
        List<String> errors = safeIssues.stream()
            .filter(issue -> issue != null && issue.severity() == Severity.ERROR)
            .map(ValidationIssue::message)
            .filter(Objects::nonNull)
            .toList();
        List<String> warnings = safeIssues.stream()
            .filter(issue -> issue != null && issue.severity() == Severity.WARNING)
            .map(ValidationIssue::message)
            .filter(Objects::nonNull)
            .toList();

        ErrorCategory category = categorizeIssues(safeIssues);
        boolean valid = errors.isEmpty() && category != ErrorCategory.UNSAFE && category != ErrorCategory.INCOMPLETE;
        return new ValidationResult(valid, category, safeIssues, errors, warnings);
    }

    private ErrorCategory categorizeIssues(List<ValidationIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return ErrorCategory.NONE;
        }

        boolean hasErrors = issues.stream().anyMatch(issue -> issue != null && issue.severity() == Severity.ERROR);
        if (!hasErrors) {
            return ErrorCategory.NONE;
        }

        for (ValidationIssue issue : issues) {
            if (issue == null || issue.code() == null || issue.severity() != Severity.ERROR) {
                continue;
            }
            switch (issue.code()) {
                case RESPONSE_NULL, INTENT_NULL, INTENT_MISSING_TYPE, INTENT_MISSING_NAME, ACTION_MISSING_NAME -> {
                    return ErrorCategory.STRUCTURAL;
                }
                case ACTION_UNREGISTERED -> {
                    return ErrorCategory.UNSAFE;
                }
                case ACTION_REQUIRED_PARAM_MISSING -> {
                    return ErrorCategory.INCOMPLETE;
                }
                default -> {
                    // keep scanning
                }
            }
        }
        return ErrorCategory.OTHER;
    }
}
