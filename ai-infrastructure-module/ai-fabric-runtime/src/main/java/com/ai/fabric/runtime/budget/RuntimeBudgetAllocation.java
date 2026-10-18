package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Exact CP-issued financial allocation, accepted only by private initialization, never startup. */
public record RuntimeBudgetAllocation(Approval approval, String runtimeFingerprint) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public RuntimeBudgetAllocation {
        Objects.requireNonNull(approval, "Control-plane allocation is required.");
        RuntimeBudgetConfiguration.canonical(approval.id());
        RuntimeBudgetConfiguration.validateScope(approval.scope());
        if (!fingerprint(approval).equals(runtimeFingerprint)) {
            throw new RuntimeBudgetBindingException(RuntimeBudgetBindingException.Code.ALLOCATION_FINGERPRINT_MISMATCH);
        }
    }

    /** Sorted compact JSON, integer epochSecond/nano expiry and full lowercase SHA-256. No aliases. */
    public static String fingerprint(Approval approval) {
        var scope = approval.scope();
        var limits = approval.limits();
        var projection = sorted(Map.of(
            "schemaVersion", "runtime-provider-budget-allocation-v1",
            "approvalId", approval.id(),
            "scope", sorted(Map.of("customerId", scope.customerId(), "deploymentId", scope.deploymentId(),
                "targetProfileId", scope.targetProfileId(), "environment", scope.environment(),
                "credentialIdentity", scope.credentialIdentity(), "rateCardReference", scope.rateCardReference())),
            "limits", sorted(Map.of("chatAttempts", limits.chatAttempts(), "totalTokens", limits.totalTokens(),
                "costNanoUsd", limits.costNanoUsd())),
            "expiresAt", sorted(Map.of("epochSecond", approval.expiresAt().getEpochSecond(), "nano", approval.expiresAt().getNano()))
        ));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(projection)));
        } catch (JsonProcessingException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("RUNTIME_BUDGET_FINGERPRINT_UNAVAILABLE");
        }
    }

    private static TreeMap<String, Object> sorted(Map<String, ?> values) { return new TreeMap<>(values); }
}
