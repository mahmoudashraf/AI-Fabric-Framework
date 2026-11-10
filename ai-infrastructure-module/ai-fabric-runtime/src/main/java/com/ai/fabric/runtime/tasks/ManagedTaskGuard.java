package com.ai.fabric.runtime.tasks;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionSideEffectLevel;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.pipeline.PipelineStep;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** One synchronous host turn. It does not alter chat or other specialist invocations. */
@Component
public final class ManagedTaskGuard implements PipelineStep {
    private final ThreadLocal<OrchestrationTurnGuard> current = new ThreadLocal<>();

    Scope open(Instant deadline, OrchestrationTurnGuard budget) {
        if (current.get() != null) throw new IllegalStateException("Nested managed tasks are not supported.");
        var guard = new OrchestrationTurnGuard() {
            private void check() {
                if (Thread.currentThread().isInterrupted() || !Instant.now().isBefore(deadline))
                    throw new OrchestrationControlException("TASK_DEADLINE_EXCEEDED", "Task deadline exceeded.");
                if (budget != null) budget.verifyBeforeOrchestration();
            }
            @Override public void verifyBeforeOrchestration() { check(); }
            @Override public void verifyBeforeResultCommit() { check(); }
            @Override public void verifyBeforeAction(AIActionMetaData metadata, ActionContext context) {
                check();
                if (metadata == null || metadata.getAccessMode() != ActionAccessMode.READ
                    || metadata.getSideEffectLevel() != ActionSideEffectLevel.NONE)
                    throw new OrchestrationControlException("TASK_SIDE_EFFECT_DENIED", "Managed tasks only permit side-effect-free reads.");
            }
        };
        current.set(guard);
        return new Scope(guard);
    }

    @Override public String getStepName() { return "ManagedTaskTurnGuard"; }
    @Override public int getOrder() { return Integer.MIN_VALUE + 100; }
    @Override public PipelineContext process(PipelineContext context) {
        var guard = current.get();
        if (guard != null) {
            guard.verifyBeforeOrchestration();
            context.getOrchestrationContext().setOrchestrationTurnGuard(guard);
        }
        return context;
    }

    final class Scope implements AutoCloseable {
        private final OrchestrationTurnGuard guard;
        private Scope(OrchestrationTurnGuard guard) { this.guard = guard; }
        void check() { guard.verifyBeforeOrchestration(); }
        @Override public void close() { current.remove(); }
    }
}
