package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.extraction.ProgressiveIntentExtractionEngine;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentExtractionStepDeterministicShortcutTest {

    @Test
    void shouldShortCircuitGreetingWithoutCallingExtractor() {
        IntentQueryExtractor extractor = mock(IntentQueryExtractor.class);
        ProgressiveIntentExtractionEngine engine = mock(ProgressiveIntentExtractionEngine.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProgressiveIntentExtractionEngine> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(engine);

        IntentExtractionStep step = new IntentExtractionStep(extractor, provider);
        PipelineContext updated = step.process(PipelineContext.from("hello", OrchestrationContext.forUser("user")));

        Intent intent = updated.getIntentResponse().getIntents().getFirst();
        assertThat(intent.getType()).isEqualTo(IntentType.INFORMATION);
        assertThat(intent.getDirectAnswer()).isEqualTo("Hi! How can I help you today?");
        assertThat(intent.requiresRetrievalOrDefault(true)).isFalse();
        assertThat(intent.requiresGenerationOrDefault(true)).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) updated.getMetadata().get("extractionDiagnostics");
        assertThat(diagnostics)
            .containsEntry("extractionPath", "deterministic_greeting")
            .containsEntry("extractionAttempts", 1)
            .containsEntry("llmCalls", 0)
            .containsEntry("providerProcessingTimeMs", 0L);
        verify(extractor, never()).extractWithTrace(any(), any());
        verify(engine, never()).extract(any(), any());
    }

    @Test
    void shouldShortCircuitSimpleInformationLookupWithoutCallingExtractor() {
        IntentQueryExtractor extractor = mock(IntentQueryExtractor.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProgressiveIntentExtractionEngine> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        IntentExtractionStep step = new IntentExtractionStep(extractor, provider);
        PipelineContext updated = step.process(
            PipelineContext.from("tell me about Alienware m18 R2", OrchestrationContext.forUser("user"))
        );

        Intent intent = updated.getIntentResponse().getIntents().getFirst();
        assertThat(intent.getType()).isEqualTo(IntentType.INFORMATION);
        assertThat(intent.requiresRetrievalOrDefault(false)).isTrue();
        assertThat(intent.requiresGenerationOrDefault(false)).isTrue();
        assertThat(intent.getOptimizedQuery()).isEqualTo("Alienware m18 R2");
        assertThat(intent.getVectorSpace()).isNull();
        assertThat(updated.getMetadata()).containsKey("extractionDiagnostics");
        @SuppressWarnings("unchecked")
        Map<String, Object> diagnostics = (Map<String, Object>) updated.getMetadata().get("extractionDiagnostics");
        assertThat(diagnostics)
            .containsEntry("extractionPath", "deterministic_information")
            .containsEntry("llmCalls", 0);
        verify(extractor, never()).extractWithTrace(any(), any());
    }

    @Test
    void shouldUseDeterministicShortcutForSimplePolicySummaryWithoutForcingVectorSpace() {
        IntentQueryExtractor extractor = mock(IntentQueryExtractor.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProgressiveIntentExtractionEngine> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        IntentExtractionStep step = new IntentExtractionStep(extractor, provider);
        PipelineContext updated = step.process(
            PipelineContext.from("summarize return policy", OrchestrationContext.forUser("user"))
        );

        Intent intent = updated.getIntentResponse().getIntents().getFirst();
        assertThat(intent.getType()).isEqualTo(IntentType.INFORMATION);
        assertThat(intent.getOptimizedQuery()).isEqualTo("return policy");
        assertThat(intent.getVectorSpace()).isNull();
        verify(extractor, never()).extractWithTrace(any(), any());
    }
}
