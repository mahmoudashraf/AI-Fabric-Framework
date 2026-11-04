package com.ai.fabric.platform.backend.support;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.ai.fabric.platform.backend.security.service.CustomerSupabaseUserVerifier;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupportNotificationControllerTest {
    private final SupportRequesterService requesters = mock(SupportRequesterService.class);
    private final SupportTicketClient client = mock(SupportTicketClient.class);
    private final CustomerSupabaseUserVerifier verifier = mock(CustomerSupabaseUserVerifier.class);
    private final SupportNotificationController controller = new SupportNotificationController(requesters, client, verifier);
    private final SupportContracts.Requester owner = new SupportContracts.Requester("customer", "user", "https://id.test", "provider-subject");

    @Test void optInUsesProviderVerifiedEmailNotAccountOrFormText() {
        when(requesters.current()).thenReturn(owner);
        when(verifier.requireVerifiedEmail("original-token", "provider-subject")).thenReturn("verified@example.test");
        try (var security = mockStatic(PlatformSecurityContext.class)) {
            security.when(PlatformSecurityContext::currentPrincipal).thenReturn(new PlatformPrincipal("old@example.test", PlatformRole.CUSTOMER_ADMIN, "Owner", "SUPABASE", "user"));
            var preference = new SupportNotificationController.Preference(true, "https://console.test/support");
            controller.update(preference, "Bearer original-token");
            verify(client).notificationPreference(owner, preference, "verified@example.test");
        }
    }

    @Test void sessionOnlyIdentityCannotAttestEmail() {
        when(requesters.current()).thenReturn(owner);
        try (var security = mockStatic(PlatformSecurityContext.class)) {
            security.when(PlatformSecurityContext::currentPrincipal).thenReturn(new PlatformPrincipal("owner@example.test", PlatformRole.CUSTOMER_ADMIN, "Owner", "SESSION", "user"));
            assertThatThrownBy(() -> controller.update(new SupportNotificationController.Preference(true, "https://console.test/support"), null)).isInstanceOf(ResponseStatusException.class);
            verifyNoInteractions(verifier, client);
        }
    }

    @Test void optOutDoesNotNeedProviderAvailabilityOrEmail() {
        when(requesters.current()).thenReturn(owner);
        var preference = new SupportNotificationController.Preference(false, null);
        controller.update(preference, null);
        verify(client).notificationPreference(owner, preference, null);
        verifyNoInteractions(verifier);
    }

    @Test void rejectedProviderIdentityNeverReachesDesk() {
        when(requesters.current()).thenReturn(owner);
        when(verifier.requireVerifiedEmail("token", "provider-subject")).thenThrow(new IllegalStateException("verification unavailable"));
        try (var security = mockStatic(PlatformSecurityContext.class)) {
            security.when(PlatformSecurityContext::currentPrincipal).thenReturn(new PlatformPrincipal("owner@example.test", PlatformRole.CUSTOMER_ADMIN, "Owner", "SUPABASE", "user"));
            assertThatThrownBy(() -> controller.update(new SupportNotificationController.Preference(true, "https://console.test/support"), "Bearer token")).isInstanceOf(IllegalStateException.class);
            verifyNoInteractions(client);
        }
    }
}
