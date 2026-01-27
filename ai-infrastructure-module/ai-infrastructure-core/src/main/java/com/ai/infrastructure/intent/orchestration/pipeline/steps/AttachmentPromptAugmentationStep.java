package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AttachmentsProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Injects normalized attachments into the LLM-visible prompt context as an authoritative section.
 *
 * <p><strong>Order:</strong> 26 (after conversation enrichment, before PII detection)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentPromptAugmentationStep implements PipelineStep {

    private static final String STEP_NAME = "AttachmentPromptAugmentation";
    private static final int STEP_ORDER = 26;

    private static final String METADATA_KEY_ATTACHMENTS_PROMPT = "attachmentsPrompt";

    private final AttachmentsProperties properties;

    @Override
    public String getStepName() {
        return STEP_NAME;
    }

    @Override
    public int getOrder() {
        return STEP_ORDER;
    }

    @Override
    public PipelineContext process(PipelineContext context) {
        if (context == null || context.isShouldTerminate()) {
            return context;
        }
        if (properties != null && !properties.isEnabled()) {
            return context;
        }

        OrchestrationContext orchContext = context.getOrchestrationContext();
        List<NormalizedAttachment> attachments = orchContext != null ? orchContext.getAttachmentsNormalized() : null;
        if (attachments == null || attachments.isEmpty()) {
            return context;
        }

        Set<String> activeIds = orchContext != null && orchContext.getActiveAttachmentIdsResolved() != null
            ? orchContext.getActiveAttachmentIdsResolved().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet())
            : Set.of();

        String prefix = buildAttachmentsBlock(attachments, activeIds);
        if (!StringUtils.hasText(prefix)) {
            return context;
        }

        String existing = context.getEffectiveQuery();
        String merged = StringUtils.hasText(existing)
            ? prefix + "\n\n" + existing
            : prefix;

        PipelineContext updated = context.toBuilder()
            .processedQuery(merged)
            .build();

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("injected", true);
        meta.put("attachmentsCount", attachments.size());
        meta.put("activeCount", activeIds.size());
        return updated.withMetadata(METADATA_KEY_ATTACHMENTS_PROMPT, Collections.unmodifiableMap(meta));
    }

    private String buildAttachmentsBlock(List<NormalizedAttachment> attachments, Set<String> activeIds) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("ATTACHMENTS (authoritative):\n");

        int index = 1;
        for (NormalizedAttachment attachment : attachments) {
            if (attachment == null
                || !StringUtils.hasText(attachment.getId())
                || !StringUtils.hasText(attachment.getVectorSpace())) {
                continue;
            }

            boolean active = activeIds != null && activeIds.contains(attachment.getId());
            sb.append(index).append(") ");
            if (active) {
                sb.append("[ACTIVE] ");
            }
            sb.append("vectorSpace=").append(attachment.getVectorSpace())
                .append(" id=").append(attachment.getId());

            if (StringUtils.hasText(attachment.getSource())) {
                sb.append(" source=").append(attachment.getSource());
            }

            if (attachment.getMetadata() != null && !attachment.getMetadata().isEmpty()) {
                sb.append(" metadata={");
                String meta = attachment.getMetadata().entrySet().stream()
                    .filter(e -> e != null && StringUtils.hasText(e.getKey()) && StringUtils.hasText(e.getValue()))
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
                sb.append(meta).append("}");
            }

            if (StringUtils.hasText(attachment.getContentSnippet())) {
                sb.append(" snippet=\"").append(attachment.getContentSnippet()).append("\"");
            }

            sb.append("\n");
            index++;
        }

        return sb.toString().trim();
    }
}

