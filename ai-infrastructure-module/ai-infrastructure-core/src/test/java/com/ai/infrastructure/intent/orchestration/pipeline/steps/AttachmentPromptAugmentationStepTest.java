package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AttachmentsProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AttachmentPromptAugmentationStepTest {

    @Test
    void shouldPrefixProcessedQueryWithAttachmentsBlock() {
        AttachmentsProperties properties = new AttachmentsProperties();
        AttachmentPromptAugmentationStep step = new AttachmentPromptAugmentationStep(properties);

        NormalizedAttachment attachment = NormalizedAttachment.builder()
            .id("1")
            .vectorSpace("product")
            .metadata(Map.of("sku", "SKU-1"))
            .build();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo-user")
            .attachmentsNormalized(List.of(attachment))
            .activeAttachmentIdsResolved(List.of("1"))
            .build();

        PipelineContext ctx = PipelineContext.from("hello", orch);
        PipelineContext updated = step.process(ctx);

        assertThat(updated.getEffectiveQuery()).startsWith("PINNED CONTEXT (authoritative;");
        assertThat(updated.getEffectiveQuery()).contains("ACTIVE ATTACHMENTS (authoritative UI context;");
        assertThat(updated.getEffectiveQuery()).contains("[ACTIVE]");
        assertThat(updated.getEffectiveQuery()).contains("vectorSpace=product id=1");
        assertThat(updated.getEffectiveQuery()).contains("metadata={sku=SKU-1}");
        assertThat(updated.getMetadata()).containsKey("attachmentsPrompt");
    }

    @Test
    void shouldIncludeAttachmentIdWhenVectorSpaceIsMissing() {
        AttachmentsProperties properties = new AttachmentsProperties();
        AttachmentPromptAugmentationStep step = new AttachmentPromptAugmentationStep(properties);

        NormalizedAttachment attachment = NormalizedAttachment.builder()
            .id("85")
            .vectorSpace(null)
            .contentText("some content")
            .build();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo-user")
            .attachmentsNormalized(List.of(attachment))
            .activeAttachmentIdsResolved(List.of("85"))
            .build();

        PipelineContext ctx = PipelineContext.from("summarize this", orch);
        PipelineContext updated = step.process(ctx);

        assertThat(updated.getEffectiveQuery()).contains("id=85");
        assertThat(updated.getEffectiveQuery()).doesNotContain("vectorSpace=null");
    }

    @Test
    void shouldIncludeStoredPinnedTargetsWhenNoAttachmentsWereProvided() {
        AttachmentsProperties properties = new AttachmentsProperties();
        AttachmentPromptAugmentationStep step = new AttachmentPromptAugmentationStep(properties);

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo-user")
            .build();

        PipelineContext ctx = PipelineContext.from("price?", orch).toBuilder()
            .resolvedTargets(List.of(
                ResolvedTarget.builder()
                    .id("30")
                    .vectorSpace("product")
                    .metadata(Map.of("sku", "SKU-BOS-20002"))
                    .build()
            ))
            .build();

        PipelineContext updated = step.process(ctx);
        assertThat(updated.getEffectiveQuery()).startsWith("PINNED CONTEXT (authoritative;");
        assertThat(updated.getEffectiveQuery()).contains("STORED PINNED TARGETS (authoritative;");
        assertThat(updated.getEffectiveQuery()).contains("id=30");
        assertThat(updated.getEffectiveQuery()).contains("vectorSpace=product");
        assertThat(updated.getEffectiveQuery()).contains("metadata={sku=SKU-BOS-20002}");
        assertThat(updated.getEffectiveQuery()).contains("\n\nprice?");
    }
}
