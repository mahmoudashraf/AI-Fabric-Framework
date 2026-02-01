package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AttachmentsProperties;
import com.ai.infrastructure.intent.KnowledgeBaseOverviewService;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttachmentNormalizationStepTest {

    @Test
    void shouldNormalizeAttachmentsAndResolveActiveIds() {
        AttachmentsProperties properties = new AttachmentsProperties();
        properties.setMaxAttachments(2);
        properties.setMaxActiveAttachmentIds(10);
        properties.setMaxContentTextChars(10);
        properties.setMaxMetadataKeys(2);
        properties.setMaxMetadataValueChars(5);
        properties.setMaxIdChars(10);
        properties.setMaxVectorSpaceChars(20);

        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> piiProvider = mock(ObjectProvider.class);
        when(piiProvider.getIfAvailable()).thenReturn(null);

        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeBaseOverviewService> kbProvider = mock(ObjectProvider.class);
        when(kbProvider.getIfAvailable()).thenReturn(null);

        AttachmentNormalizationStep step = new AttachmentNormalizationStep(properties, piiProvider, kbProvider);

        OrchestrationAttachment a1 = OrchestrationAttachment.builder()
            .id(" 1 ")
            .vectorSpace("product")
            .contentText("hello world")
            .metadata(Map.of(
                "sku", "SKU-123456",
                "rank", 7,
                "ignored", List.of("not-scalar")
            ))
            .source(" ui-card ")
            .build();

        OrchestrationAttachment a2 = OrchestrationAttachment.builder()
            .id("2")
            .vectorSpace("product")
            .metadata(Map.of("price", "123456"))
            .build();

        OrchestrationAttachment a3 = OrchestrationAttachment.builder()
            .id("3")
            .vectorSpace("product")
            .build();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo")
            .attachments(List.of(a1, a2, a3))
            .activeAttachmentIds(List.of("1", "3", "missing"))
            .build();

        PipelineContext ctx = PipelineContext.from("q", orch);
        PipelineContext updated = step.process(ctx);

        OrchestrationContext out = updated.getOrchestrationContext();
        assertThat(out.getAttachmentsNormalized()).hasSize(2);
        assertThat(out.getActiveAttachmentIdsResolved()).containsExactly("1");

        NormalizedAttachment first = out.getAttachmentsNormalized().getFirst();
        assertThat(first.getId()).isEqualTo("1");
        assertThat(first.getVectorSpace()).isEqualTo("product");
        assertThat(first.getContentText()).isEqualTo("hello worl");
        assertThat(first.isContentTextTruncated()).isTrue();
        assertThat(first.getSource()).isEqualTo("ui-card");
        assertThat(first.getMetadata()).containsEntry("sku", "SKU-1");
        assertThat(first.getMetadata()).containsEntry("rank", "7");
        assertThat(first.getMetadata()).doesNotContainKey("ignored");

        assertThat(updated.getMetadata()).containsKey("attachments");
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) updated.getMetadata().get("attachments");
        assertThat(meta.get("providedCount")).isEqualTo(3);
        assertThat(meta.get("acceptedCount")).isEqualTo(2);
        assertThat(meta.get("truncated")).isEqualTo(true);
        assertThat(meta.get("activeProvidedCount")).isEqualTo(3);
        assertThat(meta.get("activeResolvedCount")).isEqualTo(1);
    }

    @Test
    void shouldRespectAttachmentLimitsWhenMaxIsZero() {
        AttachmentsProperties properties = new AttachmentsProperties();
        properties.setMaxAttachments(0);
        properties.setMaxActiveAttachmentIds(0);

        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> piiProvider = mock(ObjectProvider.class);
        when(piiProvider.getIfAvailable()).thenReturn(null);

        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeBaseOverviewService> kbProvider = mock(ObjectProvider.class);
        when(kbProvider.getIfAvailable()).thenReturn(null);

        AttachmentNormalizationStep step = new AttachmentNormalizationStep(properties, piiProvider, kbProvider);

        OrchestrationAttachment a1 = OrchestrationAttachment.builder()
            .id("1")
            .vectorSpace("product")
            .build();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo")
            .attachments(List.of(a1))
            .activeAttachmentIds(List.of("1"))
            .build();

        PipelineContext ctx = PipelineContext.from("q", orch);
        PipelineContext updated = step.process(ctx);

        assertThat(updated.getOrchestrationContext().getAttachmentsNormalized()).isEmpty();
        assertThat(updated.getOrchestrationContext().getActiveAttachmentIdsResolved()).isEmpty();

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = (Map<String, Object>) updated.getMetadata().get("attachments");
        assertThat(meta.get("truncated")).isEqualTo(true);
        assertThat(meta.get("activeTruncated")).isEqualTo(true);
    }

    @Test
    void shouldAcceptAttachmentWhenVectorSpaceIsMissing() {
        AttachmentsProperties properties = new AttachmentsProperties();
        properties.setMaxAttachments(10);
        properties.setMaxActiveAttachmentIds(10);

        @SuppressWarnings("unchecked")
        ObjectProvider<PIIDetectionService> piiProvider = mock(ObjectProvider.class);
        when(piiProvider.getIfAvailable()).thenReturn(null);

        @SuppressWarnings("unchecked")
        ObjectProvider<KnowledgeBaseOverviewService> kbProvider = mock(ObjectProvider.class);
        when(kbProvider.getIfAvailable()).thenReturn(null);

        AttachmentNormalizationStep step = new AttachmentNormalizationStep(properties, piiProvider, kbProvider);

        OrchestrationAttachment attachment = OrchestrationAttachment.builder()
            .id("att-1")
            .vectorSpace(null)
            .contentText("hello")
            .build();

        OrchestrationContext orch = OrchestrationContext.builder()
            .userId("demo")
            .attachments(List.of(attachment))
            .activeAttachmentIds(List.of("att-1"))
            .build();

        PipelineContext ctx = PipelineContext.from("q", orch);
        PipelineContext updated = step.process(ctx);

        assertThat(updated.getOrchestrationContext().getAttachmentsNormalized()).hasSize(1);
        NormalizedAttachment normalized = updated.getOrchestrationContext().getAttachmentsNormalized().getFirst();
        assertThat(normalized.getId()).isEqualTo("att-1");
        assertThat(normalized.getVectorSpace()).isNull();
        assertThat(normalized.isContentTextTruncated()).isFalse();
        assertThat(updated.getOrchestrationContext().getActiveAttachmentIdsResolved()).containsExactly("att-1");
    }
}
