package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.security.PlatformRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

abstract class ProviderUsageGrantVoidUnusedContract extends ProviderUsageAllowanceContract {
    VoidUnusedCommand voidCommand(ProviderUsageGrant grant) {
        return new VoidUnusedCommand("reviewed-scope-correction", codec.fingerprint(grant), 0L,
            new ZeroAllocatedCredits(0L, 0L, 0L), "Correct unused approval scope", "private-owner-clarification-ref");
    }

    @Test void voidUnusedRequiresAdministratorEvenWithoutSecurityProxy() {
        service.approveGrant(grant());
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("AUTHENTICATION_REQUIRED");
        for (var role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT)) {
            authenticate(role);
            assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT lifecycle_status FROM provider_usage_grants", String.class)).isEqualTo("ACTIVE");
    }

    @Test void unusedVoidPreservesImmutableApprovalAndCountersAndCreatesNoReplacementFunding() {
        var before = service.approveGrant(grant());
        var terms = jdbc.queryForMap("SELECT payload_json,fingerprint,approved_by,approved_role,approved_at FROM provider_usage_grants WHERE id='grant'");
        var receipt = service.voidUnused("grant", voidCommand(grant()));
        assertThat(receipt.status()).isEqualTo(GrantStatus.VOID_UNUSED);
        assertThat(receipt.grantFingerprint()).isEqualTo(before.grantFingerprint());
        assertThat(receipt.command()).isEqualTo(voidCommand(grant()));
        assertThat(jdbc.queryForMap("SELECT payload_json,fingerprint,approved_by,approved_role,approved_at FROM provider_usage_grants WHERE id='grant'"))
            .isEqualTo(terms);
        var after = service.readQuota("grant");
        assertThat(after.status()).isEqualTo(GrantStatus.VOID_UNUSED);
        assertThat(after.lifetimeCredits()).isEqualTo(before.lifetimeCredits());
        assertThat(after.lifetimeAllocated()).isEqualTo(before.lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
        assertThat(after.remaining()).isEqualTo(before.remaining());
        assertThat(after.availableForAllocation()).isEqualTo(new Limits(0, 0, 0));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grants", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='GRANT_VOID_UNUSED' AND grant_id='grant' AND subject_id='grant' AND actor_role='PLATFORM_ADMIN' AND payload_fingerprint=?",
            Integer.class, codec.fingerprint(receipt))).isEqualTo(1);
        verifyNoInteractions(secrets);
    }

    @Test void exactVoidReplayAfterRestartAndExpiryNeverReactivatesOrDuplicatesAudit() {
        service.approveGrant(grant());
        var first = service.voidUnused("grant", voidCommand(grant()));
        var restarted = instance(NOW.plusSeconds(7200));
        assertThat(restarted.voidUnused("grant", voidCommand(grant()))).isEqualTo(first);
        assertThat(restarted.approveGrant(grant()).status()).isEqualTo(GrantStatus.VOID_UNUSED);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events", Integer.class)).isEqualTo(2);
    }

    @Test void changedVoidCommandAndOperationKeyReuseOnDifferentSourceAreRejected() {
        var grant = grant(); service.approveGrant(grant); var original = voidCommand(grant);
        var first = service.voidUnused(grant.id(), original);
        for (var changed : List.of(
            new VoidUnusedCommand("different-operation", original.expectedGrantFingerprint(), 0L, original.expectedAllocated(), original.reason(), original.ownerApprovalEvidenceRef()),
            new VoidUnusedCommand(original.operationKey(), "0".repeat(64), 0L, original.expectedAllocated(), original.reason(), original.ownerApprovalEvidenceRef()),
            new VoidUnusedCommand(original.operationKey(), original.expectedGrantFingerprint(), 0L, original.expectedAllocated(), "Different reason", original.ownerApprovalEvidenceRef()),
            new VoidUnusedCommand(original.operationKey(), original.expectedGrantFingerprint(), 0L, original.expectedAllocated(), original.reason(), "different-owner-evidence")))
            assertThatThrownBy(() -> service.voidUnused(grant.id(), changed)).hasMessageContaining("VOID_COMMAND_CHANGED");
        var replacement = copyGrant("replacement", grant.credits()); service.approveGrant(replacement);
        assertThatThrownBy(() -> service.voidUnused(replacement.id(), voidCommand(replacement))).hasMessageContaining("VOID_COMMAND_CHANGED");
        assertThat(service.voidUnused(grant.id(), original)).isEqualTo(first);
        assertThat(service.readQuota(replacement.id()).status()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isEqualTo(1);
    }

    @Test void changedExpectedGrantFingerprintHasNoEffects() {
        var before = service.approveGrant(grant()); var command = voidCommand(grant());
        var changed = new VoidUnusedCommand(command.operationKey(), "0".repeat(64), 0L, command.expectedAllocated(), command.reason(), command.ownerApprovalEvidenceRef());
        assertThatThrownBy(() -> service.voidUnused("grant", changed)).hasMessageContaining("GRANT_FINGERPRINT_CHANGED");
        assertThat(service.readQuota("grant")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    @Test void evenZeroCreditAllocationRowPreventsUnusedVoid() {
        service.approveGrant(grant());
        service.allocate(command("deployment", new Limits(0, 0, 0)), r7("deployment"));
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_NOT_UNUSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    @Test void usedAndRetiredGrantCannotBeVoidedOrRefunded() {
        var allocation = allocated(); UUID store = UUID.randomUUID();
        service.markInitialized(allocation.scope(), store, allocation.runtimeAllocationFingerprint());
        service.markActive(allocation.scope(), store, allocation.runtimeAllocationFingerprint());
        changeR7("deployment", "state", "RELEASED"); deploymentRows.remove("deployment");
        var before = service.readQuota("grant");
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_NOT_UNUSED");
        assertThat(service.readQuota("grant")).isEqualTo(before);
        assertThat(before.lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events", Integer.class)).isEqualTo(4);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    @Test void everyNonzeroLifetimeCounterPreventsVoidEvenWithoutAllocationRows() {
        service.approveGrant(grant());
        for (String column : List.of("allocated_chat_attempts", "allocated_total_tokens", "allocated_cost_nano_usd")) {
            jdbc.update("UPDATE provider_usage_grants SET " + column + "=1 WHERE id='grant'");
            assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_NOT_UNUSED");
            assertThat(jdbc.queryForObject("SELECT " + column + " FROM provider_usage_grants WHERE id='grant'", Long.class)).isEqualTo(1);
            jdbc.update("UPDATE provider_usage_grants SET " + column + "=0 WHERE id='grant'");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    @Test void allocationHistoryPreventsVoidEvenWhenAllCurrentCountersAndRowsAreZero() {
        service.approveGrant(grant()); insertUsageHistory("grant");
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_HAS_USAGE_HISTORY");
        assertThat(service.readQuota("grant").status()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    @Test void unattributedLegacyUsageHistoryFailsClosedButAttributedOtherGrantHistoryDoesNot() {
        var other = new ProviderUsageGrant("other", "other-campaign", "customer", "target", "staging", grant().credits(),
            grant().expiresAt(), binding, grant().rateCardReference(), "private-ref", "payer");
        service.approveGrant(other); insertUsageHistory(other.id()); service.approveGrant(grant());
        service.voidUnused("grant", voidCommand(grant()));
        var replacement = copyGrant("replacement", grant().credits()); service.approveGrant(replacement);
        insertUsageHistory(null);
        assertThatThrownBy(() -> service.voidUnused(replacement.id(), new VoidUnusedCommand("other-correction", codec.fingerprint(replacement), 0L,
            new ZeroAllocatedCredits(0L, 0L, 0L), "Correct unused approval scope", "private-ref")))
            .hasMessageContaining("GRANT_HAS_USAGE_HISTORY");
    }

    @Test void auditFailureRollsBackStatusAndReceiptAndAllowsExactRetry() {
        var before = service.approveGrant(grant());
        jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD CONSTRAINT fixture_void_audit_failure CHECK (event_type<>'GRANT_VOID_UNUSED')");
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.readQuota("grant")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
        jdbc.execute("ALTER TABLE provider_usage_allowance_events DROP CONSTRAINT fixture_void_audit_failure");
        assertThat(service.voidUnused("grant", voidCommand(grant())).status()).isEqualTo(GrantStatus.VOID_UNUSED);
    }

    @Test void receiptFailureLeavesApprovalAndAuditUntouched() {
        var before = service.approveGrant(grant());
        jdbc.execute("ALTER TABLE provider_usage_grant_void_receipts ADD CONSTRAINT fixture_void_receipt_failure CHECK (grant_id<>'grant')");
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(service.readQuota("grant")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    @Test void reviewedReplacementSubgrantsUseNormalApprovalAndNeverDuplicateWholeTaskCredits() {
        var original = copyGrant("grant", new Limits(50, 200_000, 12_000_000_000L)); service.approveGrant(original);
        var staging = copyGrant("staging-portion", new Limits(10, 50_000, 3_000_000_000L));
        assertThatThrownBy(() -> service.approveGrant(staging)).hasMessageContaining("CAMPAIGN_ALREADY_REVIEWED");
        service.voidUnused(original.id(), voidCommand(original));
        authenticate(PlatformRole.PLATFORM_OPERATOR);
        assertThatThrownBy(() -> service.approveGrant(staging)).hasMessageContaining("ADMIN_REVIEW_REQUIRED");
        authenticate(PlatformRole.PLATFORM_ADMIN); var stageQuota = service.approveGrant(staging);
        var productionTarget = new DeploymentTargetProfileEntity(); productionTarget.setId("production-target");
        productionTarget.setEnvironmentName("production"); productionTarget.setActive(true);
        when(targets.findById(productionTarget.getId())).thenReturn(Optional.of(productionTarget));
        var production = new ProviderUsageGrant("production-portion", original.campaignId(), original.customerId(), productionTarget.getId(),
            "production", new Limits(40, 150_000, 9_000_000_000L), original.expiresAt(), binding, original.rateCardReference(), "private-plan-production-ref", original.payerRef());
        var productionQuota = service.approveGrant(production);
        assertThat(stageQuota.availableForAllocation().chatAttempts() + productionQuota.availableForAllocation().chatAttempts()).isEqualTo(50);
        assertThat(stageQuota.availableForAllocation().totalTokens() + productionQuota.availableForAllocation().totalTokens()).isEqualTo(200_000);
        assertThat(stageQuota.availableForAllocation().costNanoUsd() + productionQuota.availableForAllocation().costNanoUsd()).isEqualTo(12_000_000_000L);
        assertThat(service.approveGrant(original).availableForAllocation()).isEqualTo(new Limits(0, 0, 0));
        assertThatThrownBy(() -> service.approveGrant(copyGrant("duplicate-stage", staging.credits()))).hasMessageContaining("CAMPAIGN_ALREADY_REVIEWED");
        var allocation = service.allocate(new AllocationCommand(staging.id(), "deployment", "target", staging.credits(), binding,
            staging.rateCardReference(), command().expectedCredentialSha256()), r7("deployment"));
        assertThat(allocation.caps()).isEqualTo(staging.credits());
        assertThat(service.readQuota(original.id()).lifetimeAllocated()).isEqualTo(new Limits(0, 0, 0));
    }

    @Test void allocationAndVoidRaceHasExactlyOneWinner() throws Exception {
        service.approveGrant(grant());
        var results = race(2, index -> () -> {
            try {
                if (index == 0) { instance(NOW).allocate(command(), r7("deployment")); return "allocation"; }
                instance(NOW).voidUnused("grant", voidCommand(grant())); return "void";
            } catch (ProviderUsageAllowanceFailure failure) {
                assertThat(failure.getReason()).isIn("PROVIDER_USAGE_GRANT_VOID_UNUSED", "PROVIDER_USAGE_GRANT_NOT_UNUSED");
                return "rejected";
            }
        });
        assertThat(results.stream().filter("rejected"::equals).count()).isEqualTo(1);
        int allocations = jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class);
        int receipts = jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class);
        assertThat(allocations + receipts).isEqualTo(1);
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(allocations == 1 ? grant().credits() : new Limits(0, 0, 0));
        assertThat(service.readQuota("grant").status()).isEqualTo(receipts == 1 ? GrantStatus.VOID_UNUSED : GrantStatus.ACTIVE);
    }

    @Test void concurrentIdenticalVoidCommandsReturnOneDurableReceipt() throws Exception {
        service.approveGrant(grant());
        var results = race(2, index -> () -> instance(NOW).voidUnused("grant", voidCommand(grant())));
        assertThat(results.getFirst()).isEqualTo(results.getLast());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='GRANT_VOID_UNUSED'", Integer.class)).isEqualTo(1);
    }

    @Test void everyAuthorizedWorkerPathRejectsVoidedSourceWithoutProviderOrRuntimeEffects() {
        var grant = runtimeGrant(); service.approveGrant(grant); service.voidUnused(grant.id(), voidCommand(grant));
        authorizeRelease("deployment"); SecurityContextHolder.clearContext(); UUID store = UUID.randomUUID();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        assertThatThrownBy(() -> service.findForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        jdbc.update("UPDATE platform_deployment_releases SET status='VERIFYING',provisioning_status='ACTIVE' WHERE id='release-deployment'");
        assertThatThrownBy(() -> service.markInitializedForAuthorizedRelease("release-deployment", store, "a".repeat(64))).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        assertThatThrownBy(() -> service.markActiveForAuthorizedRelease("release-deployment", store, "a".repeat(64))).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
        verifyNoInteractions(secrets);
    }

    @Test void workerSelectionIgnoresVoidedGrantAndUsesOnlyNormallyReviewedReplacement() {
        var original = runtimeGrant(); service.approveGrant(original); service.voidUnused(original.id(), voidCommand(original));
        var replacement = new ProviderUsageGrant("replacement", original.campaignId(), original.customerId(), original.targetProfileId(), original.environment(),
            new Limits(5, 10_000, 100_000_000), original.expiresAt(), original.binding(), original.rateCardReference(), "private-plan-ref", original.payerRef());
        service.approveGrant(replacement); authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        var allocation = service.prepareForAuthorizedRelease("release-deployment");
        assertThat(allocation.caps()).isEqualTo(replacement.credits());
        authenticate(PlatformRole.PLATFORM_ADMIN);
        assertThat(service.readQuota(original.id()).availableForAllocation()).isEqualTo(new Limits(0, 0, 0));
        assertThat(service.readQuota(replacement.id()).lifetimeAllocated()).isEqualTo(replacement.credits());
    }

    @Test void corruptedVoidedSourceCannotIssueOrReplaySpendCapableProjections() {
        var allocation = allocated();
        jdbc.update("UPDATE provider_usage_grants SET lifecycle_status='VOID_UNUSED' WHERE id='grant'");
        UUID store = UUID.randomUUID();
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThatThrownBy(() -> service.markInitialized(allocation.scope(), store, allocation.runtimeAllocationFingerprint())).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThatThrownBy(() -> service.markActive(allocation.scope(), store, allocation.runtimeAllocationFingerprint())).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThatThrownBy(() -> service.readAllocation(allocation.scope())).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThatThrownBy(() -> service.findForDeployment("deployment", "target")).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThat(jdbc.queryForObject("SELECT allocated_cost_nano_usd FROM provider_usage_grants WHERE id='grant'", Long.class)).isEqualTo(grant().credits().costNanoUsd());
    }

    @Test void durableVoidReceiptStillBlocksAllocationIfStatusWasIncorrectlyReactivated() {
        var original = runtimeGrant(); service.approveGrant(original); service.voidUnused(original.id(), voidCommand(original));
        jdbc.update("UPDATE provider_usage_grants SET lifecycle_status='ACTIVE' WHERE id='grant'");
        assertThatThrownBy(() -> service.allocate(command(), r7("deployment"))).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThatThrownBy(() -> service.readQuota("grant")).hasMessageContaining("VOID_RECORD_CHANGED");
        authorizeRelease("deployment"); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("GRANT_VOID_UNUSED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
    }

    private ProviderUsageGrant copyGrant(String id, Limits credits) {
        var original = grant();
        return new ProviderUsageGrant(id, original.campaignId(), original.customerId(), original.targetProfileId(), original.environment(), credits,
            original.expiresAt(), original.binding(), original.rateCardReference(), "private-reviewed-portion-ref", original.payerRef());
    }

    private void insertUsageHistory(String grantId) {
        jdbc.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,grant_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES (?,'ALLOWANCE_ALLOCATED',?,?,'staff','PLATFORM_ADMIN',?,?)",
            UUID.randomUUID().toString(), "historical-removed-allocation-" + UUID.randomUUID(), grantId, "a".repeat(64), Timestamp.from(NOW));
    }
}
