package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.InMemoryPendingActionStore;
import com.ai.infrastructure.intent.actiondraft.InMemoryActionDraftStore;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
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

class IntentHandlingStepPostActionGenerationTest {

    @Test
    void shouldGeneratePostActionSummaryWhenHandlerProvidesFacts() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("test_action")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(any())).thenReturn(true);
        when(handler.getConfirmationMessage(any(), any())).thenReturn("Confirm?");

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("OK")
            .data(Map.of("result", "value"))
            .build();
        when(handler.executeAction(any(), any())).thenReturn(actionResult);
        when(handler.buildPostActionLlmFacts(eq(actionResult), any())).thenReturn(Optional.of(Map.of(
            "status", "ok",
            "result", "value"
        )));

        AICoreService aiCoreService = mock(AICoreService.class);
        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION)))
            .thenReturn(AIGenerationResponse.builder().content("Summary").model("test-model").build());

        PostActionGenerationProperties properties = new PostActionGenerationProperties();
        properties.setEnabled(true);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            properties,
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("test_action")
            .requiresGeneration(true)
            .generationInstructions("Explain the result.")
            .actionParams(Map.of("x", "y"))
            .build();

        PipelineContext context = PipelineContext.from("Do the thing", OrchestrationContext.forUser("user-1"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Summary");
        assertThat(result.getData()).containsKey("postActionGeneration");
        assertThat(result.getData()).containsKey("summary");

        verify(handler).executeAction(any(), any());
        verify(aiCoreService).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION));
    }

    @Test
    void shouldSkipPostActionGenerationWhenHandlerOptsOut() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("test_action")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(any())).thenReturn(true);

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("OK")
            .build();
        when(handler.executeAction(any(), any())).thenReturn(actionResult);
        when(handler.buildPostActionLlmFacts(eq(actionResult), any())).thenReturn(Optional.empty());

        AICoreService aiCoreService = mock(AICoreService.class);

        PostActionGenerationProperties properties = new PostActionGenerationProperties();
        properties.setEnabled(true);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            properties,
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("test_action")
            .requiresGeneration(true)
            .actionParams(Map.of())
            .build();

        PipelineContext context = PipelineContext.from("Do the thing", OrchestrationContext.forUser("user-1"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("OK");

        verify(aiCoreService, never()).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION));
    }

    @Test
    void shouldNotFailActionWhenPostActionGenerationThrows() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("test_action")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(any())).thenReturn(true);

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("OK")
            .build();
        when(handler.executeAction(any(), any())).thenReturn(actionResult);
        when(handler.buildPostActionLlmFacts(eq(actionResult), any())).thenReturn(Optional.of(Map.of("status", "ok")));

        AICoreService aiCoreService = mock(AICoreService.class);
        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION)))
            .thenThrow(new RuntimeException("provider down"));

        PostActionGenerationProperties properties = new PostActionGenerationProperties();
        properties.setEnabled(true);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            properties,
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("test_action")
            .requiresGeneration(true)
            .actionParams(Map.of())
            .build();

        PipelineContext context = PipelineContext.from("Do the thing", OrchestrationContext.forUser("user-1"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("OK");
        assertThat(result.getData()).containsKey("postActionGeneration");
    }

    @Test
    void shouldSkipPostActionGenerationWhenHandlerFactsBuilderThrows() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("test_action")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(any())).thenReturn(true);

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("OK")
            .build();
        when(handler.executeAction(any(), any())).thenReturn(actionResult);
        when(handler.buildPostActionLlmFacts(eq(actionResult), any()))
            .thenThrow(new RuntimeException("oops"));

        AICoreService aiCoreService = mock(AICoreService.class);

        PostActionGenerationProperties properties = new PostActionGenerationProperties();
        properties.setEnabled(true);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            properties,
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("test_action")
            .requiresGeneration(true)
            .actionParams(Map.of())
            .build();

        PipelineContext context = PipelineContext.from("Do the thing", OrchestrationContext.forUser("user-1"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("OK");
        assertThat(result.getData()).containsKey("postActionGeneration");

        verify(aiCoreService, never()).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION));
    }

    @Test
    void shouldNotReplaceActionMessageWhenGenerationReturnsEmptyContent() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("test_action")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(any())).thenReturn(true);

        ActionResult actionResult = ActionResult.builder()
            .success(true)
            .message("OK")
            .build();
        when(handler.executeAction(any(), any())).thenReturn(actionResult);
        when(handler.buildPostActionLlmFacts(eq(actionResult), any())).thenReturn(Optional.of(Map.of("status", "ok")));

        AICoreService aiCoreService = mock(AICoreService.class);
        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.GENERATION)))
            .thenReturn(AIGenerationResponse.builder().content("  ").build());

        PostActionGenerationProperties properties = new PostActionGenerationProperties();
        properties.setEnabled(true);

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            properties,
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("test_action")
            .requiresGeneration(true)
            .actionParams(Map.of())
            .build();

        PipelineContext context = PipelineContext.from("Do the thing", OrchestrationContext.forUser("user-1"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("OK");
        assertThat(result.getData()).containsKey("postActionGeneration");
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
