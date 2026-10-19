package com.ai.fabric.platform.backend.deployment.budget;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageGrantLifecycleContracts.*;
import static org.assertj.core.api.Assertions.*;

class ProviderUsageAllowancePostgresTest extends ProviderUsageGrantVoidUnusedContract {
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    @BeforeAll static void start() { POSTGRES.start(); }
    @AfterAll static void stop() { POSTGRES.stop(); }
    @Override DataSource freshDataSource() throws Exception {
        var source = new PGSimpleDataSource(); source.setURL(POSTGRES.getJdbcUrl());
        source.setUser(POSTGRES.getUsername()); source.setPassword(POSTGRES.getPassword());
        String schema = "allowance_" + UUID.randomUUID().toString().replace("-", "");
        try (var connection = source.getConnection(); var statement = connection.createStatement()) { statement.execute("CREATE SCHEMA " + schema); }
        source.setCurrentSchema(schema); return source;
    }

    @Test void voidReceiptAndAuditAreAppendOnlyEvenThroughDirectDatabaseCommands() {
        service.approveGrant(grant()); var receipt = service.voidUnused("grant", voidCommand(grant()));
        for (String sql : List.of(
            "UPDATE provider_usage_grant_void_receipts SET actor_id='changed' WHERE grant_id='grant'",
            "DELETE FROM provider_usage_grant_void_receipts WHERE grant_id='grant'",
            "TRUNCATE provider_usage_grant_void_receipts",
            "UPDATE provider_usage_allowance_events SET actor_id='changed' WHERE grant_id='grant'",
            "DELETE FROM provider_usage_allowance_events WHERE grant_id='grant'",
            "TRUNCATE provider_usage_allowance_events"))
            assertThatThrownBy(() -> jdbc.execute(sql)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(service.voidUnused("grant", voidCommand(grant()))).isEqualTo(receipt);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_allowance_events", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isEqualTo(1);
    }

    @Test void realFlywayUpgradeAttributesKnownLegacyHistoryWithoutBlockingUnusedGrant() throws Exception {
        var allocation = allocated(); var terms = codec.read(jdbc.queryForObject(
            "SELECT payload_json FROM provider_usage_allocations WHERE approval_id=?", String.class, allocation.approvalId()), AllocationTerms.class);
        var legacySource = freshDataSource(); var legacy = new JdbcTemplate(legacySource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V190__provider_usage_allowance.sql")).execute(legacySource);
        var unused = new ProviderUsageGrant("unused", "unused-campaign", "customer", "target", "staging", grant().credits(),
            grant().expiresAt(), binding, grant().rateCardReference(), "private-ref", "payer");
        insertLegacyGrant(legacy, grant()); insertLegacyGrant(legacy, unused);
        legacy.update("UPDATE provider_usage_grants SET allocated_chat_attempts=?,allocated_total_tokens=?,allocated_cost_nano_usd=? WHERE id='grant'",
            grant().credits().chatAttempts(), grant().credits().totalTokens(), grant().credits().costNanoUsd());
        legacy.update("""
            INSERT INTO provider_usage_allocations
            (approval_id,grant_id,customer_id,deployment_id,target_profile_id,environment_name,approval_fingerprint,runtime_allocation_fingerprint,payload_json,initialization_state,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,'UNINITIALIZED',?)
            """, terms.approvalId(), terms.grantId(), "customer", "deployment", "target", "staging", codec.fingerprint(terms),
            allocation.runtimeAllocationFingerprint(), codec.write(terms), Timestamp.from(NOW));
        for (var grant : List.of(grant(), unused)) insertLegacyEvent(legacy, "GRANT_APPROVED", grant.id(), codec.fingerprint(grant));
        for (String event : List.of("ALLOWANCE_ALLOCATED", "ALLOWANCE_INITIALIZED", "ALLOWANCE_ACTIVE"))
            insertLegacyEvent(legacy, event, allocation.approvalId(), codec.fingerprint(terms));
        var result = Flyway.configure().dataSource(legacySource).locations("classpath:db/migration")
            .baselineOnMigrate(true).baselineVersion("203").target("204").load().migrate();
        assertThat(result.migrationsExecuted).isEqualTo(1);
        assertThat(legacy.queryForList("SELECT DISTINCT grant_id FROM provider_usage_allowance_events WHERE event_type<>'GRANT_APPROVED'", String.class))
            .containsExactly("grant");
        assertThat(legacy.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE grant_id IS NULL", Integer.class)).isZero();
        source = legacySource; jdbc = legacy; service = instance(NOW);
        var before = service.readQuota("grant");
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_NOT_UNUSED");
        assertThat(service.readQuota("grant")).isEqualTo(before);
        assertThat(service.voidUnused(unused.id(), voidCommand(unused)).status()).isEqualTo(GrantStatus.VOID_UNUSED);
    }

    @Test void realFlywayUpgradePreservesUnknownLegacyHistoryAndBlocksVoid() throws Exception {
        var legacySource = freshDataSource(); var legacy = new JdbcTemplate(legacySource);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V190__provider_usage_allowance.sql")).execute(legacySource);
        insertLegacyGrant(legacy, grant()); insertLegacyEvent(legacy, "GRANT_APPROVED", "grant", codec.fingerprint(grant()));
        insertLegacyEvent(legacy, "ALLOWANCE_ALLOCATED", "missing-legacy-allocation", "a".repeat(64));
        Flyway.configure().dataSource(legacySource).locations("classpath:db/migration").baselineOnMigrate(true)
            .baselineVersion("203").target("204").load().migrate();
        assertThat(legacy.queryForObject("SELECT count(*) FROM provider_usage_allowance_events WHERE grant_id IS NULL", Integer.class)).isEqualTo(1);
        source = legacySource; jdbc = legacy; service = instance(NOW);
        assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_HAS_USAGE_HISTORY");
        assertThat(service.readQuota("grant").status()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    @Test void databaseScopeConstraintAllowsOnlyOneActiveReviewedReplacement() {
        service.approveGrant(grant());
        var duplicate = new ProviderUsageGrant("replacement", grant().campaignId(), "customer", "target", "staging", grant().credits(),
            grant().expiresAt(), binding, grant().rateCardReference(), "private-ref", "payer");
        assertThatThrownBy(() -> insertLegacyGrant(jdbc, duplicate)).isInstanceOf(DataIntegrityViolationException.class);
        service.voidUnused("grant", voidCommand(grant()));
        assertThat(service.approveGrant(duplicate).status()).isEqualTo(GrantStatus.ACTIVE);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grants WHERE lifecycle_status='ACTIVE'", Integer.class)).isEqualTo(1);
    }

    @Test void concurrentReadAllocateAndVoidKeepGrantBeforeAllocationLockOrder() throws Exception {
        var allocation = allocated(); UUID store = UUID.randomUUID();
        var results = race(6, index -> () -> {
            return switch (index) {
                case 0 -> service.readAllocation(allocation.scope()).approvalId();
                case 1 -> service.findForDeployment("deployment", "target").orElseThrow().approvalId();
                case 2 -> service.allocate(command(), r7("deployment")).approvalId();
                case 3 -> service.markInitialized(allocation.scope(), store, allocation.runtimeAllocationFingerprint()).approvalId();
                case 4 -> {
                    assertThatThrownBy(() -> service.voidUnused("grant", voidCommand(grant()))).hasMessageContaining("GRANT_NOT_UNUSED");
                    yield "void-rejected";
                }
                default -> service.readQuota("grant").grantId();
            };
        });
        assertThat(results).containsExactlyInAnyOrder(allocation.approvalId(), allocation.approvalId(), allocation.approvalId(),
            allocation.approvalId(), "void-rejected", "grant");
        assertThat(service.readQuota("grant").lifetimeAllocated()).isEqualTo(grant().credits());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM provider_usage_grant_void_receipts", Integer.class)).isZero();
    }

    private void insertLegacyGrant(JdbcTemplate target, ProviderUsageGrant grant) {
        target.update("""
            INSERT INTO provider_usage_grants
            (id,campaign_id,customer_id,target_profile_id,environment_name,fingerprint,payload_json,approved_by,approved_role,approved_at)
            VALUES (?,?,?,?,?,?,?,'staff','PLATFORM_ADMIN',?)
            """, grant.id(), grant.campaignId(), grant.customerId(), grant.targetProfileId(), grant.environment(),
            codec.fingerprint(grant), codec.write(grant), Timestamp.from(NOW));
    }

    private void insertLegacyEvent(JdbcTemplate target, String event, String subject, String fingerprint) {
        target.update("INSERT INTO provider_usage_allowance_events(id,event_type,subject_id,actor_id,actor_role,payload_fingerprint,created_at) VALUES (?,?,?,'staff','PLATFORM_ADMIN',?,?)",
            UUID.randomUUID().toString(), event, subject, fingerprint, Timestamp.from(NOW));
    }
}
