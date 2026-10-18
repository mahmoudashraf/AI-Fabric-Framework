package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.junit.jupiter.api.AfterEach;

final class RuntimeBudgetFixture {
    static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    static final String CREDENTIAL = "sha256:" + "a".repeat(64);
    static final Scope SCOPE = new Scope("customer", "deployment", "target", "staging", CREDENTIAL,
        ReviewedOpenAiRateCard.REFERENCE);
    static final Approval APPROVAL = new Approval("approved-budget", SCOPE,
        new Limits(50, 100_000, 2_000_000_000L), NOW.plusSeconds(3600));
    static final RuntimeBudgetAllocation ALLOCATION = allocation(APPROVAL);

    final DriverManagerDataSource dataSource;
    final JdbcProviderBudgetLedger ledger;

    RuntimeBudgetFixture() throws Exception {
        dataSource = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        // Test-owned schema only. Production must use the parent's reviewed Flyway migration.
        // H2 2.4's IN check retains its schema-creation session; keep it alive until this test ends.
        var connection = dataSource.getConnection();
        TestScope.sessions.get().add(connection);
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("com/ai/fabric/budget/provider-budget-schema.sql"));
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("com/ai/fabric/runtime/budget/runtime-provider-budget-binding.sql"));
        ledger = new JdbcProviderBudgetLedger(dataSource, CLOCK);
    }

    RuntimeBudgetBindingService service() { return service(SCOPE); }

    RuntimeBudgetBindingService service(Scope scope) {
        return new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, scope, "tenant"), ledger, dataSource, CLOCK);
    }

    RuntimeBudgetBindingService register() {
        var service = service();
        activate(service, service.initialize(ALLOCATION));
        return service;
    }

    static RuntimeBudgetAllocation allocation(Approval approval) {
        return new RuntimeBudgetAllocation(approval, RuntimeBudgetAllocation.fingerprint(approval));
    }

    static RuntimeBudgetBindingService.StoreReceipt activate(RuntimeBudgetBindingService service, RuntimeBudgetBindingService.StoreReceipt receipt) {
        return service.activate(new RuntimeBudgetBindingService.Activation(receipt.approvalId(), receipt.storeUuid(), receipt.runtimeFingerprint()));
    }

    static RuntimeResolvedIdentity identity(String customer, String deployment, List<String> scopes) {
        return RuntimeResolvedIdentity.builder().authContext(RuntimeAuthContext.builder()
            .subjectId("owner").authMode(RuntimeAuthMode.PRIVATE_RUNTIME_BACKEND_MEDIATED)
            .tenantId("tenant")
            .customerId(customer).deploymentId(deployment).expiresAt(NOW.plusSeconds(600))
            .grantedScopes(scopes).build()).build();
    }

    static RuntimeResolvedIdentity chatIdentity() {
        return identity("customer", "deployment", List.of("chat:query"));
    }

    abstract static class TestScope {
        private static final ThreadLocal<List<Connection>> sessions = ThreadLocal.withInitial(ArrayList::new);

        @AfterEach
        void closeFixtureSchemaSessions() throws Exception {
            try {
                for (var session : sessions.get()) session.close();
            } finally {
                sessions.remove();
            }
        }
    }
}
