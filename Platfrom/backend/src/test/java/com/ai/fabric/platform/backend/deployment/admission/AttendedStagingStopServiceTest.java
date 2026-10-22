package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AttendedStagingStopContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AttendedStagingStopServiceTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    private AttendedStagingStopService service(AdmissionStore store) {
        return new AttendedStagingStopService(store, null, null, null, null, null, null, null,
            null, null, null, null, null);
    }
    private void authenticate(PlatformRole role) {
        var principal = new PlatformPrincipal("synthetic-staff", role, "Fixture", "SESSION");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "unused", List.of()));
    }
    @Test void unauthenticatedAndCustomerActorsCannotEvenPlanAStop() {
        var store = mock(AdmissionStore.class);
        assertThatThrownBy(() -> service(store).plan("reservation", null)).hasMessageContaining("ATTENDED_STOP_STAFF_ONLY");
        authenticate(PlatformRole.CUSTOMER_ADMIN);
        assertThatThrownBy(() -> service(store).plan("reservation", null)).hasMessageContaining("ATTENDED_STOP_STAFF_ONLY");
        verifyNoInteractions(store);
    }
    @Test void staffMustAcknowledgeDowntimeVolatileLossAndRecoveryWaiverBeforeAnyRead() {
        authenticate(PlatformRole.PLATFORM_ADMIN);var store = mock(AdmissionStore.class);
        assertThatThrownBy(() -> service(store).plan("reservation", new Request(
            new AdmissionContracts.Fence("reservation", 32, "generation"), "Attended pilot stop", false)))
            .hasMessageContaining("ATTENDED_STOP_EXPLICIT_ACCEPTANCE_REQUIRED");
        verifyNoInteractions(store);
    }
    @Test void fenceJsonRequiresAnExplicitIntegralRevisionWithoutCoercion() throws Exception {
        var mapper = new ObjectMapper();
        for (String revision : List.of("", ",\"revision\":null", ",\"revision\":32.1", ",\"revision\":\"32\"")) {
            String raw = "{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\"" + revision
                + "},\"reason\":\"Attended stop\",\"acceptDowntimeVolatileStateLossAndWaiveRecoveryWindow\":true}";
            assertThatThrownBy(() -> mapper.readValue(raw, Request.class)).isInstanceOf(Exception.class);
        }
        var parsed = mapper.readValue("{\"fence\":{\"reservationId\":\"reservation\",\"generation\":\"generation\",\"revision\":32},"
            + "\"reason\":\"Attended stop\",\"acceptDowntimeVolatileStateLossAndWaiveRecoveryWindow\":true}", Request.class);
        assertThat(parsed.fence().revision()).isEqualTo(32);
    }
}
