package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Scope;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.ReviewedOpenAiRateCard;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RuntimeOpenAiBudgetScopeResolverTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void bothReviewedCardsRetainTheirOriginalImmutableApprovalScope() throws Exception {
        for (String card : java.util.List.of(ReviewedOpenAiRateCard.REFERENCE, ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE)) {
            var fixture = new RuntimeBudgetFixture();
            var scope = new Scope(SCOPE.customerId(), SCOPE.deploymentId(), SCOPE.targetProfileId(), SCOPE.environment(), CREDENTIAL, card);
            var approval = new Approval(APPROVAL.id(), scope, APPROVAL.limits(), APPROVAL.expiresAt());
            var service = fixture.service(scope);
            activate(service, service.initialize(allocation(approval)));
            var binding = new RuntimeOpenAiBudgetScopeResolver(service).requireBinding(CREDENTIAL);
            assertThat(binding.scope()).isEqualTo(scope);
            assertThat(binding.scope().rateCardReference()).isEqualTo(card);
            assertThat(fixture.ledger.read(approval.id(), scope).approval()).isEqualTo(approval);
        }
    }

    @Test
    void backgroundThreadUsesOnlyTheExactStartupApprovalWithoutRequestContext() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var resolver = new RuntimeOpenAiBudgetScopeResolver(fixture.register());
        try (var executor = Executors.newSingleThreadExecutor()) {
            var binding = executor.submit(() -> resolver.requireBinding(CREDENTIAL)).get(5, TimeUnit.SECONDS);
            assertThat(binding.approvalId()).isEqualTo(APPROVAL.id());
            assertThat(binding.scope()).isEqualTo(SCOPE);
        }
        assertThat(fixture.ledger.read(APPROVAL.id(), SCOPE).providerAttempts()).isZero();
    }

    @Test
    void unregisteredCredentialsDoNotSelectOrCreateAnotherApproval() throws Exception {
        var service = new RuntimeBudgetFixture().register();
        var resolver = new RuntimeOpenAiBudgetScopeResolver(service);
        assertThatThrownBy(() -> resolver.requireBinding("sha256:" + "b".repeat(64)))
            .isInstanceOfSatisfying(OpenAiBudgetException.class,
                error -> assertThat(error.code()).isEqualTo(OpenAiBudgetException.Code.CREDENTIAL_SCOPE_MISMATCH));
        assertThat(service.readCurrent().providerAttempts()).isZero();
        assertThat(resolver.requireBinding(CREDENTIAL).approvalId()).isEqualTo(APPROVAL.id());
    }

    @Test
    void exactButUnreviewedRateCardCannotAuthorizeAnOpenAiExchange() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var scope = new Scope("customer", "deployment", "target", "staging", CREDENTIAL, "unreviewed-card");
        var approval = new Approval(APPROVAL.id(), scope, APPROVAL.limits(), APPROVAL.expiresAt());
        var service = fixture.service(scope);
        activate(service, service.initialize(allocation(approval)));
        var resolver = new RuntimeOpenAiBudgetScopeResolver(service);
        assertThatThrownBy(() -> resolver.requireBinding(CREDENTIAL)).isInstanceOfSatisfying(OpenAiBudgetException.class,
            error -> assertThat(error.code()).isEqualTo(OpenAiBudgetException.Code.RATE_CARD_MISMATCH));
    }

    @Test
    void nativeBindingNeverRegistersMissingApprovalAndRechecksDurableStop() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var resolver = new RuntimeOpenAiBudgetScopeResolver(fixture.service());
        assertThatThrownBy(() -> resolver.requireBinding(CREDENTIAL)).isInstanceOfSatisfying(RuntimeBudgetBindingException.class,
            error -> assertThat(error.code()).isEqualTo(RuntimeBudgetBindingException.Code.NOT_INITIALIZED));
        fixture.register();
        resolver.requireBinding(CREDENTIAL);
        fixture.ledger.stop(APPROVAL.id(), SCOPE, "OWNER_STOP");
        assertThatThrownBy(() -> resolver.requireBinding(CREDENTIAL)).isInstanceOfSatisfying(BudgetException.class,
            error -> assertThat(error.code()).isEqualTo(BudgetException.Code.STOPPED));
    }
}
