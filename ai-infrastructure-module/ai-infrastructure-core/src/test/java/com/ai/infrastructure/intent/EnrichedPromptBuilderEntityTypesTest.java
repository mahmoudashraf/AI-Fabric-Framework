package com.ai.infrastructure.intent;

import com.ai.infrastructure.intent.action.AvailableActionsRegistry;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for EnrichedPromptBuilder's entity type inclusion in LLM prompts.
 */
@DisplayName("EnrichedPromptBuilder - Entity Types in Prompt")
class EnrichedPromptBuilderEntityTypesTest {

    @Test
    @DisplayName("Should include entity types in prompt when available")
    void shouldIncludeEntityTypesInPromptWhenAvailable() {
        // Arrange
        SystemContextBuilder contextBuilder = createContextBuilderWithEntityTypes(
            Set.of("customer", "order", "product")
        );
        EnrichedPromptBuilder promptBuilder = new EnrichedPromptBuilder(contextBuilder);
        
        // Act
        String prompt = promptBuilder.buildSystemPrompt(OrchestrationContext.forUser("user-123"));
        
        // Assert
        assertThat(prompt)
            .as("Prompt should contain relationship_query entityTypes rule")
            .contains("When action == \"relationship_query\"");
        
        assertThat(prompt)
            .as("Prompt should list available entity types")
            .contains("Available entity types: ")
            .contains("customer")
            .contains("order")
            .contains("product");
        
        assertThat(prompt)
            .as("Prompt should instruct to only use listed types")
            .contains("Only use entity types from this list");
        
        assertThat(prompt)
            .as("Prompt should include example with entityTypes")
            .contains("\"entityTypes\":[\"customer\",\"order\"]");
    }

    @Test
    @DisplayName("Should show 'no entity types registered' message when empty")
    void shouldShowNoEntityTypesMessageWhenEmpty() {
        // Arrange
        SystemContextBuilder contextBuilder = createContextBuilderWithEntityTypes(Set.of());
        EnrichedPromptBuilder promptBuilder = new EnrichedPromptBuilder(contextBuilder);
        
        // Act
        String prompt = promptBuilder.buildSystemPrompt(OrchestrationContext.forUser("user-123"));
        
        // Assert
        assertThat(prompt)
            .as("Prompt should contain relationship_query entityTypes rule")
            .contains("When action == \"relationship_query\"");
        
        assertThat(prompt)
            .as("Prompt should indicate no entity types registered")
            .contains("No entity types are currently registered");
        
        assertThat(prompt)
            .as("Prompt should still include fallback instruction")
            .contains("Use [] when unknown");
    }

    @Test
    @DisplayName("Should include entityTypes extraction instruction for relationship queries")
    void shouldIncludeEntityTypesExtractionInstruction() {
        // Arrange
        SystemContextBuilder contextBuilder = createContextBuilderWithEntityTypes(Set.of("user"));
        EnrichedPromptBuilder promptBuilder = new EnrichedPromptBuilder(contextBuilder);
        
        // Act
        String prompt = promptBuilder.buildSystemPrompt(OrchestrationContext.forUser("user-123"));
        
        // Assert
        assertThat(prompt)
            .as("Should instruct to extract entityTypes")
            .contains("extract entityTypes from the user request");
        
        assertThat(prompt)
            .as("Should specify array of lower-case strings")
            .contains("array of lower-case strings");
        
        assertThat(prompt)
            .as("Should provide example")
            .contains("\"entityTypes\":");
    }

    private SystemContextBuilder createContextBuilderWithEntityTypes(Set<String> entityTypes) {
        AvailableActionsRegistry registry = mock(AvailableActionsRegistry.class);
        when(registry.getAllAvailableActions()).thenReturn(List.of());
        
        KnowledgeBaseOverviewService overviewService = mock(KnowledgeBaseOverviewService.class);
        when(overviewService.getOverview()).thenReturn(
            KnowledgeBaseOverview.builder().totalIndexedDocuments(0).build()
        );
        
        ObjectProvider<com.ai.infrastructure.spi.BehaviorContextProvider> behaviorProvider = 
            mock(ObjectProvider.class);
        when(behaviorProvider.getIfAvailable()).thenReturn(null);
        
        ObjectProvider<Clock> clockProvider = mock(ObjectProvider.class);
        when(clockProvider.getIfAvailable()).thenReturn(Clock.systemUTC());
        when(clockProvider.getIfAvailable(Mockito.any())).thenReturn(Clock.systemUTC());
        
        // Create a builder that will return specific entity types
        SystemContextBuilder builder = new SystemContextBuilder(
            registry,
            overviewService,
            behaviorProvider,
            clockProvider,
            mock(ObjectProvider.class)
        ) {
            @Override
            public SystemContext buildContext(OrchestrationContext context) {
                SystemContext ctx = super.buildContext(context);
                // Override entity types for testing
                return SystemContext.builder()
                    .availableActions(ctx.getAvailableActions())
                    .knowledgeBaseOverview(ctx.getKnowledgeBaseOverview())
                    .userId(ctx.getUserId())
                    .sessionId(ctx.getSessionId())
                    .authenticated(ctx.isAuthenticated())
                    .locale(ctx.getLocale())
                    .metadata(ctx.getMetadata())
                    .timestamp(ctx.getTimestamp())
                    .availableEntityTypes(entityTypes)  // Override for test
                    .build();
            }
        };
        
        return builder;
    }
}

