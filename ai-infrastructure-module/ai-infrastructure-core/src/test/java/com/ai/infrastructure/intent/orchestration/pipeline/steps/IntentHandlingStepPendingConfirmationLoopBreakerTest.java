package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.PromptBundleProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.core.LlmPurpose;
import com.ai.infrastructure.dto.AIGenerationRequest;
import com.ai.infrastructure.dto.AIGenerationResponse;
import com.ai.infrastructure.dto.AIChatMessage;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionContext;
import com.ai.infrastructure.intent.action.ActionPayload;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.InMemoryPendingActionStore;
import com.ai.infrastructure.intent.actiondraft.InMemoryActionDraftStore;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.prompt.ClasspathPromptTemplateStore;
import com.ai.infrastructure.prompt.PromptRenderer;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepPendingConfirmationLoopBreakerTest {

    @Test
    void shouldExecutePendingActionWhenLlmReissuesSameActionInsteadOfConfirmation() {
        InMemoryPendingActionStore pendingActionStore = new InMemoryPendingActionStore();

        AtomicInteger executeCount = new AtomicInteger();

        AIActionHandler handler = new AIActionHandler() {
            @Override
            public AIActionMetaData getActionMetadata() {
                return AIActionMetaData.builder()
                    .name("add_to_cart")
                    .description("Add sku to cart")
                    .requiredParameters(Set.of("sku"))
                    .build();
            }

            @Override
            public boolean requiresConfirmation() {
                return true;
            }

            @Override
            public String getConfirmationMessage(Map<String, Object> params, ActionContext context) {
                Object sku = params != null ? params.get("sku") : null;
                return "Add 1 × " + (sku != null ? sku.toString() : "unknown") + " to your cart?";
            }

            @Override
            public ActionResult executeAction(Map<String, Object> params, ActionContext context) {
                executeCount.incrementAndGet();
                return ActionResult.builder()
                    .success(true)
                    .message("Added to cart")
                    .data(ActionPayload.object(Map.of("ok", true)))
                    .build();
            }
        };

        AIActionRegistry registry = mock(AIActionRegistry.class);
        when(registry.findHandler("add_to_cart")).thenReturn(Optional.of(handler));
        when(registry.findMetadata("add_to_cart")).thenReturn(Optional.of(handler.getActionMetadata()));

        AICoreService aiCoreService = mock(AICoreService.class);
        when(aiCoreService.generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION)))
            .thenReturn(AIGenerationResponse.builder()
                .content("{\"decision\":\"POSITIVE\",\"confidence\":1.0}")
                .model("test-model")
                .build());

        IntentHandlingStep step = new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
            aiCoreService,
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            new PostActionGenerationProperties(),
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            pendingActionStore,
            new InMemoryActionDraftStore(),
            promptTemplateResolver(),
            new PromptRenderer()
        );

        OrchestrationContext orchContext = OrchestrationContext.builder()
            .userId("user-1")
            .conversationId("chat-1")
            .build();

        Intent actionIntent = Intent.builder()
            .type(IntentType.ACTION)
            .action("add_to_cart")
            .actionParams(Map.of("sku", "SKU-1"))
            .build();

        // Turn 1: user asks to add an explicit SKU -> confirmation required (pending action pushed).
        PipelineContext turn1 = PipelineContext.from("add SKU-1 to cart", orchContext)
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(actionIntent)).build())
            .build();

        OrchestrationResult turn1Result = step.process(turn1).getIntentResult();
        assertThat(turn1Result.getType()).isEqualTo(OrchestrationResultType.CONFIRMATION_REQUIRED);
        assertThat(pendingActionStore.peekPendingAction("chat-1", "user-1")).isPresent();
        assertThat(executeCount.get()).isEqualTo(0);

        // Turn 2: user confirms, but the LLM incorrectly re-issues the ACTION again.
        PipelineContext turn2 = PipelineContext.from("Yes, confirm", orchContext)
            .toBuilder()
            .historyMessages(List.of(AIChatMessage.user("add SKU-1 to cart")))
            .intentResponse(MultiIntentResponse.builder().intents(List.of(actionIntent)).build())
            .build();

        OrchestrationResult turn2Result = step.process(turn2).getIntentResult();
        assertThat(turn2Result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(turn2Result.isSuccess()).isTrue();
        assertThat(executeCount.get()).isEqualTo(1);
        assertThat(pendingActionStore.peekPendingAction("chat-1", "user-1")).isEmpty();

        verify(aiCoreService, times(1)).generateContent(any(AIGenerationRequest.class), eq(LlmPurpose.ORCHESTRATION));
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private PromptTemplateResolver promptTemplateResolver() {
        return new PromptTemplateResolver(
            new ClasspathPromptTemplateStore(new DefaultResourceLoader()),
            new PromptBundleProperties()
        );
    }
}
