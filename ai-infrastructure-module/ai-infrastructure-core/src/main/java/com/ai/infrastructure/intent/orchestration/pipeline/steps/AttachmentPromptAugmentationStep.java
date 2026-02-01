package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AttachmentsProperties;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.targets.ResolvedTarget;
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
        List<ResolvedTarget> storedPinnedTargets = context.getResolvedTargets();
        boolean hasAttachments = attachments != null && !attachments.isEmpty();
        boolean hasStoredPinnedTargets = storedPinnedTargets != null && !storedPinnedTargets.isEmpty();
        if (!hasAttachments && !hasStoredPinnedTargets) {
            return context;
        }

        Set<String> activeIds = orchContext != null && orchContext.getActiveAttachmentIdsResolved() != null
            ? orchContext.getActiveAttachmentIdsResolved().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .collect(Collectors.toSet())
            : Set.of();

        String prefix = buildPinnedContextBlock(activeIds, storedPinnedTargets, attachments);
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
        meta.put("attachmentsCount", hasAttachments ? attachments.size() : 0);
        meta.put("activeCount", activeIds.size());
        meta.put("storedPinnedTargetsCount", hasStoredPinnedTargets ? storedPinnedTargets.size() : 0);
        return updated.withMetadata(METADATA_KEY_ATTACHMENTS_PROMPT, Collections.unmodifiableMap(meta));
    }

    private String buildPinnedContextBlock(Set<String> activeAttachmentIds,
                                           List<ResolvedTarget> storedPinnedTargets,
                                           List<NormalizedAttachment> attachments) {
        StringBuilder sb = new StringBuilder(768);
        sb.append("PINNED CONTEXT (authoritative; prefer answering from this; avoid retrieval if sufficient):\n");

        boolean hasAny = false;

        String storedTargetsBlock = buildStoredPinnedTargetsBlock(activeAttachmentIds, storedPinnedTargets);
        if (StringUtils.hasText(storedTargetsBlock)) {
            hasAny = true;
            sb.append(storedTargetsBlock).append("\n");
        }

        String attachmentsBlock = buildAttachmentsBlock(attachments, activeAttachmentIds);
        if (StringUtils.hasText(attachmentsBlock)) {
            hasAny = true;
            if (StringUtils.hasText(storedTargetsBlock)) {
                sb.append("\n");
            }
            sb.append(attachmentsBlock).append("\n");
        }

        return hasAny ? sb.toString().trim() : null;
    }

    private String buildStoredPinnedTargetsBlock(Set<String> activeAttachmentIds, List<ResolvedTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append("STORED PINNED TARGETS (authoritative; may be stale):\n");
        int index = 1;
        for (ResolvedTarget target : targets) {
            if (target == null || !StringUtils.hasText(target.getId())) {
                continue;
            }
            String id = target.getId().trim();
            if (!StringUtils.hasText(id)) {
                continue;
            }
            // Active attachments (this request) override stored pinned targets.
            if (activeAttachmentIds != null && activeAttachmentIds.contains(id)) {
                continue;
            }

            sb.append(index).append(") ");
            if (StringUtils.hasText(target.getVectorSpace())) {
                sb.append("vectorSpace=").append(target.getVectorSpace()).append(" ");
            }
            sb.append("id=").append(id);

            if (target.getMetadata() != null && !target.getMetadata().isEmpty()) {
                sb.append(" metadata={");
                String meta = target.getMetadata().entrySet().stream()
                    .filter(e -> e != null && StringUtils.hasText(e.getKey()) && StringUtils.hasText(e.getValue()))
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "));
                sb.append(meta).append("}");
            }

            if (StringUtils.hasText(target.getContentText())) {
                sb.append(" contentTextTruncated=").append(target.isContentTextTruncated());
                sb.append(" contentText=\"").append(target.getContentText()).append("\"");
            }

            sb.append("\n");
            index++;
        }

        String out = sb.toString().trim();
        return out.endsWith(":") ? null : out;
    }

    private String buildAttachmentsBlock(List<NormalizedAttachment> attachments, Set<String> activeIds) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append("ACTIVE ATTACHMENTS (authoritative UI context; [ACTIVE] is pinned):\n");

        int index = 1;
        for (NormalizedAttachment attachment : attachments) {
            if (attachment == null
                || !StringUtils.hasText(attachment.getId())) {
                continue;
            }

            boolean active = activeIds != null && activeIds.contains(attachment.getId());
            sb.append(index).append(") ");
            if (active) {
                sb.append("[ACTIVE] ");
            }
            if (StringUtils.hasText(attachment.getVectorSpace())) {
                sb.append("vectorSpace=").append(attachment.getVectorSpace()).append(" ");
            }
            sb.append("id=").append(attachment.getId());

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

            if (StringUtils.hasText(attachment.getContentText())) {
                sb.append(" contentTextTruncated=").append(attachment.isContentTextTruncated());
                sb.append(" contentText=\"").append(attachment.getContentText()).append("\"");
            }

            sb.append("\n");
            index++;
        }

        return sb.toString().trim();
    }
}
