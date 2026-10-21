package com.ai.fabric.runtime.budget;

import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static com.ai.fabric.runtime.web.admin.RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.JdbcProviderBudgetLedger;
import com.ai.fabric.budget.ProviderBudgetLedger.*;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class RuntimeBudgetValidityExtensionTest extends RuntimeBudgetFixture.TestScope {
    static final String PATH = RuntimeProviderBudgetAdminController.PATH + "/validity-extensions";

    @Test void expiredActiveStoreExtendsWithoutChangingApprovalFingerprintBindingOrHolds() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var original = fixture.register();
        fixture.ledger.reserveAttempt(APPROVAL.id(), SCOPE, UUID.randomUUID(), new Usage(4, 3, 10));
        var before = original.readCurrent(); var binding = original.readStore().binding();
        var service = at(fixture, APPROVAL.expiresAt().plusSeconds(1));
        assertThat(service.readiness().ready()).isFalse();
        var command = command(binding, APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(3600));
        var receipt = service.extendValidity(command);
        assertThat(service.requireActive()).isEqualTo(before);
        assertThat(service.effectiveExpiresAt()).isEqualTo(command.expiresAt());
        assertThat(service.initialize(ALLOCATION)).isEqualTo(binding);
        assertThat(service.readStore().binding()).isEqualTo(binding);
        assertThat(service.readValidityExtension(command.extensionId())).contains(receipt);
        assertThat(RuntimeBudgetAllocation.fingerprint(service.requireActive().approval())).isEqualTo(binding.runtimeFingerprint());
        var changed = new Approval(APPROVAL.id(), SCOPE, APPROVAL.limits(), command.expiresAt());
        assertThatThrownBy(() -> service.initialize(allocation(changed))).isInstanceOf(RuntimeBudgetBindingException.class);
        assertThat(at(fixture, command.expiresAt()).readiness().ready()).isFalse();
    }

    @Test void onlyExactActiveStoreCanExtendAndNewExtensionsNeverClearStop() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var service = fixture.service();
        var absent = new RuntimeBudgetBindingService.StoreReceipt(RuntimeBudgetBindingService.State.ACTIVE,
            UUID.randomUUID(), APPROVAL.id(), ALLOCATION.runtimeFingerprint());
        assertThatThrownBy(() -> service.extendValidity(command(absent, APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60))))
            .isInstanceOf(RuntimeBudgetBindingException.class);
        var initialized = service.initialize(ALLOCATION);
        assertThatThrownBy(() -> service.extendValidity(command(initialized, APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60))))
            .isInstanceOf(RuntimeBudgetBindingException.class);
        var binding = activate(service, initialized);
        for (var wrong : List.of(
            new RuntimeBudgetBindingService.StoreReceipt(binding.state(), UUID.randomUUID(), binding.approvalId(), binding.runtimeFingerprint()),
            new RuntimeBudgetBindingService.StoreReceipt(binding.state(), binding.storeUuid(), "foreign", binding.runtimeFingerprint()),
            new RuntimeBudgetBindingService.StoreReceipt(binding.state(), binding.storeUuid(), binding.approvalId(), "b".repeat(64))))
            assertThatThrownBy(() -> service.extendValidity(command(wrong, APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60))))
                .isInstanceOf(RuntimeBudgetBindingException.class);
        var command = command(binding, APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60));
        var receipt = service.extendValidity(command);
        fixture.ledger.stop(APPROVAL.id(), SCOPE, "OWNER_STOP");
        assertThat(service.extendValidity(command)).isEqualTo(receipt);
        assertThat(service.readValidityExtension(command.extensionId())).contains(receipt);
        var next = new ValidityExtensionCommand(1, "next", binding.approvalId(), binding.storeUuid(), binding.runtimeFingerprint(), command.expiresAt(), command.expiresAt().plusSeconds(60), "next review");
        assertThatThrownBy(() -> service.extendValidity(next)).isInstanceOf(BudgetException.class)
            .satisfies(error -> assertThat(((BudgetException) error).code()).isEqualTo(BudgetException.Code.STOPPED));
        assertThat(service.readiness().ready()).isFalse();
    }

    @Test void foreignConfiguredScopeCannotExtendOrReadReceipt() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var service = fixture.register();
        var command = command(service.readStore().binding(), APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60));
        service.extendValidity(command);
        for (Scope changed : List.of(
            new Scope("other", SCOPE.deploymentId(), SCOPE.targetProfileId(), SCOPE.environment(), CREDENTIAL, SCOPE.rateCardReference()),
            new Scope(SCOPE.customerId(), "other", SCOPE.targetProfileId(), SCOPE.environment(), CREDENTIAL, SCOPE.rateCardReference()),
            new Scope(SCOPE.customerId(), SCOPE.deploymentId(), "other", SCOPE.environment(), CREDENTIAL, SCOPE.rateCardReference()),
            new Scope(SCOPE.customerId(), SCOPE.deploymentId(), SCOPE.targetProfileId(), "production", CREDENTIAL, SCOPE.rateCardReference()))) {
            assertThatThrownBy(() -> fixture.service(changed).extendValidity(command)).isInstanceOf(BudgetException.class);
            assertThatThrownBy(() -> fixture.service(changed).readValidityExtension(command.extensionId())).isInstanceOf(BudgetException.class);
        }
    }

    @Test void readbackSeparatesOriginalAndEffectiveExpiryWithoutChangingGoldenFingerprint() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var service = fixture.register();
        var command = command(service.readStore().binding(), APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60));
        service.extendValidity(command);
        var request = new MockHttpServletRequest(); var auth = authorized(List.of(RUNTIME_ADMIN_OVERVIEW));
        var readback = new RuntimeBudgetAdminReadback(service, auth).read(request);
        assertThat(readback.expiresAt()).isEqualTo(APPROVAL.expiresAt());
        assertThat(readback.effectiveExpiresAt()).isEqualTo(command.expiresAt());
        assertThat(readback.runtimeFingerprint()).isEqualTo(ALLOCATION.runtimeFingerprint());
        assertThat(readback.limits()).isEqualTo(APPROVAL.limits());
    }

    @Test void actualPostGetRoutesReturnExactDurableReceiptAndAuthorizeActualPath() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var service = fixture.register();
        var command = command(service.readStore().binding(), APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60));
        var auth = authorized(List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var mvc = MockMvcBuilders.standaloneSetup(controller(service, auth))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        String json = mapper.writeValueAsString(command);
        String response = mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var receipt = mapper.readValue(response, ValidityExtensionReceipt.class);
        assertThat(receipt).isEqualTo(service.readValidityExtension(command.extensionId()).orElseThrow());
        assertThat(response).doesNotContain("\"reason\":", command.reason());
        mvc.perform(get(PATH + "/" + command.extensionId())).andExpect(status().isOk()).andExpect(content().json(response));
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isOk()).andExpect(content().json(response));
        mvc.perform(get(PATH + "/unknown")).andExpect(status().isNotFound());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json.replace("approved continuation", "changed reason")))
            .andExpect(status().isConflict());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(json.replace("\"schemaVersion\":1", "\"schemaVersion\":2")))
            .andExpect(status().isBadRequest());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isBadRequest());
        verify(auth, atLeastOnce()).resolveVerifiedPrivateContext(any(), eq(PATH));
        verify(auth).resolveVerifiedPrivateContext(any(), eq(PATH + "/" + command.extensionId()));
    }

    @Test void extensionRoutesRequireSeparateWriteReadScopesAndPrivateOwnedIdentity() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var service = fixture.register();
        var command = command(service.readStore().binding(), APPROVAL.expiresAt(), APPROVAL.expiresAt().plusSeconds(60));
        var readOnly = controller(service, authorized(List.of(RUNTIME_ADMIN_OVERVIEW)));
        var request = new MockHttpServletRequest("POST", PATH);
        assertThatThrownBy(() -> readOnly.extendValidity(request, command)).isInstanceOf(ResponseStatusException.class);
        var writeOnly = controller(service, authorized(List.of(RuntimeProviderBudgetAdminController.WRITE_SCOPE)));
        assertThatThrownBy(() -> writeOnly.readValidityExtension(new MockHttpServletRequest("GET", PATH + "/extension"), "extension"))
            .isInstanceOf(ResponseStatusException.class);
        for (var foreign : List.of(
            identity("foreign", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE)),
            identity("customer", "foreign", List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE)),
            identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE)).toBuilder()
                .authContext(identity("customer", "deployment", List.of()).getAuthContext().toBuilder().tenantId("foreign").build()).build())) {
            var auth = mock(RuntimeRequestAuthResolver.class);
            when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(foreign);
            assertThatThrownBy(() -> controller(service, auth).extendValidity(request, command)).isInstanceOf(ResponseStatusException.class);
            assertThatThrownBy(() -> controller(service, auth).readValidityExtension(request, "extension")).isInstanceOf(ResponseStatusException.class);
        }
        var publicRequest = new MockHttpServletRequest("POST", PATH); publicRequest.addHeader("Authorization", "Bearer fixture-public-token");
        var realAuth = new RuntimeRequestAuthResolver(new RuntimeAuthProperties());
        assertThatThrownBy(() -> controller(service, realAuth).extendValidity(publicRequest, command)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> controller(service, realAuth).readValidityExtension(publicRequest, "extension")).isInstanceOf(ResponseStatusException.class);
        assertThat(service.readValidityExtension("extension")).isEmpty();
    }

    static RuntimeBudgetBindingService at(RuntimeBudgetFixture fixture, Instant now) {
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        return new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, SCOPE, "tenant"),
            new JdbcProviderBudgetLedger(fixture.dataSource, clock), fixture.dataSource, clock);
    }

    static ValidityExtensionCommand command(RuntimeBudgetBindingService.StoreReceipt binding, Instant expected, Instant expiry) {
        return new ValidityExtensionCommand(1, "extension", binding.approvalId(), binding.storeUuid(), binding.runtimeFingerprint(), expected, expiry, "approved continuation");
    }

    static RuntimeRequestAuthResolver authorized(List<String> scopes) {
        var auth = mock(RuntimeRequestAuthResolver.class);
        when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(identity("customer", "deployment", scopes));
        return auth;
    }

    static RuntimeProviderBudgetAdminController controller(RuntimeBudgetBindingService service, RuntimeRequestAuthResolver auth) {
        return new RuntimeProviderBudgetAdminController(service, new RuntimeBudgetAdminReadback(service, auth));
    }
}
