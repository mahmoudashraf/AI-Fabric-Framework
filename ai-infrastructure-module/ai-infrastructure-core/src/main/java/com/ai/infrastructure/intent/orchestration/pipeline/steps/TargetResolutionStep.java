package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.OrchestrationResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationResultType;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.intent.orchestration.target.ResolvedTarget;
import com.ai.infrastructure.intent.orchestration.target.ResolvedTargetSource;
import com.ai.infrastructure.dto.Intent;
import com.ai.infrastructure.dto.MultiIntentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves request targets deterministically (active attachments first; working set later).
 *
 * <p><strong>Order:</strong> 52 (after intent extraction; before vectorSpace resolution)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TargetResolutionStep implements PipelineStep {

    private static final String STEP_NAME = "TargetResolution";
    private static final int STEP_ORDER = 52;

    private static final String METADATA_KEY_TARGET_RESOLUTION = "targetResolution";
    private static final String MSG_CLARIFICATION_REQUIRED = "Which item are you referring to? Select an attachment or specify an explicit identifier.";

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

        MultiIntentResponse intentResponse = context.getIntentResponse();
        if (intentResponse == null || intentResponse.getIntents() == null || intentResponse.getIntents().isEmpty()) {
            return context;
        }

        if (!anyIntentRequiresTargetResolution(intentResponse.getIntents())) {
            return context;
        }

        OrchestrationContext orchContext = context.getOrchestrationContext();
        if (orchContext == null) {
            return context;
        }

        List<String> activeIds = orchContext.getActiveAttachmentIdsResolved();
        List<NormalizedAttachment> attachments = orchContext.getAttachmentsNormalized();
        if (activeIds == null || activeIds.isEmpty() || attachments == null || attachments.isEmpty()) {
            // If targets already exist (e.g., seeded from conversation working set), just attach resolution metadata.
            if (context.getResolvedTargets() != null && !context.getResolvedTargets().isEmpty()) {
                ResolvedTargetSource source = resolveUniformSource(context.getResolvedTargets());
                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put("source", source != null ? source.name() : "MIXED");
                meta.put("count", context.getResolvedTargets().size());
                return context.withMetadata(METADATA_KEY_TARGET_RESOLUTION, Collections.unmodifiableMap(meta));
            }
            return context.terminate(OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message(MSG_CLARIFICATION_REQUIRED)
                .data(Map.of("reason", "TARGET_REQUIRED"))
                .build());
        }

        List<ResolvedTarget> resolved = new ArrayList<>();
        for (String id : activeIds) {
            if (!StringUtils.hasText(id)) {
                continue;
            }
            NormalizedAttachment attachment = findById(attachments, id.trim());
            if (attachment == null) {
                continue;
            }
            resolved.add(ResolvedTarget.builder()
                .id(attachment.getId())
                .vectorSpace(attachment.getVectorSpace())
                .contentSnippet(attachment.getContentSnippet())
                .metadata(attachment.getMetadata() != null ? attachment.getMetadata() : Map.of())
                .source(ResolvedTargetSource.ACTIVE_ATTACHMENTS)
                .build());
        }

        if (resolved.isEmpty()) {
            return context.terminate(OrchestrationResult.builder()
                .type(OrchestrationResultType.CLARIFICATION_REQUIRED)
                .success(false)
                .message(MSG_CLARIFICATION_REQUIRED)
                .data(Map.of("reason", "TARGET_REQUIRED"))
                .build());
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("source", ResolvedTargetSource.ACTIVE_ATTACHMENTS.name());
        meta.put("count", resolved.size());

        PipelineContext updated = context.toBuilder()
            .resolvedTargets(Collections.unmodifiableList(resolved))
            .build();

        return updated.withMetadata(METADATA_KEY_TARGET_RESOLUTION, Collections.unmodifiableMap(meta));
    }

    private boolean anyIntentRequiresTargetResolution(List<Intent> intents) {
        if (intents == null || intents.isEmpty()) {
            return false;
        }
        for (Intent intent : intents) {
            if (intent != null && Boolean.TRUE.equals(intent.getRequiresTargetResolution())) {
                return true;
            }
        }
        return false;
    }

    private NormalizedAttachment findById(List<NormalizedAttachment> attachments, String id) {
        for (NormalizedAttachment attachment : attachments) {
            if (attachment == null || !StringUtils.hasText(attachment.getId())) {
                continue;
            }
            if (attachment.getId().equals(id)) {
                return attachment;
            }
        }
        return null;
    }

    private ResolvedTargetSource resolveUniformSource(List<ResolvedTarget> targets) {
        if (targets == null || targets.isEmpty()) {
            return null;
        }
        ResolvedTargetSource source = null;
        for (ResolvedTarget target : targets) {
            if (target == null || target.getSource() == null) {
                return null;
            }
            if (source == null) {
                source = target.getSource();
                continue;
            }
            if (source != target.getSource()) {
                return null;
            }
        }
        return source;
    }
}
