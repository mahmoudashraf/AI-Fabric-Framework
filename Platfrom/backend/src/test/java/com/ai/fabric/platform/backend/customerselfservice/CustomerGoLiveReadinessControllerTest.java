package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerGoLiveReadinessContracts.Summary;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerGoLiveReadinessService;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerGoLiveReadinessController;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
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
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(CustomerGoLiveReadinessControllerTest.Config.class)
@WebAppConfiguration
class CustomerGoLiveReadinessControllerTest {
    private static final String ROUTE = "/api/customer-self-service/catalogue/offer/deployments/deployment/go-live-readiness";

    @Autowired WebApplicationContext context;
    @Autowired CustomerGoLiveReadinessService service;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        reset(service);
        mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void customerOwnerReceivesOnlyTheExactCandidateProjection() throws Exception {
        var projectedAt = Instant.parse("2026-10-16T00:00:00Z");
        when(service.project("offer", "deployment", "release", "consumer")).thenReturn(new Summary(
            "deployment", "offer", "version", "release", "production-target", "candidate",
            "consumer", 4, true, false, List.of(), List.of(), projectedAt));

        mvc.perform(get(ROUTE).param("releaseId", "release").param("consumerId", "consumer")
                .with(auth(PlatformRole.CUSTOMER_ADMIN)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deploymentId").value("deployment"))
            .andExpect(jsonPath("$.releaseId").value("release"))
            .andExpect(jsonPath("$.consumerId").value("consumer"))
            .andExpect(jsonPath("$.candidateFingerprint").value("candidate"))
            .andExpect(jsonPath("$.activationReady").value(true));
        verify(service).project("offer", "deployment", "release", "consumer");
    }

    @Test
    void staffPublicAndUnauthenticatedCallersCannotReadCustomerReadiness() throws Exception {
        for (PlatformRole role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR,
            PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(get(ROUTE).param("releaseId", "release").param("consumerId", "consumer")
                    .with(auth(role)))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get(ROUTE).param("releaseId", "release").param("consumerId", "consumer"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor auth(PlatformRole role) {
        return authentication(new UsernamePasswordAuthenticationToken(new PlatformPrincipal(
            "owner@test.invalid", role, "Owner", "SESSION"), null,
            List.of(new SimpleGrantedAuthority(role.authority()))));
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class Config {
        @Bean CustomerGoLiveReadinessService service() { return mock(CustomerGoLiveReadinessService.class); }
        @Bean CustomerGoLiveReadinessController controller(CustomerGoLiveReadinessService service) {
            return new CustomerGoLiveReadinessController(service);
        }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
