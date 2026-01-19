package com.ai.infrastructure.intent;

import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Provider-agnostic normalizer for relationship query hint prefixes and post-action directives.
 *
 * <p>This parser keeps the relational query clean while preserving trailing non-relational directives
 * (e.g., "then summarize/explain") as generation instructions.</p>
 */
public final class RelationshipQueryTextParser {

    private static final String[] PREFIXES = { "relationship query:", "relationship_query:", "relationship-query:" };

    private RelationshipQueryTextParser() {
    }

    public static Parts split(String text) {
        if (!StringUtils.hasText(text)) {
            return new Parts(null, null);
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new Parts(null, null);
        }

        String withoutPrefix = stripHintPrefix(trimmed);
        if (!StringUtils.hasText(withoutPrefix)) {
            return new Parts(null, null);
        }

        SplitResult split = splitTrailingDirective(withoutPrefix);
        return new Parts(split.relationalQuery(), split.generationInstructions());
    }

    private static String stripHintPrefix(String trimmed) {
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String prefix : PREFIXES) {
            if (lower.startsWith(prefix)) {
                return trimmed.substring(prefix.length()).trim();
            }
        }
        return trimmed;
    }

    private static SplitResult splitTrailingDirective(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return new SplitResult(null, null);
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);

        int idx = lower.indexOf(" and then ");
        int boundaryLen = " and then ".length();
        if (idx < 0) {
            idx = lower.indexOf(" then ");
            boundaryLen = " then ".length();
        }
        if (idx < 0) {
            return new SplitResult(trimmed, null);
        }

        String afterLower = lower.substring(idx + boundaryLen);
        if (!looksNonRelational(afterLower)) {
            return new SplitResult(trimmed, null);
        }

        String relational = trimmed.substring(0, idx).trim();
        String directive = trimmed.substring(idx + boundaryLen).trim();
        directive = stripLeadingPunctuation(directive);

        if (!StringUtils.hasText(relational)) {
            return new SplitResult(null, StringUtils.hasText(directive) ? directive : null);
        }

        return new SplitResult(relational, StringUtils.hasText(directive) ? directive : null);
    }

    private static boolean looksNonRelational(String lowerText) {
        if (!StringUtils.hasText(lowerText)) {
            return false;
        }

        String after = lowerText.trim();
        return after.contains("summariz")
            || after.contains("explain")
            || after.contains(" why ")
            || after.startsWith("why ")
            || after.contains("describe")
            || after.contains("analyz")
            || after.contains("recommend")
            || after.contains("write ")
            || after.contains("generate ")
            || after.contains("tell me");
    }

    private static String stripLeadingPunctuation(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        int idx = 0;
        while (idx < text.length()) {
            char ch = text.charAt(idx);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '-' || ch == '–' || ch == '—' || ch == ',' || ch == '.') {
                idx++;
                continue;
            }
            break;
        }
        String trimmed = text.substring(idx).trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public record Parts(String relationalQuery, String generationInstructions) {
    }

    private record SplitResult(String relationalQuery, String generationInstructions) {
    }
}
