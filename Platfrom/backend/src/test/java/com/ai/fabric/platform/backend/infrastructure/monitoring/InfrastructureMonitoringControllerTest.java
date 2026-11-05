package com.ai.fabric.platform.backend.infrastructure.monitoring;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(InfrastructureMonitoringControllerTest.Config.class)
@WebAppConfiguration
class InfrastructureMonitoringControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired CustomerOfferEntitlementService access;
    @Autowired InfrastructureMonitoringService monitoring;
    MockMvc mvc;

    @BeforeEach void setup() {
        reset(access, monitoring); mvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void fleetAndDirectHistoryAreAdminOnly() throws Exception {
        for (var path : List.of("/servers", "/servers/staging", "/servers/staging/history")) {
            mvc.perform(get("/api/admin/infrastructure/monitoring" + path)).andExpect(status().isForbidden());
            for (var role : List.of("CUSTOMER_ADMIN", "PLATFORM_OPERATOR", "PUBLIC_API_CLIENT")) {
                mvc.perform(get("/api/admin/infrastructure/monitoring" + path).with(user("caller").roles(role)))
                    .andExpect(status().isForbidden());
            }
        }
        verifyNoInteractions(monitoring, access);
    }

    @Test void activeAdminReadsAreUncachedAndOutagesStayLocalToMonitoring() throws Exception {
        when(monitoring.fleet()).thenReturn(new ObjectMapper().readTree("{\"sources\":[],\"servers\":[]}"));
        mvc.perform(get("/api/admin/infrastructure/monitoring/servers").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        when(monitoring.server("staging")).thenThrow(new MonitoringUnavailableException("MONITORING_SOURCE_UNAVAILABLE"));
        mvc.perform(get("/api/admin/infrastructure/monitoring/servers/staging").with(user("admin").roles("PLATFORM_ADMIN")))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.errorCode").value("MONITORING_SOURCE_UNAVAILABLE"));
        verify(access, times(2)).requireStaffAdmin();
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean CustomerOfferEntitlementService access() { return mock(CustomerOfferEntitlementService.class); }
        @Bean InfrastructureMonitoringService monitoring() { return mock(InfrastructureMonitoringService.class); }
        @Bean InfrastructureMonitoringController controller(InfrastructureMonitoringService m, CustomerOfferEntitlementService a) { return new InfrastructureMonitoringController(m, a); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.csrf(c -> c.disable()).authorizeHttpRequests(r -> r.anyRequest().authenticated()).build(); }
    }
}
