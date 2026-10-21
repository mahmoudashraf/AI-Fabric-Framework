package com.ai.fabric.runtime;

import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.*;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.ai.fabric.runtime.budget.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;

/** Real migration and durable restart contract; no model transport is constructed. */
@Testcontainers
class RuntimeProviderBudgetPostgresMigrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void actualMigrationCreatesNoApprovalAndPreservesUnknownReservationsAcrossRestart() {
        var source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/runtime-migration").load();
        flyway.migrate();
        var jdbc = new JdbcTemplate(source);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_budget_approval", Long.class)).isZero();
        var clock = Clock.fixed(Instant.parse("2026-10-18T12:00:00Z"), ZoneOffset.UTC);
        var scope = new Scope("customer", "deployment", "target", "staging", "sha256:" + "a".repeat(64),
            ReviewedOpenAiRateCard.REFERENCE);
        var config = new RuntimeBudgetConfiguration(true, scope, "tenant");
        var ledger = new JdbcProviderBudgetLedger(source, clock);
        var service = new RuntimeBudgetBindingService(config, ledger, source, clock);
        assertThat(service.readStore().binding().state()).isEqualTo(RuntimeBudgetBindingService.State.UNINITIALIZED);
        assertThat(service.readiness().ready()).isFalse();
        var approval = new Approval("fixture-approval", scope, new Limits(50, 100_000, 2_000_000_000L),
            clock.instant().plusSeconds(3600));
        var allocation = new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval));
        var initialized = service.initialize(allocation);
        assertThat(service.readiness().ready()).isFalse();
        service.activate(new RuntimeBudgetBindingService.Activation(initialized.approvalId(), initialized.storeUuid(),
            initialized.runtimeFingerprint()));
        var attempt = UUID.randomUUID();
        ledger.reserveAttempt(approval.id(), scope, attempt, ReviewedOpenAiRateCard.embedding(8192));
        ledger.markAmbiguous(approval.id(), scope, attempt, "fixture-unknown-provider-outcome");

        var before = ledger.read(approval.id(), scope);
        var bindingBefore = service.readStore().binding();
        flyway.migrate();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(ledger.read(approval.id(), scope)).isEqualTo(before);
        assertThat(service.readStore().binding()).isEqualTo(bindingBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM provider_budget_validity_extension", Long.class)).isZero();
        var command = new ValidityExtensionCommand(1, "extension", approval.id(), initialized.storeUuid(),
            initialized.runtimeFingerprint(), approval.expiresAt(), approval.expiresAt().plusSeconds(3600), "approved continuation");
        var extended = service.extendValidity(command);
        assertThat(ledger.read(approval.id(), scope)).isEqualTo(before);
        assertThat(service.readStore().binding()).isEqualTo(bindingBefore);

        var restartedLedger = new JdbcProviderBudgetLedger(source, clock);
        var restarted = new RuntimeBudgetBindingService(config, restartedLedger, source, clock);
        assertThat(restarted.readiness().ready()).isTrue();
        assertThat(restarted.readStore().binding().storeUuid()).isEqualTo(initialized.storeUuid());
        assertThat(restarted.readCurrent().accountedUsage()).isEqualTo(ReviewedOpenAiRateCard.embedding(8192));
        assertThat(restartedLedger.reserveAttempt(approval.id(), scope, attempt, ReviewedOpenAiRateCard.embedding(8192)).newlyReserved())
            .isFalse();
        var changed = new Approval(approval.id(), scope, new Limits(50, 200_000, 2_000_000_000L), approval.expiresAt());
        assertThatThrownBy(() -> restarted.initialize(new RuntimeBudgetAllocation(changed, RuntimeBudgetAllocation.fingerprint(changed))))
            .isInstanceOf(RuntimeBudgetBindingException.class);
        assertThat(restarted.readCurrent().approval().limits().totalTokens()).isEqualTo(100_000);
        assertThat(restarted.effectiveExpiresAt()).isEqualTo(command.expiresAt());
        assertThat(restarted.readValidityExtension(command.extensionId())).contains(extended);
        assertThat(restarted.extendValidity(command)).isEqualTo(extended);
        flyway.validate();
    }

    @Test
    void upgradeFromV19PreservesExistingApprovalUsageAndActiveStore() {
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        String url = POSTGRES.getJdbcUrl() + (POSTGRES.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema;
        var source = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        var old = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/runtime-migration").target("19").load();
        old.migrate();
        var jdbc = new JdbcTemplate(source);
        var now = Instant.parse("2026-10-18T12:00:00Z"); var clock = Clock.fixed(now, ZoneOffset.UTC);
        var scope = new Scope("customer", "deployment", "target", "staging", "sha256:" + "a".repeat(64), ReviewedOpenAiRateCard.REFERENCE);
        var approval = new Approval("historical", scope, new Limits(50, 100_000, 2_000_000_000L), now.plusSeconds(3600));
        var service = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, scope, "tenant"),
            new JdbcProviderBudgetLedger(source, clock), source, clock);
        var binding = service.initialize(new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval)));
        // Test-owned V19 history, as written by the prior runtime before V20 existed.
        jdbc.update("UPDATE runtime_provider_budget_binding SET binding_state='ACTIVE' WHERE singleton_id=1");
        jdbc.update("UPDATE provider_budget_approval SET provider_attempts=1,accounted_input_tokens=4,accounted_output_tokens=3,accounted_cost_nano_usd=10 WHERE approval_id='historical'");
        jdbc.update("INSERT INTO provider_budget_attempt (approval_id,attempt_id,bound_input_tokens,bound_output_tokens,bound_cost_nano_usd,attempt_state,ambiguity_reason) VALUES ('historical',?,4,3,10,'AMBIGUOUS','unknown transport')", UUID.randomUUID().toString());
        var tables = java.util.List.of("provider_budget_approval", "provider_budget_chat_ingress", "provider_budget_attempt", "runtime_provider_budget_binding");
        var before = tables.stream().map(table -> jdbc.queryForList("SELECT * FROM " + table)).toList();
        var upgraded = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/runtime-migration").load();
        upgraded.migrate(); upgraded.validate();
        assertThat(upgraded.info().current().getVersion().getVersion()).isEqualTo("20");
        assertThat(tables.stream().map(table -> jdbc.queryForList("SELECT * FROM " + table)).toList()).isEqualTo(before);
        var command = new ValidityExtensionCommand(1, "historical-extension", approval.id(), binding.storeUuid(), binding.runtimeFingerprint(), approval.expiresAt(), approval.expiresAt().plusSeconds(60), "reviewed extension");
        service.extendValidity(command);
        assertThat(service.requireActive().approval()).isEqualTo(approval);
        assertThat(service.requireActive().accountedUsage()).isEqualTo(new Usage(4, 3, 10));
        assertThat(tables.stream().map(table -> jdbc.queryForList("SELECT * FROM " + table)).toList()).isEqualTo(before);
    }
}
