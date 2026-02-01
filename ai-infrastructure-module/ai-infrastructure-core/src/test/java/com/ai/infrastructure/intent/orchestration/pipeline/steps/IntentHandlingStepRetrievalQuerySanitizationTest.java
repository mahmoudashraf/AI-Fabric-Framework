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
import com.ai.infrastructure.dto.RAGRequest;
import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.InMemoryPendingActionStore;
import com.ai.infrastructure.intent.actiondraft.InMemoryActionDraftStore;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.vectorspace.RankBasedMerger;
import com.ai.infrastructure.prompt.ClasspathPromptTemplateStore;
import com.ai.infrastructure.prompt.PromptRenderer;
import com.ai.infrastructure.prompt.PromptTemplateResolver;
import com.ai.infrastructure.spi.AdvancedRAGProvider;
import com.ai.infrastructure.spi.RAGProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepRetrievalQuerySanitizationTest {

    @Test
    void shouldNotUseInjectedPinnedContextAsRetrievalQueryWhenOptimizedQueryEchoesPrompt() {
        RAGProvider ragProvider = mock(RAGProvider.class);
        when(ragProvider.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder().documents(List.of()).success(true).build()
        );

        IntentHandlingStep step = newStep(ragProvider);

        String effectiveQuery = """
            PINNED CONTEXT (authoritative; prefer answering from this; avoid retrieval if sufficient):
            STORED PINNED TARGETS (authoritative; may be stale):
            1) vectorSpace=product id=46 metadata={id=46, sku=SKU-APP-38713, category=Electronics, name=Apple AirPods Max, price=549, currency=USD}

            Conversation History:
            ---BEGIN HISTORY---
            User: give summary of specs
            Assistant: ok
            ---END HISTORY---

            Current Query:
            ---BEGIN QUERY---
            compare between them
            ---END QUERY---
            """;

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("compare_products")
            .requiresRetrieval(true)
            .requiresGeneration(false)
            .vectorSpace("product")
            // Simulate a bad LLM output that copies the whole effective prompt as "optimizedQuery".
            .optimizedQuery(effectiveQuery)
            .build();

        PipelineContext context = PipelineContext.from("compare between them", OrchestrationContext.forUser("user"))
            .toBuilder()
            .processedQuery(effectiveQuery)
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        step.process(context);

        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragProvider).performRag(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getQuery())
            .doesNotContain("PINNED CONTEXT")
            .doesNotContain("---BEGIN HISTORY---")
            .contains("compare between them");
    }

    private IntentHandlingStep newStep(RAGProvider ragProvider) {
        VectorSpaceRoutingProperties routingProperties = new VectorSpaceRoutingProperties();
        routingProperties.setClarificationThreshold(0.0d);
        routingProperties.setFanOutTopKPerSpace(2);
        routingProperties.setFanOutRagThreshold(0.25d);
        return new IntentHandlingStep(
            mock(AIActionRegistry.class),
            providerOf(ragProvider),
            mock(AICoreService.class),
            mock(AIServiceConfig.class),
            providerOf((AdvancedRAGProvider) null),
            routingProperties,
            new RankBasedMerger(),
            new RelationshipQueryPostActionGenerationProperties(),
            new PostActionGenerationProperties(),
            providerOf(new ObjectMapper()),
            new OrchestrationProperties(),
            providerOf((KnowledgeBaseOverviewService) null),
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

