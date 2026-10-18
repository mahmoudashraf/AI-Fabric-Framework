package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.ProviderBudgetLedger.Usage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Reviewed 2026-10-18; standard uncached prices, not an invoice reconciliation. */
public final class ReviewedOpenAiRateCard {
    public static final String REFERENCE = "openai-gpt4omini-embedding3small-20261018-v1";
    public static final String CHAT_MODEL = "gpt-4o-mini";
    public static final String EMBEDDING_MODEL = "text-embedding-3-small";
    public static final long CHAT_CONTEXT_TOKENS = 128_000;
    public static final long CHAT_MAX_OUTPUT_TOKENS = 16_384;
    public static final long EMBEDDING_INPUT_TOKENS = 8_192;
    public static final long EMBEDDING_REQUEST_TOKENS = 300_000;
    public static final long CHAT_INPUT_NANO_USD = 150;
    public static final long CHAT_OUTPUT_NANO_USD = 600;
    public static final long EMBEDDING_INPUT_NANO_USD = 20;

    private ReviewedOpenAiRateCard() { }

    public static Usage chat(long input, long output) {
        return new Usage(input, output, Math.addExact(Math.multiplyExact(input, CHAT_INPUT_NANO_USD),
            Math.multiplyExact(output, CHAT_OUTPUT_NANO_USD)));
    }

    public static Usage embedding(long input) {
        return new Usage(input, 0, Math.multiplyExact(input, EMBEDDING_INPUT_NANO_USD));
    }

    public static String credentialIdentity(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new OpenAiBudgetException(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH);
        }
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA_256_UNAVAILABLE");
        }
    }
}
