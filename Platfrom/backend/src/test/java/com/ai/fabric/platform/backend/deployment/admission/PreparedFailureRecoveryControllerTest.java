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
import static com.ai.fabric.platform.backend.deployment.admission.PreparedFailureRecoveryContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(PreparedFailureRecoveryControllerTest.Config.class)
class PreparedFailureRecoveryControllerTest {
    @Autowired PreparedFailureRecoveryController controller;
    @Autowired PreparedFailureRecoveryService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(service); }

    @Test void anonymousCannotPlanOrRecover() {
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "r", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controller.recover("offer", "deployment", "r", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_OPERATOR", "CUSTOMER_VIEWER"})
    void ownerRecoveryHasNoStaffFallback(String role) {
        authenticate(role);
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "r", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.recover("offer", "deployment", "r", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    @Test void ownerUsesExactScopeAndCommand() {
        authenticate("CUSTOMER_ADMIN"); var request = new PlanRequest("release", "operation", "Repair pre-native failure");
        controller.plan("offer", "deployment", "r", request); verify(service).plan("offer", "deployment", "r", request);
        var command = new Command(new AdmissionContracts.Fence("r", 2, "g"), "release", "operation", "Repair", "a".repeat(64));
        controller.recover("offer", "deployment", "r", command); verify(service).recover("offer", "deployment", "r", command);
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"fence\":null}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":\"2\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":2.1}}"})
    void fenceCannotBeMissingOrCoerced(String body) {
        assertThatThrownBy(() -> new ObjectMapper().readValue(body, Command.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean PreparedFailureRecoveryService service() { return mock(PreparedFailureRecoveryService.class); }
        @Bean PreparedFailureRecoveryController controller(PreparedFailureRecoveryService service) { return new PreparedFailureRecoveryController(service); }
    }
}
