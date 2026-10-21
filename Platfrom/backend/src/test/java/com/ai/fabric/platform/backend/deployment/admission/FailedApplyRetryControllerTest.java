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
import static com.ai.fabric.platform.backend.deployment.admission.FailedApplyRetryContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(FailedApplyRetryControllerTest.Config.class)
class FailedApplyRetryControllerTest {
    @Autowired FailedApplyRetryController controller;
    @Autowired FailedApplyRetryService service;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(service); }
    @Test void anonymousCannotAuthorizeNativeJobs() {
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "r", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        assertThatThrownBy(() -> controller.retry("offer", "deployment", "r", null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(service);
    }
    @ParameterizedTest @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_OPERATOR", "CUSTOMER_VIEWER"})
    void retryHasNoStaffFallback(String role) {
        authenticate(role);
        assertThatThrownBy(() -> controller.plan("offer", "deployment", "r", null)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> controller.retry("offer", "deployment", "r", null)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service);
    }
    @Test void ownerUsesExactScopeAndRedeployAcceptance() {
        authenticate("CUSTOMER_ADMIN"); var request = new PlanRequest("release", "operation", "Retry owned services", true);
        controller.plan("offer", "deployment", "r", request); verify(service).plan("offer", "deployment", "r", request);
        var command = new Command(new AdmissionContracts.Fence("r", 6, "g"), "release", "operation", "Retry", true, "a".repeat(64));
        controller.retry("offer", "deployment", "r", command); verify(service).retry("offer", "deployment", "r", command);
    }

    @Test void authenticatedReadbackConflictIsNotRedispatchedAsAnUnauthorizedResponse() throws Exception {
        authenticate("CUSTOMER_ADMIN");
        when(service.plan(eq("offer"), eq("deployment"), eq("r"), any()))
            .thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "FAILED_APPLY_RETRY_GENERATION_READBACK_UNPROVEN", new IllegalStateException("RUNTIME_GENERATION_READBACK_INVALID")));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new com.ai.fabric.platform.backend.web.PlatformApiExceptionHandler()).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                "/api/customer-self-service/catalogue/offer/deployments/deployment/admission-reservations/r/failed-apply-retry-plan")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"releaseId\":\"release\",\"operationKey\":\"retry\",\"reason\":\"Retry\",\"acceptOwnedServiceRedeploy\":true}"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                .value("FAILED_APPLY_RETRY_GENERATION_READBACK_UNPROVEN"));
    }
    @ParameterizedTest @ValueSource(strings = {"{}", "{\"fence\":null}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":\"6\"}}",
        "{\"fence\":{\"reservationId\":\"r\",\"generation\":\"g\",\"revision\":6.1}}"})
    void revisionCannotBeMissingOrCoerced(String body) {
        assertThatThrownBy(() -> new ObjectMapper().readValue(body, Command.class)).isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
    private void authenticate(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("fixture", "unused",
            List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }
    @Configuration @EnableMethodSecurity(proxyTargetClass = true)
    static class Config {
        @Bean FailedApplyRetryService service() { return mock(FailedApplyRetryService.class); }
        @Bean FailedApplyRetryController controller(FailedApplyRetryService service) { return new FailedApplyRetryController(service); }
    }
}
