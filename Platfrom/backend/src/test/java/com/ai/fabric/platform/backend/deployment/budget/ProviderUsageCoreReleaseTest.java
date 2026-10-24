package com.ai.fabric.platform.backend.deployment.budget;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;

/** Direct financial authorization must work with the optional admission schema entirely absent. */
class ProviderUsageCoreReleaseTest {
    Fixture f;

    @BeforeEach void setup() throws Exception { f = fixture(); f.initialize(); }
    @AfterEach void cleanup() { f.clearIdentity(); }
    Fixture fixture() { return new Fixture(); }

    @Test void issuesReadsAndActivatesFromCoreWithoutAnyAdmissionTablesOrAmbientPrincipal() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED");
        dropAdmissionTables(); SecurityContextHolder.clearContext();
        assertThat(f.service.findForAuthorizedRelease("release-deployment")).isEmpty();
        var allocation = f.service.prepareForAuthorizedRelease("release-deployment");
        assertThat(f.instance(Fixture.NOW).prepareForAuthorizedRelease("release-deployment")).isEqualTo(allocation);
        assertThat(f.service.findForAuthorizedRelease("release-deployment")).contains(allocation);
        f.jdbc.update("UPDATE platform_deployment_releases SET status='VERIFYING',provisioning_status='ACTIVE'");
        var store = UUID.randomUUID();
        f.service.markInitializedForAuthorizedRelease("release-deployment", store, allocation.runtimeAllocationFingerprint());
        var active = f.service.markActiveForAuthorizedRelease("release-deployment", store, allocation.runtimeAllocationFingerprint());
        assertThat(active.state()).isEqualTo(InitializationState.ACTIVE);
        assertThat(active.storeUuid()).isEqualTo(store);
        assertThat(active.caps()).isEqualTo(f.runtimeGrant().credits());
        assertThat(f.service.markActiveForAuthorizedRelease("release-deployment", store, allocation.runtimeAllocationFingerprint())).isEqualTo(active);
        var terms = terms(allocation);
        assertThat(terms.initialAdmission()).isNull();
        assertThat(terms.initialCoreRelease()).isEqualTo(new CoreReleaseContext("release-deployment", "version-deployment", "config-hash", "a".repeat(64)));
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForList("SELECT actor_id FROM provider_usage_allowance_events WHERE event_type<>'GRANT_APPROVED'", String.class))
            .containsOnly("system:provider-usage-release");
        verifyNoInteractions(f.deploymentAccess);
    }

    @Test void missingFundingNeverBecomesANonfundedContract() {
        authorize("deployment", "FUNDED"); dropAdmissionTables(); SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("REVIEWED_GRANT_REQUIRED");
        assertNoDebit();
    }

    @Test void expiryDoesNotReissueTheGrantOrDisableItsContract() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED"); dropAdmissionTables();
        var allocation = f.service.prepareForAuthorizedRelease("release-deployment");
        String before = f.jdbc.queryForObject("SELECT payload_json FROM provider_usage_allocations", String.class);
        assertThatThrownBy(() -> f.instance(allocation.expiresAt()).prepareForAuthorizedRelease("release-deployment"))
            .hasMessageContaining("ALLOWANCE_EXPIRED");
        assertThat(f.jdbc.queryForObject("SELECT payload_json FROM provider_usage_allocations", String.class)).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT allocated_total_tokens FROM provider_usage_grants", Long.class)).isEqualTo(allocation.caps().totalTokens());
    }

    @Test void nonfundedCoreAuthorizationCannotMintAnAllowance() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "TEAM_AUTHORIZED");
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("FUNDED_MODE_REQUIRED");
        assertNoDebit();
    }

    @Test void exactCoreOwnershipVersionArtifactAndHashRemainRequired() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED");
        var changes = Map.of("customer_id", "other", "tenant_id", "other", "deployment_id", "other", "target_profile_id", "other",
            "version_id", "other", "version_config_hash", "other", "source_artifact_id", "other");
        for (var change : changes.entrySet()) {
            String previous = f.jdbc.queryForObject("SELECT " + change.getKey() + " FROM platform_release_authorizations", String.class);
            f.jdbc.update("UPDATE platform_release_authorizations SET " + change.getKey() + "=?", change.getValue());
            assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment"))
                .as(change.getKey()).isInstanceOf(ProviderUsageAllowanceFailure.class);
            assertNoDebit();
            f.jdbc.update("UPDATE platform_release_authorizations SET " + change.getKey() + "=?", previous);
        }
    }

    @Test void deniedOrIncompleteCoreRecordNeverFallsBackToTheValidLegacyAdmissionReceipt() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED");
        f.jdbc.update("UPDATE platform_release_authorizations SET decision='DENY'");
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CORE_AUTHORIZATION_DENIED");
        f.jdbc.update("UPDATE platform_release_authorizations SET decision='BYPASSED',request_fingerprint=NULL");
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CORE_AUTHORIZATION_CHANGED");
        assertNoDebit();
    }

    @Test void coreAuthorizationDoesNotSkipCredentialModelPhaseOrCustomerChecks() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED");
        f.secretValue.set(null);
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CREDENTIAL_UNRESOLVED");
        f.secretValue.set("fictional-provider-credential");
        f.versionRows.get("version-deployment").setProviderConfigJson(Fixture.DECLARED_PROVIDER_CONFIG.replace("gpt-4o-mini", "unreviewed"));
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("MODEL_NOT_REVIEWED");
        f.versionRows.get("version-deployment").setProviderConfigJson(Fixture.DECLARED_PROVIDER_CONFIG);
        f.jdbc.update("UPDATE platform_deployment_releases SET status='FAILED'");
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("RELEASE_PHASE_DENIED");
        f.jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING'"); f.customer.setStatus("DISABLED");
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment")).hasMessageContaining("CUSTOMER_INACTIVE");
        assertNoDebit();
    }

    @Test void oldFundedAllocationHistoryAndStoreSurviveNewCoreReleaseWithoutAdmissionReadback() {
        var grant = f.runtimeGrant(); f.service.approveGrant(grant);
        var old = f.service.allocate(new AllocationCommand(grant.id(), "deployment", "target", grant.credits(), grant.binding(),
            grant.rateCardReference(), ProviderUsageAllowanceCodec.sha256(f.secretValue.get())), f.r7("deployment"));
        var store = UUID.randomUUID();
        f.service.markInitialized(old.scope(), store, old.runtimeAllocationFingerprint());
        var active = f.service.markActive(old.scope(), store, old.runtimeAllocationFingerprint());
        String originalTerms = f.jdbc.queryForObject("SELECT payload_json FROM provider_usage_allocations", String.class);
        authorize("deployment", "FUNDED"); dropAdmissionTables(); SecurityContextHolder.clearContext();
        assertThat(f.service.prepareForAuthorizedRelease("release-deployment")).isEqualTo(active);
        f.jdbc.update("UPDATE platform_deployment_releases SET status='VERIFYING',provisioning_status='ACTIVE'");
        assertThat(f.service.markActiveForAuthorizedRelease("release-deployment", store, active.runtimeAllocationFingerprint())).isEqualTo(active);
        assertThatThrownBy(() -> f.service.markInitializedForAuthorizedRelease("release-deployment", UUID.randomUUID(), active.runtimeAllocationFingerprint()))
            .hasMessageContaining("STORE_REPLACEMENT_FORBIDDEN");
        assertThat(f.jdbc.queryForObject("SELECT payload_json FROM provider_usage_allocations", String.class)).isEqualTo(originalTerms);
        assertThat(f.jdbc.queryForObject("SELECT allocated_total_tokens FROM provider_usage_grants", Long.class)).isEqualTo(grant.credits().totalTokens());
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentCoreReplaysDebitExactlyOnceWithoutAnAdmissionMutex() throws Exception {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED"); dropAdmissionTables();
        var results = f.race(4, index -> () -> {
            SecurityContextHolder.clearContext();
            return f.instance(Fixture.NOW).prepareForAuthorizedRelease("release-deployment");
        });
        assertThat(results).allMatch(results.getFirst()::equals);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isEqualTo(1);
    }

    @Test void twoCoreAuthorizedDeploymentsCannotSpendOneGrantTwice() throws Exception {
        f.service.approveGrant(f.runtimeGrant()); f.addDeployment("second");
        authorize("deployment", "FUNDED"); authorize("second", "FUNDED"); dropAdmissionTables();
        var releases = List.of("release-deployment", "release-second");
        var results = f.race(2, index -> () -> {
            SecurityContextHolder.clearContext();
            try { return f.instance(Fixture.NOW).prepareForAuthorizedRelease(releases.get(index)); }
            catch (ProviderUsageAllowanceFailure failure) {
                assertThat(failure.getReason()).isEqualTo("PROVIDER_USAGE_LIFETIME_CREDITS_EXCEEDED");
                return null;
            }
        });
        assertThat(results.stream().filter(java.util.Objects::nonNull)).hasSize(1);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isEqualTo(1);
    }

    @Test void financialAuditFailureStillRollsBackDebitAndAllocation() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED"); dropAdmissionTables();
        f.jdbc.execute("ALTER TABLE provider_usage_allowance_events ADD CONSTRAINT reject_allocation CHECK (event_type<>'ALLOWANCE_ALLOCATED')");
        assertThatThrownBy(() -> f.service.prepareForAuthorizedRelease("release-deployment"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertNoDebit();
    }

    @Test void coreAllocationCannotBeActivatedThroughTheLegacyStaffMutationWithoutReleaseAuthority() {
        f.service.approveGrant(f.runtimeGrant()); authorize("deployment", "FUNDED"); dropAdmissionTables();
        var allocation = f.service.prepareForAuthorizedRelease("release-deployment");
        assertThatThrownBy(() -> f.service.markInitialized(allocation.scope(), UUID.randomUUID(), allocation.runtimeAllocationFingerprint()))
            .hasMessageContaining("AUTHORIZED_RELEASE_REQUIRED");
    }

    @Test void optionalCoreAuditFieldPreservesLegacyJsonAndFingerprint() {
        var terms = new AllocationTerms("approval", "grant", new AllocationScope("approval", "customer", "deployment", "target", "staging"),
            f.grant().credits(), f.grant().expiresAt(), f.grant().rateCardReference(), f.binding,
            "c".repeat(64), f.r7("deployment"));
        var historicalJson = f.mapper.valueToTree(terms);
        assertThat(historicalJson.has("initialCoreRelease")).isFalse();
        var roundTrip = f.codec.read(f.codec.write(historicalJson), AllocationTerms.class);
        assertThat(roundTrip).isEqualTo(terms);
        assertThat(f.codec.fingerprint(roundTrip)).isEqualTo(f.codec.fingerprint(historicalJson));
        assertThat(ProviderUsageRuntimeAllocationIdentity.fingerprint(roundTrip)).isEqualTo(ProviderUsageRuntimeAllocationIdentity.fingerprint(terms));
    }

    private void authorize(String deployment, String mode) {
        f.authorizeRelease(deployment);
        f.jdbc.update("UPDATE platform_deployment_releases SET source_artifact_id='artifact' WHERE id=?", "release-" + deployment);
        ProviderUsageCoreReleaseTestSchema.authorize(f.jdbc, deployment, mode);
    }

    private AllocationTerms terms(SafeAllowanceProjection allocation) {
        return f.codec.read(f.jdbc.queryForObject("SELECT payload_json FROM provider_usage_allocations WHERE approval_id=?",
            String.class, allocation.approvalId()), AllocationTerms.class);
    }

    private void assertNoDebit() {
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allocations", Integer.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE event_type='ALLOWANCE_ALLOCATED'", Integer.class)).isZero();
        assertThat(f.jdbc.queryForList("SELECT allocated_total_tokens FROM provider_usage_grants", Long.class)).allMatch(tokens -> tokens == 0);
    }

    private void dropAdmissionTables() {
        for (String table : List.of("deployment_admission_release_bindings", "deployment_admission_authorizations",
            "deployment_admission_activity_watermarks", "deployment_admission_extension_commands", "deployment_capacity_reservations",
            "deployment_admission_reviews", "deployment_admission_mutex")) f.jdbc.execute("DROP TABLE " + table);
    }

    static class Fixture extends ProviderUsageAllowanceContract {
        @Override DataSource freshDataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:core_funding_" + UUID.randomUUID()
                + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=15000", "sa", "");
        }
    }
}
