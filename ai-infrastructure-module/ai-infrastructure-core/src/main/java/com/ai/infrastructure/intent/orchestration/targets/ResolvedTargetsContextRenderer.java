package com.ai.infrastructure.intent.orchestration.targets;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Renders a list of {@link ResolvedTarget}s into a compact, LLM-visible block.
 *
 * <p>This is intended for prompt context only; it must never be used as an embeddings query input.</p>
 */
public final class ResolvedTargetsContextRenderer {

    private ResolvedTargetsContextRenderer() {
    }

    public static String render(String header, String refPrefix, List<ResolvedTarget> targets) {
        if (!StringUtils.hasText(header) || targets == null || targets.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder(512);
        sb.append(header.trim()).append("\n");

        int index = 1;
        for (ResolvedTarget target : targets) {
            if (target == null) {
                continue;
            }

            sb.append(index).append(") ");
            sb.append("ref=").append(StringUtils.hasText(refPrefix) ? refPrefix.trim() : "target")
                .append("#").append(index).append(" ");

            if (StringUtils.hasText(target.getVectorSpace())) {
                sb.append("vectorSpace=").append(target.getVectorSpace()).append(" ");
            }
            if (StringUtils.hasText(target.getId())) {
                sb.append("id=").append(target.getId()).append(" ");
            }

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

        return sb.toString().trim();
    }
}

