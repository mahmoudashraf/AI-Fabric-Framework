package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static com.ai.fabric.runtime.web.admin.RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ai.fabric.runtime.auth.RuntimeAuthMode;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.auth.RuntimeResolvedIdentity;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class RuntimeBudgetAdminReadbackTest extends RuntimeBudgetFixture.TestScope {
    @Test
    void privateAdminCanReadUninitializedStateWithoutInventingAnAccountingBaseline() throws Exception {
        var budgets = new RuntimeBudgetFixture().service();
        var auth = mock(RuntimeRequestAuthResolver.class);
        var request = new MockHttpServletRequest();
        when(auth.resolveVerifiedPrivateContext(eq(request), anyString()))
            .thenReturn(identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW)));
        var readback = new RuntimeBudgetAdminReadback(budgets, auth).read(request);
        assertThat(readback.state()).isEqualTo(RuntimeBudgetBindingService.State.UNINITIALIZED);
        assertThat(readback.storeUuid()).isNull();
        assertThat(readback.limits()).isNull();
        assertThat(readback.accountedUsage()).isNull();
        assertThat(budgets.readiness().ready()).isFalse();
    }

    @Test
    void privateScopedReadbackIsSecretFreeAndDoesNotMutateAccounting() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var budgets = fixture.register();
        budgets.recordVerifiedChatIngress(chatIdentity());
        fixture.ledger.stop(APPROVAL.id(), SCOPE, "private-owner-reason-not-for-readback");
        var before = budgets.readCurrent();
        var auth = mock(RuntimeRequestAuthResolver.class);
        var request = new MockHttpServletRequest();
        var identity = identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW));
        when(auth.resolveVerifiedPrivateContext(eq(request), anyString())).thenReturn(identity);

        var readback = new RuntimeBudgetAdminReadback(budgets, auth).read(request);

        assertThat(readback.targetProfileId()).isEqualTo("target");
        assertThat(readback.chatAttempts()).isOne();
        assertThat(readback.stopped()).isTrue();
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(readback);
        assertThat(json).doesNotContain(CREDENTIAL, "credentialIdentity", "private-owner-reason", "stopReason");
        assertThat(budgets.readCurrent()).isEqualTo(before);
        verify(auth).resolveVerifiedPrivateContext(eq(request), anyString());
        verify(auth).requireScope(eq(identity), eq(RUNTIME_ADMIN_OVERVIEW), anyString());
        verifyNoMoreInteractions(auth);
    }

    @Test
    void missingForeignUnscopedAndPublicIdentitiesCannotReadAccounting() throws Exception {
        var fixture = new RuntimeBudgetFixture();
        var budgets = fixture.register();
        var auth = mock(RuntimeRequestAuthResolver.class);
        var request = new MockHttpServletRequest();
        var admin = identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW));
        for (RuntimeResolvedIdentity denied : List.of(
            RuntimeResolvedIdentity.builder().build(),
            identity("foreign", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW)),
            identity("customer", "foreign", List.of(RUNTIME_ADMIN_OVERVIEW)),
            identity("customer", "deployment", List.of("chat:query")),
            admin.toBuilder().authContext(admin.getAuthContext().toBuilder()
                .authMode(RuntimeAuthMode.PUBLIC_RUNTIME_AUTHENTICATED).build()).build())) {
            when(auth.resolveVerifiedPrivateContext(eq(request), anyString())).thenReturn(denied);
            assertThatThrownBy(() -> new RuntimeBudgetAdminReadback(budgets, auth).read(request))
                .isInstanceOf(ResponseStatusException.class);
        }
        assertThat(budgets.readCurrent().chatAttempts()).isZero();
    }

    @Test
    void actualPrivateResolverDoesNotAcceptPublicBearerAsAdminAuthentication() throws Exception {
        var budgets = new RuntimeBudgetFixture().register();
        var auth = new RuntimeRequestAuthResolver(new RuntimeAuthProperties());
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer fixture-public-token");
        assertThatThrownBy(() -> new RuntimeBudgetAdminReadback(budgets, auth).read(request))
            .isInstanceOf(ResponseStatusException.class);
        assertThat(budgets.readCurrent().chatAttempts()).isZero();
    }
}
