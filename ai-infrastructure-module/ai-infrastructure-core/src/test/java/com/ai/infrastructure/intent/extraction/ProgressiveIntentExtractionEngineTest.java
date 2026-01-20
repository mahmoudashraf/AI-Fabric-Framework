package com.ai.infrastructure.intent.extraction;

import com.ai.infrastructure.config.ProgressiveIntentExtractionProperties;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.IntentExtractionPostProcessor;
import com.ai.infrastructure.intent.IntentExtractionValidator;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProgressiveIntentExtractionEngineTest {

    @Test
    void shouldReturnCompoundFastPathWhenSuccessful() {
        ProgressiveIntentExtractionProperties properties = new ProgressiveIntentExtractionProperties();
        properties.setRepairEnabled(true);
        properties.setRepairMaxAttempts(1);
        properties.setMultiStepEnabled(true);
        properties.setMaxTotalLlmCalls(5);

        CompoundIntentExtractionStrategy compound = mock(CompoundIntentExtractionStrategy.class);
        RepairIntentExtractionStrategy repair = mock(RepairIntentExtractionStrategy.class);
        MultiStepIntentExtractionStrategy multiStep = mock(MultiStepIntentExtractionStrategy.class);
        IntentExtractionPostProcessor postProcessor = mock(IntentExtractionPostProcessor.class);
        IntentExtractionValidator validator = mock(IntentExtractionValidator.class);

        MultiIntentResponse response = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder().type(IntentType.INFORMATION).intent("refund_policy").build()))
            .build();

        ExtractionAttempt compoundAttempt = ExtractionAttempt.builder()
            .success(true)
            .response(response)
            .validationResult(new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of()))
            .strategyName("compound")
            .llmCalls(1)
            .build();

        when(compound.attemptExtract(anyString(), any(OrchestrationContext.class))).thenReturn(compoundAttempt);
        when(postProcessor.postProcess(any(MultiIntentResponse.class), anyString())).thenReturn(response);
        when(validator.validate(any(MultiIntentResponse.class)))
            .thenReturn(new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of()));

        ProgressiveIntentExtractionEngine engine = new ProgressiveIntentExtractionEngine(
            properties,
            compound,
            repair,
            multiStep,
            postProcessor,
            validator
        );

        ProgressiveIntentExtractionEngine.ExtractionOutput output = engine.extract("q", OrchestrationContext.forUser("user"));

        assertThat(output).isNotNull();
        assertThat(output.response()).isNotNull();
        assertThat(output.response().hasIntents()).isTrue();
        assertThat(output.diagnostics()).containsEntry("extractionPath", "compound");
        assertThat(output.diagnostics()).containsEntry("llmCalls", 1);

        verify(repair, never()).attemptRepair(anyString(), any(), any());
        verify(multiStep, never()).attemptExtract(anyString(), any());
    }

    @Test
    void shouldAttemptRepairOnStructuralFailureAndReturnWhenRepairSucceeds() {
        ProgressiveIntentExtractionProperties properties = new ProgressiveIntentExtractionProperties();
        properties.setRepairEnabled(true);
        properties.setRepairMaxAttempts(1);
        properties.setMultiStepEnabled(true);
        properties.setMaxTotalLlmCalls(5);

        CompoundIntentExtractionStrategy compound = mock(CompoundIntentExtractionStrategy.class);
        RepairIntentExtractionStrategy repair = mock(RepairIntentExtractionStrategy.class);
        MultiStepIntentExtractionStrategy multiStep = mock(MultiStepIntentExtractionStrategy.class);
        IntentExtractionPostProcessor postProcessor = mock(IntentExtractionPostProcessor.class);
        IntentExtractionValidator validator = mock(IntentExtractionValidator.class);

        ExtractionAttempt compoundAttempt = ExtractionAttempt.builder()
            .success(false)
            .validationResult(new IntentExtractionValidator.ValidationResult(
                false,
                IntentExtractionValidator.ErrorCategory.STRUCTURAL,
                List.of("parse failed"),
                List.of()
            ))
            .strategyName("compound")
            .llmCalls(1)
            .build();

        MultiIntentResponse repaired = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder().type(IntentType.OUT_OF_SCOPE).intent("out_of_scope").build()))
            .build();

        ExtractionAttempt repairAttempt = ExtractionAttempt.builder()
            .success(true)
            .response(repaired)
            .validationResult(new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of()))
            .strategyName("repair")
            .llmCalls(1)
            .build();

        when(compound.attemptExtract(anyString(), any(OrchestrationContext.class))).thenReturn(compoundAttempt);
        when(repair.attemptRepair(anyString(), any(OrchestrationContext.class), any(ExtractionAttempt.class))).thenReturn(repairAttempt);
        when(postProcessor.postProcess(any(MultiIntentResponse.class), anyString())).thenReturn(repaired);
        when(validator.validate(any(MultiIntentResponse.class)))
            .thenReturn(new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of()));

        ProgressiveIntentExtractionEngine engine = new ProgressiveIntentExtractionEngine(
            properties,
            compound,
            repair,
            multiStep,
            postProcessor,
            validator
        );

        ProgressiveIntentExtractionEngine.ExtractionOutput output = engine.extract("q", OrchestrationContext.forUser("user"));

        assertThat(output.diagnostics()).containsEntry("extractionPath", "repair");
        assertThat(output.diagnostics()).containsEntry("llmCalls", 2);
        verify(multiStep, never()).attemptExtract(anyString(), any());
    }

    @Test
    void shouldAttemptMultiStepAfterRepairFailure() {
        ProgressiveIntentExtractionProperties properties = new ProgressiveIntentExtractionProperties();
        properties.setRepairEnabled(true);
        properties.setRepairMaxAttempts(1);
        properties.setMultiStepEnabled(true);
        properties.setMaxTotalLlmCalls(5);

        CompoundIntentExtractionStrategy compound = mock(CompoundIntentExtractionStrategy.class);
        RepairIntentExtractionStrategy repair = mock(RepairIntentExtractionStrategy.class);
        MultiStepIntentExtractionStrategy multiStep = mock(MultiStepIntentExtractionStrategy.class);
        IntentExtractionPostProcessor postProcessor = mock(IntentExtractionPostProcessor.class);
        IntentExtractionValidator validator = mock(IntentExtractionValidator.class);

        ExtractionAttempt compoundAttempt = ExtractionAttempt.builder()
            .success(false)
            .validationResult(new IntentExtractionValidator.ValidationResult(
                false,
                IntentExtractionValidator.ErrorCategory.STRUCTURAL,
                List.of("parse failed"),
                List.of()
            ))
            .strategyName("compound")
            .llmCalls(1)
            .build();

        ExtractionAttempt repairAttempt = ExtractionAttempt.builder()
            .success(false)
            .validationResult(new IntentExtractionValidator.ValidationResult(
                false,
                IntentExtractionValidator.ErrorCategory.STRUCTURAL,
                List.of("repair failed"),
                List.of()
            ))
            .strategyName("repair")
            .llmCalls(1)
            .build();

        MultiIntentResponse multiStepResponse = MultiIntentResponse.builder()
            .intents(List.of(Intent.builder().type(IntentType.INFORMATION).intent("refund_policy").build()))
            .build();

        ExtractionAttempt multiStepAttempt = ExtractionAttempt.builder()
            .success(true)
            .response(multiStepResponse)
            .validationResult(new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of()))
            .strategyName("multi_step")
            .llmCalls(2)
            .build();

        when(compound.attemptExtract(anyString(), any(OrchestrationContext.class))).thenReturn(compoundAttempt);
        when(repair.attemptRepair(anyString(), any(OrchestrationContext.class), any(ExtractionAttempt.class))).thenReturn(repairAttempt);
        when(multiStep.attemptExtract(anyString(), any(OrchestrationContext.class))).thenReturn(multiStepAttempt);
        when(postProcessor.postProcess(any(MultiIntentResponse.class), anyString())).thenReturn(multiStepResponse);
        when(validator.validate(any(MultiIntentResponse.class)))
            .thenReturn(new IntentExtractionValidator.ValidationResult(true, IntentExtractionValidator.ErrorCategory.NONE, List.of(), List.of()));

        ProgressiveIntentExtractionEngine engine = new ProgressiveIntentExtractionEngine(
            properties,
            compound,
            repair,
            multiStep,
            postProcessor,
            validator
        );

        ProgressiveIntentExtractionEngine.ExtractionOutput output = engine.extract("q", OrchestrationContext.forUser("user"));

        assertThat(output.diagnostics()).containsEntry("extractionPath", "multi_step");
        assertThat(output.diagnostics()).containsEntry("llmCalls", 4);
        verify(multiStep).attemptExtract(anyString(), any(OrchestrationContext.class));
    }
}
