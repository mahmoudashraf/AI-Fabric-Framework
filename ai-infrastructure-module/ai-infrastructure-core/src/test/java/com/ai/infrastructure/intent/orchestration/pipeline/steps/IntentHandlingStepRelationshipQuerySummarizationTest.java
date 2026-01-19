package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionHandlerRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepRelationshipQuerySummarizationTest {

    @Test
    void shouldChainRelationshipQueryToGenerationWhenEnabledAndRequested() {
        ActionHandler handler = mock(ActionHandler.class);
        when(handler.validateActionAllowed("user")).thenReturn(true);
        when(handler.getConfirmationMessage(any())).thenReturn("Confirm?");

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("Found 2 results")
            .data(Map.of(
                "totalResults", 2,
                "returnedResults", 2,
                "documents", List.of(
                    RAGResponse.RAGDocument.builder().id("b1").title("Brand 1").type("brand").score(0.9d).content("Nike").build(),
                    RAGResponse.RAGDocument.builder().id("b2").title("Brand 2").type("brand").score(0.8d).content("Adidas").build()
                )
            ))
            .build();
        when(handler.executeAction(any(), eq("user"))).thenReturn(actionResult);

        ActionHandlerRegistry registry = mock(ActionHandlerRegistry.class);
        when(registry.findHandler("relationship_query")).thenReturn(Optional.of(handler));
        when(registry.findMetadata("relationship_query")).thenReturn(Optional.empty());

        AICoreService aiCoreService = mock(AICoreService.class);
        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION)))
            .thenReturn(AIGenerationResponse.builder().content("Summary").model("test-model").build());

        RelationshipQueryPostActionGenerationProperties props = new RelationshipQueryPostActionGenerationProperties();
        props.setEnabled(true);
        props.setMaxItems(10);
        props.setMaxChars(12_000);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new ObjectMapper(),
            props
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("relationship_query")
            .actionParams(Map.of("query", "find brands", "entityTypes", List.of("brand")))
            .requiresGeneration(true)
            .generationInstructions("Summarize the results.")
            .build();

        PipelineContext context = PipelineContext.from("relationship query: find brands and then summarize", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Summary");
        assertThat(result.getData()).containsKey("actionResult");
        assertThat(result.getData()).containsEntry("summary", "Summary");
        assertThat(result.getMetadata()).containsEntry("relationshipQuery.executed", true);
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.used", true);
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.purpose", "GENERATION");
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.model", "test-model");

        ArgumentCaptor<AIGenerationRequest> captor = ArgumentCaptor.forClass(AIGenerationRequest.class);
        verify(aiCoreService).generateContent(captor.capture(), eq(LlmPurpose.GENERATION));
        assertThat(captor.getValue().getPrompt()).contains("FACTS_JSON");
    }

    @Test
    void shouldNotCallGenerationWhenRelationshipQueryReturnsNoResults() {
        ActionHandler handler = mock(ActionHandler.class);
        when(handler.validateActionAllowed("user")).thenReturn(true);

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("No results found")
            .data(Map.of(
                "totalResults", 0,
                "returnedResults", 0,
                "documents", List.of()
            ))
            .build();
        when(handler.executeAction(any(), eq("user"))).thenReturn(actionResult);

        ActionHandlerRegistry registry = mock(ActionHandlerRegistry.class);
        when(registry.findHandler("relationship_query")).thenReturn(Optional.of(handler));
        when(registry.findMetadata("relationship_query")).thenReturn(Optional.empty());

        AICoreService aiCoreService = mock(AICoreService.class);

        RelationshipQueryPostActionGenerationProperties props = new RelationshipQueryPostActionGenerationProperties();
        props.setEnabled(true);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new ObjectMapper(),
            props
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("relationship_query")
            .actionParams(Map.of("query", "find brands", "entityTypes", List.of("brand")))
            .requiresGeneration(true)
            .generationInstructions("Summarize the results.")
            .build();

        PipelineContext context = PipelineContext.from("relationship query: find brands and then summarize", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("No results found");
        assertThat(result.getData()).containsEntry("summary", "No results found");
        assertThat(result.getMetadata()).containsEntry("postActionGeneration.used", false);

        verify(aiCoreService, never()).generateContent(any(AIGenerationRequest.class), any(LlmPurpose.class));
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}

