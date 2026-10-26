package com.ai.fabric.platform.backend.security;

import com.ai.fabric.platform.backend.config.CustomerSupabaseAuthProperties;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseIdentityService;
import com.ai.fabric.platform.backend.security.web.CustomerSupabaseAuthController;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class CustomerSupabaseAuthControllerTest {
    @Test void linkingFailsClosedWhenDedicatedConsoleOriginsAreNotConfigured() {
        var properties = new CustomerSupabaseAuthProperties(true, "https://auth.example.test", null,
            null, null, null, List.of());
        var identities = mock(CustomerSupabaseIdentityService.class);
        var controller = new CustomerSupabaseAuthController(properties, identities);
        var request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ORIGIN, "https://console.example.test");

        assertThatThrownBy(() -> controller.link(new CustomerSupabaseAuthController.LinkRequest("native-token"), request))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> org.assertj.core.api.Assertions.assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(identities);
    }
}
