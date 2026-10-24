package com.ai.fabric.budget.openai;

import com.ai.fabric.budget.ProviderBudgetLedger.Usage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Versioned standard uncached prices, not an invoice reconciliation. */
public final class ReviewedOpenAiRateCard {
    // Keep the original reference and model coverage immutable for existing allocations.
    public static final String REFERENCE = "openai-gpt4omini-embedding3small-20261018-v1";
    public static final String GPT_54_MINI_REFERENCE = "openai-gpt4omini-gpt54mini-embedding3small-20261024-v2";
    public static final String CHAT_MODEL = "gpt-4o-mini";
    public static final String GPT_54_MINI_MODEL = "gpt-5.4-mini";
    public static final String GPT_54_MINI_SNAPSHOT = "gpt-5.4-mini-2026-03-17";
    public static final String EMBEDDING_MODEL = "text-embedding-3-small";
    public static final long CHAT_CONTEXT_TOKENS = 128_000;
    public static final long CHAT_MAX_OUTPUT_TOKENS = 16_384;
    public static final long EMBEDDING_INPUT_TOKENS = 8_192;
    public static final long EMBEDDING_REQUEST_TOKENS = 300_000;
    public static final long CHAT_INPUT_NANO_USD = 150;
    public static final long CHAT_OUTPUT_NANO_USD = 600;
    // https://developers.openai.com/api/docs/models/gpt-5.4-mini (reviewed 2026-10-24).
    public static final long GPT_54_MINI_CONTEXT_TOKENS = 400_000;
    public static final long GPT_54_MINI_INPUT_NANO_USD = 750;
    public static final long GPT_54_MINI_OUTPUT_NANO_USD = 4_500;
    public static final long EMBEDDING_INPUT_NANO_USD = 20;

    private ReviewedOpenAiRateCard() { }

    public static Usage chat(long input, long output) {
        return new Usage(input, output, Math.addExact(Math.multiplyExact(input, CHAT_INPUT_NANO_USD),
            Math.multiplyExact(output, CHAT_OUTPUT_NANO_USD)));
    }

    public static boolean isReviewedReference(String reference) {
        return REFERENCE.equals(reference) || GPT_54_MINI_REFERENCE.equals(reference);
    }

    public static boolean isGpt54Mini(String model) {
        return GPT_54_MINI_MODEL.equals(model) || GPT_54_MINI_SNAPSHOT.equals(model);
    }

    public static boolean isReviewedChatModel(String model) {
        return CHAT_MODEL.equals(model) || isGpt54Mini(model);
    }

    public static boolean supportsModel(String reference, String model) {
        return isReviewedReference(reference) && (CHAT_MODEL.equals(model) || EMBEDDING_MODEL.equals(model)
            || (GPT_54_MINI_REFERENCE.equals(reference) && isGpt54Mini(model)));
    }

    public static long chatContextTokens(String model) {
        if (!isReviewedChatModel(model)) throw new OpenAiBudgetException(OpenAiBudgetException.Code.UNSUPPORTED_CONFIGURATION);
        return isGpt54Mini(model) ? GPT_54_MINI_CONTEXT_TOKENS : CHAT_CONTEXT_TOKENS;
    }

    public static Usage chat(String model, long input, long output) {
        chatContextTokens(model);
        return isGpt54Mini(model) ? new Usage(input, output,
            Math.addExact(Math.multiplyExact(input, GPT_54_MINI_INPUT_NANO_USD),
                Math.multiplyExact(output, GPT_54_MINI_OUTPUT_NANO_USD))) : chat(input, output);
    }

    static boolean matchesChatResponse(String requested, String actual) {
        if (CHAT_MODEL.equals(requested)) return CHAT_MODEL.equals(actual) || "gpt-4o-mini-2024-07-18".equals(actual);
        if (GPT_54_MINI_MODEL.equals(requested)) return isGpt54Mini(actual);
        return GPT_54_MINI_SNAPSHOT.equals(requested) && GPT_54_MINI_SNAPSHOT.equals(actual);
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
