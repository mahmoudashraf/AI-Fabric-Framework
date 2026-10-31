package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.service.CustomerAccountRecoveryService;
import com.ai.fabric.platform.backend.security.web.CustomerAccountRecoveryController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerAccountRecoveryControllerTest {
    @Test void emptyDedicatedOriginAllowlistFailsBeforeConsumingAnEmailToken() {
        var service = mock(CustomerAccountRecoveryService.class);
        var properties = new CustomerSupabaseAuthProperties(true, "https://auth.example.test", "public", null, null, null, List.of());
        var controller = new CustomerAccountRecoveryController(properties, service);
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        request.addHeader(HttpHeaders.ORIGIN, "https://console.example.test");
        assertThatThrownBy(() -> controller.recover(new CustomerAccountRecoveryController.RecoveryRequest("proof", "owner@example.test"), request, response))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        verifyNoInteractions(service);
    }

    @Test void exactOriginDelegatesOnlySubmittedProofAndEmailAndDoesNotSetALegacyCookie() {
        var service = mock(CustomerAccountRecoveryService.class);
        var properties = new CustomerSupabaseAuthProperties(true, "https://auth.example.test", "public", null, null, null,
            List.of(), List.of("https://console.example.test"));
        var controller = new CustomerAccountRecoveryController(properties, service);
        var request = new MockHttpServletRequest(); var response = new MockHttpServletResponse();
        request.addHeader(HttpHeaders.ORIGIN, "https://console.example.test");
        var session = new CustomerAccountRecoveryService.RecoverySession("access", "refresh", 123, "subject");
        when(service.recover("opaque-proof", "Confirmed@example.test")).thenReturn(session);
        assertThat(controller.recover(new CustomerAccountRecoveryController.RecoveryRequest("opaque-proof", "Confirmed@example.test"), request, response))
            .isSameAs(session);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getCookies()).isEmpty();
        verify(service).recover("opaque-proof", "Confirmed@example.test"); verifyNoMoreInteractions(service);
    }
}
