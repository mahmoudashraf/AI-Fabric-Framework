package com.ai.fabric.platform.backend.deployment.budget;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import static com.ai.fabric.platform.backend.deployment.budget.ProviderUsageTopUpContracts.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(ProviderUsageTopUpControllerTest.Config.class)
@WebAppConfiguration
class ProviderUsageTopUpControllerTest {
    static final String ROUTE = "/api/deployments/provider-usage/top-ups";
    @Autowired WebApplicationContext context;
    @Autowired ProviderUsageTopUpService service;
    ProviderUsageTopUpService target;
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    final ReviewRequest review = new ReviewRequest("top-up", new AllocationScope("approval", "customer", "deployment", "target", "staging"),
        "release", "a".repeat(64), UUID.randomUUID(), new Limits(50, 1000, 2000), new Limits(10, 100, 200),
        "new-owner-funding", "payer", "Additional authorized funding");
    MockMvc mvc;

    @BeforeEach void setup() throws Exception {
        target = org.springframework.test.util.AopTestUtils.getTargetObject(service); reset(target);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void allRoutesRequireAdminAndAnonymousOperatorsAndCustomersCannotReachService() throws Exception {
        for (var role : List.of(PlatformRole.PLATFORM_OPERATOR, PlatformRole.CUSTOMER_ADMIN, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(review)), role)).andExpect(status().isForbidden());
            mvc.perform(as(post(ROUTE + "/top-up/execute").contentType("application/json").content("{}"), role)).andExpect(status().isForbidden());
            mvc.perform(as(get(ROUTE + "/top-up"), role)).andExpect(status().isForbidden());
            mvc.perform(as(post(ROUTE + "/top-up/reconcile"), role)).andExpect(status().isForbidden());
        }
        mvc.perform(get(ROUTE + "/top-up")).andExpect(status().isForbidden()); verifyNoInteractions(target);
    }

    @Test void adminUsesPlanExactExecuteReadAndExplicitReconcile() throws Exception {
        var body = new RuntimeCommand(1, review.topUpId(), "approval", review.storeUuid(), "b".repeat(64),
            review.expectedEffectiveLimits(), review.additionalLimits(), "admin", review.approvalEvidenceRef(), review.payerRef(), review.reason());
        var command = new Command(review, body, "https://synthetic.invalid", "c".repeat(64), "e".repeat(64), "d".repeat(64));
        mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(review)), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        mvc.perform(as(post(ROUTE + "/top-up/execute").contentType("application/json").content(mapper.writeValueAsString(command)), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        mvc.perform(as(get(ROUTE + "/top-up"), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        mvc.perform(as(post(ROUTE + "/top-up/reconcile"), PlatformRole.PLATFORM_ADMIN)).andExpect(status().isOk());
        verify(target).plan(review); verify(target).execute("top-up", command); verify(target).read("top-up"); verify(target).reconcile("top-up");
        verifyNoMoreInteractions(target);
    }

    @Test void strictJsonRejectsExtraDuplicateCoercedMissingFundingAndExpiryResetFields() throws Exception {
        String good = mapper.writeValueAsString(review);
        for (String body : List.of(good.replaceFirst("\\{", "{\"expiresAt\":\"2099-01-01T00:00:00Z\","),
            good.replaceFirst("\\{", "{\"authorizedBy\":\"caller\","), good.replaceFirst("\\{", "{\"resetUsage\":true,"),
            good.replace("\"topUpId\":", "\"topUpId\":\"other\",\"topUpId\":"),
            good.replace("\"reason\":\"Additional authorized funding\"", "\"reason\":123"),
            good.replace("\"chatAttempts\":10", "\"chatAttempts\":\"10\""),
            good.replace("\"chatAttempts\":10", "\"chatAttempts\":10.5"),
            good.replace("\"chatAttempts\":10", "\"chatAttempts\":null"),
            good.replace("\"chatAttempts\":10,", ""),
            good.replace("\"approvalEvidenceRef\":\"new-owner-funding\"", "\"approvalEvidenceRef\":\" \""),
            good.replace("\"payerRef\":\"payer\",", ""),
            good.replace("\"storeUuid\":\"" + review.storeUuid() + "\"", "\"storeUuid\":null"), "null", good + " {}")) {
            mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(body), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(target);
    }

    @Test void perCommandBoundsRequireNonzeroNonnegativeAdditionAndCheckedEffectiveSum() throws Exception {
        for (var limits : List.of(new Limits(0, 0, 0), new Limits(10_001, 0, 0), new Limits(0, 100_000_001, 0), new Limits(0, 0, 100_000_000_001L))) {
            var body = mapper.valueToTree(review); ((com.fasterxml.jackson.databind.node.ObjectNode) body).set("additionalLimits", mapper.valueToTree(limits));
            mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(body)), PlatformRole.PLATFORM_ADMIN))
                .andExpect(status().isBadRequest());
        }
        var overflow = mapper.valueToTree(review);
        ((com.fasterxml.jackson.databind.node.ObjectNode) overflow.path("expectedEffectiveLimits")).put("chatAttempts", Long.MAX_VALUE);
        mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(overflow)), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(target);
        var maximum = mapper.valueToTree(review); ((com.fasterxml.jackson.databind.node.ObjectNode) maximum).set("additionalLimits", mapper.valueToTree(MAX_ADDITION));
        mvc.perform(as(post(ROUTE + "/plan").contentType("application/json").content(mapper.writeValueAsString(maximum)), PlatformRole.PLATFORM_ADMIN))
            .andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request, PlatformRole role) {
        return request.with(authentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal("admin", role, "test", "SESSION"), null,
            List.of(new SimpleGrantedAuthority(role.authority())))));
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean ObjectMapper mapper() { return new ObjectMapper().registerModule(new JavaTimeModule()); }
        @Bean ProviderUsageTopUpService service() { return mock(ProviderUsageTopUpService.class); }
        @Bean ProviderUsageTopUpController controller(ProviderUsageTopUpService service, ObjectMapper mapper) { return new ProviderUsageTopUpController(service, mapper); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
