package com.ai.fabric.platform.backend.deployment.budget;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

public final class ProviderUsageAllowanceContracts {
    private ProviderUsageAllowanceContracts() { }

    public record Limits(long chatAttempts, long totalTokens, long costNanoUsd) {
        public Limits { if (chatAttempts < 0 || totalTokens < 0 || costNanoUsd < 0) throw invalid(); }
    }

    /** Reviewed literal model allowlist and the existing deployment secret-resolution purpose. Never an API key. */
    public record ProviderBinding(String provider, List<String> modelIds, String secretPurpose, String managedSecretRef) {
        public ProviderBinding {
            provider = text(provider, 128); secretPurpose = text(secretPurpose, 128);
            if (modelIds == null || modelIds.isEmpty() || modelIds.size() > 32) throw invalid();
            modelIds.forEach(model -> text(model, 256));
            if (modelIds.stream().distinct().count() != modelIds.size()) throw invalid();
            modelIds = modelIds.stream().sorted().toList();
            if (managedSecretRef != null) managedSecretRef = text(managedSecretRef, 255);
        }
    }

    public record ProviderUsageGrant(String id, String campaignId, String customerId, String targetProfileId,
                                    String environment, Limits credits, Instant expiresAt, ProviderBinding binding,
                                    String rateCardReference, String ownerApprovalEvidenceRef, String payerRef) {
        public ProviderUsageGrant {
            id = text(id, 96); campaignId = text(campaignId, 128); customerId = text(customerId, 64);
            targetProfileId = text(targetProfileId, 64); environment = text(environment, 64);
            if (credits == null || expiresAt == null || binding == null) throw invalid();
            rateCardReference = text(rateCardReference, 255); ownerApprovalEvidenceRef = text(ownerApprovalEvidenceRef, 255);
            payerRef = text(payerRef, 255);
        }
    }

    /** Internal server command, not a customer-facing raw-limit request. */
    public record AllocationCommand(String grantId, String deploymentId, String targetProfileId, Limits caps,
                                    ProviderBinding binding, String rateCardReference, String expectedCredentialSha256) {
        public AllocationCommand {
            grantId = text(grantId, 96); deploymentId = text(deploymentId, 64); targetProfileId = text(targetProfileId, 64);
            if (caps == null || binding == null) throw invalid();
            rateCardReference = text(rateCardReference, 255); expectedCredentialSha256 = sha(expectedCredentialSha256);
        }
    }

    /** Read-only financial history shape; never authority for issuing or mutating an allowance. */
    public record ReviewedR7Context(String reservationId, String generation, String versionId) {
        public ReviewedR7Context {
            reservationId = text(reservationId, 64); generation = text(generation, 64); versionId = text(versionId, 64);
        }
    }

    record CoreReleaseContext(String releaseId, String versionId, String versionConfigHash, String requestFingerprint) {
        CoreReleaseContext {
            releaseId = text(releaseId, 64); versionId = text(versionId, 64);
            versionConfigHash = text(versionConfigHash, 255); requestFingerprint = text(requestFingerprint, 255);
        }
    }

    public record AllocationScope(String approvalId, String customerId, String deploymentId,
                                  String targetProfileId, String environment) {
        public AllocationScope {
            approvalId = text(approvalId, 96); customerId = text(customerId, 64); deploymentId = text(deploymentId, 64);
            targetProfileId = text(targetProfileId, 64); environment = text(environment, 64);
        }
    }

    public enum InitializationState { UNINITIALIZED, INITIALIZED, ACTIVE }

    /** Staff/internal projection: binding contains a managed-secret reference, never expose to customers. */
    public record SafeAllowanceProjection(String approvalId, AllocationScope scope, Limits caps, Instant expiresAt,
                                          String pricingReference, ProviderBinding binding, String credentialSha256,
                                          String approvalFingerprint, String runtimeAllocationFingerprint,
                                          InitializationState state, UUID storeUuid,
                                          String initializationFingerprint, Instant effectiveExpiresAt) {
        public SafeAllowanceProjection(String approvalId, AllocationScope scope, Limits caps, Instant expiresAt,
            String pricingReference, ProviderBinding binding, String credentialSha256, String approvalFingerprint,
            String runtimeAllocationFingerprint, InitializationState state, UUID storeUuid, String initializationFingerprint) {
            this(approvalId, scope, caps, expiresAt, pricingReference, binding, credentialSha256, approvalFingerprint,
                runtimeAllocationFingerprint, state, storeUuid, initializationFingerprint, expiresAt);
        }
    }

    public record GrantQuota(String grantId, String campaignId, String customerId, String targetProfileId,
                             String environment, Limits lifetimeCredits, Limits lifetimeAllocated, Limits remaining,
                             Instant expiresAt, String pricingReference) { }

    // Internal immutable payload, separate from lifecycle metadata and never changed on retirement.
    record AllocationTerms(String approvalId, String grantId, AllocationScope scope, Limits caps, Instant expiresAt,
                           String pricingReference, ProviderBinding binding, String credentialSha256,
                           ReviewedR7Context initialAdmission,
                           @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
                           CoreReleaseContext initialCoreRelease) {
        AllocationTerms(String approvalId, String grantId, AllocationScope scope, Limits caps, Instant expiresAt,
                        String pricingReference, ProviderBinding binding, String credentialSha256, ReviewedR7Context initialAdmission) {
            this(approvalId, grantId, scope, caps, expiresAt, pricingReference, binding, credentialSha256, initialAdmission, null);
        }
    }

    static String text(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw invalid();
        return value;
    }

    static String sha(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw invalid();
        return value;
    }
}
