package com.ai.fabric.runtime.budget;

import static org.assertj.core.api.Assertions.*;

import ai.fabric.intent.action.AIActionMetaData;
import ai.fabric.intent.action.ActionContext;
import ai.fabric.intent.orchestration.OrchestrationContext;
import ai.fabric.intent.orchestration.OrchestrationResult;
import ai.fabric.intent.orchestration.OrchestrationResultType;
import ai.fabric.intent.orchestration.RAGOrchestrator;
import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import ai.fabric.intent.orchestration.control.OrchestrationTurnGuard;
import ai.fabric.intent.orchestration.pipeline.DefaultOrchestrationPipeline;
import ai.fabric.intent.orchestration.pipeline.PipelineContext;
import ai.fabric.intent.orchestration.pipeline.PipelineStep;
import com.ai.fabric.budget.BudgetException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Characterizations of missing framework guarantees, not claims of receipt-preserving integration. */
class FrameworkBudgetHookGapTest {
    @Test
    void currentCatchAllDiscardsAnExistingActionReceiptAfterALaterProviderFailure() {
        var executedWrites = new AtomicInteger();
        var receipt = receipt();
        var pipeline = new DefaultOrchestrationPipeline(List.of(
            step(1, context -> {
                executedWrites.incrementAndGet();
                return context.toBuilder().intentResult(receipt).build();
            }),
            step(2, context -> { throw new BudgetException(BudgetException.Code.LIMIT_EXCEEDED); })
        ));

        var result = pipeline.execute("complete approved action", OrchestrationContext.builder().userId("owner").build());

        assertThat(executedWrites).hasValue(1);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result).isNotSameAs(receipt);
        assertThat(result.getData()).doesNotContainKey("actions");
        assertThat(result.getChildren()).isEmpty();
    }

    @Test
    void finalControlExceptionCannotReturnTheAlreadyExecutedReceiptToTheHost() {
        var receipt = receipt();
        var resultSeenByGuard = new AtomicReference<OrchestrationResult>();
        var context = OrchestrationContext.builder().userId("owner").orchestrationTurnGuard(new OrchestrationTurnGuard() {
            @Override public void verifyBeforeOrchestration() { }
            @Override public void verifyBeforeAction(AIActionMetaData metadata, ActionContext actionContext) { }
            @Override public void verifyBeforeResultCommit() { throw denied(); }
            @Override public void verifyBeforeResultCommit(OrchestrationResult result) {
                resultSeenByGuard.set(result);
                throw denied();
            }
            private OrchestrationControlException denied() {
                return new OrchestrationControlException("PROVIDER_BUDGET_DENIED", "Provider spending was denied.");
            }
        }).build();
        var orchestrator = new RAGOrchestrator(new DefaultOrchestrationPipeline(List.of(
                step(1, pipelineContext -> pipelineContext.toBuilder().intentResult(receipt).build()))));

        assertThatThrownBy(() -> orchestrator.orchestrate("complete approved action", context))
            .isInstanceOfSatisfying(OrchestrationControlException.class,
                error -> assertThat(error.code()).isEqualTo("PROVIDER_BUDGET_DENIED"));
        assertThat(resultSeenByGuard).hasValue(receipt);
        assertThat(receipt.getData()).containsKey("actions");
    }

    private static OrchestrationResult receipt() {
        return OrchestrationResult.builder().type(OrchestrationResultType.ACTION_EXECUTED).success(true)
            .data(Map.of("actions", List.of(Map.of("receiptId", "write-1", "success", true))))
            .build();
    }

    private static PipelineStep step(int order, java.util.function.UnaryOperator<PipelineContext> process) {
        return new PipelineStep() {
            @Override public PipelineContext process(PipelineContext context) { return process.apply(context); }
            @Override public String getStepName() { return "budget-hook-gap-" + order; }
            @Override public int getOrder() { return order; }
        };
    }
}
