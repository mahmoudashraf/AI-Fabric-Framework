package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.security.PlatformPrincipal;
import com.ai.fabric.platform.backend.security.PlatformRole;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.ai.fabric.platform.backend.deployment.admission.RuntimeArtifactRepairContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RuntimeArtifactRepairServiceTest {
    RuntimeArtifactRepairJournalTest fixture;
    RuntimeArtifactRepairOperations operations;
    RuntimeArtifactRepairEvidence evidence;
    CustomerOfferEntitlementService entitlements;
    RuntimeArtifactRepairService service;
    @BeforeEach void setup() {
        fixture = new RuntimeArtifactRepairJournalTest(); fixture.database(); fixture.prepare();
        operations = mock(RuntimeArtifactRepairOperations.class); entitlements = mock(CustomerOfferEntitlementService.class);
        evidence = spy(new RuntimeArtifactRepairEvidence(null, fixture.json, fixture.store, null, null, null, null));
        doReturn(fixture.plan.local()).when(evidence).read(anyString(), anyString(), any(), any());
        service = new RuntimeArtifactRepairService(fixture.journal, evidence, operations, fixture.store, fixture.json, entitlements);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new PlatformPrincipal("admin", PlatformRole.PLATFORM_ADMIN, "reviewed staff", "API_KEY"), null, java.util.List.of()));
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void lostResponseNeverAutomaticallyRepeatsEffectOrReleasesClaim() {
        service.claim("deployment1", "release", fixture.command());
        when(operations.perform(fixture.plan, Effect.QUIESCE_CONNECTOR, "admin")).thenThrow(new IllegalStateException("synthetic lost response"));
        assertThatThrownBy(() -> service.execute("deployment1", "release", fixture.command(), Effect.QUIESCE_CONNECTOR)).hasMessageContaining("lost response");
        assertThatThrownBy(() -> service.execute("deployment1", "release", fixture.command(), Effect.QUIESCE_CONNECTOR)).hasMessageContaining("READ_ONLY_RECONCILIATION_REQUIRED");
        verify(operations, times(1)).perform(fixture.plan, Effect.QUIESCE_CONNECTOR, "admin");
        assertThat(fixture.store.require(fixture.reservation.id()).workerToken()).isNotNull();
    }
    @Test void currentAuthorizationFailureOccursBeforeIntentOrRemoteCall() {
        service.claim("deployment1", "release", fixture.command());
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "entitlement revoked"))
            .when(evidence).read(anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> service.execute("deployment1", "release", fixture.command(), Effect.QUIESCE_CONNECTOR)).hasMessageContaining("entitlement revoked");
        assertThat(fixture.jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='ART_INTENT'", Integer.class)).isZero();
        verifyNoInteractions(operations);
    }
    @Test void immutableLocalDriftIsRejectedBeforeIntent() {
        service.claim("deployment1", "release", fixture.command()); var old = fixture.plan.local();
        var changed = new LocalEvidence(old.scope(), old.request(), old.original(), old.replacement(), Map.of("graph", "changed"), old.validUntil(), old.executionDeadline());
        doReturn(changed).when(evidence).read(anyString(), anyString(), any(), any());
        assertThatThrownBy(() -> service.execute("deployment1", "release", fixture.command(), Effect.QUIESCE_CONNECTOR)).hasMessageContaining("LOCAL_STATE_CHANGED");
        verifyNoInteractions(operations);
    }
    @Test void wrongDeploymentCannotReuseAnotherPlansReceipt() {
        assertThatThrownBy(() -> service.read("foreign-deployment", "release", fixture.command())).hasMessageContaining("SCOPE_DENIED");
        verifyNoInteractions(operations);
    }
    @Test void currentStaffAuthorizationIsRequiredEvenForACompletedReadback() {
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "staff disabled")).when(entitlements).requireStaffAdmin();
        assertThatThrownBy(() -> service.read("deployment1", "release", fixture.command())).hasMessageContaining("staff disabled");
        verifyNoInteractions(operations);
    }
    @Test void windowRenewalRechecksLocalEvidenceWithoutDispatchingRemoteEffects() {
        fixture.plan = fixture.plan("service-window", AdmissionFixture.NOW.plusSeconds(300), Mode.IDLE_CHECKPOINT_COLD_RESTART);
        doReturn(fixture.plan.local()).when(evidence).read(anyString(), anyString(), any(), any());
        service.claim("deployment1", "release", fixture.command());
        var request = new RenewWindow(fixture.command(), fixture.plan.local().executionDeadline(),
            AdmissionFixture.NOW.plusSeconds(900), "Continue the same reviewed repair", true);
        var renewed = service.renewWindow("deployment1", "release", request);
        assertThat(renewed.review()).isEqualTo(request);
        verifyNoInteractions(operations);
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "staff disabled")).when(entitlements).requireStaffAdmin();
        assertThatThrownBy(() -> service.renewWindow("deployment1", "release", request)).hasMessageContaining("staff disabled");
    }
    @Test void finalVerificationFailureLeavesOriginalWalletAndWorkerHeld() {
        service.claim("deployment1", "release", fixture.command()); fixture.completeEffects();
        when(operations.finalProof(eq(fixture.plan), any())).thenThrow(new IllegalStateException("wallet changed"));
        assertThatThrownBy(() -> service.verify("deployment1", "release", fixture.command())).hasMessageContaining("wallet changed");
        assertThat(fixture.store.require(fixture.reservation.id()).workerToken()).isNotNull();
        assertThat(fixture.journal.verifiedRepair("release")).isNull();
    }
}
