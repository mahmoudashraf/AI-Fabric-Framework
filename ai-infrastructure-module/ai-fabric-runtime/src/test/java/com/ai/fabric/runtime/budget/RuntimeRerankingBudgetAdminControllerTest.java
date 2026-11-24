package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.ProviderBudgetLedger;
import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static com.ai.fabric.runtime.web.admin.RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeRerankingBudgetAdminControllerTest extends RuntimeBudgetFixture.TestScope {
    private static final ProviderBudgetLedger.Scope RANKING_SCOPE = new ProviderBudgetLedger.Scope("customer", "deployment",
        "target", "staging", CREDENTIAL, ReviewedCohereRerankingRateCard.REFERENCE);

    @Test void separatePrivateActivationKeepsGenerationAccountingAndOriginalTermsUnchanged() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var generation = fixture.register(); var before = generation.readCurrent();
        var budgets = binding(fixture);
        var auth = mock(RuntimeRequestAuthResolver.class); var request = new MockHttpServletRequest();
        request.setRequestURI("/api/admin/reranking-budget/initialize");
        when(auth.resolveVerifiedPrivateContext(eq(request), anyString())).thenReturn(identity("customer", "deployment",
            List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE)));
        var controller = new RuntimeRerankingBudgetAdminController(budgets, auth);
        assertThat(controller.read(request).accountedUsage()).isNull();
        var approved = allocation(new ProviderBudgetLedger.Approval("reranking-approval", RANKING_SCOPE,
            new ProviderBudgetLedger.Limits(0, 1_000_000, 10_000_000), NOW.plusSeconds(3600)));
        var receipt = controller.initialize(request, approved);
        assertThat(budgets.readiness().ready()).isFalse();
        controller.activate(request, new RuntimeBudgetBindingService.Activation(receipt.approvalId(), receipt.storeUuid(), receipt.runtimeFingerprint()));
        var readback = controller.read(request);
        assertThat(readback.state()).isEqualTo(RuntimeBudgetBindingService.State.ACTIVE);
        assertThat(readback.approvalId()).isEqualTo("reranking-approval");
        assertThat(generation.readCurrent()).isEqualTo(before);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(readback)).doesNotContain(CREDENTIAL);
    }

    @Test void publicForeignAndReadOnlyIdentitiesCannotInitializeReranking() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = binding(fixture);
        var request = new MockHttpServletRequest(); request.addHeader("Authorization", "Bearer public-token");
        var controller = new RuntimeRerankingBudgetAdminController(budgets, new RuntimeRequestAuthResolver(new RuntimeAuthProperties()));
        assertThatThrownBy(() -> controller.initialize(request, ALLOCATION)).isInstanceOf(ResponseStatusException.class);
        var auth = mock(RuntimeRequestAuthResolver.class);
        var scoped = identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE));
        var protectedController = new RuntimeRerankingBudgetAdminController(budgets, auth);
        for (RuntimeResolvedIdentity denied : List.of(identity("foreign", "deployment", scoped.getAuthContext().getGrantedScopes()),
            identity("customer", "foreign", scoped.getAuthContext().getGrantedScopes()),
            identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW)),
            scoped.toBuilder().authContext(scoped.getAuthContext().toBuilder().tenantId("foreign").build()).build(),
            scoped.toBuilder().authContext(scoped.getAuthContext().toBuilder().authMode(RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED).build()).build())) {
            when(auth.resolveVerifiedPrivateContext(eq(request), anyString())).thenReturn(denied);
            assertThatThrownBy(() -> protectedController.initialize(request, ALLOCATION)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(budgets.readStore().binding().storeUuid()).isNull();
    }

    @Test void machineFailuresUseBoundedCodesNotExceptionPayloads() throws Exception {
        var controller = new RuntimeRerankingBudgetAdminController(binding(new RuntimeBudgetFixture()), mock(RuntimeRequestAuthResolver.class));
        var scopeFailure = controller.bindingFailure(new RuntimeBudgetBindingException(RuntimeBudgetBindingException.Code.ALLOCATION_SCOPE_MISMATCH));
        assertThat(scopeFailure.getStatusCode().value()).isEqualTo(403);
        assertThat(scopeFailure.getBody()).containsExactlyEntriesOf(java.util.Map.of("code", "RUNTIME_BUDGET_ALLOCATION_SCOPE_MISMATCH"));
        assertThat(controller.ledgerFailure(new com.ai.fabric.budget.BudgetException(com.ai.fabric.budget.BudgetException.Code.LIMIT_EXCEEDED))
            .getStatusCode().value()).isEqualTo(409);
        assertThat(controller.ledgerFailure(new com.ai.fabric.budget.BudgetException(com.ai.fabric.budget.BudgetException.Code.STORAGE_UNAVAILABLE))
            .getStatusCode().value()).isEqualTo(503);
    }

    private RuntimeBudgetBindingService binding(RuntimeBudgetFixture fixture) {
        return new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, RANKING_SCOPE, "tenant"), fixture.ledger,
            fixture.dataSource, CLOCK, RuntimeBudgetBindingService.Account.RERANKING);
    }
}
