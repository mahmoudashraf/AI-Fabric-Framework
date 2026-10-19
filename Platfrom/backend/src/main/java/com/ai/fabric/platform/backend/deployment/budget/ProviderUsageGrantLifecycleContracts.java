package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.IOException;
import java.math.BigInteger;
import java.time.Instant;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.invalid;

/** Correction of unused approvals only; never a refund, balance reset or new funding source. */
public final class ProviderUsageGrantLifecycleContracts {
    private ProviderUsageGrantLifecycleContracts() { }

    public enum GrantStatus { ACTIVE, VOID_UNUSED }

    public record ZeroAllocatedCredits(
        @JsonDeserialize(using = ExactZero.class) Long chatAttempts,
        @JsonDeserialize(using = ExactZero.class) Long totalTokens,
        @JsonDeserialize(using = ExactZero.class) Long costNanoUsd) {
        public ZeroAllocatedCredits { zero(chatAttempts); zero(totalTokens); zero(costNanoUsd); }
        @JsonAnySetter public void unknown(String field, JsonNode value) { throw invalid(); }
    }

    @JsonDeserialize(using = ProviderUsageVoidUnusedCommandDeserializer.class)
    public record VoidUnusedCommand(String operationKey, String expectedGrantFingerprint,
        @JsonDeserialize(using = ExactZero.class) Long expectedAllocationCount, ZeroAllocatedCredits expectedAllocated,
        String reason, String ownerApprovalEvidenceRef) {
        public VoidUnusedCommand {
            exactText(operationKey, 128); sha(expectedGrantFingerprint); zero(expectedAllocationCount);
            if (expectedAllocated == null) throw invalid();
            exactText(reason, 1000); exactText(ownerApprovalEvidenceRef, 255);
        }
    }

    public record VoidUnusedReceipt(String receiptId, String grantId, GrantStatus status,
        String grantFingerprint, String commandFingerprint, VoidUnusedCommand command,
        String actorId, String actorRole, Instant voidedAt) {
        public VoidUnusedReceipt {
            text(receiptId, 96); text(grantId, 96); sha(grantFingerprint); sha(commandFingerprint); text(actorId, 255);
            if (status != GrantStatus.VOID_UNUSED || command == null || !"PLATFORM_ADMIN".equals(actorRole) || voidedAt == null
                || !grantFingerprint.equals(command.expectedGrantFingerprint())) throw invalid();
        }
    }

    public record GrantQuotaView(String grantId, String campaignId, String customerId, String targetProfileId,
        String environment, Limits lifetimeCredits, Limits lifetimeAllocated, Limits remaining,
        Instant expiresAt, String pricingReference, GrantStatus status, String grantFingerprint,
        Limits availableForAllocation) { }

    // Boxed fields reject omission/null; this deserializer also rejects coercion from strings or decimals.
    public static final class ExactZero extends JsonDeserializer<Long> {
        @Override public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT || !BigInteger.ZERO.equals(parser.getBigIntegerValue()))
                return context.reportInputMismatch(Long.class, "An explicit integer zero is required");
            return 0L;
        }
    }

    private static void zero(Long value) { if (value == null || value != 0) throw invalid(); }
    private static void exactText(String value, int limit) {
        text(value, limit);
        if (!value.equals(value.strip()) || value.chars().anyMatch(Character::isISOControl)) throw invalid();
    }
}
