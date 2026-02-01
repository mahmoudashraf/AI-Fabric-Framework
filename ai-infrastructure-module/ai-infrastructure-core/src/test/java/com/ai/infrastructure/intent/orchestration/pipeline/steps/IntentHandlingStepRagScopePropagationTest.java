package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.PromptBundleProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
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
import com.ai.infrastructure.intent.orchestration.OrchestrationContextMetadataKeys;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepRagScopePropagationTest {

    @Test
    void shouldAttachRagScopeToRagRequestContextWithoutIncludingPinnedContentTextInQuery() {
        RAGProvider ragProvider = mock(RAGProvider.class);
        when(ragProvider.performRag(any(RAGRequest.class))).thenReturn(
            RAGResponse.builder().documents(List.of()).success(true).build()
        );

        IntentHandlingStep step = newStep(ragProvider, null);

        ResolvedTarget target = ResolvedTarget.builder()
            .id("30")
            .vectorSpace("product")
            .contentText("SENSITIVE_ATTACHMENT_SNIPPET")
            .metadata(Map.of("id", "30", "name", "Bose Pro Headphones"))
            .source(ResolvedTargetSource.ACTIVE_ATTACHMENTS)
            .build();

        OrchestrationContext orch = OrchestrationContext.forUser("user")
            .toBuilder()
            .activeAttachmentIdsResolved(List.of("30"))
            .build();

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("price_check")
            .requiresRetrieval(true)
            .requiresGeneration(false)
            .vectorSpace("product")
            .optimizedQuery("price?")
            .build();

        PipelineContext context = PipelineContext.from("price?", orch)
            .toBuilder()
            .processedQuery("price?")
            .resolvedTargets(List.of(target))
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        step.process(context);

        ArgumentCaptor<RAGRequest> requestCaptor = ArgumentCaptor.forClass(RAGRequest.class);
        verify(ragProvider).performRag(requestCaptor.capture());

        RAGRequest ragRequest = requestCaptor.getValue();
        assertThat(ragRequest.getQuery()).doesNotContain("SENSITIVE_ATTACHMENT_SNIPPET");
        assertThat(ragRequest.getContext()).isNotNull();
        assertThat(ragRequest.getContext()).containsKey("ragScope");

        Object scopeRaw = ragRequest.getContext().get("ragScope");
        assertThat(scopeRaw).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> scope = (Map<String, Object>) scopeRaw;
        assertThat(scope).containsEntry("targetCount", 1);
        assertThat(scope).containsKey("targets");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> targets = (List<Map<String, Object>>) scope.get("targets");
        assertThat(targets).hasSize(1);
        assertThat(targets.getFirst()).containsEntry("id", "30");
        assertThat(targets.getFirst()).doesNotContainKey("contentText");
    }

    @Test
    void shouldIncludeRagScopeInAdvancedRagRequestMetadata() {
        RAGProvider ragProvider = mock(RAGProvider.class);

        AdvancedRAGProvider advanced = mock(AdvancedRAGProvider.class);
        when(advanced.performAdvancedRAG(any(AdvancedRAGRequest.class))).thenReturn(
            AdvancedRAGResponse.builder()
                .success(true)
                .response("ok")
                .expandedQueries(List.of("expanded"))
                .documents(List.of())
                .build()
        );

        IntentHandlingStep step = newStep(ragProvider, advanced);

        ResolvedTarget target = ResolvedTarget.builder()
            .id("30")
            .vectorSpace("product")
            .contentText("SENSITIVE_ATTACHMENT_SNIPPET")
            .metadata(Map.of("id", "30", "name", "Bose Pro Headphones"))
            .source(ResolvedTargetSource.ACTIVE_ATTACHMENTS)
            .build();

        OrchestrationContext orch = OrchestrationContext.forUser("user")
            .toBuilder()
            .metadata(Map.of(OrchestrationContextMetadataKeys.USE_ADVANCED_RAG, true))
            .activeAttachmentIdsResolved(List.of("30"))
            .build();

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("summarize")
            .requiresRetrieval(true)
            .requiresGeneration(true)
            .vectorSpace("product")
            .optimizedQuery("summarize this")
            .build();

        PipelineContext context = PipelineContext.from("summarize this", orch)
            .toBuilder()
            .processedQuery("summarize this")
            .resolvedTargets(List.of(target))
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .build();

        step.process(context);

        ArgumentCaptor<AdvancedRAGRequest> requestCaptor = ArgumentCaptor.forClass(AdvancedRAGRequest.class);
        verify(advanced).performAdvancedRAG(requestCaptor.capture());

        AdvancedRAGRequest request = requestCaptor.getValue();
        assertThat(request.getMetadata()).isNotNull();
        assertThat(request.getMetadata()).containsKey("ragScope");
    }

    private IntentHandlingStep newStep(RAGProvider ragProvider, AdvancedRAGProvider advancedRagProvider) {
        VectorSpaceRoutingProperties routingProperties = new VectorSpaceRoutingProperties();
        routingProperties.setClarificationThreshold(0.0d);
        routingProperties.setFanOutTopKPerSpace(2);
        routingProperties.setFanOutRagThreshold(0.25d);

        return new IntentHandlingStep(
            mock(AIActionRegistry.class),
            providerOf(ragProvider),
            mock(AICoreService.class),
            mock(AIServiceConfig.class),
            providerOf(advancedRagProvider),
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

