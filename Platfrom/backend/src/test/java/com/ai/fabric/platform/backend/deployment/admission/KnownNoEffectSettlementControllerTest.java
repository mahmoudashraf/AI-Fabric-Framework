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

import static com.ai.fabric.platform.backend.deployment.admission.KnownNoEffectSettlementContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(KnownNoEffectSettlementControllerTest.Config.class)
class KnownNoEffectSettlementControllerTest {
    @Autowired KnownNoEffectSettlementController controller;
    @Autowired KnownNoEffectSettlementService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(service); }

    @Test void anonymousCannotPlanOrSettle() {
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "reservation", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controller.settle("offer", "deployment", "reservation", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(service);
    }

    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_OPERATOR", "CUSTOMER_VIEWER"})
    void noStaffFallbackForTheOwnerOnlyAction(String role) {
        authenticate(role);
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "reservation", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.settle("offer", "deployment", "reservation", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }

    @Test void customerRoleForwardsExactScopeAndCommandToNormalSessionService() {
        authenticate("CUSTOMER_ADMIN"); var request = new PlanRequest("explicit-key", "Review cleanup");
        controller.plan("offer", "deployment", "reservation", request);
        verify(service).plan("offer", "deployment", "reservation", request);
        controller.settle("offer", "deployment", "reservation", null);
        verify(service).settle("offer", "deployment", "reservation", null);
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"fence\":null}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":\"0\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":0.5}}"})
    void missingOrCoercedFenceIsNotAnImplicitRevisionZero(String body) {
        assertThatThrownBy(() -> new ObjectMapper().readValue(body, Command.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }

    private void authenticate(String role) { SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", "unused", List.of(new SimpleGrantedAuthority("ROLE_" + role)))); }
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean KnownNoEffectSettlementService service() { return mock(KnownNoEffectSettlementService.class); }
        @Bean KnownNoEffectSettlementController controller(KnownNoEffectSettlementService service) { return new KnownNoEffectSettlementController(service); }
    }
}
