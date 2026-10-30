package com.ai.fabric.runtime.budget;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger.ChatReceipt;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Synchronous host boundary only. The resolver must capture the observer before SDK dispatch, attach it
 * to that immutable operation and invoke it on failure. Pass this instance as the native Capture.
 * Ledger authorization never consults this context. Unpropagated async query paths are unsupported.
 */
public final class RuntimeBudgetTurnScopes implements OpenAiBudgetInvocation.Capture {
    public static final String INTERRUPTION_KEY = "budgetInterruption";
    public static final String REPORTED_OUTCOME_KEY = "budgetInterruptedOutcome";
    public static final String INTERRUPTION_CODE = "PROVIDER_BUDGET_INTERRUPTED";
    private static final String INTERRUPTION_MESSAGE = "Provider spending was interrupted; actions may already have completed.";
    private final RuntimeBudgetBindingService budgets;
    private final ThreadLocal<Turn> current = new ThreadLocal<>();
    private final ThreadLocal<EmbeddingWork> indexing = new ThreadLocal<>();

    public RuntimeBudgetTurnScopes(RuntimeBudgetBindingService budgets) {
        this.budgets = Objects.requireNonNull(budgets);
    }

    public Turn openChat(RuntimeResolvedIdentity verifiedIdentity, OrchestrationTurnGuard delegate) {
        requireNoScope();
        try {
            return open(budgets.recordVerifiedChatIngress(verifiedIdentity), delegate);
        } catch (BudgetException denied) {
            throw control(denied);
        } catch (RuntimeBudgetBindingException denied) {
            throw control(denied);
        }
    }

    public Turn openSuggestions(RuntimeResolvedIdentity verifiedIdentity) {
        requireNoScope();
        try {
            return open(budgets.recordVerifiedSuggestionsIngress(verifiedIdentity), null);
        } catch (BudgetException denied) {
            throw control(denied);
        } catch (RuntimeBudgetBindingException denied) {
            throw control(denied);
        }
    }

    /** Capture at model resolution, not inside an interceptor/SDK async callback on another thread. */
    @Override
    public OpenAiBudgetInvocation capture(OpenAiBudgetInvocation.Operation operation) {
        if (operation == null || operation == OpenAiBudgetInvocation.Operation.UNSUPPORTED) {
            throw new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_REQUEST);
        }
        Turn turn = current.get();
        if (turn != null) return new OpenAiBudgetInvocation(UUID.randomUUID(), turn::observeFailure);
        EmbeddingWork work = indexing.get();
        if (work != null && operation == OpenAiBudgetInvocation.Operation.EMBEDDING) {
            return new OpenAiBudgetInvocation(UUID.randomUUID(), work::observeFailure);
        }
        throw new OpenAiBudgetException(OpenAiBudgetException.Code.INVOCATION_CONTEXT_REQUIRED);
    }

    /** Parent must open this inside the actual indexing worker, not on its enqueueing thread. */
    public EmbeddingWork openIndexingWork() {
        return openEmbeddingWork();
    }

    /** A verified synchronous retrieval surface may spend on embeddings, never generation. */
    public EmbeddingWork openEmbeddingRead() {
        return openEmbeddingWork();
    }

    private EmbeddingWork openEmbeddingWork() {
        requireNoScope();
        budgets.requireActive();
        var work = new EmbeddingWork();
        indexing.set(work);
        return work;
    }

    void verifyIndexingBeforeVectorWrite() {
        EmbeddingWork work = indexing.get();
        if (work == null) throw new IllegalStateException("RUNTIME_BUDGET_INDEX_WORK_SCOPE_REQUIRED");
        work.verifyComplete();
    }

    private Turn open(ChatReceipt receipt, OrchestrationTurnGuard delegate) {
        Turn turn = new Turn(receipt, delegate);
        current.set(turn);
        return turn;
    }

    private void requireNoScope() {
        if (current.get() != null || indexing.get() != null) throw new IllegalStateException("RUNTIME_BUDGET_NESTED_TURN_UNSUPPORTED");
    }

    /** Explicit child-task propagation; shares the paid turn, never admits another chat. */
    public Runnable propagateTask(Runnable task) {
        Turn parent = current.get();
        if (parent == null) return task;
        return () -> {
            requireNoScope();
            Turn child = new Turn(parent.receipt, parent.delegate, parent.interruption);
            current.set(child);
            try (child) {
                try {
                    child.verifyBeforeOrchestration();
                    task.run();
                } catch (RuntimeException failure) {
                    if (task instanceof java.util.concurrent.Future<?> future) future.cancel(false);
                    throw failure;
                }
            }
        };
    }

    private static OrchestrationControlException control(BudgetException denied) {
        return new OrchestrationControlException("PROVIDER_BUDGET_" + denied.code().name(), "Provider budget admission was denied.");
    }

    private static OrchestrationControlException control(RuntimeBudgetBindingException denied) {
        return new OrchestrationControlException("PROVIDER_BUDGET_" + denied.code().name(), "Provider budget admission was denied.");
    }

    public record BudgetInterruption(String code, UUID operationId, UUID providerAttemptId, BudgetException.Code budgetCode,
                                     OpenAiBudgetException.Code guardCode) { }
    public record ReportedOutcome(OrchestrationResultType type, boolean success, String errorCode) { }

    private static BudgetInterruption interruption(OpenAiBudgetInvocation.Denial failure) {
        Objects.requireNonNull(failure);
        return new BudgetInterruption(INTERRUPTION_CODE, failure.operationId(), failure.attemptId(), failure.budgetCode(), failure.guardCode());
    }

    public final class EmbeddingWork implements AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final AtomicReference<BudgetInterruption> failure = new AtomicReference<>();
        private EmbeddingWork() { }
        private void observeFailure(OpenAiBudgetInvocation.Denial denied) { failure.compareAndSet(null, RuntimeBudgetTurnScopes.interruption(denied)); }
        public Optional<BudgetInterruption> interruption() { return Optional.ofNullable(failure.get()); }
        /** Call before marking the indexing work successful, even if an embedding layer swallowed a failure. */
        public void verifyComplete() {
            requireOwner();
            if (failure.get() != null) throw new OrchestrationControlException(INTERRUPTION_CODE, "Provider-funded indexing was interrupted.");
        }
        private void requireOwner() {
            if (Thread.currentThread() != owner || indexing.get() != this) {
                throw new IllegalStateException("RUNTIME_BUDGET_INDEX_WORK_SCOPE_REQUIRED");
            }
        }
        @Override public void close() { requireOwner(); indexing.remove(); }
    }

    public final class Turn implements OrchestrationTurnGuard, AutoCloseable {
        private final Thread owner = Thread.currentThread();
        private final ChatReceipt receipt;
        private final OrchestrationTurnGuard delegate;
        private final AtomicReference<BudgetInterruption> interruption;

        private Turn(ChatReceipt receipt, OrchestrationTurnGuard delegate) {
            this(receipt, delegate, new AtomicReference<>());
        }

        private Turn(ChatReceipt receipt, OrchestrationTurnGuard delegate, AtomicReference<BudgetInterruption> interruption) {
            this.receipt = receipt;
            this.delegate = delegate;
            this.interruption = interruption;
        }

        public Optional<ChatReceipt> chatReceipt() { return Optional.ofNullable(receipt); }
        public Optional<BudgetInterruption> interruption() { return Optional.ofNullable(interruption.get()); }

        private void observeFailure(OpenAiBudgetInvocation.Denial failure) {
            interruption.compareAndSet(null, RuntimeBudgetTurnScopes.interruption(failure));
        }

        @Override
        public void verifyBeforeOrchestration() {
            requireOwnerScope();
            try { budgets.requireActive(); }
            catch (BudgetException denied) { throw control(denied); }
            catch (RuntimeBudgetBindingException denied) { throw control(denied); }
            if (delegate != null) delegate.verifyBeforeOrchestration();
        }

        @Override
        public void verifyBeforeAction(AIActionMetaData metadata, ActionContext context) {
            // Budget admission is not authority to revoke an already-authorized nonpaid action.
            if (delegate != null) delegate.verifyBeforeAction(metadata, context);
        }

        @Override
        public void verifyBeforeResultCommit() {
            if (delegate != null) delegate.verifyBeforeResultCommit();
        }

        @Override
        public void verifyBeforeResultCommit(OrchestrationResult result) {
            requireOwnerScope();
            if (delegate != null) delegate.verifyBeforeResultCommit(result);
            BudgetInterruption denied = interruption.get();
            if (denied == null) return;
            Objects.requireNonNull(result, "Result-aware budget completion requires the original result.");
            var metadata = copy(result.getMetadata());
            Object original = metadata.get(REPORTED_OUTCOME_KEY);
            if (!(denied.equals(metadata.get(INTERRUPTION_KEY)) && original instanceof ReportedOutcome)) {
                original = new ReportedOutcome(result.getType(), result.isSuccess(), result.getErrorCode());
            }
            metadata.put(REPORTED_OUTCOME_KEY, original);
            metadata.put(INTERRUPTION_KEY, denied);
            result.setMetadata(Collections.unmodifiableMap(metadata));
            var sanitized = copy(result.getSanitizedPayload());
            var sanitizedMetadata = copyMap(sanitized.get("metadata"));
            sanitizedMetadata.put(REPORTED_OUTCOME_KEY, original);
            sanitizedMetadata.put(INTERRUPTION_KEY, denied);
            sanitized.put("metadata", sanitizedMetadata);
            sanitized.put("success", false);
            sanitized.put("type", OrchestrationResultType.ERROR.name());
            sanitized.put("errorCode", INTERRUPTION_CODE);
            sanitized.put("answer", INTERRUPTION_MESSAGE);
            sanitized.put("safeSummary", INTERRUPTION_MESSAGE);
            sanitized.put("message", INTERRUPTION_MESSAGE);
            result.setSanitizedPayload(sanitized);
            result.setSuccess(false);
            result.setType(OrchestrationResultType.ERROR);
            result.setErrorCode(INTERRUPTION_CODE);
            result.setMessage(INTERRUPTION_MESSAGE);
        }

        private void requireOwnerScope() {
            if (Thread.currentThread() != owner || current.get() != this) {
                throw new IllegalStateException("RUNTIME_BUDGET_SYNCHRONOUS_TURN_SCOPE_REQUIRED");
            }
        }

        @Override
        public void close() {
            requireOwnerScope();
            current.remove();
        }
    }

    private static LinkedHashMap<String, Object> copy(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static LinkedHashMap<String, Object> copyMap(Object source) {
        var result = new LinkedHashMap<String, Object>();
        if (source instanceof Map<?, ?> map) {
            map.forEach((key, value) -> { if (key instanceof String text) result.put(text, value); });
        }
        return result;
    }
}
