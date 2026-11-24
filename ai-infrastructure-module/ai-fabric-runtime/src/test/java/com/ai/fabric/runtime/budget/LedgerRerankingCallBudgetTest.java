package com.ai.fabric.runtime.budget;

import ai.fabric.spi.RerankingProvider;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;

class LedgerRerankingCallBudgetTest extends RuntimeBudgetFixture.TestScope {
    private static final String KEY = "fixture-not-a-real-credential";
    private static final String MODEL = ReviewedCohereRerankingRateCard.FAST;
    private static final ProviderBudgetLedger.Scope COHERE = new ProviderBudgetLedger.Scope("customer", "deployment", "target",
        "staging", ReviewedCohereRerankingRateCard.credentialIdentity(KEY), ReviewedCohereRerankingRateCard.REFERENCE);

    @Test
    void independentBindingRequiresExplicitActivationAndDoesNotChangeOpenAiApproval() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var generation = fixture.register();
        var before = generation.readCurrent();
        var reranking = binding(fixture);
        var guard = new LedgerRerankingCallBudget(fixture.ledger, reranking::approval, () -> KEY, MODEL);
        assertThatThrownBy(() -> guard.reserve("cohere", MODEL, request())).isInstanceOf(RuntimeBudgetBindingException.class);
        var allocation = allocation(new ProviderBudgetLedger.Approval("reranking", COHERE,
            new ProviderBudgetLedger.Limits(0, 1_000_000, 10_000_000), NOW.plusSeconds(3600)));
        var initialized = reranking.initialize(allocation);
        assertThatThrownBy(() -> guard.reserve("cohere", MODEL, request())).isInstanceOf(RuntimeBudgetBindingException.class);
        activate(reranking, initialized);
        var attempt = guard.reserve("cohere", MODEL, request());
        assertThat(reranking.readCurrent().accountedUsage().inputTokens()).isEqualTo(65_536);
        attempt.settle(new RerankingProvider.Usage(Map.of("input_tokens", 123L)), "native-123");
        assertThat(reranking.readCurrent().accountedUsage()).isEqualTo(new ProviderBudgetLedger.Usage(123, 0, 2460));
        assertThat(reranking.readCurrent().chatAttempts()).isZero();
        assertThat(generation.readCurrent()).isEqualTo(before);
        assertThat(binding(fixture).readCurrent()).isEqualTo(reranking.readCurrent());
    }

    @Test
    void deniedCostAllowanceDoesNotIssueAnAttempt() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        fixture.ledger.approve(new ProviderBudgetLedger.Approval("reranking", COHERE,
            new ProviderBudgetLedger.Limits(0, 1_000_000, 1), NOW.plusSeconds(3600)));
        var guard = guard(fixture);
        assertThatThrownBy(() -> guard.reserve("cohere", MODEL, request())).isInstanceOf(BudgetException.class);
        assertThat(fixture.ledger.read("reranking", COHERE).providerAttempts()).isZero();
    }

    @Test
    void unknownNativeUsageKeepsTheFullHoldAcrossRestart() throws Exception {
        var fixture = fundedFixture();
        var attempt = guard(fixture).reserve("cohere", MODEL, request());
        var reserved = fixture.ledger.read("reranking", COHERE).accountedUsage();
        assertThatThrownBy(() -> attempt.settle(new RerankingProvider.Usage(Map.of("search_units", 1L)), "native"))
            .hasMessageContaining("BILLING_UNIT_MISMATCH");
        attempt.ambiguous("untrusted message not persisted");
        assertThat(fixture.ledger.read("reranking", COHERE).accountedUsage()).isEqualTo(reserved);
        assertThat(guard(fixture).reserve("cohere", MODEL, request()).id()).isNotEqualTo(attempt.id());
    }

    @Test
    void mismatchedProviderModelCredentialOrRateCardCannotSpend() throws Exception {
        var fixture = fundedFixture();
        var guard = guard(fixture);
        assertThatThrownBy(() -> guard.reserve("other", MODEL, request())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> guard.reserve("cohere", ReviewedCohereRerankingRateCard.PRO, request())).isInstanceOf(SecurityException.class);
        var wrongKey = new LedgerRerankingCallBudget(fixture.ledger,
            () -> fixture.ledger.read("reranking", COHERE).approval(), () -> "different-key", MODEL);
        assertThatThrownBy(() -> wrongKey.reserve("cohere", MODEL, request())).isInstanceOf(SecurityException.class);
        assertThat(fixture.ledger.read("reranking", COHERE).providerAttempts()).isZero();
    }

    @Test
    void usageOutsideTheNativeContextContractStopsFurtherSpending() throws Exception {
        var fixture = fundedFixture();
        var attempt = guard(fixture).reserve("cohere", MODEL, request());
        assertThatThrownBy(() -> attempt.settle(new RerankingProvider.Usage(Map.of("input_tokens", 100_000L)), "native"))
            .hasMessageContaining("USAGE_EXCEEDS_RESERVED_CONTRACT");
        attempt.ambiguous("contract violation");
        assertThat(fixture.ledger.read("reranking", COHERE).stopped()).isTrue();
        assertThatThrownBy(() -> guard(fixture).reserve("cohere", MODEL, request())).isInstanceOf(BudgetException.class);
    }

    @Test
    void modelPricesUseNativeTokensAndNeverAssumeSearchUnitsOrZeroCost() {
        assertThat(ReviewedCohereRerankingRateCard.measured(MODEL,
            new RerankingProvider.Usage(Map.of("input_tokens", 1_000_000L))).costNanoUsd()).isEqualTo(20_000_000L);
        assertThat(ReviewedCohereRerankingRateCard.measured(ReviewedCohereRerankingRateCard.PRO,
            new RerankingProvider.Usage(Map.of("input_tokens", 1_000_000L))).costNanoUsd()).isEqualTo(50_000_000L);
        assertThatThrownBy(() -> ReviewedCohereRerankingRateCard.nanoUsdPerToken("rerank-v3.5"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReviewedCohereRerankingRateCard.measured(MODEL,
            new RerankingProvider.Usage(Map.of("input_tokens", 0L)))).isInstanceOf(IllegalStateException.class);
    }

    private RuntimeBudgetFixture fundedFixture() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        fixture.ledger.approve(new ProviderBudgetLedger.Approval("reranking", COHERE,
            new ProviderBudgetLedger.Limits(0, 1_000_000, 10_000_000), NOW.plusSeconds(3600)));
        return fixture;
    }

    private LedgerRerankingCallBudget guard(RuntimeBudgetFixture fixture) {
        return new LedgerRerankingCallBudget(fixture.ledger,
            () -> fixture.ledger.read("reranking", COHERE).approval(), () -> KEY, MODEL);
    }

    private RuntimeBudgetBindingService binding(RuntimeBudgetFixture fixture) {
        return new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, COHERE, "tenant"), fixture.ledger,
            fixture.dataSource, CLOCK, RuntimeBudgetBindingService.Account.RERANKING);
    }

    private RerankingProvider.Request request() {
        return new RerankingProvider.Request("question", List.of(new RerankingProvider.Candidate("a", "approved a"),
            new RerankingProvider.Candidate("b", "approved b")), Duration.ofSeconds(5), 1024);
    }
}
