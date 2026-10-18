package com.ai.fabric.platform.backend.deployment.budget;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;

/** Exact runtime-provider-budget-allocation-v1 wire identity, independent of CP audit terms. */
final class ProviderUsageRuntimeAllocationIdentity {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ProviderUsageRuntimeAllocationIdentity() { }

    static String fingerprint(AllocationTerms terms) {
        return fingerprint(terms.approvalId(), terms.scope(), terms.caps(), terms.expiresAt(),
            terms.credentialSha256(), terms.pricingReference());
    }

    static String fingerprint(String approvalId, AllocationScope scope, Limits caps, Instant expiresAt,
                              String credentialSha256, String rateCardReference) {
        var projection = sorted(Map.of(
            "schemaVersion", "runtime-provider-budget-allocation-v1",
            "approvalId", approvalId,
            "scope", sorted(Map.of("customerId", scope.customerId(), "deploymentId", scope.deploymentId(),
                "targetProfileId", scope.targetProfileId(), "environment", scope.environment(),
                "credentialIdentity", "sha256:" + sha(credentialSha256), "rateCardReference", rateCardReference)),
            "limits", sorted(Map.of("chatAttempts", caps.chatAttempts(), "totalTokens", caps.totalTokens(),
                "costNanoUsd", caps.costNanoUsd())),
            "expiresAt", sorted(Map.of("epochSecond", expiresAt.getEpochSecond(), "nano", expiresAt.getNano()))
        ));
        try { return ProviderUsageAllowanceCodec.sha256(JSON.writeValueAsString(projection)); }
        catch (JsonProcessingException impossible) { throw new IllegalStateException("RUNTIME_BUDGET_FINGERPRINT_UNAVAILABLE"); }
    }

    private static TreeMap<String, Object> sorted(Map<String, ?> values) { return new TreeMap<>(values); }
}
