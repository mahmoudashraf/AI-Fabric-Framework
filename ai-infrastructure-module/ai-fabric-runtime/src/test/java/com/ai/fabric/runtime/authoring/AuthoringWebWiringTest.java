package com.ai.fabric.runtime.authoring;

import static com.ai.fabric.runtime.authoring.AuthoringTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import ai.fabric.core.AICoreService;
import ai.fabric.dto.AIGenerationResponse;
import ai.fabric.llm.structured.DefaultStructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonCallExecutor;
import ai.fabric.llm.structured.StructuredJsonExtractor;
import com.ai.fabric.budget.ProviderBudgetLedger.ChatReceipt;
import com.ai.fabric.budget.openai.BudgetGuardedSpringAiModelResolver;
import com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport;
import com.ai.fabric.runtime.auth.RuntimePrivateAssertionService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.budget.RuntimeBudgetBindingService;
import com.ai.fabric.runtime.budget.RuntimeBudgetTurnScopes;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

class AuthoringWebWiringTest {
    @Test void enabledMvcWiringPreservesJackson2DynamicValuesWithoutReplacingOtherConverters() throws Exception {
        var properties = RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
        properties.getIngress().setAcceptedAudiences(List.of("dedicated-authoring"));
        var budgets = mock(RuntimeBudgetBindingService.class);
        when(budgets.recordVerifiedAuthoringIngress(any())).thenReturn(new ChatReceipt(UUID.randomUUID(), 1, true));
        var core = mock(AICoreService.class);
        when(core.generateContent(any(), any())).thenReturn(AIGenerationResponse.builder().status("SUCCESS").content(output().toString()).build());
        new WebApplicationContextRunner()
            .withUserConfiguration(DelegatingWebMvcConfiguration.class, ConfigurationAuthoringConfiguration.class,
                ConfigurationAuthoringController.class, AuthoringBodyLimitAdvice.class)
            .withBean(ObjectMapper.class, () -> MAPPER)
            .withBean(AICoreService.class, () -> core)
            .withBean(StructuredJsonCallExecutor.class, () -> new DefaultStructuredJsonCallExecutor(new StructuredJsonExtractor(), MAPPER))
            .withBean(RuntimeBudgetBindingService.class, () -> budgets)
            .withBean(RuntimeBudgetTurnScopes.class, () -> new RuntimeBudgetTurnScopes(budgets))
            .withBean(BudgetGuardedSpringAiModelResolver.class, () -> mock(BudgetGuardedSpringAiModelResolver.class))
            .withBean(RuntimeRequestAuthResolver.class, () -> new RuntimeRequestAuthResolver(properties))
            .withPropertyValues("loomai.authoring.enabled=true")
            .run(context -> {
                assertThat(context).hasNotFailed();
                var adapter = context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class);
                assertThat(adapter.getMessageConverters().getFirst()).isInstanceOf(AuthoringHttpMessageConverter.class);
                assertThat(adapter.getMessageConverters()).anyMatch(converter -> converter.canRead(java.util.Map.class, MediaType.APPLICATION_JSON));
                var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                var result = mvc.perform(post(ConfigurationAuthoringController.PATH).contentType("application/json")
                    .content(MAPPER.writeValueAsBytes(request()))
                    .header(properties.getIngress().getTrustedBackend().getApiKeyHeader(), "runtime-secret")
                    .header(properties.getIngress().getPrivateAssertions().getAuthorizationHeader(),
                        "Bearer " + new RuntimePrivateAssertionService(properties).issueAssertion(identity().getAuthContext())))
                    .andReturn().getResponse();
                assertThat(result.getStatus()).isEqualTo(200);
                var body = MAPPER.readTree(result.getContentAsString());
                assertThat(body.path("changes")).isEqualTo(output().path("changes"));
                assertThat(body.path("questions")).isEqualTo(output().path("questions"));
                assertThat(body.path("runtime").path("accountingReceipt").path("newlyCounted").asBoolean()).isTrue();
            });
    }
}
