package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static com.ai.fabric.runtime.web.admin.RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class RuntimeProviderBudgetAdminControllerTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void exactPrivateTwoPhaseProtocolEchoesOnlyTheSingleRuntimeFingerprint() throws Exception {
        var budgets = new RuntimeBudgetFixture().service();
        var auth = mock(RuntimeRequestAuthResolver.class);
        var request = new MockHttpServletRequest();
        when(auth.resolveVerifiedPrivateContext(eq(request), anyString()))
            .thenReturn(identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE)));
        var controller = new RuntimeProviderBudgetAdminController(budgets, new RuntimeBudgetAdminReadback(budgets, auth));
        var initialized = controller.initialize(request, ALLOCATION);
        assertThat(initialized.runtimeFingerprint()).isEqualTo(ALLOCATION.runtimeFingerprint());
        assertThat(budgets.readiness().ready()).isFalse();
        var active = controller.activate(request, new RuntimeBudgetBindingService.Activation(initialized.approvalId(),
            initialized.storeUuid(), initialized.runtimeFingerprint()));
        assertThat(active.state()).isEqualTo(RuntimeBudgetBindingService.State.ACTIVE);
        assertThat(controller.read(request).storeUuid()).isEqualTo(initialized.storeUuid());
        var mapper = new ObjectMapper().findAndRegisterModules();
        String json = mapper.writeValueAsString(ALLOCATION);
        assertThat(json).contains("\"runtimeFingerprint\"").doesNotContain("\"fingerprint\"", "allocationFingerprint");
        assertThat(mapper.readValue(json, RuntimeBudgetAllocation.class)).isEqualTo(ALLOCATION);
    }

    @Test
    void overviewReadScopeCannotInitializeOrActivateEvenOnAnIdenticalReplay() throws Exception {
        var budgets = new RuntimeBudgetFixture().service();
        var auth = mock(RuntimeRequestAuthResolver.class);
        var request = new MockHttpServletRequest();
        when(auth.resolveVerifiedPrivateContext(eq(request), anyString()))
            .thenReturn(identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW)));
        var controller = new RuntimeProviderBudgetAdminController(budgets, new RuntimeBudgetAdminReadback(budgets, auth));
        assertThatThrownBy(() -> controller.initialize(request, ALLOCATION)).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.read(request).state()).isEqualTo(RuntimeBudgetBindingService.State.UNINITIALIZED);
        var initialized = budgets.initialize(ALLOCATION);
        assertThatThrownBy(() -> controller.initialize(request, ALLOCATION)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller.activate(request, new RuntimeBudgetBindingService.Activation(initialized.approvalId(),
            initialized.storeUuid(), initialized.runtimeFingerprint()))).isInstanceOf(ResponseStatusException.class);
        assertThat(controller.read(request).state()).isEqualTo(RuntimeBudgetBindingService.State.INITIALIZED);
    }

    @Test
    void publicBearerCannotInitializeOrActivateAndForeignTenantCannotReadOrWrite() throws Exception {
        var budgets = new RuntimeBudgetFixture().service();
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fixture-public-token");
        var realPrivateAuth = new RuntimeRequestAuthResolver(new RuntimeAuthProperties());
        var controller = new RuntimeProviderBudgetAdminController(budgets, new RuntimeBudgetAdminReadback(budgets, realPrivateAuth));
        assertThatThrownBy(() -> controller.initialize(request, ALLOCATION)).isInstanceOf(ResponseStatusException.class);
        var mockedAuth = mock(RuntimeRequestAuthResolver.class);
        var foreign = identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW));
        when(mockedAuth.resolveVerifiedPrivateContext(eq(request), anyString())).thenReturn(foreign.toBuilder()
            .authContext(foreign.getAuthContext().toBuilder().tenantId("other-tenant").build()).build());
        var foreignController = new RuntimeProviderBudgetAdminController(budgets, new RuntimeBudgetAdminReadback(budgets, mockedAuth));
        assertThatThrownBy(() -> foreignController.initialize(request, ALLOCATION)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> foreignController.read(request)).isInstanceOf(ResponseStatusException.class);
        assertThat(budgets.readStore().binding().storeUuid()).isNull();
    }
}
