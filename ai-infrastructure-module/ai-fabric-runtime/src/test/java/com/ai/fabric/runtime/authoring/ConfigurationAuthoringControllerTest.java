package com.ai.fabric.runtime.authoring;

import static com.ai.fabric.runtime.authoring.AuthoringTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport;
import com.ai.fabric.runtime.auth.RuntimeAuthContext;
import com.ai.fabric.runtime.auth.RuntimePrivateAssertionService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

class ConfigurationAuthoringControllerTest {
    private final ConfigurationAuthoringService service = mock(ConfigurationAuthoringService.class);
    private final com.ai.fabric.runtime.config.RuntimeAuthProperties properties = RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
    private MockMvc mvc;

    @BeforeEach void setup() {
        properties.getIngress().setAcceptedAudiences(List.of("dedicated-authoring"));
        mvc = MockMvcBuilders.standaloneSetup(new ConfigurationAuthoringController(new RuntimeRequestAuthResolver(properties), service))
            .setMessageConverters(new AuthoringHttpMessageConverter(MAPPER))
            .setControllerAdvice(new AuthoringBodyLimitAdvice()).build();
    }

    @Test void privateOnlyRouteRequiresAssertionBackendKeyAndExactScope() throws Exception {
        mvc.perform(base()).andExpect(status().isUnauthorized());
        mvc.perform(base().header("Authorization", "Bearer public-token")).andExpect(status().isUnauthorized());
        mvc.perform(signed(identity().getAuthContext()).header(properties.getPublicTokens().getAuthorizationHeader(), "Bearer public-token"))
            .andExpect(status().isBadRequest());
        var wrongScope = identity().getAuthContext().toBuilder().grantedScopes(List.of("chat:query")).build();
        mvc.perform(signed(wrongScope)).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void exactSessionOrSubjectFallbackIsBoundToThisTurn() throws Exception {
        when(service.generate(any(), any())).thenReturn(ResponseEntity.ok(output()));
        mvc.perform(signed(identity().getAuthContext())).andExpect(status().isOk());
        mvc.perform(signed(identity().getAuthContext().toBuilder().sessionId(null).build())).andExpect(status().isOk());
        verify(service, times(2)).generate(any(), any());
    }

    @Test void rejectsConflictingSessionPriorTurnAndOtherSessionBeforeInference() throws Exception {
        for (var binding : List.of("authoring:other:turn-1", "authoring:session-1:prior-turn", "session-1")) {
            mvc.perform(signed(identity().getAuthContext().toBuilder().sessionId(binding).build())).andExpect(status().isForbidden());
            mvc.perform(signed(identity().getAuthContext().toBuilder().sessionId(null).subjectId(binding).build())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void rejectsUntrustedIssuerAudienceAndMissingBackendKey() throws Exception {
        mvc.perform(signed(identity().getAuthContext().toBuilder().issuer("rogue").build())).andExpect(status().isUnauthorized());
        mvc.perform(signed(identity().getAuthContext().toBuilder().audiences(List.of("customer-serving-runtime")).build()))
            .andExpect(status().isUnauthorized());
        var signed = new RuntimePrivateAssertionService(properties).issueAssertion(identity().getAuthContext());
        mvc.perform(base().header(properties.getIngress().getPrivateAssertions().getAuthorizationHeader(), "Bearer " + signed))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void bodyBytesAndFieldLimitsAreEnforcedBeforeInference() throws Exception {
        mvc.perform(signed(identity().getAuthContext()).content(" ".repeat(AuthoringJsonContract.MAX_INPUT_BYTES + 1)))
            .andExpect(status().isPayloadTooLarge());
        var invalid = MAPPER.valueToTree(request());
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).put("query", "x".repeat(8_001));
        mvc.perform(signed(identity().getAuthContext()).content(invalid.toString())).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void requestRequiresOneWholeUnambiguousJsonObject() throws Exception {
        String valid = MAPPER.writeValueAsString(request());
        mvc.perform(signed(identity().getAuthContext()).content(valid + " {}"))
            .andExpect(status().isBadRequest());
        mvc.perform(signed(identity().getAuthContext()).content("{\"sessionId\":\"other\"," + valid.substring(1)))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void routeIsAbsentByDefaultAndOptInCannotStartWithoutGuardedAccounting() {
        new ApplicationContextRunner().withUserConfiguration(ConfigurationAuthoringConfiguration.class, ConfigurationAuthoringController.class)
            .run(context -> {
                assertThat(context).doesNotHaveBean(ConfigurationAuthoringController.class);
                assertThat(context).doesNotHaveBean(ConfigurationAuthoringService.class);
            });
        new ApplicationContextRunner().withUserConfiguration(ConfigurationAuthoringConfiguration.class)
            .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> MAPPER)
            .withBean(ai.fabric.core.AICoreService.class, () -> mock(ai.fabric.core.AICoreService.class))
            .withBean(ai.fabric.llm.structured.StructuredJsonCallExecutor.class, () -> mock(ai.fabric.llm.structured.StructuredJsonCallExecutor.class))
            .withBean(RuntimeBudgetBindingService.class, () -> mock(RuntimeBudgetBindingService.class))
            .withBean(RuntimeBudgetTurnScopes.class, () -> mock(RuntimeBudgetTurnScopes.class))
            .withPropertyValues("loomai.authoring.enabled=true").run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasMessageContaining("BudgetGuardedSpringAiModelResolver");
            });
    }

    @Test void noUnboundedDeadlineOrOutputConfiguration() {
        assertThatThrownBy(() -> new ConfigurationAuthoringService(null, null, MAPPER, null, null, null, 0, 4096))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ConfigurationAuthoringService(null, null, MAPPER, null, null, null, 45000, 100_000))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private MockHttpServletRequestBuilder base() throws Exception {
        return post(ConfigurationAuthoringController.PATH).contentType("application/json").content(MAPPER.writeValueAsBytes(request()));
    }
    private MockHttpServletRequestBuilder signed(RuntimeAuthContext context) throws Exception {
        return base().header(properties.getIngress().getTrustedBackend().getApiKeyHeader(), "runtime-secret")
            .header(properties.getIngress().getPrivateAssertions().getAuthorizationHeader(),
                "Bearer " + new RuntimePrivateAssertionService(properties).issueAssertion(context));
    }
}
