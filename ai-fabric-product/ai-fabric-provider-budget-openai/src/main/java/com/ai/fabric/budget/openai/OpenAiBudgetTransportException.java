package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.BudgetException;

import java.io.IOException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Typed SDK cause: host integration must inspect this type, never parse exception prose. */
public final class OpenAiBudgetTransportException extends IOException {
    private final UUID attemptId;
    private final BudgetException.Code budgetCode;
    private final OpenAiBudgetException.Code guardCode;

    OpenAiBudgetTransportException(UUID attemptId, BudgetException.Code budgetCode, OpenAiBudgetException.Code guardCode) {
        super(budgetCode == null ? guardCode.name() : budgetCode.name());
        this.attemptId = attemptId;
        this.budgetCode = budgetCode;
        this.guardCode = guardCode;
    }

    public UUID attemptId() { return attemptId; }
    public Optional<BudgetException.Code> budgetCode() { return Optional.ofNullable(budgetCode); }
    public Optional<OpenAiBudgetException.Code> guardCode() { return Optional.ofNullable(guardCode); }

    public static Optional<OpenAiBudgetTransportException> find(Throwable error) {
        Set<Throwable> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable current = error; current != null && visited.add(current); current = current.getCause()) {
            if (current instanceof OpenAiBudgetTransportException failure) return Optional.of(failure);
        }
        return Optional.empty();
    }
}
