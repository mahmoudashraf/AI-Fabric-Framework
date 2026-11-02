package com.ai.fabric.platform.backend.customerselfservice;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerExistingVectorBindingService;
import com.ai.fabric.platform.backend.customerselfservice.web.CustomerExistingVectorBindingController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CustomerExistingVectorBindingControllerTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"VIEWER", "PLATFORM_OPERATOR", "PUBLIC_API_CLIENT", "ANONYMOUS"})
    void otherRolesCannotReachCustomerBindingFacade(String role) {
        authenticate(role);
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(CustomerExistingVectorBindingController.class);
            assertThatThrownBy(() -> controller.get("dep", "target")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.bind("dep", "target", null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(CustomerExistingVectorBindingService.class));
        }
    }

    @Test void customerRoleReachesOwnedDeploymentFacadeNotTheAdminController() {
        authenticate("CUSTOMER_ADMIN");
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(CustomerExistingVectorBindingController.class);
            controller.get("dep", "target"); controller.bind("dep", "target", null);
            var service = context.getBean(CustomerExistingVectorBindingService.class);
            verify(service).get("dep", "target"); verify(service).bind("dep", "target", null);
        }
    }

    @Test void malformedCredentialsProduceOnlySanitizedErrorCode() throws Exception {
        var service = mock(CustomerExistingVectorBindingService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new CustomerExistingVectorBindingController(service))
            .setControllerAdvice(new CustomerExistingVectorBindingController.InvalidRequestHandler()).build();
        mvc.perform(post("/api/customer-self-service/deployments/dep/targets/target/existing-vector-binding")
            .contentType("application/json").content("{\"authType\":\"synthetic-private-value\",\"token\":\"synthetic-serving-token\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("EXTERNAL_VECTOR_REQUEST_INVALID"))
            .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("synthetic"))));
        verifyNoInteractions(service);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration @EnableMethodSecurity static class Config {
        @Bean CustomerExistingVectorBindingService service() { return mock(CustomerExistingVectorBindingService.class); }
        @Bean CustomerExistingVectorBindingController controller(CustomerExistingVectorBindingService service) {
            return new CustomerExistingVectorBindingController(service);
        }
    }
}
