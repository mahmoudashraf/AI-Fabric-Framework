package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentQueryExtractor;
import com.ai.infrastructure.intent.extraction.IntentExtractionInput;
import com.ai.infrastructure.intent.extraction.ProgressiveIntentExtractionEngine;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IntentExtractionStepProgressiveEngineTest {

    @Test
    void shouldUseProgressiveEngineWhenAvailableAndAttachDiagnostics() {
        IntentQueryExtractor extractor = mock(IntentQueryExtractor.class);
        ProgressiveIntentExtractionEngine engine = mock(ProgressiveIntentExtractionEngine.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<ProgressiveIntentExtractionEngine> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(engine);

        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder().type(IntentType.INFORMATION).intent("refund_policy").build()))
            .build();

        ProgressiveIntentExtractionEngine.ExtractionOutput output =
            new ProgressiveIntentExtractionEngine.ExtractionOutput(response, Map.of("extractionPath", "compound"));

        when(engine.extract(any(IntentExtractionInput.class), any(OrchestrationContext.class))).thenReturn(output);

        IntentExtractionStep step = new IntentExtractionStep(extractor, provider);
        PipelineContext ctx = PipelineContext.from("q", OrchestrationContext.forUser("user"));

        PipelineContext updated = step.process(ctx);

        assertThat(updated.getIntentResponse()).isNotNull();
        assertThat(updated.getIntentResponse().hasIntents()).isTrue();
        assertThat(updated.getMetadata()).containsKey("extractionDiagnostics");
        verify(extractor, never()).extract(any(IntentExtractionInput.class), any(OrchestrationContext.class));
    }
}
