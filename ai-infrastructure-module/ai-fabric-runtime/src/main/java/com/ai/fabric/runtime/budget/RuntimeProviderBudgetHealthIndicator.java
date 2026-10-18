package com.ai.fabric.runtime.budget;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

/** Allocation/accounting readiness only. Never publish this indicator as a liveness requirement. */
public final class RuntimeProviderBudgetHealthIndicator implements HealthIndicator {
    private final RuntimeBudgetBindingService budgets;
    public RuntimeProviderBudgetHealthIndicator(RuntimeBudgetBindingService budgets) { this.budgets = budgets; }
    @Override public Health health() {
        var status = budgets.readiness();
        return (status.ready() ? Health.up() : Health.down()).withDetail("budgetState", status.code()).build();
    }
}
