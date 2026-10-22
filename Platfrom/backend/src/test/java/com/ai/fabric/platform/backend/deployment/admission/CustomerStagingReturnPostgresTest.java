package com.ai.fabric.platform.backend.deployment.admission;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.EnumSet;
import java.util.List;
import java.time.Instant;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionColdTerminationReview.*;
import static com.ai.fabric.platform.backend.deployment.admission.AttendedStagingStopContracts.*;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
class CustomerStagingReturnPostgresTest extends CustomerStagingReturnTest {
    @Container static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Override @BeforeEach
    void activeTarget() {
        if (store == null) database();
        super.activeTarget();
    }

    @Override
    void database() {
        String schema = "return_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl()
            + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule()); json = new AdmissionJson(mapper);
        org.flywaydb.core.Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT MAX(version::integer) FROM flyway_schema_history WHERE success AND version IS NOT NULL", Integer.class))
            .isEqualTo(208);
        assertThat(jdbc.queryForObject("SELECT character_maximum_length FROM information_schema.columns WHERE table_schema=current_schema() "
            + "AND table_name='deployment_admission_reviews' AND column_name='kind'", Integer.class)).isEqualTo(16);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Override
    void seedTargetRows() {
        var now = java.sql.Timestamp.from(NOW);
        jdbc.update("INSERT INTO platform_customers (id,name,slug,status,created_at,updated_at) VALUES ('customer','Customer','return-customer','ACTIVE',?,?)", now, now);
        for (String tenant : java.util.List.of("tenant", "foreign"))
            jdbc.update("INSERT INTO platform_customer_tenants (id,customer_id,name,slug,status,created_at,updated_at) VALUES (?,'customer',?,?,'ACTIVE',?,?)",
                tenant, tenant, tenant, now, now);
        jdbc.update("""
            INSERT INTO platform_deployments (id,name,environment_name,template_id,status,customer_id,tenant_id,active_version_id,created_at,updated_at)
            VALUES ('deployment1','Preview','staging','template','ACTIVE','customer','tenant','version',?,?)
            """, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_drafts (id,deployment_id,revision_number,status,actions_config_json,entity_config_json,routing_config_json,
                provider_config_json,security_config_json,created_at,updated_at)
            VALUES ('draft','deployment1',1,'PUBLISHED','{}','{}','{}','{}','{}',?,?)
            """, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_versions (id,deployment_id,source_draft_id,version_label,status,config_hash,reindex_required,actions_config_json,
                entity_config_json,routing_config_json,provider_config_json,security_config_json,actions_artifact_yaml,entity_artifact_yaml,
                routing_artifact_yaml,manifest_json,published_at)
            VALUES ('version','deployment1','draft','v1','PUBLISHED','config-hash',FALSE,'{}','{}','{}','{}','{}','','','','{}',?)
            """, now);
        jdbc.update("""
            INSERT INTO deployment_target_profiles (id,name,provider_type,environment_name,source_strategy,active,created_at,updated_at)
            VALUES (?,'Reviewed staging','COOLIFY','staging','IMMUTABLE_IMAGE',TRUE,?,?)
            """, TARGET, now, now);
        jdbc.update("""
            INSERT INTO platform_deployment_releases (id,deployment_id,target_profile_id,deployment_version_id,status,verification_status,provisioning_status,
                provisioning_target,provisioning_details_json,provider_type,created_at,applied_at,updated_at)
            VALUES ('release','deployment1',?,'version','APPLIED_VERIFIED','PASSED','ACTIVE','CUSTOMER_STAGING','{}','COOLIFY',?,?,?)
            """, TARGET, now, now, now);
        jdbc.update("INSERT INTO deployment_managed_dependency_specs (id,fingerprint,spec_json,reviewed_by,reviewed_at,review_reference,active) VALUES ('spec',?,'{}','staff',?,'return-spec-review',TRUE)",
            "a".repeat(64), now);
        jdbc.update("""
            INSERT INTO deployment_managed_dependency_graphs (id,deployment_id,target_profile_id,customer_id,tenant_id,version_id,release_id,spec_ref,
                spec_fingerprint,binding_fingerprint,status,step,spec_json,state_json,created_at,updated_at)
            VALUES ('graph','deployment1',?,'customer','tenant','version','release','spec',?,'binding-fingerprint','READY','READY','{}','{}',?,?)
            """, TARGET, "a".repeat(64), now, now);
        jdbc.update("INSERT INTO platform_target_retirement_gates (id,deployment_id,target_profile_id,state,epoch,updated_at) VALUES ('gate','deployment1',?,'AVAILABLE',7,?)",
            TARGET, now);
    }

    @Test
    void deploymentWorkerLockDeniesReturnWithoutWaitingOrChangingHolds() throws Exception {
        try (var worker = dataSource.getConnection()) {
            worker.setAutoCommit(false);
            try (var lock = worker.prepareStatement("SELECT id FROM platform_deployments WHERE id=? FOR UPDATE")) {
                lock.setString(1, "deployment1");
                lock.executeQuery().close();
            }
            assertThatThrownBy(() -> service.returnStaging("offer", "deployment1", request(true)))
                .hasMessageContaining("STAGING_RETURN_BUSY");
            assertThat(store.require(allocation.id())).isEqualTo(allocation);
            worker.rollback();
        }
    }

    @Test void explicitlyReturnedActivatedTargetCanSettleStoppedComputeWithoutRewritingItsHistory() {
        var plan = returnedStopPlan(true);
        var original = plan.review().capture().reservation();
        var returnReceipt = store.customerReturnAuthority(original);
        var reviewsBefore = jdbc.queryForList("SELECT * FROM deployment_admission_reviews ORDER BY kind,record_key");
        var claim = store.claimAttendedStop(plan, "staff", () -> 9L);
        var proof = new StoppedReadback(plan.id(), claim.planFingerprint(), claim.targetEpoch(), plan.resources(),
            plan.review().frozenAt(), "actual-host-stopped-proof");
        var receipt = store.settleAttendedStop(claim, proof, "staff", () -> { });
        var settled = receipt.reservation();
        assertThat(settled.state()).isEqualTo(State.RETAINED_EXTERNAL);
        assertThat(settled.expiryState()).isEqualTo(ExpiryState.SETTLED_RETAINED);
        assertThat(settled.retirementOperationId()).isEqualTo("retirement");
        assertThat(settled.demand()).isEqualTo(original.demand());
        assertThat(settled.chargedDemand().amounts().get(Dimension.MEMORY_BYTES)).isZero();
        assertThat(settled.chargedDemand().amounts().get(Dimension.CPU_MILLICORES)).isZero();
        assertThat(settled.chargedDemand().amounts().get(Dimension.DISK_BYTES))
            .isEqualTo(original.chargedDemand().amounts().get(Dimension.DISK_BYTES));
        assertThat(settled.chargedDemand().amounts().get(Dimension.DATABASES))
            .isEqualTo(original.chargedDemand().amounts().get(Dimension.DATABASES));
        assertThat(store.releaseBinding("release").state()).isEqualTo("COLD_STOPPED");
        assertThat(jdbc.queryForList("SELECT * FROM deployment_admission_reviews ORDER BY kind,record_key"))
            .containsAll(reviewsBefore);
        assertThat(store.reviewed("STAGING_RETURN", json.fingerprint(java.util.Map.of("reservation", original.id(),
            "generation", original.generation())), CustomerStagingReturnContracts.Receipt.class)).isEqualTo(returnReceipt);
        assertThat(store.settleAttendedStop(claim, proof, "staff", () -> { throw new AssertionError("duplicate"); }))
            .isEqualTo(receipt);
        assertThatThrownBy(() -> store.outcome(original.fence(), State.CLEANUP_FAILED, "late", "worker", proof.observedAt()))
            .hasMessageContaining("STALE_RESERVATION_FENCE");
    }

    @Test void anUnwaivedCustomerReturnCannotAuthorizeAttendedStopOrReleaseAnything() {
        var plan = returnedStopPlan(false);
        var before = store.require(allocation.id());
        assertThatThrownBy(() -> store.claimAttendedStop(plan, "staff", () -> { throw new AssertionError("fence"); }))
            .hasMessageContaining("ATTENDED_STOP_EXPLICIT_RETURN_REQUIRED");
        assertThat(store.require(allocation.id())).isEqualTo(before);
        assertThat(store.releaseBinding("release").state()).isEqualTo("ACTIVE");
    }

    private Plan returnedStopPlan(boolean waive) {
        service.returnStaging("offer", "deployment1", request(waive));
        var claimed = store.require(allocation.id());
        var r = store.handoff(store.expiryWork(claimed), "retirement", "target-retirement:retirement:fingerprint", "worker", NOW);
        Instant observed = r.drainDeadline().plusSeconds(1);
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(observed, ZoneOffset.UTC));
        var resource = new com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ResourceGeneration(
            "runtime", "release", com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType.COOLIFY,
            "APPLICATION", "runtime-uuid", STAGING.serverUuid(), "project", "environment", STAGING.destinationUuid(),
            "runtime", "Managed by AI Fabric deployment deployment1", "runtime-generation", "native-identity", List.of(), false);
        var snapshot = new com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Snapshot(
            r.customerId(), r.tenantId(), r.deploymentId(), TARGET, "target-generation", 8,
            List.of(new com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.ReleaseGeneration(
                "release", "version", "release-generation")), List.of(resource), List.of());
        var confirmations = EnumSet.allOf(Confirmation.class);
        confirmations.remove(Confirmation.NEVER_ACTIVATED);
        confirmations.remove(Confirmation.NO_RETIREMENT_OPERATION);
        var capture = new ServerCapture("returned-capture", observed, observed.plusSeconds(300), r, snapshot,
            List.of(store.releaseBinding("release")), STAGING, "project", "environment", confirmations);
        var expected = new Expectation(r.fence(), r.customerId(), r.tenantId(), r.deploymentId(), TARGET, r.workerToken());
        var ack = new Acknowledgement("returned-review", capture.reference(), "staff", "Explicit return and stopped host",
            observed, observed.plusSeconds(180), EnumSet.allOf(LossAcknowledgement.class));
        var review = freezeReturned(expected, capture, ack, observed);
        return new Plan("returned-stop", "staff", review.payload(), null, -1, null,
            List.of(new NativeResource("APPLICATION", "runtime-uuid", "runtime", resource.deploymentMarker(),
                STAGING.serverUuid(), STAGING.destinationUuid(), "environment")), observed.plusSeconds(180));
    }
}
