package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static org.assertj.core.api.Assertions.*;
import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetException;
import com.ai.fabric.budget.openai.OpenAiBudgetInvocation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RuntimeManagedTaskBudgetTest extends RuntimeBudgetFixture.TestScope {
    @Test void exactTaskGrantUsesExistingLedgerAndCleansItsScopeWithoutChatAuthority() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        var turns = new RuntimeBudgetTurnScopes(budgets);
        var caller = identity("customer", "deployment", List.of("task:assess-request:execute"));
        try (var turn = turns.openTask(caller, "assess-request")) {
            turn.verifyBeforeOrchestration();
            assertThat(turn.chatReceipt()).isPresent();
            var call = turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION);
            call.observer().onDenied(new OpenAiBudgetInvocation.Denial(call.operationId(), UUID.randomUUID(), BudgetException.Code.LIMIT_EXCEEDED, null));
            assertThat(turn.interruption()).isPresent();
        }
        assertThat(budgets.readCurrent().chatAttempts()).isEqualTo(1);
        assertThat(caller.getAuthContext().getGrantedScopes()).containsExactly("task:assess-request:execute");
        assertThatThrownBy(() -> turns.capture(OpenAiBudgetInvocation.Operation.CHAT_COMPLETION)).isInstanceOf(OpenAiBudgetException.class);
    }
    @Test void readChatWildcardForeignAndInvalidCodeCannotConsumeATaskAttempt() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        for (var scopes : List.of(List.of("task:assess-request:read"), List.of("task:*:execute"), List.of("chat:query"))) {
            assertThatThrownBy(() -> budgets.recordVerifiedTaskIngress(identity("customer", "deployment", scopes), "assess-request"))
                .isInstanceOf(ResponseStatusException.class);
        }
        assertThatThrownBy(() -> budgets.recordVerifiedTaskIngress(identity("customer", "foreign", List.of("task:assess-request:execute")), "assess-request"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> budgets.recordVerifiedTaskIngress(chatIdentity(), "bad:code"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(budgets.readCurrent().chatAttempts()).isZero();
    }
}
