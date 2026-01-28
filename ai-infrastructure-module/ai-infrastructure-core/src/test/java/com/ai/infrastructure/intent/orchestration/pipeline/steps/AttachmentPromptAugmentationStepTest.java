package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AttachmentsProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
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

        assertThat(updated.getEffectiveQuery()).startsWith("ATTACHMENTS (authoritative):");
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
            .contentSnippet("some content")
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
}
