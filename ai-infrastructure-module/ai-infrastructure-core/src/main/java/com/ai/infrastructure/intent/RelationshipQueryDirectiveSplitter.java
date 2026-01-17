package com.ai.infrastructure.intent;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Provider-agnostic normalization for relationship query text.
 *
 * <p>When users write "relationship query: ... and then summarize/explain", the relationship_query
 * action handler expects only the relational part, while post-action generation (if enabled) can
 * use the stripped directive as generation instructions.</p>
 */
final class RelationshipQueryDirectiveSplitter {

    private RelationshipQueryDirectiveSplitter() {}

    record SplitResult(String relationalQuery, String generationInstructions) {}

    static SplitResult split(String query) {
        if (!StringUtils.hasText(query)) {
            return new SplitResult(null, null);
        }

        String trimmed = query.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // Common hint used by tests and some callers to guide intent extraction.
        String[] prefixes = {"relationship query:", "relationship_query:", "relationship-query:"};
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                trimmed = trimmed.substring(prefix.length()).trim();
                lower = trimmed.toLowerCase(Locale.ROOT);
                break;
            }
        }

        // Prefer the last boundary (directive is typically appended at the end).
        Boundary boundary = chooseBoundary(lower);
        if (boundary == null) {
            return new SplitResult(trimmed, null);
        }

        String afterLower = lower.substring(boundary.index() + boundary.length());
        if (!looksNonRelationalDirective(afterLower)) {
            return new SplitResult(trimmed, null);
        }

        String relational = trimmed.substring(0, boundary.index()).trim();
        String directive = trimmed.substring(boundary.index() + boundary.length()).trim();
        return new SplitResult(relational, StringUtils.hasText(directive) ? directive : null);
    }

    private static Boundary chooseBoundary(String lower) {
        int idxAndThen = lower.lastIndexOf(" and then ");
        // Avoid selecting the " then " inside an " and then " boundary.
        int idxThen = idxAndThen >= 0
            ? lower.lastIndexOf(" then ", Math.max(0, idxAndThen - 1))
            : lower.lastIndexOf(" then ");
        int idxAnd = lower.lastIndexOf(" and ");

        Boundary best = null;
        if (idxAndThen >= 0) {
            best = new Boundary(idxAndThen, " and then ".length());
        }
        if (idxThen >= 0 && (best == null || idxThen > best.index() || (idxThen == best.index() && " then ".length() > best.length()))) {
            best = new Boundary(idxThen, " then ".length());
        }
        if (idxAnd >= 0 && (best == null || idxAnd > best.index() || (idxAnd == best.index() && " and ".length() > best.length()))) {
            best = new Boundary(idxAnd, " and ".length());
        }
        return best;
    }

    private static boolean looksNonRelationalDirective(String afterLower) {
        if (!StringUtils.hasText(afterLower)) {
            return false;
        }
        String after = afterLower.trim();
        return after.contains("summariz")
            || after.contains("explain")
            || after.contains(" why ")
            || after.startsWith("why ")
            || after.contains("describe")
            || after.contains("analyz")
            || after.contains("recommend")
            || after.contains("write ")
            || after.contains("generate ")
            || after.contains("tell me")
            || after.contains("insight");
    }

    private record Boundary(int index, int length) {}
}
