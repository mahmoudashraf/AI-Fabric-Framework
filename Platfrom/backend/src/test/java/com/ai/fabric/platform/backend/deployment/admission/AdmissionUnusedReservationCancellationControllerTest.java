package com.ai.fabric.platform.backend.deployment.admission;

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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(AdmissionUnusedReservationCancellationControllerTest.Config.class)
class AdmissionUnusedReservationCancellationControllerTest {
    @Autowired AdmissionUnusedReservationCancellationController controller;
    @Autowired Config config;

    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(config.service); }

    @Test void anonymousCannotCancel() {
        assertThatThrownBy(() -> controller.cancel("deployment", "target", null))
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(config.service);
    }

    @Test void customerAdministratorCannotCancel() {
        authenticate("CUSTOMER_ADMIN");
        assertThatThrownBy(() -> controller.cancel("deployment", "target", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(config.service);
    }

    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_OPERATOR"})
    void staffForwardsExactScopeWithoutCreatingItsOwnEvidence(String role) {
        authenticate(role);
        controller.cancel("deployment", "target", null);
        verify(config.service).cancelUnusedReservation("deployment", "target", null);
    }

    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "synthetic-staff", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        final DeploymentAdmissionService service = mock(DeploymentAdmissionService.class);
        @Bean DeploymentAdmissionService admissions() { return service; }
        @Bean AdmissionUnusedReservationCancellationController controller(DeploymentAdmissionService admissions) {
            return new AdmissionUnusedReservationCancellationController(admissions);
        }
    }
}
