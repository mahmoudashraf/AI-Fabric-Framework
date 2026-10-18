package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.web.DeploymentExistingVectorBindingController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExistingVectorBindingSecurityTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings = {"CUSTOMER_ADMIN", "PLATFORM_OPERATOR", "VIEWER", "ANONYMOUS"})
    void nonAdminCannotReadOrWriteBinding(String role) {
        authenticate(role);
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(DeploymentExistingVectorBindingController.class);
            assertThatThrownBy(() -> controller.get("dep", "target")).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> controller.bind("dep", "target", null)).isInstanceOf(AccessDeniedException.class);
            verifyNoInteractions(context.getBean(DeploymentExistingVectorBindingService.class));
        }
    }

    @Test void platformAdminCanInvokeReviewedBindingService() {
        authenticate("PLATFORM_ADMIN");
        try (var context = new AnnotationConfigApplicationContext(Config.class)) {
            var controller = context.getBean(DeploymentExistingVectorBindingController.class);
            controller.get("dep", "target"); controller.bind("dep", "target", null);
            var service = context.getBean(DeploymentExistingVectorBindingService.class);
            verify(service).get("dep", "target"); verify(service).bind("dep", "target", null);
        }
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration @EnableMethodSecurity static class Config {
        @Bean DeploymentExistingVectorBindingService service() { return mock(DeploymentExistingVectorBindingService.class); }
        @Bean DeploymentExistingVectorBindingController controller(DeploymentExistingVectorBindingService service) {
            return new DeploymentExistingVectorBindingController(service);
        }
    }
}
