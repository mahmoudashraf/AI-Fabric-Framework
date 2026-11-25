package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import java.time.Instant;
import java.util.UUID;

/** Additional model pricing authorization, never a replacement financial allocation. */
public final class RuntimeBudgetPricingAmendment {
    private RuntimeBudgetPricingAmendment() { }

    public record Command(int schemaVersion, String amendmentId, String approvalId, UUID storeUuid,
                          String runtimeFingerprint, String originalRateCardReference, String additionalRateCardReference,
                          String authorizedBy, String approvalEvidenceRef, String reason) {
        public Command {
            if (schemaVersion != 1 || storeUuid == null || runtimeFingerprint == null
                || !runtimeFingerprint.matches("[a-f0-9]{64}") || amendmentId == null
                || !amendmentId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}")
                || !ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE.equals(originalRateCardReference)
                || !ReviewedOpenAiRateCard.ASTRA_REFERENCE.equals(additionalRateCardReference))
                throw new BudgetException(BudgetException.Code.INVALID_REQUEST);
            approvalId = ProviderBudgetLedger.text(approvalId, 128);
            authorizedBy = ProviderBudgetLedger.text(authorizedBy, 256);
            approvalEvidenceRef = ProviderBudgetLedger.text(approvalEvidenceRef, 255);
            reason = ProviderBudgetLedger.text(reason, 512);
        }
        @Override public String toString() { return "PricingAmendmentCommand[amendmentId=" + amendmentId + "]"; }
    }

    public record Receipt(int schemaVersion, String amendmentId, String approvalId, UUID storeUuid,
                          String runtimeFingerprint, String originalRateCardReference, String additionalRateCardReference,
                          String authorizedBy, String approvalEvidenceRef, String reasonSha256, Instant authorizedAt) { }
}
