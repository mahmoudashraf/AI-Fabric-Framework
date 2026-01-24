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
import com.ai.infrastructure.intent.action.AIActionMetaData;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IntentHandlingStepRequiredParamsPlaceholderTest {

    @Test
    void shouldTreatMetadataDescriptionCopiedIntoParamsAsMissing() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("change_delivery_address")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        AIActionMetaData meta = AIActionMetaData.builder()
            .name("change_delivery_address")
            .description("Change delivery address")
            .category("commerce")
            .parameters(Map.of(
                "shippingAddress", "New shipping address (required)"
            ))
            .requiredParameters(Set.of("shippingAddress"))
            .build();
        when(registry.findMetadata("change_delivery_address")).thenReturn(Optional.of(meta));

        IntentHandlingStep step = new IntentHandlingStep(
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
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        // Simulate the extractor "filling" the missing value by copying the parameter description.
        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("change_delivery_address")
            .actionParams(Map.of(
                "shippingAddress", "New shipping address (required)"
            ))
            .build();

        PipelineContext context = PipelineContext.from("change my address", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).compound(false).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getData()).containsEntry("missingRequiredParameters", List.of("shippingAddress"));
    }

    @Test
    void shouldTreatInstructionLikeValueAsMissing() {
        AIActionRegistry registry = mock(AIActionRegistry.class);
        AIActionHandler handler = mock(AIActionHandler.class);
        when(registry.findHandler("change_delivery_address")).thenReturn(Optional.of(handler));
        when(handler.validateActionAllowed(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        AIActionMetaData meta = AIActionMetaData.builder()
            .name("change_delivery_address")
            .description("Change delivery address")
            .category("commerce")
            .parameters(Map.of(
                "shippingAddress", "New shipping address (required)"
            ))
            .requiredParameters(Set.of("shippingAddress"))
            .build();
        when(registry.findMetadata("change_delivery_address")).thenReturn(Optional.of(meta));

        IntentHandlingStep step = new IntentHandlingStep(
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
            new InMemoryPendingActionStore(),
            new InMemoryActionDraftStore()
        );

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .action("change_delivery_address")
            .actionParams(Map.of(
                "shippingAddress", "New shipping address required"
            ))
            .build();

        PipelineContext context = PipelineContext.from("change my address", OrchestrationContext.forUser("user"))
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).compound(false).build())
            .build();

        PipelineContext updated = step.process(context);
        OrchestrationResult result = updated.getIntentResult();

        assertThat(result.getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getData()).containsEntry("missingRequiredParameters", List.of("shippingAddress"));
    }

    private static <T> ObjectProvider<T> providerOf(T value) {
        return new ObjectProvider<>() {
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
            public T getObject() {
                return value;
            }
        };
    }
}
