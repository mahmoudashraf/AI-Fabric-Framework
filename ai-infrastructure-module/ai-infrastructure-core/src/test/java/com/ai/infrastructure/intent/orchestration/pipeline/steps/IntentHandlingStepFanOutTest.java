package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepFanOutTest {

    @Test
    void shouldMergeFanOutDocumentsByRankAndTagVectorSpace() {
        RAGProvider ragProvider = mock(RAGProvider.class);
        when(ragProvider.performRag(any(RAGRequest.class))).thenAnswer(invocation -> {
            RAGRequest request = invocation.getArgument(0);
            if ("faq".equals(request.getEntityType())) {
                return RAGResponse.builder()
                    .documents(List.of(
                        doc("faq-1", 0.9d, "FAQ one"),
                        doc("faq-2", 0.2d, "FAQ two")
                    ))
                    .success(true)
                    .build();
            }
            if ("policies".equals(request.getEntityType())) {
                return RAGResponse.builder()
                    .documents(List.of(
                        doc("pol-1", 0.8d, "Policy one"),
                        doc("pol-2", 0.1d, "Policy two")
                    ))
                    .success(true)
                    .build();
            }
            return RAGResponse.builder().documents(List.of()).success(true).build();
        });

        IntentHandlingStep step = newStep(ragProvider, mock(AICoreService.class));

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace("faq,policies")
            .requiresGeneration(false)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        List<RAGResponse.RAGDocument> docs = (List<RAGResponse.RAGDocument>) result.getData().get("documents");
        assertThat(docs).extracting(RAGResponse.RAGDocument::getId).containsExactly("faq-1", "pol-1", "faq-2", "pol-2");
        assertThat(docs.get(0).getMetadata()).containsEntry("vectorSpace", "faq");
        assertThat(docs.get(1).getMetadata()).containsEntry("vectorSpace", "policies");

        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragProvider, org.mockito.Mockito.times(2)).performRag(requestCaptor.capture());
        assertThat(requestCaptor.getAllValues()).hasSize(2);
        assertThat(requestCaptor.getAllValues())
            .extracting(RAGRequest::getThreshold)
            .containsOnly(0.25d);
    }

    @Test
    void shouldReturnClarificationWhenFanOutIsWeak() {
        RAGProvider ragProvider = mock(RAGProvider.class);
        when(ragProvider.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .documents(List.of(doc("d1", 0.1d, "low score")))
                .success(true)
                .build()
        );

        VectorSpaceRoutingProperties routingProperties = new VectorSpaceRoutingProperties();
        routingProperties.setClarificationThreshold(0.4d);
        routingProperties.setFanOutTopKPerSpace(1);

        IntentHandlingStep step = new IntentHandlingStep(
            mock(ActionHandlerRegistry.class),
            providerOf(ragProvider),
            mock(AICoreService.class),
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            routingProperties,
            new RankBasedMerger()
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace("faq,policies")
            .requiresGeneration(false)
            .build();

        PipelineContext context = PipelineContext.from("q", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getData()).containsEntry("candidateVectorSpaces", List.of("faq", "policies"));
    }

    @Test
    void shouldUseGenerationPurposeWhenGeneratingFanOutAnswer() {
        RAGProvider ragProvider = mock(RAGProvider.class);
        when(ragProvider.performRAGQuery(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder()
                .documents(List.of(doc("faq-1", 0.9d, "FAQ one"), doc("pol-1", 0.8d, "Policy one")))
                .success(true)
                .build()
        );

        AICoreService aiCoreService = mock(AICoreService.class);
        when(aiCoreService.generateText(anyString(), eq(LlmPurpose.GENERATION))).thenReturn("Answer");

        VectorSpaceRoutingProperties routingProperties = new VectorSpaceRoutingProperties();
        routingProperties.setClarificationThreshold(0.0d);
        routingProperties.setFanOutTopKPerSpace(2);

        IntentHandlingStep step = new IntentHandlingStep(
            mock(ActionHandlerRegistry.class),
            providerOf(ragProvider),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            routingProperties,
            new RankBasedMerger()
        );

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("refund_policy")
            .vectorSpace("faq,policies")
            .requiresGeneration(true)
            .build();

        PipelineContext context = PipelineContext.from("What is the refund policy?", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.INFORMATION_PROVIDED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Answer");
        verify(aiCoreService).generateText(anyString(), eq(LlmPurpose.GENERATION));
        verify(aiCoreService, never()).generateText(anyString(), eq(LlmPurpose.ORCHESTRATION));
    }

    private IntentHandlingStep newStep(RAGProvider ragProvider, AICoreService aiCoreService) {
        VectorSpaceRoutingProperties routingProperties = new VectorSpaceRoutingProperties();
        routingProperties.setClarificationThreshold(0.0d);
        routingProperties.setFanOutTopKPerSpace(2);
        routingProperties.setFanOutRagThreshold(0.25d);
        return new IntentHandlingStep(
            mock(ActionHandlerRegistry.class),
            providerOf(ragProvider),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            routingProperties,
            new RankBasedMerger()
        );
    }

    private RAGResponse.RAGDocument doc(String id, double score, String content) {
        return RAGResponse.RAGDocument.builder()
            .id(id)
            .score(score)
            .content(content)
            .metadata(Map.of())
            .build();
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
