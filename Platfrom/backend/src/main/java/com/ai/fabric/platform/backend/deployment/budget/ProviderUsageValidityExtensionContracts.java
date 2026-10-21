package com.ai.fabric.platform.backend.deployment.budget;

import java.time.Instant;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceFailure.*;

public final class ProviderUsageValidityExtensionContracts {
    private ProviderUsageValidityExtensionContracts() { }

    public record ReviewRequest(String extensionId, AllocationScope scope, String releaseId,
                                String expectedApprovalFingerprint, UUID storeUuid,
                                Instant expectedEffectiveExpiresAt, Instant expiresAt, String reason) {
        public ReviewRequest {
            extensionId = identifier(extensionId); releaseId = text(releaseId, 64);
            expectedApprovalFingerprint = sha(expectedApprovalFingerprint); reason = text(reason, 512);
            if (scope == null || storeUuid == null || expectedEffectiveExpiresAt == null || expiresAt == null) throw invalid();
        }
    }

    /** This body is sent unchanged to the runtime; it contains no credit or counter fields. */
    public record RuntimeCommand(int schemaVersion, String extensionId, String approvalId, UUID storeUuid,
                                 String runtimeFingerprint, Instant expectedEffectiveExpiresAt, Instant expiresAt, String reason) {
        public RuntimeCommand {
            if (schemaVersion != 1 || storeUuid == null || expectedEffectiveExpiresAt == null || expiresAt == null) throw invalid();
            extensionId = identifier(extensionId); approvalId = text(approvalId, 96);
            runtimeFingerprint = sha(runtimeFingerprint); reason = text(reason, 512);
        }
        @Override public String toString() { return "RuntimeCommand[extensionId=" + extensionId + ", reason=REDACTED]"; }
    }

    public record RuntimeScope(String customerId, String deploymentId, String targetProfileId, String environment,
                               String credentialIdentity, String rateCardReference) { }
    public record RuntimeReceipt(int schemaVersion, String extensionId, String approvalId, RuntimeScope scope,
                                 UUID storeUuid, String runtimeFingerprint, Instant previousEffectiveExpiresAt,
                                 Instant expiresAt, String reasonSha256, Instant authorizedAt) { }
    public record Command(ReviewRequest review, RuntimeCommand runtimeCommand, String runtimeBaseUrl,
                          String authorityFingerprint, String capabilityManifestHash, String reviewFingerprint) {
        public Command {
            if (review == null || runtimeCommand == null || !review.extensionId().equals(runtimeCommand.extensionId())
                || !review.scope().approvalId().equals(runtimeCommand.approvalId()) || !review.storeUuid().equals(runtimeCommand.storeUuid())
                || !review.expectedEffectiveExpiresAt().equals(runtimeCommand.expectedEffectiveExpiresAt())
                || !review.expiresAt().equals(runtimeCommand.expiresAt()) || !review.reason().equals(runtimeCommand.reason())) throw invalid();
            runtimeBaseUrl = text(runtimeBaseUrl, 2048); authorityFingerprint = sha(authorityFingerprint);
            capabilityManifestHash = sha(capabilityManifestHash); reviewFingerprint = sha(reviewFingerprint);
        }
    }
    public enum Status { REVIEWED, UNKNOWN, ACKNOWLEDGED }
    public record Result(String extensionId, Status status, Instant originalExpiresAt, Instant effectiveExpiresAt,
                         RuntimeReceipt receipt) { }
    public record Plan(Command command, Instant originalExpiresAt, boolean financialMutationPerformed) { }

    record Review(Command command, String actorId, Instant reviewedAt) { }
    record Event(String extensionId, String commandFingerprint, String actorId, Instant recordedAt, RuntimeReceipt receipt) { }

    static String identifier(String value) {
        text(value, 128);
        // Receipt IDs are one URL path segment, never caller-supplied runtime paths.
        if (!value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) throw invalid();
        return value;
    }
}
