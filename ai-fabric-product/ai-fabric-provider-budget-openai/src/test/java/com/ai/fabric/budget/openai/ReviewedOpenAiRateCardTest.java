package com.ai.fabric.budget.openai;

import org.junit.jupiter.api.Test;

import static com.ai.fabric.budget.openai.ReviewedOpenAiRateCard.*;
import static org.junit.jupiter.api.Assertions.*;

class ReviewedOpenAiRateCardTest {
    @Test void legacyReferencePricesAndCoverageRemainUnchanged() {
        assertEquals("openai-gpt4omini-embedding3small-20261018-v1", REFERENCE);
        assertEquals(150_000_000L, chat(1_000_000, 0).costNanoUsd());
        assertEquals(600_000_000L, chat(0, 1_000_000).costNanoUsd());
        assertEquals(20_000_000L, embedding(1_000_000).costNanoUsd());
        assertTrue(supportsModel(REFERENCE, CHAT_MODEL));
        assertTrue(supportsModel(REFERENCE, EMBEDDING_MODEL));
        assertFalse(supportsModel(REFERENCE, GPT_54_MINI_MODEL));
        assertFalse(supportsModel(REFERENCE, GPT_54_MINI_SNAPSHOT));
    }

    @Test void miniAliasAndSnapshotUseReviewedStandardUncachedRates() {
        for (String model : new String[]{GPT_54_MINI_MODEL, GPT_54_MINI_SNAPSHOT}) {
            assertEquals(750_000_000L, chat(model, 1_000_000, 0).costNanoUsd());
            assertEquals(4_500_000_000L, chat(model, 0, 1_000_000).costNanoUsd());
            assertEquals(22_500L, chat(model, 12, 3).costNanoUsd());
            assertEquals(400_000L, chatContextTokens(model));
            assertTrue(supportsModel(GPT_54_MINI_REFERENCE, model));
        }
        assertTrue(supportsModel(GPT_54_MINI_REFERENCE, CHAT_MODEL));
        assertTrue(supportsModel(GPT_54_MINI_REFERENCE, EMBEDDING_MODEL));
        assertFalse(supportsModel("unreviewed", GPT_54_MINI_MODEL));
        assertFalse(supportsModel(GPT_54_MINI_REFERENCE, "gpt-5.4"));
        assertFalse(isReviewedChatModel("gpt-5.4-mini-2099-01-01"));
        assertThrows(OpenAiBudgetException.class, () -> chat("gpt-5.4", 12, 3));
        assertThrows(ArithmeticException.class, () -> chat(GPT_54_MINI_MODEL, Long.MAX_VALUE, 1));
    }
}
