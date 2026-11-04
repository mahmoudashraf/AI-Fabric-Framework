package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService.CustomerActor;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerEnvironmentQuotaController;
import com.ai.fabric.platform.backend.deployment.service.CustomerEnvironmentQuotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(CustomerEnvironmentQuotaControllerTest.Config.class)
@WebAppConfiguration
class CustomerEnvironmentQuotaControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired CustomerOfferEntitlementService access;
    @Autowired CustomerEnvironmentQuotaService quotas;
    private MockMvc mvc;
    private static final String MINE="/api/customer-self-service/infrastructure-quota";
    private static final String STAFF="/api/customer-self-service/staff/customers/owner/infrastructure-quota";

    @BeforeEach void setup() {
        reset(access, quotas);
        mvc=webAppContextSetup(context).apply(springSecurity()).build();
        when(access.requireCustomerActor()).thenReturn(new CustomerActor("owner","user"));
        when(quotas.summary("owner")).thenReturn(new CustomerEnvironmentQuotaService.Summary("owner",2,0,2,List.of()));
    }

    @Test void customerScopeComesFromIdentityNotRequestParameters() throws Exception {
        mvc.perform(get(MINE).param("customerId","other").with(user("owner").roles("CUSTOMER_ADMIN")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.customerId").value("owner"));
        verify(quotas).summary("owner"); verify(quotas,never()).summary("other");
    }

    @Test void customerCannotChangeAllowanceAndUnauthenticatedCannotReadIt() throws Exception {
        mvc.perform(get(MINE)).andExpect(status().isForbidden());
        for (String role : List.of("CUSTOMER_ADMIN","PLATFORM_OPERATOR","PUBLIC_API_CLIENT")) {
            mvc.perform(put(STAFF).with(user("caller").roles(role)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"limit\":50,\"reason\":\"Increase\"}")).andExpect(status().isForbidden());
            mvc.perform(get(STAFF).with(user("caller").roles(role))).andExpect(status().isForbidden());
        }
        verifyNoInteractions(quotas);
    }

    @Test void staffAdminCanSetAnAuditedExplicitLimit() throws Exception {
        when(quotas.updateLimit("owner",4,"Customer agreement")).thenReturn(new CustomerEnvironmentQuotaService.Summary("owner",4,0,4,List.of()));
        mvc.perform(put(STAFF).with(user("admin").roles("PLATFORM_ADMIN")).contentType(MediaType.APPLICATION_JSON)
            .content("{\"limit\":4,\"reason\":\"Customer agreement\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(4));
        verify(access).requireStaffAdmin();
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean CustomerOfferEntitlementService access() { return mock(CustomerOfferEntitlementService.class); }
        @Bean CustomerEnvironmentQuotaService quotas() { return mock(CustomerEnvironmentQuotaService.class); }
        @Bean CustomerEnvironmentQuotaController controller(CustomerOfferEntitlementService a, CustomerEnvironmentQuotaService q) { return new CustomerEnvironmentQuotaController(a,q); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return http.csrf(csrf->csrf.disable()).authorizeHttpRequests(r->r.anyRequest().authenticated()).build(); }
    }
}
