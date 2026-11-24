package com.ai.fabric.runtime.budget;

import ai.fabric.reranking.cohere.CohereRerankingProvider;
import ai.fabric.spi.RerankingProvider;
import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.assertj.core.api.Assertions.*;

/** Opt-in actual provider test; one persistent private ledger across models/runs, never a fresh test allowance. */
@EnabledIfSystemProperty(named = "loomai.cohere.live", matches = "true")
class CohereRerankingLiveTest {
    @Test
    void verifiesBothReviewedModelsWithNativeUsageAndOneAggregateOwnerCap() throws Exception {
        Path path = Path.of(System.getProperty("loomai.cohere.live.config")).toAbsolutePath();
        assertThat(Files.getPosixFilePermissions(path)).doesNotContain(PosixFilePermission.GROUP_READ,
            PosixFilePermission.OTHERS_READ, PosixFilePermission.GROUP_WRITE, PosixFilePermission.OTHERS_WRITE);
        JsonNode config;
        try { config = new ObjectMapper().readTree(Files.readString(path)); }
        catch (Exception invalid) { throw new IllegalStateException("Private Cohere qualification configuration could not be read"); }
        assertThat(config.path("paidTestsEnabled").asBoolean()).isTrue();
        assertThat(config.path("externalRecipientApproved").asBoolean()).isTrue();
        String key = config.path("apiKey").asText();
        assertThat(key.isBlank()).isFalse();
        long cap = config.path("qualificationSpendingLimitUsd").decimalValue().movePointRight(9).longValueExact();
        assertThat(cap).isPositive().isLessThanOrEqualTo(10_000_000_000L);
        var source = new DriverManagerDataSource("jdbc:h2:file:" + path.resolveSibling("cohere-qualification-budget")
            + ";MODE=PostgreSQL;DB_CLOSE_ON_EXIT=FALSE", "sa", "");
        try (var connection = source.getConnection()) {
            boolean schemaPresent;
            try (var tables = connection.getMetaData().getTables(null, null, "PROVIDER_BUDGET_APPROVAL", null)) {
                schemaPresent = tables.next();
            }
            if (!schemaPresent) ScriptUtils.executeSqlScript(connection,
                new ClassPathResource("com/ai/fabric/budget/provider-budget-schema.sql"));
            var ledger = new JdbcProviderBudgetLedger(source, Clock.systemUTC());
            var scope = new ProviderBudgetLedger.Scope("loomai-internal-qualification", "retrieval-01073",
                "cohere-native-qualification", "qualification", ReviewedCohereRerankingRateCard.credentialIdentity(key),
                ReviewedCohereRerankingRateCard.REFERENCE);
            var approval = new ProviderBudgetLedger.Approval(config.path("qualificationApprovalId").asText(), scope,
                new ProviderBudgetLedger.Limits(0, 500_000_000L, cap), Instant.parse(config.path("qualificationExpiresAt").asText()));
            ledger.approve(approval);
            var candidates = List.of(new RerankingProvider.Candidate("infrastructure-policy",
                    "A customer workspace can have two provisioned environments. Stopped infrastructure still counts until removed. Drafts and resource-free Engage apps do not count."),
                new RerankingProvider.Candidate("account-help", "A customer can request an email sign-in link to their verified account."),
                new RerankingProvider.Candidate("branding-help", "The Engage appearance editor can change the company name and logo."));
            for (String model : List.of(ReviewedCohereRerankingRateCard.FAST, ReviewedCohereRerankingRateCard.PRO)) {
                var guard = new LedgerRerankingCallBudget(ledger, () -> ledger.read(approval.id(), scope).approval(), () -> key, model);
                var provider = new CohereRerankingProvider(() -> key, model, guard);
                var result = provider.rerank(new RerankingProvider.Request("Does stopping an environment free an infrastructure slot?",
                    candidates, Duration.ofSeconds(15), 1024));
                assertThat(result.candidates()).hasSize(3);
                assertThat(result.candidates().getFirst().reference()).isEqualTo("infrastructure-policy");
                assertThat(result.usage().billedUnits()).containsKey("input_tokens");
                var observed = ledger.read(approval.id(), scope);
                System.out.println("COHERE_QUALIFICATION " + new ObjectMapper().writeValueAsString(java.util.Map.of(
                    "model", model, "bestReference", result.candidates().getFirst().reference(),
                    "nativeBilledUnits", result.usage().billedUnits(), "aggregateCostNanoUsd", observed.accountedUsage().costNanoUsd(),
                    "aggregateCapNanoUsd", cap, "providerAttempts", observed.providerAttempts())));
            }
            assertThat(ledger.read(approval.id(), scope).accountedUsage().costNanoUsd()).isLessThanOrEqualTo(cap);
        }
    }
}
