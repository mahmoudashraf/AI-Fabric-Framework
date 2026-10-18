package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import java.util.Objects;
import java.util.regex.Pattern;

/** Startup-only configuration. Never populate this projection from a chat request or token claims. */
public record RuntimeBudgetConfiguration(boolean enabled, Scope scope, String tenantId) {
    private static final Pattern CREDENTIAL_IDENTITY = Pattern.compile("sha256:[0-9a-f]{64}");

    public RuntimeBudgetConfiguration {
        if (enabled) {
            validateScope(Objects.requireNonNull(scope, "Trusted budget scope is required."));
            canonical(tenantId);
        }
    }

    public static RuntimeBudgetConfiguration disabled() {
        return new RuntimeBudgetConfiguration(false, null, null);
    }

    static void validateScope(Scope scope) {
        canonical(scope.customerId()); canonical(scope.deploymentId()); canonical(scope.targetProfileId());
        canonical(scope.environment()); canonical(scope.rateCardReference());
        if (!CREDENTIAL_IDENTITY.matcher(scope.credentialIdentity()).matches()) {
            throw new IllegalArgumentException("Budget credential identity must be a full SHA-256 identity.");
        }
    }

    static void canonical(String value) {
        if (value == null || value.isBlank() || value.length() > 256
            || !value.equals(value.strip()) || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Budget startup identifiers must be canonical.");
        }
    }
}
