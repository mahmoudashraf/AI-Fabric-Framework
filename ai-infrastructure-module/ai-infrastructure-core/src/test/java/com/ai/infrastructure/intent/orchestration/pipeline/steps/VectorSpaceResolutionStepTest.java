package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverview;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.vectorspace.RoutingResult;
import com.ai.infrastructure.intent.vectorspace.RoutingStrategy;
import com.ai.infrastructure.intent.vectorspace.VectorSpaceRouter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VectorSpaceResolutionStepTest {

    @Test
    void shouldSkipWhenVectorSpaceAlreadyPresent() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace("policies")
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getIntentResponse().getIntents().getFirst().getVectorSpace()).isEqualTo("policies");
        verify(router, never()).route(any(), anyString());
    }

    @Test
    void shouldFallbackToFanOutWhenVectorSpaceNotAvailable() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);

        KnowledgeBaseOverviewService overviewService = mock(KnowledgeBaseOverviewService.class);
        when(overviewService.getOverview()).thenReturn(KnowledgeBaseOverview.builder()
            .entityTypes(List.of("faq", "policies"))
            .build());

        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf(overviewService)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("search_products")
            .requiresRetrieval(true)
            .vectorSpace("commerce")
            .build();

        PipelineContext context = PipelineContext.from("smartphone", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getIntentResponse().getIntents().getFirst().getVectorSpace()).isEqualTo("faq,policies");
        assertThat(updated.getMetadata()).containsKey("vectorSpaceRouting");
        verify(router, never()).route(any(), anyString());
    }

    @Test
    void shouldSkipWhenIntentDoesNotRequireRetrieval() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .requiresRetrieval(false)
            .vectorSpace(null)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getIntentResponse().getIntents().getFirst().getVectorSpace()).isNull();
        verify(router, never()).route(any(), anyString());
    }

    @Test
    void shouldResolveSingleVectorSpace() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        when(router.route(any(), anyString())).thenReturn(RoutingResult.builder()
            .success(true)
            .vectorSpace("policies")
            .strategy(RoutingStrategy.HEURISTIC)
            .confidence(0.5d)
            .rationale("test")
            .build());

        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace(null)
            .requiresRetrieval(true)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getIntentResponse().getIntents().getFirst().getVectorSpace()).isEqualTo("policies");
        assertThat(updated.getMetadata()).containsKey("vectorSpaceRouting");
    }

    @Test
    void shouldResolveFanOutIntoCommaSeparatedVectorSpace() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        when(router.route(any(), anyString())).thenReturn(RoutingResult.builder()
            .success(true)
            .candidateSpaces(List.of("faq", "policies"))
            .strategy(RoutingStrategy.FAN_OUT)
            .confidence(0.5d)
            .rationale("fan-out")
            .build());

        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace(null)
            .requiresRetrieval(true)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getIntentResponse().getIntents().getFirst().getVectorSpace()).isEqualTo("faq,policies");
    }

    @Test
    void shouldResolveSingleCandidateFanOutWithoutClarification() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        when(router.route(any(), anyString())).thenReturn(RoutingResult.builder()
            .success(true)
            .candidateSpaces(List.of("faq"))
            .strategy(RoutingStrategy.FAN_OUT)
            .confidence(0.5d)
            .rationale("single candidate")
            .build());

        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace(null)
            .requiresRetrieval(true)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getIntentResponse().getIntents().getFirst().getVectorSpace()).isEqualTo("faq");
    }

    @Test
    void shouldTerminateWithClarificationWhenRoutingFailsWithCandidates() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        when(router.route(any(), anyString())).thenReturn(RoutingResult.builder()
            .success(false)
            .candidateSpaces(List.of("faq", "policies"))
            .strategy(RoutingStrategy.CLARIFICATION)
            .confidence(0.0d)
            .rationale("need clarification")
            .build());

        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace(null)
            .requiresRetrieval(true)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isTrue();
        assertThat(updated.getEarlyTerminationResult()).isNotNull();
        assertThat(updated.getEarlyTerminationResult().getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(updated.getEarlyTerminationResult().getData()).containsEntry("candidateVectorSpaces", List.of("faq", "policies"));
    }

    @Test
    void shouldTerminateWithClarificationWhenRoutingFailsWithoutCandidates() {
        VectorSpaceRouter router = mock(VectorSpaceRouter.class);
        when(router.route(any(), anyString())).thenReturn(RoutingResult.builder()
            .success(false)
            .candidateSpaces(List.of())
            .strategy(RoutingStrategy.CLARIFICATION)
            .confidence(0.0d)
            .rationale("no overview")
            .build());

        VectorSpaceResolutionStep step = new VectorSpaceResolutionStep(
            router,
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null)
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace(null)
            .requiresRetrieval(true)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isTrue();
        assertThat(updated.getEarlyTerminationResult()).isNotNull();
        assertThat(updated.getEarlyTerminationResult().getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(updated.getEarlyTerminationResult().getData()).containsEntry("candidateVectorSpaces", List.of());
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
