package com.ai.fabric.runtime.tasks;

import static com.ai.fabric.runtime.tasks.ManagedTaskFixtures.*;
import static com.ai.fabric.runtime.tasks.ManagedTaskServiceTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import com.ai.fabric.runtime.RuntimePrivateAssertionTestSupport;
import com.ai.fabric.runtime.auth.RuntimePrivateAssertionService;
import com.ai.fabric.runtime.auth.RuntimeRequestAuthResolver;
import com.ai.fabric.runtime.config.RuntimeAuthProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

class ManagedTaskWebTest {
    @Test void realMvcPreservesJsonOutputAndPrivateAuthenticationAndRejectsOverrides() throws Exception {
        try (var h = new Harness(fixture())) {
            var properties = RuntimePrivateAssertionTestSupport.strictPrivateRuntimeProperties();
            new WebApplicationContextRunner()
                .withUserConfiguration(DelegatingWebMvcConfiguration.class, ManagedTaskConfiguration.class,
                    ManagedTaskController.class, ManagedTaskBodyLimitAdvice.class)
                .withBean(com.fasterxml.jackson.databind.ObjectMapper.class, () -> MAPPER)
                .withBean(ManagedTaskService.class, () -> h.service)
                .withBean(RuntimeRequestAuthResolver.class, () -> new RuntimeRequestAuthResolver(properties))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var mvc = MockMvcBuilders.webAppContextSetup(context).build();
                    var execution = signed(post(ManagedTaskController.PATH + "/assess-request/execute"), properties, EXECUTE)
                        .contentType("application/json").content("{\"input\":{\"request\":\"Explain the next step\"}}");
                    var response = mvc.perform(execution).andReturn().getResponse();
                    assertThat(response.getStatus()).isEqualTo(200);
                    var result = MAPPER.readTree(response.getContentAsString());
                    assertThat(result.at("/output/answer").asText()).isEqualTo("A validated answer.");
                    assertThat(result.path("startedAt").isTextual()).isTrue();
                    assertThat(result.path("failure").isNull()).isTrue();
                    clearInvocations(h.budgets, h.client);
                    assertThat(mvc.perform(get(ManagedTaskController.PATH)).andReturn().getResponse().getStatus()).isEqualTo(401);
                    var hidden = mvc.perform(signed(get(ManagedTaskController.PATH), properties, EXECUTE)).andReturn().getResponse();
                    assertThat(MAPPER.readTree(hidden.getContentAsString()).path("tasks")).isEmpty();
                    var visible = mvc.perform(signed(get(ManagedTaskController.PATH), properties, READ)).andReturn().getResponse();
                    assertThat(MAPPER.readTree(visible.getContentAsString()).path("tasks")).hasSize(1);
                    for (String invalid : List.of("{\"input\":{\"request\":\"x\"},\"prompt\":\"override\"}",
                        "{\"input\":{\"request\":\"x\"},\"input\":{}}", "{\"input\":{\"request\":\"x\"}} {}")) {
                        var rejection = mvc.perform(signed(post(ManagedTaskController.PATH + "/assess-request/execute"), properties, EXECUTE)
                            .contentType("application/json").content(invalid)).andReturn().getResponse();
                        assertThat(rejection.getStatus()).as(invalid).isEqualTo(400);
                        assertThat(rejection.getContentAsString()).doesNotContain("override");
                    }
                    var huge = mvc.perform(signed(post(ManagedTaskController.PATH + "/assess-request/execute"), properties, EXECUTE)
                        .contentType("application/json").content("{\"input\":{\"request\":\"" + "x".repeat(65_536) + "\"}}"))
                        .andReturn().getResponse();
                    assertThat(huge.getStatus()).isEqualTo(413);
                    verifyNoInteractions(h.budgets, h.client);
                });
        }
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, RuntimeAuthProperties properties, String scope) {
        return request.header(properties.getIngress().getTrustedBackend().getApiKeyHeader(), "runtime-secret")
            .header(properties.getIngress().getPrivateAssertions().getAuthorizationHeader(),
                "Bearer " + new RuntimePrivateAssertionService(properties).issueAssertion(identity(scope).getAuthContext()));
    }
}
