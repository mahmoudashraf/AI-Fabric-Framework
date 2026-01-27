package com.ai.infrastructure.intent.orchestration.pipeline.steps;

import com.ai.infrastructure.config.AttachmentsProperties;
import com.ai.infrastructure.dto.PIIDetectionResult;
import com.ai.infrastructure.intent.orchestration.OrchestrationContext;
import com.ai.infrastructure.intent.orchestration.attachment.NormalizedAttachment;
import com.ai.infrastructure.intent.orchestration.attachment.OrchestrationAttachment;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineContext;
import com.ai.infrastructure.intent.orchestration.pipeline.PipelineStep;
import com.ai.infrastructure.privacy.pii.PIIDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Normalizes request attachments into a bounded, scalar-only structure for downstream steps.
 *
 * <p><strong>Order:</strong> 23 (after policy resolution, before conversation enrichment)</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentNormalizationStep implements PipelineStep {

    private static final String STEP_NAME = "AttachmentNormalization";
    private static final int STEP_ORDER = 23;

    private static final String METADATA_KEY_ATTACHMENTS = "attachments";

    private final AttachmentsProperties properties;
    private final ObjectProvider<PIIDetectionService> piiDetectionServiceProvider;

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
        if (orchContext == null) {
            return context;
        }

        List<OrchestrationAttachment> raw = orchContext.getAttachments();
        List<String> active = orchContext.getActiveAttachmentIds();
        boolean hasAny = (raw != null && !raw.isEmpty()) || (active != null && !active.isEmpty());
        if (!hasAny) {
            return context;
        }

        PIIDetectionService pii = piiDetectionServiceProvider != null ? piiDetectionServiceProvider.getIfAvailable() : null;

        int maxAttachments = Math.max(0, properties != null ? properties.getMaxAttachments() : 10);
        int maxActiveIds = Math.max(0, properties != null ? properties.getMaxActiveAttachmentIds() : 10);

        List<NormalizedAttachment> normalized = new ArrayList<>();
        boolean attachmentsTruncated = false;

        int provided = raw != null ? raw.size() : 0;
        if (raw != null && !raw.isEmpty() && maxAttachments > 0) {
            for (OrchestrationAttachment attachment : raw) {
                if (normalized.size() >= maxAttachments) {
                    attachmentsTruncated = true;
                    break;
                }
                NormalizedAttachment normalizedAttachment = normalizeAttachment(attachment, pii);
                if (normalizedAttachment != null) {
                    normalized.add(normalizedAttachment);
                }
            }
            if (raw.size() > maxAttachments) {
                attachmentsTruncated = true;
            }
        } else if (raw != null && !raw.isEmpty()) {
            attachmentsTruncated = true;
        }

        Set<String> normalizedIds = new LinkedHashSet<>();
        for (NormalizedAttachment attachment : normalized) {
            if (attachment != null && StringUtils.hasText(attachment.getId())) {
                normalizedIds.add(attachment.getId());
            }
        }

        List<String> activeResolved = new ArrayList<>();
        boolean activeTruncated = false;
        int activeProvided = active != null ? active.size() : 0;
        if (active != null && !active.isEmpty() && maxActiveIds > 0) {
            for (String id : active) {
                if (activeResolved.size() >= maxActiveIds) {
                    activeTruncated = true;
                    break;
                }
                String normalizedId = normalizeToken(id, properties != null ? properties.getMaxIdChars() : 80, false, pii);
                if (StringUtils.hasText(normalizedId) && normalizedIds.contains(normalizedId)) {
                    activeResolved.add(normalizedId);
                }
            }
            if (active.size() > maxActiveIds) {
                activeTruncated = true;
            }
        } else if (active != null && !active.isEmpty()) {
            activeTruncated = true;
        }

        OrchestrationContext updatedOrchContext = orchContext.toBuilder()
            .attachmentsNormalized(List.copyOf(normalized))
            .activeAttachmentIdsResolved(List.copyOf(activeResolved))
            .build();

        Map<String, Object> attachmentMeta = new LinkedHashMap<>();
        attachmentMeta.put("providedCount", provided);
        attachmentMeta.put("acceptedCount", normalized.size());
        attachmentMeta.put("truncated", attachmentsTruncated);
        attachmentMeta.put("activeProvidedCount", activeProvided);
        attachmentMeta.put("activeResolvedCount", activeResolved.size());
        attachmentMeta.put("activeTruncated", activeTruncated);

        PipelineContext updated = context.toBuilder()
            .orchestrationContext(updatedOrchContext)
            .build();

        return updated.withMetadata(METADATA_KEY_ATTACHMENTS, Collections.unmodifiableMap(attachmentMeta));
    }

    private NormalizedAttachment normalizeAttachment(OrchestrationAttachment attachment, PIIDetectionService pii) {
        if (attachment == null) {
            return null;
        }

        String id = normalizeToken(attachment.getId(), properties != null ? properties.getMaxIdChars() : 80, false, pii);
        String vectorSpace = normalizeToken(attachment.getVectorSpace(), properties != null ? properties.getMaxVectorSpaceChars() : 80, false, pii);
        if (!StringUtils.hasText(id) || !StringUtils.hasText(vectorSpace)) {
            return null;
        }

        String snippet = normalizeToken(
            attachment.getContentSnippet(),
            properties != null ? properties.getMaxContentSnippetChars() : 300,
            true,
            pii
        );

        Map<String, String> metadata = normalizeMetadata(attachment.getMetadata(), pii);

        String source = normalizeToken(
            attachment.getSource(),
            properties != null ? properties.getMaxSourceChars() : 60,
            false,
            pii
        );

        return NormalizedAttachment.builder()
            .id(id)
            .vectorSpace(vectorSpace)
            .contentSnippet(snippet)
            .metadata(metadata)
            .source(source)
            .build();
    }

    private Map<String, String> normalizeMetadata(Map<String, Object> raw, PIIDetectionService pii) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }

        int maxKeys = Math.max(0, properties != null ? properties.getMaxMetadataKeys() : 12);
        if (maxKeys == 0) {
            return Map.of();
        }

        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : raw.entrySet()) {
            if (out.size() >= maxKeys) {
                break;
            }
            if (entry == null) {
                continue;
            }

            String key = normalizeMetadataKey(entry.getKey());
            if (!StringUtils.hasText(key) || out.containsKey(key)) {
                continue;
            }

            String value = coerceScalarToString(entry.getValue());
            if (!StringUtils.hasText(value)) {
                continue;
            }

            value = normalizeToken(value, properties != null ? properties.getMaxMetadataValueChars() : 120, false, pii);
            if (!StringUtils.hasText(value)) {
                continue;
            }

            out.put(key, value);
        }

        return out.isEmpty() ? Map.of() : Collections.unmodifiableMap(out);
    }

    private String normalizeMetadataKey(String key) {
        if (!StringUtils.hasText(key)) {
            return null;
        }
        String trimmed = key.trim();
        trimmed = trimmed.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        trimmed = trimmed.replaceAll("\\s+", " ");
        if (!StringUtils.hasText(trimmed)) {
            return null;
        }
        // Keep keys short; values carry the content.
        if (trimmed.length() > 48) {
            trimmed = trimmed.substring(0, 48);
        }
        return trimmed;
    }

    private String coerceScalarToString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String str) {
            return str;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof Character) {
            return String.valueOf(value);
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return null;
    }

    private String normalizeToken(String input, int maxChars, boolean allowLongerWhitespace, PIIDetectionService pii) {
        if (!StringUtils.hasText(input)) {
            return null;
        }

        String normalized = input.trim();
        normalized = normalized.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        if (!allowLongerWhitespace) {
            normalized = normalized.replaceAll("\\s+", " ");
        } else {
            // Preserve some whitespace but collapse extremes.
            normalized = normalized.replaceAll("\\s{3,}", "  ");
        }

        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        if (maxChars > 0 && normalized.length() > maxChars) {
            normalized = normalized.substring(0, maxChars).trim();
        }

        if (!StringUtils.hasText(normalized)) {
            return null;
        }

        if (pii != null) {
            try {
                PIIDetectionResult result = pii.detectAndProcess(normalized);
                if (result != null && StringUtils.hasText(result.getProcessedQuery())) {
                    normalized = result.getProcessedQuery().trim();
                }
            } catch (Exception ex) {
                // Never fail the request due to optional PII processing.
                log.debug("PII normalization failed: {}", ex.getMessage());
            }
        }

        return StringUtils.hasText(normalized) ? normalized : null;
    }
}

