package com.ai.fabric.runtime.budget;

import com.ai.fabric.budget.BudgetException;
import com.ai.fabric.budget.ProviderBudgetLedger.*;
import com.ai.fabric.budget.openai.*;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.runtime.budget.RuntimeBudgetFixture.*;
import static com.ai.fabric.runtime.web.admin.RuntimeAdminScopeCatalog.RUNTIME_ADMIN_OVERVIEW;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RuntimeBudgetPricingAmendmentTest extends RuntimeBudgetFixture.TestScope {
    static final String PATH = RuntimeProviderBudgetAdminController.PATH + "/pricing-amendments";
    static final Scope MINI = new Scope(SCOPE.customerId(), SCOPE.deploymentId(), SCOPE.targetProfileId(), SCOPE.environment(),
        CREDENTIAL, ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE);
    static final Approval ORIGINAL = new Approval(APPROVAL.id(), MINI, new Limits(100, 2_000_000, 5_000_000_000L), APPROVAL.expiresAt());

    @Test void amendmentAndReplayPreserveEveryFinancialRowAndTheOriginalStore() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture); var binding = budgets.readStore().binding();
        fixture.ledger.recordChatIngress(ORIGINAL.id(), MINI, UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            var id = UUID.randomUUID(); fixture.ledger.reserveAttempt(ORIGINAL.id(), MINI, id, new Usage(100, 10, 1000));
            if (i == 1) fixture.ledger.settle(ORIGINAL.id(), MINI, id, new Usage(10, 1, 100), "offline-native-usage");
            if (i == 2) fixture.ledger.markAmbiguous(ORIGINAL.id(), MINI, id, "offline-unknown");
        }
        budgets.topUp(new TopUpCommand(1, "existing-topup", ORIGINAL.id(), binding.storeUuid(), binding.runtimeFingerprint(),
            ORIGINAL.limits(), new Limits(0, 3_000_000, 20_000_000_000L), "operator", "approved-existing-credit", "payer", "existing topup"));
        budgets.extendValidity(new ValidityExtensionCommand(1, "existing-validity", ORIGINAL.id(), binding.storeUuid(),
            binding.runtimeFingerprint(), ORIGINAL.expiresAt(), ORIGINAL.expiresAt().plusSeconds(3600), "existing validity"));
        var jdbc = new JdbcTemplate(fixture.dataSource); var before = financialRows(jdbc); var accounting = budgets.readCurrent();
        var receipt = budgets.amendPricing(command(binding, "astra-quality", "private-reason-canary"));
        assertThat(budgets.amendPricing(command(binding, "astra-quality", "private-reason-canary"))).isEqualTo(receipt);
        assertThat(budgets.readCurrent()).isEqualTo(accounting); assertThat(budgets.readStore().binding()).isEqualTo(binding);
        assertThat(financialRows(jdbc)).isEqualTo(before);
        assertThat(receipt.originalRateCardReference()).isEqualTo(MINI.rateCardReference());
        assertThat(receipt.additionalRateCardReference()).isEqualTo(ReviewedOpenAiRateCard.ASTRA_REFERENCE);
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(receipt)).doesNotContain("private-reason-canary", CREDENTIAL);
        assertThat(fixture.service(MINI).pricingAmendment()).contains(receipt);
        var scopes = new RuntimeOpenAiBudgetScopeResolver(budgets);
        assertThat(scopes.requireBinding(CREDENTIAL, ReviewedOpenAiRateCard.ASTRA_MODEL).scope()).isEqualTo(MINI);
        assertThat(scopes.requireBinding(CREDENTIAL, ReviewedOpenAiRateCard.CHAT_MODEL).additionalRateCardReference()).isNull();
    }

    @Test void absentAmendmentWrongStoreAndLegacyCardCannotAuthorizeAstra() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture); var binding = budgets.readStore().binding();
        assertThatThrownBy(() -> new RuntimeOpenAiBudgetScopeResolver(budgets).requireBinding(CREDENTIAL, ReviewedOpenAiRateCard.ASTRA_MODEL))
            .isInstanceOf(OpenAiBudgetException.class);
        var command = command(new RuntimeBudgetBindingService.StoreReceipt(binding.state(), UUID.randomUUID(), binding.approvalId(),
            binding.runtimeFingerprint()), "astra-quality", "reason");
        assertThatThrownBy(() -> budgets.amendPricing(command)).isInstanceOf(RuntimeBudgetBindingException.class);
        assertThat(budgets.pricingAmendment()).isEmpty();
        var legacy = new RuntimeBudgetFixture().register();
        assertThatThrownBy(() -> legacy.amendPricing(command(legacy.readStore().binding(), "legacy-attempt", "reason")))
            .isInstanceOf(RuntimeBudgetBindingException.class);
    }

    @Test void conflictAndConcurrentReplaysCannotReplaceTheOneImmutableAuthorization() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture); var binding = budgets.readStore().binding();
        var command = command(binding, "astra-quality", "reason");
        try (var executor = Executors.newFixedThreadPool(6)) {
            var futures = new ArrayList<java.util.concurrent.Future<RuntimeBudgetPricingAmendment.Receipt>>();
            for (int i = 0; i < 12; i++) futures.add(executor.submit(() -> budgets.amendPricing(command)));
            var receipt = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (var future : futures) assertThat(future.get(5, TimeUnit.SECONDS)).isEqualTo(receipt);
        }
        assertThatThrownBy(() -> budgets.amendPricing(command(binding, "astra-quality", "changed")))
            .isInstanceOf(RuntimeBudgetBindingException.class).hasMessageContaining("PRICING_AMENDMENT_CONFLICT");
        assertThatThrownBy(() -> budgets.amendPricing(command(binding, "other", "reason")))
            .isInstanceOf(RuntimeBudgetBindingException.class).hasMessageContaining("PRICING_AMENDMENT_CONFLICT");
        assertThat(new JdbcTemplate(fixture.dataSource).queryForObject("SELECT count(*) FROM runtime_provider_budget_pricing_amendment", Integer.class)).isOne();
    }

    @Test void stoppedOrExpiredFundsAreNotRevivedAndExistingReceiptRemainsReadable() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture); var binding = budgets.readStore().binding();
        var command = command(binding, "astra-quality", "reason"); var receipt = budgets.amendPricing(command);
        fixture.ledger.stop(ORIGINAL.id(), MINI, "existing-stop"); var stopped = budgets.readCurrent();
        assertThat(budgets.amendPricing(command)).isEqualTo(receipt);
        assertThat(budgets.readCurrent()).isEqualTo(stopped);
        assertThatThrownBy(() -> new RuntimeOpenAiBudgetScopeResolver(budgets).requireBinding(CREDENTIAL, ReviewedOpenAiRateCard.ASTRA_MODEL))
            .isInstanceOf(BudgetException.class);
        var other = new RuntimeBudgetFixture(); var active = registered(other); var activeBinding = active.readStore().binding();
        var expired = new RuntimeBudgetBindingService(new RuntimeBudgetConfiguration(true, MINI, "tenant"), other.ledger, other.dataSource,
            Clock.fixed(ORIGINAL.expiresAt().plusSeconds(1), ZoneOffset.UTC));
        assertThatThrownBy(() -> expired.amendPricing(command(activeBinding, "expired", "reason"))).isInstanceOf(BudgetException.class);
        assertThat(expired.pricingAmendment()).isEmpty();
    }

    @Test void privateRoutesRequireWriteScopeAndReturnOnlyTheExactReceipt() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture); var command = command(budgets.readStore().binding(), "astra-quality", "reason-canary");
        var auth = mock(RuntimeRequestAuthResolver.class);
        when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW)));
        var controller = new RuntimeProviderBudgetAdminController(budgets, new RuntimeBudgetAdminReadback(budgets, auth));
        var request = new MockHttpServletRequest("POST", PATH);
        assertThatThrownBy(() -> controller.amendPricing(request, command)).isInstanceOf(ResponseStatusException.class);
        when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(identity("customer", "deployment",
            List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE)));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).build();
        String body = mapper.writeValueAsString(command);
        String wire = mvc.perform(post(PATH).contentType("application/json").content(body)).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(wire).doesNotContain("reason-canary", CREDENTIAL);
        mvc.perform(post(PATH).contentType("application/json").content(body)).andExpect(status().isOk()).andExpect(content().json(wire));
        mvc.perform(get(PATH + "/astra-quality")).andExpect(status().isOk()).andExpect(content().json(wire));
        mvc.perform(get(PATH + "/unknown")).andExpect(status().isNotFound());
        mvc.perform(post(PATH).contentType("application/json").content(body.replace("reason-canary", "changed"))).andExpect(status().isConflict());
        mvc.perform(post(PATH).contentType("application/json").content(body.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))).andExpect(status().isBadRequest());
        verify(auth, atLeastOnce()).resolveVerifiedPrivateContext(any(), eq(PATH));
        assertThat(controller.read(request).rateCardReference()).isEqualTo(MINI.rateCardReference());
        assertThat(controller.read(request).pricingAmendment()).isEqualTo(budgets.pricingAmendment().orElseThrow());
    }

    @Test void publicBearerAndForeignIdentitiesCannotAmendEvenWithAnExistingReceipt() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture); var command = command(budgets.readStore().binding(), "astra-quality", "reason");
        var request = new MockHttpServletRequest("POST", PATH); request.addHeader("Authorization", "Bearer public-fixture-token");
        var publicController = new RuntimeProviderBudgetAdminController(budgets,
            new RuntimeBudgetAdminReadback(budgets, new RuntimeRequestAuthResolver(new RuntimeAuthProperties())));
        assertThatThrownBy(() -> publicController.amendPricing(request, command)).isInstanceOf(ResponseStatusException.class);
        for (var identity : List.of(identity("foreign", "deployment", List.of(RuntimeProviderBudgetAdminController.WRITE_SCOPE)),
            identity("customer", "foreign", List.of(RuntimeProviderBudgetAdminController.WRITE_SCOPE)),
            identity("customer", "deployment", List.of(RuntimeProviderBudgetAdminController.WRITE_SCOPE)).toBuilder()
                .authContext(identity("customer", "deployment", List.of()).getAuthContext().toBuilder().tenantId("foreign").build()).build())) {
            var auth = mock(RuntimeRequestAuthResolver.class); when(auth.resolveVerifiedPrivateContext(any(), anyString())).thenReturn(identity);
            var controller = new RuntimeProviderBudgetAdminController(budgets, new RuntimeBudgetAdminReadback(budgets, auth));
            assertThatThrownBy(() -> controller.amendPricing(request, command)).isInstanceOf(ResponseStatusException.class);
        }
        assertThat(budgets.pricingAmendment()).isEmpty();
    }

    @Test void actualSignedPrivateHttpRequiresBothTheServiceKeyAndOwnedWriteAssertionOnReplay() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture);
        var command = command(budgets.readStore().binding(), "signed-review", "reason");
        var properties = com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
        properties.getIngress().setAcceptedAudiences(List.of("deployment"));
        var controller = new RuntimeProviderBudgetAdminController(budgets,
            new RuntimeBudgetAdminReadback(budgets, new RuntimeRequestAuthResolver(properties)));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(mapper)).build();
        var trusted = identity("customer", "deployment", List.of(RUNTIME_ADMIN_OVERVIEW, RuntimeProviderBudgetAdminController.WRITE_SCOPE))
            .getAuthContext().toBuilder().subjectType(com.ai.fabric.runtime.auth.RuntimeAuthSubjectType.SYSTEM_PROCESS)
            .callerType(com.ai.fabric.runtime.auth.RuntimeAuthCallerType.TRUSTED_BACKEND).issuer("backend-test")
            .audiences(List.of("deployment")).expiresAt(Instant.now().plusSeconds(600)).build();
        String body = mapper.writeValueAsString(command);
        for (String kind : List.of("valid", "readOnly", "foreign", "forged", "missingKey")) {
            var claims = kind.equals("readOnly") ? trusted.toBuilder().grantedScopes(List.of(RUNTIME_ADMIN_OVERVIEW)).build()
                : kind.equals("foreign") ? trusted.toBuilder().tenantId("other-tenant").build() : trusted;
            var headers = new MockHttpServletRequest();
            com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport.addPrivateRuntimeHeaders(headers, properties, claims);
            var request = post(PATH).contentType("application/json").content(body);
            headers.getHeaderNames().asIterator().forEachRemaining(name -> {
                if (kind.equals("missingKey") && name.equalsIgnoreCase(properties.getIngress().getTrustedBackend().getApiKeyHeader())) return;
                request.header(name, headers.getHeader(name) + (kind.equals("forged") && name.equalsIgnoreCase(
                    properties.getIngress().getPrivateAssertions().getAuthorizationHeader()) ? "x" : ""));
            });
            mvc.perform(request).andExpect(status().is(kind.equals("valid") ? 200
                : kind.equals("readOnly") || kind.equals("foreign") ? 403 : 401));
        }
        assertThat(budgets.pricingAmendment()).isPresent();
    }

    @Test void newAuthorizationCannotReviveAStoppedAllocationOrAlterItsRows() throws Exception {
        var fixture = new RuntimeBudgetFixture(); var budgets = registered(fixture);
        fixture.ledger.stop(ORIGINAL.id(), MINI, "already-stopped"); var before = financialRows(new JdbcTemplate(fixture.dataSource));
        assertThatThrownBy(() -> budgets.amendPricing(command(budgets.readStore().binding(), "stopped", "reason")))
            .isInstanceOf(BudgetException.class).hasMessageContaining("STOPPED");
        assertThat(budgets.pricingAmendment()).isEmpty(); assertThat(financialRows(new JdbcTemplate(fixture.dataSource))).isEqualTo(before);
    }

    @Test void freshSchemaMatchesTheAdditiveV30DefinitionExactly() throws Exception {
        String fresh, migration;
        try (var input = new org.springframework.core.io.ClassPathResource("com/ai/fabric/runtime/budget/runtime-provider-budget-binding.sql").getInputStream()) {
            fresh = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        try (var input = new org.springframework.core.io.ClassPathResource("db/runtime-migration/V30__provider_budget_pricing_amendment.sql").getInputStream()) {
            migration = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        assertThat(fresh.substring(fresh.indexOf("CREATE TABLE runtime_provider_budget_pricing_amendment")).strip()).isEqualTo(migration.strip());
    }

    static RuntimeBudgetBindingService registered(RuntimeBudgetFixture fixture) {
        var budgets = fixture.service(MINI); activate(budgets, budgets.initialize(allocation(ORIGINAL))); return budgets;
    }
    static RuntimeBudgetPricingAmendment.Command command(RuntimeBudgetBindingService.StoreReceipt binding, String id, String reason) {
        return new RuntimeBudgetPricingAmendment.Command(1, id, binding.approvalId(), binding.storeUuid(), binding.runtimeFingerprint(),
            ReviewedOpenAiRateCard.GPT_54_MINI_REFERENCE, ReviewedOpenAiRateCard.ASTRA_REFERENCE, "operator", "owner-quality-approval", reason);
    }
    static Map<String, List<Map<String, Object>>> financialRows(JdbcTemplate jdbc) {
        var rows = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (String table : List.of("provider_budget_approval", "provider_budget_attempt", "provider_budget_chat_ingress",
            "provider_budget_top_up", "provider_budget_validity_extension", "runtime_provider_budget_binding"))
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table));
        return rows;
    }
}
