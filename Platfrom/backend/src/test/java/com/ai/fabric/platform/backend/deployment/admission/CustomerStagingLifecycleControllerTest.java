package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerSelfServiceExceptionHandler;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig(CustomerStagingLifecycleControllerTest.Config.class)
@WebAppConfiguration
class CustomerStagingLifecycleControllerTest {
    @Autowired WebApplicationContext context;
    @Autowired CustomerStagingLifecycleService service;

    @Test
    void endpointIsCustomerOnlyAndRejectsProviderOverrides() throws Exception {
        var mvc = webAppContextSetup(context).apply(springSecurity()).build();
        String route = "/api/customer-self-service/catalogue/offer/deployments/deployment/staging-lifecycle/extensions";
        String body = "{\"targetProfileId\":\"target\",\"allocationId\":\"allocation\",\"expectedRevision\":0,"
            + "\"operationKey\":\"extend\",\"reason\":\"Continue preview\",\"keepAfterPromotion\":false}";
        for (var role : List.of(PlatformRole.PLATFORM_ADMIN, PlatformRole.PLATFORM_OPERATOR, PlatformRole.PUBLIC_API_CLIENT)) {
            mvc.perform(post(route).contentType("application/json").content(body).with(authentication(new UsernamePasswordAuthenticationToken(
                new PlatformPrincipal("staff", role, "Staff", "SESSION"), null, List.of(new SimpleGrantedAuthority(role.authority()))))))
                .andExpect(status().isForbidden());
        }
        var customer = new UsernamePasswordAuthenticationToken(new PlatformPrincipal("owner", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION"),
            null, List.of(new SimpleGrantedAuthority(PlatformRole.CUSTOMER_ADMIN.authority())));
        mvc.perform(post(route).contentType("application/json").content(body).with(authentication(customer))).andExpect(status().isOk());
        mvc.perform(post(route).contentType("application/json").content(body.substring(0, body.length() - 1) + ",\"providerToken\":\"x\"}")
            .with(authentication(customer))).andExpect(status().isBadRequest());
    }

    @org.springframework.boot.test.context.TestConfiguration
    @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean CustomerStagingLifecycleService service() { return mock(CustomerStagingLifecycleService.class); }
        @Bean CustomerStagingLifecycleController controller(CustomerStagingLifecycleService service) {
            return new CustomerStagingLifecycleController(service, new CustomerOfferCodec(new ObjectMapper()));
        }
        @Bean CustomerSelfServiceExceptionHandler errors() { return new CustomerSelfServiceExceptionHandler(); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable()).authorizeHttpRequests(requests -> requests.anyRequest().authenticated()).build();
        }
    }
}
