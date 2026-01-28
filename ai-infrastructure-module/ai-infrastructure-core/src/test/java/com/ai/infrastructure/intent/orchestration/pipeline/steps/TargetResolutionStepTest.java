package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.IntentType;
import com.ai.infrastructure.dto.MultiIntentResponse;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTargetSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TargetResolutionStepTest {

    @Test
    void shouldResolveActiveAttachmentsEvenWhenNoIntentRequiresTargetResolution() {
        TargetResolutionStep step = new TargetResolutionStep();

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("info")
            .requiresTargetResolution(false)
            .build();

        OrchestrationContext orchContext = OrchestrationContext.builder()
            .userId("user-1")
            .attachmentsNormalized(List.of(NormalizedAttachment.builder()
                .id("att-1")
                .vectorSpace("product")
                .metadata(Map.of("sku", "ELEC-1"))
                .build()))
            .activeAttachmentIdsResolved(List.of("att-1"))
            .build();

        PipelineContext context = PipelineContext.from("q", orchContext)
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).compound(false).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getResolvedTargets()).hasSize(1);
        assertThat(updated.getResolvedTargets().getFirst().getSource()).isEqualTo(ResolvedTargetSource.ACTIVE_ATTACHMENTS);
        assertThat(updated.getMetadata()).containsKey("targetResolution");
    }

    @Test
    void shouldResolveActiveAttachmentsWhenRequired() {
        TargetResolutionStep step = new TargetResolutionStep();

        Intent intent = Intent.builder()
            .type(IntentType.ACTION)
            .intent("add_to_cart")
            .requiresTargetResolution(true)
            .build();

        OrchestrationContext orchContext = OrchestrationContext.builder()
            .userId("user-1")
            .attachmentsNormalized(List.of(NormalizedAttachment.builder()
                .id("att-1")
                .vectorSpace("product")
                .metadata(Map.of("sku", "ELEC-1"))
                .build()))
            .activeAttachmentIdsResolved(List.of("att-1"))
            .build();

        PipelineContext context = PipelineContext.from("add it", orchContext)
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).compound(false).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isFalse();
        assertThat(updated.getResolvedTargets()).hasSize(1);
        assertThat(updated.getResolvedTargets().getFirst().getSource()).isEqualTo(ResolvedTargetSource.ACTIVE_ATTACHMENTS);
        assertThat(updated.getMetadata()).containsKey("targetResolution");
    }

    @Test
    void shouldTerminateWithClarificationWhenRequiredButNoTargets() {
        TargetResolutionStep step = new TargetResolutionStep();

        Intent intent = Intent.builder()
            .type(IntentType.INFORMATION)
            .intent("compare")
            .requiresTargetResolution(true)
            .build();

        OrchestrationContext orchContext = OrchestrationContext.forUser("user-1");

        PipelineContext context = PipelineContext.from("compare both", orchContext)
            .toBuilder()
            .intentResponse(MultiIntentResponse.builder().intents(List.of(intent)).compound(false).build())
            .build();

        PipelineContext updated = step.process(context);

        assertThat(updated.isShouldTerminate()).isTrue();
        assertThat(updated.getEarlyTerminationResult()).isNotNull();
        assertThat(updated.getEarlyTerminationResult().getType()).isEqualTo(OrchestrationResultType.CLARIFICATION_REQUIRED);
    }
}
