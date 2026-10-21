package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.web.admin.RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW;

import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.ProviderBudgetLedger.Usage;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Endpoint-free adapter; readback can neither register an approval nor authorize paid work. */
public final class RuntimeBudgetAdminReadback {
    private static final String SURFACE = "runtime budget accounting";
    private final RuntimeBudgetBindingService budgets;
    private final RuntimeRequestAuthResolver auth;

    public RuntimeBudgetAdminReadback(RuntimeBudgetBindingService budgets, RuntimeRequestAuthResolver auth) {
        this.budgets = Objects.requireNonNull(budgets);
        this.auth = Objects.requireNonNull(auth);
    }

    public Readback read(HttpServletRequest request) {
        authorize(request, SURFACE, RUNTIME_ADMIN_OVERVIEW);
        var store = budgets.readStore();
        var snapshot = store.accounting();
        var scope = budgets.scope();
        var binding = store.binding();
        return new Readback(binding.state(), binding.storeUuid(), binding.approvalId(), binding.runtimeFingerprint(),
            scope.customerId(), scope.deploymentId(), scope.targetProfileId(), scope.environment(), scope.rateCardReference(),
            snapshot == null ? null : snapshot.approval().limits(), snapshot == null ? null : snapshot.approval().expiresAt(),
            snapshot == null ? null : snapshot.chatAttempts(), snapshot == null ? null : snapshot.providerAttempts(),
            snapshot == null ? null : snapshot.accountedUsage(), snapshot == null ? null : snapshot.stopped(),
            snapshot == null ? null : budgets.effectiveExpiresAt());
    }

    void authorize(HttpServletRequest request, String surface, String requiredScope) {
        var identity = auth.resolveVerifiedPrivateContext(request, surface);
        var context = budgets.requireVerifiedDeployment(identity);
        if (context.getAuthMode() != RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED
            && context.getAuthMode() != RuntimeAuthMode.PLATFORM_PROXY_SESSION) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "RUNTIME_BUDGET_PRIVATE_AUTH_REQUIRED");
        }
        if (context.getGrantedScopes() == null || !context.getGrantedScopes().contains(requiredScope)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "RUNTIME_BUDGET_ADMIN_SCOPE_REQUIRED");
        }
        auth.requireScope(identity, requiredScope, surface);
    }

    public record Readback(RuntimeBudgetBindingService.State state, UUID storeUuid, String approvalId, String runtimeFingerprint,
                           String customerId, String deploymentId, String targetProfileId,
                           String environment, String rateCardReference, Limits limits, Instant expiresAt,
                           Long chatAttempts, Long providerAttempts, Usage accountedUsage, Boolean stopped, Instant effectiveExpiresAt) { }
}
