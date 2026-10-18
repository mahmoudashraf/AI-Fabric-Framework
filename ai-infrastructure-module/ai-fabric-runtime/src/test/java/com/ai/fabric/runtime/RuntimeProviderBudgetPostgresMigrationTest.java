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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("19");
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
        flyway.validate();
    }
}
