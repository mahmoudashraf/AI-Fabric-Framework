package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import java.util.List;
import static com.ai.fabric.platform.backend.deployment.admission.PostProvisionResumeContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PostProvisionResumeControllerTest.Config.class)
class PostProvisionResumeControllerTest {
    @Autowired PostProvisionResumeController controller;
    @Autowired PostProvisionResumeService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(service); }
    @Test void anonymousCannotAuthorizeDependencyProvisioning() {
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "r", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controller.resume("offer", "deployment", "r", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_OPERATOR", "CUSTOMER_VIEWER"})
    void continuationHasNoStaffFallback(String role) {
        authenticate(role);
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "r", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.resume("offer", "deployment", "r", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    @Test void ownerUsesExactScopeAndDependencyAcceptance() {
        authenticate("CUSTOMER_ADMIN"); var request = new PlanRequest("release", "operation", "Continue setup", true);
        controller.plan("offer", "deployment", "r", request); verify(service).plan("offer", "deployment", "r", request);
        var command = new Command(new AdmissionContracts.Fence("r", 9, "g"), "release", "operation", "Continue", true, "a".repeat(64));
        controller.resume("offer", "deployment", "r", command); verify(service).resume("offer", "deployment", "r", command);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"fence\":null}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":\"9\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":9.1}}"})
    void revisionCannotBeMissingOrCoerced(String body) {
        assertThatThrownBy(() -> new ObjectMapper().readValue(body, Command.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean PostProvisionResumeService service() { return mock(PostProvisionResumeService.class); }
        @Bean PostProvisionResumeController controller(PostProvisionResumeService service) { return new PostProvisionResumeController(service); }
    }
}
