package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.PromptBundleProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionAccessMode;
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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepAnonymousActionPolicyTest {

    @Test
    void shouldAllowAnonymousGuestSafeActionsToExecute() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        AIActionMetaData metadata = AIActionMetaData.builder()
            .name("search_products")
            .description("Search products")
            .category("commerce")
            .accessMode(ActionAccessMode.READ)
            .build();

        when(registry.findHandler("search_products")).thenReturn(Optional.of(handler));
        when(registry.findMetadata("search_products")).thenReturn(Optional.of(metadata));
        when(handler.validateActionAllowed(any())).thenReturn(true);
        when(handler.requiresConfirmation()).thenReturn(false);
        when(handler.executeAction(anyMap(), any())).thenReturn(ActionResult.builder()
            .success(true)
            .message("Products")
            .build());

        IntentHandlingStep step = newStep(registry);

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("search_products")
            .build();

        PipelineContext context = PipelineContext.from("Search for products", OrchestrationContext.anonymous())
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        OrchestrationResult result = step.process(context).getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).isEqualTo("Products");
        verify(handler).executeAction(anyMap(), any());
    }

    @Test
    void shouldKeepSensitiveActionsDeniedForAnonymousUsers() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        IntentHandlingStep step = newStep(registry);

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("list_orders")
            .build();

        PipelineContext context = PipelineContext.from("List my orders", OrchestrationContext.anonymous())
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        OrchestrationResult result = step.process(context).getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.ACTION_DENIED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).isEqualTo("Action not permitted for anonymous users.");
        verify(registry, never()).findHandler("list_orders");
    }

    private IntentHandlingStep newStep(AIActionRegistry registry) {
        return new IntentHandlingStep(
            registry,
            providerOf(mock(RAGProvider.class)),
            mock(AICoreService.class),
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            new VectorSpaceRoutingProperties(),
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            new PostActionGenerationProperties(),
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
            null,
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore(),
            promptTemplateResolver(),
            new PromptRenderer()
        );
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
