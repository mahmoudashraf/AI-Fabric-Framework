package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AIServiceConfig;
import com.ai.infrastructure.config.OrchestrationProperties;
import com.ai.infrastructure.config.PostActionGenerationProperties;
import com.ai.infrastructure.config.RelationshipQueryPostActionGenerationProperties;
import com.ai.infrastructure.config.VectorSpaceRoutingProperties;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.action.AIActionHandler;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.intent.action.AIActionParamSchema;
import com.ai.infrastructure.intent.action.AIActionParamType;
import com.ai.infrastructure.intent.action.AIActionRegistry;
import com.ai.infrastructure.intent.action.ActionAccessMode;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.ActionResultContracts;
import com.ai.infrastructure.intent.action.InMemoryPendingActionStore;
import com.ai.infrastructure.intent.actiondraft.InMemoryActionDraftStore;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentHandlingStepBatchTargetsTest {

    @Test
    void shouldExpandBatchTargetsFromResolvedTargetsWhenOnlyOneItemProvided() {
        AIActionMetaData meta = addToCartMeta();
        AIActionHandler handler = mock(AIActionHandler.class);
        when(handler.validateActionAllowed(any())).thenReturn(true);
        when(handler.requiresConfirmation()).thenReturn(false);
        when(handler.executeAction(anyMap(), any())).thenReturn(ActionResult.builder()
            .success(true)
            .message("ok")
            .data(ActionResultContracts.object(Map.of()))
            .build());

        AIActionRegistry registry = mock(AIActionRegistry.class);
        when(registry.findHandler("add_to_cart")).thenReturn(Optional.of(handler));
        when(registry.findMetadata("add_to_cart")).thenReturn(Optional.of(meta));

        IntentHandlingStep step = newStep(registry);

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("add_to_cart")
            .actionParams(Map.of(
                "items", List.of(Map.of("sku", "SKU-HP-31150", "quantity", 1))
            ))
            .build();

        PipelineContext context = PipelineContext.from("add to cart", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).build())
            .resolvedTargets(List.of(
                target("p1", Map.of("sku", "SKU-HP-31150")),
                target("p2", Map.of("sku", "SKU-APP-83635"))
            ))
            .build();

        step.process(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(handler, times(1)).executeAction(paramsCaptor.capture(), any());

        Object raw = paramsCaptor.getValue().get("items");
        assertThat(raw).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) raw;
        assertThat(items).hasSize(2);
        assertThat(items).anySatisfy(item -> assertThat(item).containsEntry("sku", "SKU-HP-31150"));
        assertThat(items).anySatisfy(item -> assertThat(item).containsEntry("sku", "SKU-APP-83635"));
    }

    @Test
    void shouldCoalesceMultipleBatchActionIntentsIntoSingleExecution() {
        AIActionMetaData meta = addToCartMeta();
        AIActionHandler handler = mock(AIActionHandler.class);
        when(handler.validateActionAllowed(any())).thenReturn(true);
        when(handler.requiresConfirmation()).thenReturn(false);
        when(handler.executeAction(anyMap(), any())).thenReturn(ActionResult.builder()
            .success(true)
            .message("ok")
            .data(ActionResultContracts.object(Map.of()))
            .build());

        AIActionRegistry registry = mock(AIActionRegistry.class);
        when(registry.findHandler("add_to_cart")).thenReturn(Optional.of(handler));
        when(registry.findMetadata("add_to_cart")).thenReturn(Optional.of(meta));

        IntentHandlingStep step = newStep(registry);

        Intent one = Intent.builder()
            .type(IntentType.ACTION)
            .action("add_to_cart")
            .actionParams(Map.of(
                "items", List.of(Map.of("sku", "SKU-HP-31150", "quantity", 1))
            ))
            .build();
        Intent two = Intent.builder()
            .type(IntentType.ACTION)
            .action("add_to_cart")
            .actionParams(Map.of(
                "items", List.of(Map.of("sku", "SKU-APP-83635", "quantity", 1))
            ))
            .build();

        PipelineContext context = PipelineContext.from("add to cart", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(one, two)).build())
            .resolvedTargets(List.of(
                target("p1", Map.of("sku", "SKU-HP-31150")),
                target("p2", Map.of("sku", "SKU-APP-83635"))
            ))
            .build();

        step.process(context);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(handler, times(1)).executeAction(paramsCaptor.capture(), any());

        Object raw = paramsCaptor.getValue().get("items");
        assertThat(raw).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) raw;
        assertThat(items).hasSize(2);
    }

    private IntentHandlingStep newStep(AIActionRegistry registry) {
        return new IntentHandlingStep(
            registry,
            providerOf((RAGProvider) null),
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
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore(),
            promptTemplateResolver(),
            new PromptRenderer()
        );
    }

    private AIActionMetaData addToCartMeta() {
        AIActionParamSchema sku = AIActionParamSchema.builder()
            .name("sku")
            .type(AIActionParamType.STRING)
            .build();
        AIActionParamSchema quantity = AIActionParamSchema.builder()
            .name("quantity")
            .type(AIActionParamType.INTEGER)
            .build();
        AIActionParamSchema item = AIActionParamSchema.builder()
            .type(AIActionParamType.OBJECT)
            .properties(Map.of("sku", sku, "quantity", quantity))
            .requiredProperties(List.of("sku", "quantity"))
            .build();
        AIActionParamSchema items = AIActionParamSchema.builder()
            .name("items")
            .type(AIActionParamType.ARRAY)
            .batchTargets(true)
            .items(item)
            .build();

        return AIActionMetaData.builder()
            .name("add_to_cart")
            .description("Add to cart")
            .category("commerce")
            .accessMode(ActionAccessMode.WRITE_ONLY)
            .parameterSchemas(Map.of("items", items))
            .requiredParameters(Set.of("items"))
            .build();
    }

    private ResolvedTarget target(String id, Map<String, String> metadata) {
        return ResolvedTarget.builder()
            .id(id)
            .vectorSpace("product")
            .metadata(metadata)
            .build();
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
            @Override
            public T getObject() {
                return value;
            }

            @Override
            public T getObject(Object... args) {
                return value;
            }

            @Override
            public T getIfAvailable() {
                return value;
            }

            @Override
            public T getIfUnique() {
                return value;
            }

            @Override
            public java.util.stream.Stream<T> stream() {
                return value == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(value);
            }
        };
    }

    private PromptTemplateResolver promptTemplateResolver() {
        return new PromptTemplateResolver(
            new ClasspathPromptTemplateStore(new DefaultResourceLoader()),
            new com.ai.infrastructure.config.PromptBundleProperties()
        );
    }
}
