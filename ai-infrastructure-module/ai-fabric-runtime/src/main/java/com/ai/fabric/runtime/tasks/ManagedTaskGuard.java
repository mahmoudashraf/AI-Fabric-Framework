package com.ai.fabric.runtime.tasks;

import ai.fabric.execution.action.ActionOutcomeProjectorRegistry;
import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.ActionAccessMode;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.action.ActionSideEffectLevel;
import ai.fabric.intent.action.invocation.GovernedActionInvocationOutcome;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.pipeline.PipelineStep;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** One synchronous host turn. It does not alter chat or other specialist invocations. */
@Component
public final class ManagedTaskGuard implements PipelineStep {
    private final ThreadLocal<OrchestrationTurnGuard> current = new ThreadLocal<>();
    private final ObjectProvider<ActionOutcomeProjectorRegistry> projectors;

    public ManagedTaskGuard(ObjectProvider<ActionOutcomeProjectorRegistry> projectors) {
        this.projectors = projectors;
    }

    Trace trace(ObjectMapper mapper) { return new Trace(mapper, projectors.getIfAvailable()); }

    Scope open(ManagedTasksBundle.Task task, Trace trace, Instant deadline, OrchestrationTurnGuard budget) {
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
                if (metadata == null || metadata.getAccessMode() == null)
                    throw new OrchestrationControlException("TASK_ACTION_DENIED", "Task action metadata is required.");
                if (metadata.getAccessMode() == ActionAccessMode.READ) {
                    if (!task.readActions().contains(metadata.getName()) || metadata.getSideEffectLevel() != ActionSideEffectLevel.NONE)
                        throw new OrchestrationControlException("TASK_ACTION_DENIED", "The task does not permit this side-effect-free read.");
                } else {
                    if (!task.writeActions().contains(metadata.getName()))
                        throw new OrchestrationControlException("TASK_SIDE_EFFECT_DENIED", "The task does not permit this write action.");
                    trace.started(metadata.getName());
                }
            }
            @Override public void afterAction(AIActionMetaData metadata, ActionContext context, GovernedActionInvocationOutcome outcome) {
                if (metadata != null && metadata.getAccessMode() != null && !metadata.getAccessMode().isReadOnly())
                    trace.completed(metadata.getName(), outcome);
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
        OrchestrationTurnGuard guard() { return guard; }
        void check() { guard.verifyBeforeOrchestration(); }
        @Override public void close() { current.remove(); }
    }

    static final class Trace {
        private static final int MAX_WRITES = 16;
        private final List<Receipt> receipts = new ArrayList<>();
        private final Map<Long, Deque<Receipt>> pending = new HashMap<>();
        private final ObjectMapper mapper;
        private final ActionOutcomeProjectorRegistry projectors;
        private int retainedDataBytes;
        Trace(ObjectMapper mapper, ActionOutcomeProjectorRegistry projectors) {
            this.mapper = mapper;
            this.projectors = projectors;
        }

        synchronized void started(String action) {
            if (receipts.size() >= MAX_WRITES)
                throw new OrchestrationControlException("TASK_WRITE_LIMIT_EXCEEDED", "Task write execution limit exceeded.");
            Receipt receipt = new Receipt("task-action-" + UUID.randomUUID(), action, Instant.now());
            receipts.add(receipt);
            pending.computeIfAbsent(Thread.currentThread().threadId(), ignored -> new ArrayDeque<>()).push(receipt);
        }

        synchronized void completed(String action, GovernedActionInvocationOutcome result) {
            var stack = pending.get(Thread.currentThread().threadId());
            if (stack == null || stack.isEmpty() || !action.equals(stack.peek().action)) return;
            Receipt receipt = stack.pop();
            if (stack.isEmpty()) pending.remove(Thread.currentThread().threadId());
            receipt.status = result.status().name();
            receipt.completed = Instant.now();
            var actual = result.actionResult();
            if (actual != null) {
                var projected = mapper.createObjectNode().put("success", actual.isSuccess());
                receipt.outcome = projected;
                if (!actual.isSuccess() || projectors == null) return;
                try {
                    var safe = projectors.require(action).project(actual);
                    projected.put("message", safe.message());
                    var data = mapper.valueToTree(safe.data());
                    int bytes = mapper.writeValueAsBytes(data).length;
                    if (bytes <= 4096 && retainedDataBytes + bytes <= 32768) {
                        projected.set("data", data);
                        retainedDataBytes += bytes;
                    } else projected.put("dataOmitted", true);
                } catch (Exception unavailable) { projected.put("dataOmitted", true); }
            }
        }

        synchronized List<ManagedTaskResponse.ActionExecution> snapshot() {
            return receipts.stream().map(receipt -> new ManagedTaskResponse.ActionExecution(receipt.id, receipt.action,
                "DISPATCHED".equals(receipt.status) ? "OUTCOME_UNKNOWN" : receipt.status,
                receipt.outcome == null ? null : receipt.outcome.deepCopy(), receipt.started, receipt.completed)).toList();
        }

        synchronized State state() {
            var snapshot = snapshot();
            boolean executed = snapshot.stream().anyMatch(receipt -> "EXECUTED".equals(receipt.status()));
            String status = snapshot.isEmpty() ? "NONE"
                : snapshot.stream().allMatch(receipt -> "EXECUTED".equals(receipt.status())) ? "EXECUTED" : "OUTCOME_UNKNOWN";
            return new State(snapshot, executed, status);
        }

        record State(List<ManagedTaskResponse.ActionExecution> receipts, boolean executed, String sideEffectsStatus) {}

        private static final class Receipt {
            final String id;
            final String action;
            final Instant started;
            String status = "DISPATCHED";
            Instant completed;
            com.fasterxml.jackson.databind.JsonNode outcome;
            Receipt(String id, String action, Instant started) { this.id = id; this.action = action; this.started = started; }
        }
    }
}
