package com.ai.fabric.runtime.budget;

import ai.fabric.spi.RerankingProvider;
import com.ai.fabric.budget.ProviderBudgetLedger.Usage;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Standard API token prices, not Model Vault capacity prices or invoice-exact charges. */
public final class ReviewedCohereRerankingRateCard {
    public static final String REFERENCE = "cohere-rerank5-20261123-v1";
    public static final String FAST = "rerank-v5.0-fast";
    public static final String PRO = "rerank-v5.0-pro";
    // https://cohere.com/blog/rerank-5, reviewed 2026-11-23: USD0.02/0.05 per million input tokens.
    private static final long CONTEXT_TOKENS_PER_DOCUMENT = 32_768;

    private ReviewedCohereRerankingRateCard() { }

    public static long nanoUsdPerToken(String model) {
        return switch (model) {
            case FAST -> 20;
            case PRO -> 50;
            default -> throw new IllegalArgumentException("COHERE_RERANKING_MODEL_NOT_QUALIFIED_FOR_ACCOUNTING");
        };
    }

    public static Usage upperBound(String model, RerankingProvider.Request request) {
        // Reserve the full documented per-document context, not an English characters/token estimate.
        long tokens = Math.multiplyExact(CONTEXT_TOKENS_PER_DOCUMENT, request.candidates().size());
        return usage(model, tokens);
    }

    public static Usage measured(String model, RerankingProvider.Usage nativeUsage) {
        Long input = nativeUsage.billedUnits().get("input_tokens");
        if (input == null || input < 1 || nativeUsage.billedUnits().entrySet().stream()
            .anyMatch(entry -> !"input_tokens".equals(entry.getKey()) && entry.getValue() != 0)) {
            throw new IllegalStateException("COHERE_RERANKING_BILLING_UNIT_MISMATCH");
        }
        return usage(model, input);
    }

    private static Usage usage(String model, long input) {
        return new Usage(input, 0, Math.multiplyExact(input, nanoUsdPerToken(model)));
    }

    public static String credentialIdentity(String key) {
        if (key == null || key.isBlank()) throw new IllegalArgumentException("COHERE_RERANKING_CREDENTIAL_REQUIRED");
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE");
        }
    }
}
