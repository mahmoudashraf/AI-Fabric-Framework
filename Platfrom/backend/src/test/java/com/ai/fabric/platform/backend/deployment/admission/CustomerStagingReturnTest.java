package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.CustomerStagingReturnContracts.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerStagingReturnTest extends AdmissionFixture {
    Reservation allocation;
    CustomerOfferEntitlementService entitlements;
    CustomerSelfServiceAuthorizationService authorization;
    CustomerStagingLifecycleService service;

    @BeforeEach
    void activeTarget() {
        var reserved = one();
        allocation = store.reconcile(reserved.fence(), readback(reserved, NOW, false, 0L, 0L), false, "worker", NOW);
        seedTargetRows();
        jdbc.update("""
            INSERT INTO deployment_admission_release_bindings
            (release_id,reservation_id,generation,authorization_key,version_id,version_config_hash,customer_id,tenant_id,deployment_id,target_profile_id,state,created_at,updated_at)
            VALUES ('release',?,?,'apply','version','config-hash','customer','tenant','deployment1',?,'ACTIVE',?,?)
            """, allocation.id(), allocation.generation(), TARGET, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        entitlements = mock(CustomerOfferEntitlementService.class);
        authorization = mock(CustomerSelfServiceAuthorizationService.class);
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        service = new CustomerStagingLifecycleService(entitlements, authorization, mock(DeploymentAdmissionService.class),
            store, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    void seedTargetRows() {
        jdbc.execute("CREATE TABLE platform_deployments (id VARCHAR(64) PRIMARY KEY,customer_id VARCHAR(64),tenant_id VARCHAR(64),active_version_id VARCHAR(64),archived_at TIMESTAMP WITH TIME ZONE)");
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment1','customer','tenant','version',NULL)");
        jdbc.execute("CREATE TABLE platform_deployment_releases (id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),target_profile_id VARCHAR(64),deployment_version_id VARCHAR(64),status VARCHAR(64),verification_status VARCHAR(64),provisioning_status VARCHAR(64))");
        jdbc.update("INSERT INTO platform_deployment_releases VALUES ('release','deployment1',?,'version','APPLIED_VERIFIED','PASSED','ACTIVE')", TARGET);
        jdbc.execute("CREATE TABLE deployment_managed_dependency_graphs (id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),customer_id VARCHAR(64),tenant_id VARCHAR(64),status VARCHAR(64),worker_token VARCHAR(64))");
        jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES ('graph','deployment1',?,'customer','tenant','READY',NULL)", TARGET);
        jdbc.execute("CREATE TABLE platform_target_retirement_gates (deployment_id VARCHAR(64),target_profile_id VARCHAR(64),epoch BIGINT,state VARCHAR(32))");
        jdbc.update("INSERT INTO platform_target_retirement_gates VALUES ('deployment1',?,7,'AVAILABLE')", TARGET);
        jdbc.execute("CREATE TABLE platform_target_retirement_operations (id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),state VARCHAR(32))");
        jdbc.execute("CREATE TABLE platform_deployment_deletion_operations (id VARCHAR(64),deployment_id VARCHAR(64),status VARCHAR(32))");
    }

    @Test
    void ownerRequestsNormalRetirementWithoutChangingExpiryHistoryOrAnyCapacity() {
        var history = jdbc.queryForList("SELECT * FROM deployment_admission_events ORDER BY revision");
        var reviews = jdbc.queryForList("SELECT * FROM deployment_admission_reviews ORDER BY kind,record_key");
        var grants = jdbc.queryForList("SELECT * FROM deployment_admission_grants");
        var binding = store.releaseBinding("release");

        var summary = service.returnStaging("offer", "deployment1", request(true));
        var claimed = store.require(allocation.id());

        assertThat(summary.expiryState()).isEqualTo("CLAIMED");
        assertThat(summary.operation()).isEqualTo(CustomerOfferContracts.Operation.TARGET_RETIRE);
        assertThat(summary.chargedDemand()).isEqualTo(allocation.chargedDemand());
        assertThat(claimed.state()).isEqualTo(State.ACTIVE);
        assertThat(claimed.revision()).isEqualTo(allocation.revision() + 1);
        assertThat(claimed.expiresAt()).isEqualTo(allocation.expiresAt()).isAfter(NOW);
        assertThat(claimed.keepUntil()).isEqualTo(allocation.keepUntil());
        assertThat(claimed.drainDeadline()).isAfter(NOW);
        assertThat(claimed.recoveryUntil()).isAfter(claimed.drainDeadline());
        assertThat(claimed.chargedDemand()).isEqualTo(allocation.chargedDemand());
        assertThat(store.expiryWork(claimed).reason()).isEqualTo("CUSTOMER_STAGING_RETURN_REQUESTED");
        assertThat(jdbc.queryForList("SELECT * FROM deployment_admission_events WHERE revision<=? ORDER BY revision", allocation.revision())).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT * FROM deployment_admission_reviews WHERE kind<>'STAGING_RETURN' ORDER BY kind,record_key")).isEqualTo(reviews);
        assertThat(jdbc.queryForList("SELECT * FROM deployment_admission_grants")).isEqualTo(grants);
        assertThat(store.releaseBinding("release")).isEqualTo(binding);
        assertThatThrownBy(() -> store.outcome(allocation.fence(), State.CLEANUP_FAILED, "stale", "worker", NOW))
            .hasMessageContaining("STALE_RESERVATION_FENCE");
        assertThatThrownBy(() -> store.reconcile(claimed.fence(), readback(claimed, NOW, false, 0L, 0L), true, "worker", NOW))
            .isInstanceOf(AdmissionFailure.class);
        assertThat(store.require(allocation.id()).chargedDemand()).isEqualTo(allocation.chargedDemand());
        verify(authorization).authorize(eq("customer"), argThat(scope -> scope.operation() == CustomerOfferContracts.Operation.TARGET_RETIRE
            && scope.offerId().equals("offer") && scope.tenantId().equals("tenant") && scope.deploymentId().equals("deployment1")
            && scope.targetProfileId().equals(TARGET) && scope.releaseId().equals("release")));
    }

    @Test
    void exactReplayIsIdempotentAndChangedConsentCannotBorrowTheClaim() {
        var request = request(true);
        var result = service.returnStaging("offer", "deployment1", request);
        var state = store.require(allocation.id());
        assertThat(service.returnStaging("offer", "deployment1", request)).isEqualTo(result);
        assertThat(store.require(allocation.id())).isEqualTo(state);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='STAGING_RETURN'", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", request(false)))
            .hasMessageContaining("STAGING_RETURN_OPERATION_REUSED");
        assertThat(store.require(allocation.id())).isEqualTo(state);
    }

    @Test
    void missingConsentStaleFenceForeignOwnerAndRevokedEntitlementDoNotClaim() {
        var valid = request(true);
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", new Request(allocation.id(), allocation.revision(),
            "return", "Return preview", null, RECOVERY_WAIVER)))
            .hasMessageContaining("ACKNOWLEDGEMENT_REQUIRED");
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", new Request(allocation.id(), allocation.revision() + 1,
            "return", "Return preview", ACKNOWLEDGEMENT, RECOVERY_WAIVER)))
            .hasMessageContaining("STALE_RESERVATION_FENCE");
        jdbc.update("UPDATE platform_deployments SET tenant_id='foreign'");
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", valid)).hasMessageContaining("RESERVATION_NOT_FOUND");
        jdbc.update("UPDATE platform_deployments SET tenant_id='tenant'");
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("foreign", "owner"));
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", valid)).hasMessageContaining("RESERVATION_NOT_FOUND");
        when(entitlements.requireCustomerActor()).thenReturn(new CustomerOfferEntitlementService.CustomerActor("customer", "owner"));
        doThrow(new AdmissionFailure(org.springframework.http.HttpStatus.FORBIDDEN, "TARGET_RETIRE_REQUIRED"))
            .when(authorization).authorize(eq("customer"), any());
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", valid)).hasMessageContaining("TARGET_RETIRE_REQUIRED");
        assertThat(store.require(allocation.id())).isEqualTo(allocation);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='STAGING_RETURN'", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"release", "dependency", "gate"})
    void activeWorkOrTargetFenceKeepsAllocationUnchanged(String blocker) {
        switch (blocker) {
            case "release" -> jdbc.update("UPDATE platform_deployment_releases SET status='PROVISIONING',provisioning_status='PROVISIONING'");
            case "dependency" -> jdbc.update("UPDATE deployment_managed_dependency_graphs SET worker_token='worker'");
            case "gate" -> jdbc.update("UPDATE platform_target_retirement_gates SET state='RETIRING'");
        }
        assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", request(true)))
            .isInstanceOf(AdmissionFailure.class);
        assertThat(store.require(allocation.id())).isEqualTo(allocation);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_reviews WHERE kind='STAGING_RETURN'", Long.class)).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void onlyExactExplicitConsentCanWaiveTheRealRecoveryWindow(boolean waive) {
        service.returnStaging("offer", "deployment1", request(waive));
        var claimed = store.require(allocation.id());
        var operations = mock(TargetRetirementOperationRepository.class);
        var operation = new TargetRetirementOperationEntity();
        operation.setState(TargetRetirementContracts.State.RUNNING);
        operation.setStep(TargetRetirementContracts.Step.AUTHORITY);
        when(operations.findById("retirement")).thenReturn(java.util.Optional.of(operation));
        var authorizer = new AdmissionTargetRetirementSystemAuthorizer(store, operations, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(authorizer.blocker(context(claimed, 7))).isEqualTo(waive ? null : "RECOVERY_WINDOW_NOT_ELAPSED");
        assertThat(authorizer.blocker(context(claimed, 8))).isEqualTo("RETIREMENT_ACTOR_REVOKED");
        operation.setStep(TargetRetirementContracts.Step.DRAIN);
        assertThat(authorizer.blocker(context(claimed, 7))).isNull();
        assertThat(claimed.expiresAt()).isEqualTo(allocation.expiresAt());
        assertThat(claimed.recoveryUntil()).isAfter(NOW);
        assertThat(claimed.chargedDemand()).isEqualTo(allocation.chargedDemand());
    }

    Request request(boolean waive) {
        return new Request(allocation.id(), allocation.revision(), "return", "Return disposable preview",
            ACKNOWLEDGEMENT, waive ? RECOVERY_WAIVER : null);
    }

    private TargetRetirementContracts.Context context(Reservation r, long epoch) {
        var snapshot = new TargetRetirementContracts.Snapshot(r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(),
            "generation", epoch, List.of(), List.of(), List.of());
        return new TargetRetirementContracts.Context("retirement", "admission-expiry:" + r.id() + ":" + r.generation(),
            "fingerprint", snapshot, epoch + 1, null, "system", "SYSTEM", r.drainDeadline(),
            new TargetRetirementContracts.Retention(TargetRetirementContracts.RetentionDecision.RECLAIM, null, null, "restore-proof"));
    }
}
