package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.ActionInfo;
import com.ai.infrastructure.intent.action.AvailableActionsRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnrichedPromptBuilderTest {

    @Test
    void shouldIncludeActionsKnowledgeBaseAndGuidanceInPrompt() {
        AvailableActionsRegistry registry = Mockito.mock(AvailableActionsRegistry.class);
        KnowledgeBaseOverviewService overviewService = Mockito.mock(KnowledgeBaseOverviewService.class);

        Mockito.when(registry.getAllAvailableActions()).thenReturn(List.of(
            ActionInfo.builder()
                .name("cancel_subscription")
                .description("Cancel active subscription")
                .category("subscription")
                .build()
        ));

        KnowledgeBaseOverview overview = KnowledgeBaseOverview.builder()
            .totalIndexedDocuments(123)
            .documentsByType(java.util.Map.of("policies", 80L, "faq", 43L))
            .build();
        Mockito.when(overviewService.getOverview()).thenReturn(overview);

        ObjectProvider<Clock> clockProvider = Mockito.mock(ObjectProvider.class);
        Mockito.when(clockProvider.getObject()).thenReturn(Clock.systemUTC());
        Mockito.when(clockProvider.getObject(Mockito.any())).thenReturn(Clock.systemUTC());
        Mockito.when(clockProvider.getIfAvailable()).thenReturn(Clock.systemUTC());
        Mockito.when(clockProvider.getIfUnique()).thenReturn(Clock.systemUTC());
        Mockito.when(clockProvider.getIfAvailable(Mockito.any())).thenAnswer(invocation -> {
            java.util.function.Supplier<Clock> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        SystemContextBuilder contextBuilder = new SystemContextBuilder(registry, overviewService, clockProvider);

        EnrichedPromptBuilder promptBuilder = new EnrichedPromptBuilder(contextBuilder);

        String prompt = promptBuilder.buildSystemPrompt("user-123");

        assertThat(prompt)
            .contains("AVAILABLE ACTIONS")
            .contains("cancel_subscription")
            .contains("KNOWLEDGE BASE OVERVIEW")
            .contains("Total documents: 123")
            .contains("NEXT-STEP RECOMMENDATIONS")
            .contains("requiresGeneration")
            .contains("optimizedQuery")
            .contains("OUTPUT JSON SCHEMA");
    }
}
