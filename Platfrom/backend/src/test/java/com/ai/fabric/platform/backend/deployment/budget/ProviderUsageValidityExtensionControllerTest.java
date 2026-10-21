package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageAllowanceContracts.*;
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageValidityExtensionContracts.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(ProviderUsageValidityExtensionControllerTest.Config.class)
@WebAppConfiguration
class ProviderUsageValidityExtensionControllerTest {
    static final String ROUTE = "/api/deployments/provider-usage/validity-extensions";
    @Autowired WebApplicationContext context;
    @Autowired ProviderUsageValidityExtensionService service;
    ProviderUsageValidityExtensionService target;
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final ReviewRequest review = new ReviewRequest("extension", new AllocationScope("approval", "customer", "deployment", "target", "staging"),
        "release", "a".repeat(64), UUID.randomUUID(), Instant.parse("2026-10-20T12:00:00Z"), Instant.parse("2026-10-21T00:00:00Z"), "Explicit review");
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        target = org.springframework.test.util.AopTestUtils.getTargetObject(service);
        reset(target); mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void onlyAdminMayReviewExecuteReadOrExplicitlyReconcile() throws Exception {
        for (var role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(review)), role)).andExpect(status().isForbidden());
            mvc.perform(as(post(ROUTE + "/extension/execute").contentType("application/json").content("{}"), role)).andExpect(status().isForbidden());
            mvc.perform(as(get(ROUTE + "/extension"), role)).andExpect(status().isForbidden());
            mvc.perform(as(post(ROUTE + "/extension/reconcile"), role)).andExpect(status().isForbidden());
        }
        mvc.perform(get(ROUTE + "/extension")).andExpect(status().isForbidden());
        verifyNoInteractions(target);
        mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(review)), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        verify(target).plan(review);
    }

    @Test void exactReturnedCommandAndExplicitReconciliationUseOnlyTheirNormalServiceMethods() throws Exception {
        var body = new RuntimeCommand(1, review.extensionId(), "approval", review.storeUuid(), "b".repeat(64),
            review.expectedEffectiveExpiresAt(), review.expiresAt(), review.reason());
        var command = new Command(review, body, "https://synthetic.invalid", "c".repeat(64), "e".repeat(64), "d".repeat(64));
        mvc.perform(as(post(ROUTE + "/extension/execute").contentType("application/json").content(mapper.writeValueAsString(command)), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk());
        mvc.perform(as(get(ROUTE + "/extension"), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        mvc.perform(as(post(ROUTE + "/extension/reconcile"), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        verify(target).execute("extension", command); verify(target).read("extension"); verify(target).reconcile("extension");
        verifyNoMoreInteractions(target);
    }

    @Test void strictReviewRejectsUnknownDuplicateCoercedMissingAndCreditFieldsBeforeService() throws Exception {
        String good = mapper.writeValueAsString(review);
        for (String body : List.of(good.replaceFirst("\\{", "{\"credits\":100,"),
            good.replace("\"extensionId\":", "\"extensionId\":\"different\",\"extensionId\":"),
            good.replace("\"reason\":\"Explicit review\"", "\"reason\":123"),
            good.replace("\"storeUuid\":\"" + review.storeUuid() + "\"", "\"storeUuid\":null"),
            good.replace("\"approvalId\":\"approval\"", "\"approvalId\":null"), "null", good + " {}"))
            mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isBadRequest());
        verifyNoInteractions(target);
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, PlatformRole role) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal("admin", role, "test", "SESSION"), null,
            List.of(new SimpleGrantedAuthority(role.authority())))));
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean ProviderUsageValidityExtensionService service() { return mock(ProviderUsageValidityExtensionService.class); }
        @Bean ProviderUsageValidityExtensionController controller(ProviderUsageValidityExtensionService service, ObjectMapper mapper) {
            return new ProviderUsageValidityExtensionController(service, mapper);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
