package com.ai.fabric.runtime.agentic;

import ai.fabric.execution.chain.SpecialistChainExecutionHandle;
import ai.fabric.execution.chain.SpecialistChainExecutionResult;
import ai.fabric.execution.chain.SpecialistChainExecutionSnapshot;
import ai.fabric.execution.chain.SpecialistChainResultView;
import ai.fabric.execution.chain.SpecialistChainStepTrace;
import ai.fabric.execution.chain.SpecialistChainWorkerTrace;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record DeploymentIntelligenceExecutionView(
    String executionId,
    String chain,
    String status,
    boolean replayed,
    boolean durable,
    String message,
    FailureView failure,
    List<ProjectedResultView> results,
    List<StepView> steps,
    Instant submittedAt,
    Instant startedAt,
    Instant updatedAt,
    Instant completedAt,
    Instant deadline
) {

    public static DeploymentIntelligenceExecutionView from(
        SpecialistChainExecutionHandle handle
    ) {
        return new DeploymentIntelligenceExecutionView(
            handle.executionId(),
            handle.chainId().toString(),
            handle.status().name(),
            handle.replayed(),
            handle.durable(),
            null,
            handle.failureReason() == null
                ? null
                : new FailureView(
                    handle.failureReason(),
                    "The agentic execution could not be started.",
                    false
                ),
            List.of(),
            List.of(),
            handle.submittedAt(),
            null,
            null,
            null,
            handle.deadline()
        );
    }

    public static DeploymentIntelligenceExecutionView from(
        SpecialistChainExecutionResult result
    ) {
        return new DeploymentIntelligenceExecutionView(
            result.executionId(),
            result.chainId().toString(),
            result.status().name(),
            result.replayed(),
            result.durable(),
            result.message(),
            result.failure() == null
                ? null
                : new FailureView(
                    result.failure().reason(),
                    result.failure().publicMessage(),
                    result.failure().retryable()
                ),
            result.projectedResults().stream()
                .map(ProjectedResultView::from)
                .toList(),
            result.steps().stream().map(StepView::from).toList(),
            result.startedAt(),
            result.startedAt(),
            result.completedAt(),
            result.completedAt(),
            null
        );
    }

    public static DeploymentIntelligenceExecutionView from(
        SpecialistChainExecutionSnapshot snapshot,
        SpecialistChainExecutionResult result
    ) {
        return new DeploymentIntelligenceExecutionView(
            snapshot.executionId(),
            snapshot.chainId().toString(),
            snapshot.status().name(),
            result != null && result.replayed(),
            snapshot.durable(),
            result != null ? result.message() : null,
            result != null && result.failure() != null
                ? new FailureView(
                    result.failure().reason(),
                    result.failure().publicMessage(),
                    result.failure().retryable()
                )
                : snapshot.failure() != null
                    ? new FailureView(
                        snapshot.failure().reason(),
                        snapshot.failure().publicMessage(),
                        snapshot.failure().retryable()
                    )
                    : null,
            result != null
                ? result.projectedResults().stream()
                    .map(ProjectedResultView::from)
                    .toList()
                : List.of(),
            snapshot.steps().stream().map(StepView::from).toList(),
            snapshot.startedAt(),
            snapshot.startedAt(),
            snapshot.updatedAt(),
            result != null ? result.completedAt() : null,
            snapshot.deadline()
        );
    }

    public record FailureView(
        String reason,
        String message,
        boolean retryable
    ) {
    }

    public record ProjectedResultView(
        String specialist,
        String summary,
        Map<String, String> facts,
        List<String> evidenceReferenceIds
    ) {
        static ProjectedResultView from(SpecialistChainResultView result) {
            return new ProjectedResultView(
                result.specialist(),
                result.summary(),
                result.facts(),
                result.evidenceReferenceIds()
            );
        }
    }

    public record StepView(
        int decisionIndex,
        String directiveType,
        String reason,
        List<WorkerView> workers,
        Instant startedAt,
        Instant completedAt
    ) {
        static StepView from(SpecialistChainStepTrace step) {
            return new StepView(
                step.decisionIndex(),
                step.directiveType().name(),
                step.reason(),
                step.workers().stream().map(WorkerView::from).toList(),
                step.startedAt(),
                step.completedAt()
            );
        }
    }

    public record WorkerView(
        String specialist,
        String relationship,
        String status,
        List<String> evidenceReferenceIds,
        String failureReason
    ) {
        static WorkerView from(SpecialistChainWorkerTrace worker) {
            return new WorkerView(
                worker.specialist(),
                worker.relationship(),
                worker.status().name(),
                worker.evidenceReferenceIds(),
                worker.failureReason()
            );
        }
    }
}
