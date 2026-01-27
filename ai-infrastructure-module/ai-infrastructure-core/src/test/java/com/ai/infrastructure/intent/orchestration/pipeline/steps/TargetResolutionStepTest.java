package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.target.ResolvedTargetSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TargetResolutionStepTest {

    @Test
    void shouldResolveTargetsFromActiveAttachments() {
        TargetResolutionStep step = new TargetResolutionStep();

        NormalizedAttachment a1 = NormalizedAttachment.builder()
            .id("1")
            .vectorSpace("product")
            .metadata(Map.of("sku", "SKU-1"))
            .build();

        NormalizedAttachment a2 = NormalizedAttachment.builder()
            .id("2")
            .vectorSpace("product")
            .metadata(Map.of("sku", "SKU-2"))
            .build();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo")
            .attachmentsNormalized(List.of(a1, a2))
            .activeAttachmentIdsResolved(List.of("2"))
            .build();

        PipelineContext ctx = PipelineContext.from("q", orch);
        PipelineContext updated = step.process(ctx);

        assertThat(updated.getResolvedTargets()).hasSize(1);
        assertThat(updated.getResolvedTargets().getFirst().getId()).isEqualTo("2");
        assertThat(updated.getResolvedTargets().getFirst().getVectorSpace()).isEqualTo("product");
        assertThat(updated.getResolvedTargets().getFirst().getSource()).isEqualTo(ResolvedTargetSource.ACTIVE_ATTACHMENTS);
        assertThat(updated.getMetadata()).containsKey("targetResolution");
    }
}

