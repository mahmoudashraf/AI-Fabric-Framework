package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;

import ai.fabric.intent.orchestration.control.OrchestrationControlException;
import com.ai.fabric.budget.ProviderBudgetLedger.Approval;
import com.ai.fabric.budget.ProviderBudgetLedger.Limits;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RuntimeAuthoringBudgetTest extends RuntimeBudgetFixture.TestScope {
    @Test void dedicatedAuthoringScopeCountsDurablyWithoutRequiringChatPermission() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var budgets = fixture.register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        try (var turn = turns.openAuthoring(identity("customer", "deployment", List.of("configuration:author")))) {
            assertThat(turn.chatReceipt()).isPresent();
            turn.verifyBeforeOrchestration();
            assertThat(turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION)).isNotNull();
        }
        assertThat(fixture.service().readCurrent().chatAttempts()).isEqualTo(1);
        assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION)).isInstanceOf(OpenAiBudgetException.class);
    }

    @Test void otherCustomerDeploymentTenantAndChatOnlyScopeNeverConsumeAuthoringBudget() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        for (var identity : List.of(identity("other", "deployment", List.of("configuration:author")),
            identity("customer", "other", List.of("configuration:author")), chatIdentity(),
            identity("customer", "deployment", List.of("configuration:author")).toBuilder().authContext(
                chatIdentity().getAuthContext().toBuilder().tenantId("other").grantedScopes(List.of("configuration:author")).build()).build())) {
            assertThatThrownBy(() -> turns.openAuthoring(identity)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(budgets.readCurrent().chatAttempts()).isZero();
    }

    @Test void stoppedUninitializedAndExhaustedBudgetsCannotFundAuthoring() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var turns = new RuntimeBudgetTurnScopes(fixture.service());
        var identity = identity("customer", "deployment", List.of("configuration:author"));
        assertThatThrownBy(() -> turns.openAuthoring(identity)).isInstanceOf(OrchestrationControlException.class);
        var approval = new Approval(APPROVAL.id(), SCOPE, new Limits(1, 100_000, 2_000_000_000L), APPROVAL.expiresAt());
        var budgets = fixture.service();
        activate(budgets, budgets.initialize(allocation(approval)));
        try (var turn = turns.openAuthoring(identity)) { assertThat(turn.chatReceipt()).isPresent(); }
        assertThatThrownBy(() -> turns.openAuthoring(identity)).isInstanceOf(OrchestrationControlException.class);
        fixture.ledger.stop(APPROVAL.id(), SCOPE, "OWNER_STOP");
        assertThatThrownBy(() -> turns.openAuthoring(identity)).isInstanceOf(OrchestrationControlException.class);
        assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(1);
    }

    @Test void expiredAllocationCannotFundAnOtherwiseUnexpiredAuthoringIdentity() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        fixture.register();
        var expired = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, SCOPE, "tenant"),
            fixture.ledger, fixture.dataSource, java.time.Clock.fixed(NOW.plusSeconds(3_601), java.time.ZoneOffset.UTC));
        var identity = identity("customer", "deployment", List.of("configuration:author"));
        identity = identity.toBuilder().authContext(identity.getAuthContext().toBuilder().expiresAt(NOW.plusSeconds(7_200)).build()).build();
        var validIdentity = identity;
        assertThatThrownBy(() -> new RuntimeBudgetTurnScopes(expired).openAuthoring(validIdentity))
            .isInstanceOfSatisfying(OrchestrationControlException.class, failure -> assertThat(failure.code()).isEqualTo("PROVIDER_BUDGET_EXPIRED"));
        assertThat(fixture.service().readCurrent().chatAttempts()).isZero();
    }
}
