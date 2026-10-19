package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.deployment.entity.*;
import com.ai.fabric.platform.backend.deployment.model.DeploymentProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.*;

class AdmissionUnusedReservationCancellationTest extends AdmissionFixture {
    Reservation reservation;
    AdmissionContext admitted;
    CancelUnusedReservation command;
    CustomerOfferContracts.AdmissionContext sourceScope;

    @BeforeEach void candidate() {
        if (dataSource == null) database();
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V195__unused_admission_reservation_cancellation.sql")).execute(dataSource);
        // Isolated source-schema fixture; the cancellation and admission ledger migrations are real.
        for (String ddl : List.of(
            "CREATE TABLE platform_deployments(id VARCHAR(64) PRIMARY KEY,customer_id VARCHAR(64),tenant_id VARCHAR(64))",
            "CREATE TABLE platform_deployment_versions(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),status VARCHAR(32),config_hash VARCHAR(255))",
            "CREATE TABLE platform_deployment_releases(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE deployment_managed_dependency_graphs(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE provider_usage_allocations(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE platform_target_retirement_operations(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64))",
            "CREATE TABLE platform_target_retirement_gates(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),state VARCHAR(64),epoch BIGINT,operation_id VARCHAR(64),row_version BIGINT NOT NULL DEFAULT 0)",
            "CREATE TABLE platform_deployment_deletion_operations(id VARCHAR(64),deployment_id VARCHAR(64))",
            "CREATE TABLE platform_consumers(id VARCHAR(64),consumer_id VARCHAR(128),customer_id VARCHAR(64),status VARCHAR(64),bound_deployment_id VARCHAR(64),bound_target_profile_id VARCHAR(64),bound_release_id VARCHAR(64),binding_revision BIGINT,row_version BIGINT)",
            "CREATE TABLE deployment_provider_resource_handles(id VARCHAR(64) PRIMARY KEY,deployment_id VARCHAR(64),target_profile_id VARCHAR(64),release_id VARCHAR(64),resource_kind VARCHAR(64),status VARCHAR(64),metadata_json TEXT,provider_type VARCHAR(32),provider_resource_uuid VARCHAR(255),provider_project_uuid VARCHAR(255),provider_environment_uuid VARCHAR(255),provider_server_uuid VARCHAR(255),fqdn TEXT,created_at TIMESTAMP WITH TIME ZONE,updated_at TIMESTAMP WITH TIME ZONE)",
            "CREATE TABLE platform_runtime_authority_snapshots(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),version_id VARCHAR(64),release_id VARCHAR(64),fingerprint VARCHAR(64),binding_json TEXT)",
            "CREATE TABLE platform_runtime_target_authorities(id VARCHAR(64),deployment_id VARCHAR(64),target_profile_id VARCHAR(64),customer_id VARCHAR(64),tenant_id VARCHAR(64),generation BIGINT)")) jdbc.execute(ddl);
        jdbc.update("INSERT INTO platform_deployments VALUES ('deployment','customer','tenant')");
        jdbc.update("INSERT INTO platform_deployment_versions VALUES ('version','deployment','PUBLISHED','config-hash')");
        approve(2);
        store.approveManifest(manifest("manifest", "deployment"), "staff", NOW);
        admitted = context("manifest");
        sourceScope = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", null, TARGET,
            CustomerOfferContracts.TargetIntent.CUSTOMER_STAGING, "offer", "offer-fingerprint", 1, "placement",
            CustomerOfferContracts.Operation.APPLY, "apply",
            new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1),
            new CustomerOfferContracts.ReviewedTopology(1, 1, 1, 1, 1, 1, 1, 1));
        reservation = store.reserve(admitted, new Reserve("apply", "manifest", sourceScope), evidence(admitted, NOW), "owner", NOW);
        var authorization = json.read(jdbc.queryForObject("SELECT payload_json FROM deployment_admission_authorizations", String.class), AuthorizationReceipt.class);
        command = new CancelUnusedReservation(reservation.fence(), "cancel", "apply", authorization.requestFingerprint(),
            reservation.manifestFingerprint(), "version", "config-hash", reservation.bindingFingerprint());
    }

    @Test void unusedCancellationIsAtomicDurableAndReplaysWithoutReclaiming() {
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource),
            Clock.fixed(NOW.plusNanos(123456789), ZoneOffset.UTC));
        var receipt = cancel();
        assertThat(receipt.outcome()).isEqualTo(CancellationOutcome.CANCELLED_UNUSED_NO_EFFECT);
        assertThat(receipt.reclamationPerformed()).isFalse();
        assertThat(receipt.reservation().state()).isEqualTo(State.RELEASED);
        assertThat(receipt.cancelledAt()).isEqualTo(NOW.plusNanos(123456000));
        assertThat(receipt.reservation().revision()).isEqualTo(reservation.revision() + 1);
        assertThat(store.held()).isEmpty();
        store = new AdmissionStore(jdbc, json, new DataSourceTransactionManager(dataSource), Clock.fixed(NOW.plusSeconds(1), ZoneOffset.UTC));
        assertThat(cancel()).isEqualTo(receipt);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_unused_cancellations", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_authorizations", Long.class)).isEqualTo(1);
        assertThatThrownBy(() -> bind("late-release")).hasMessageContaining("ALLOCATION_NOT_CURRENT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM platform_deployment_releases", Long.class)).isZero();
    }

    @Test void retainedBindingReleasesOnlyItsNeverMaterializedClaimAndPreservesMetadataAndHistory() {
        var handle = externalBinding();
        String metadata = handle.getMetadataJson();
        var receipt = cancel();
        assertThat(receipt.evidence().retainedBindings()).singleElement().satisfies(binding -> {
            assertThat(binding.disposition()).isEqualTo(BindingCancellationDisposition.RETAINED_UNUSED);
            assertThat(binding.identityFingerprint()).hasSize(64);
        });
        assertThat(jdbc.queryForObject("SELECT status FROM deployment_provider_resource_handles", String.class)).isEqualTo("RETAINED_UNUSED");
        assertThat(jdbc.queryForObject("SELECT metadata_json FROM deployment_provider_resource_handles", String.class)).isEqualTo(metadata);
        handle.setStatus("RETAINED_UNUSED");
        var proofs = new AdmissionUnusedReservationEvidence(jdbc, json);
        assertThat(proofs.permitsFreshBinding(handle, "customer", "tenant", "same-owner-approval", "fresh-prefix")).isTrue();
        assertThat(proofs.permitsFreshBinding(handle, "customer", "tenant", "same-owner-approval", "old-prefix")).isFalse();
        assertThat(proofs.permitsFreshBinding(handle, "other", "tenant", "same-owner-approval", "fresh-prefix")).isFalse();
        assertThat(proofs.permitsFreshBinding(handle, "customer", "tenant", "other-approval", "fresh-prefix")).isFalse();
        handle.setProviderResourceUuid("different-cluster");
        assertThat(proofs.permitsFreshBinding(handle, "customer", "tenant", "same-owner-approval", "fresh-prefix")).isFalse();
        assertThat(json.write(receipt)).doesNotContain("synthetic-secret-ref", "old-prefix", "synthetic-credential");
    }

    @Test void statusAloneCannotReleaseAnExternalClaim() {
        var handle = externalBinding(); handle.setStatus("RETAINED_UNUSED");
        jdbc.update("UPDATE deployment_provider_resource_handles SET status='RETAINED_UNUSED'");
        assertThat(new AdmissionUnusedReservationEvidence(jdbc, json)
            .permitsFreshBinding(handle, "customer", "tenant", "same-owner-approval", "fresh-prefix")).isFalse();
        assertThatThrownBy(this::cancel).hasMessageContaining("BINDING_METADATA_INVALID");
        assertHeld();
    }

    @Test void preparedAuthorityIsPinnedWithoutRevocationOrSecretInspection() {
        String binding = "{\"customerId\":\"customer\",\"tenantId\":\"tenant\",\"deploymentId\":\"deployment\",\"targetProfileId\":\"" + TARGET
            + "\",\"versionId\":\"version\",\"versionConfigHash\":\"config-hash\",\"generation\":3,\"secrets\":{\"never-return\":\"synthetic-secret-ref\"}}";
        jdbc.update("INSERT INTO platform_runtime_target_authorities VALUES ('authority','deployment',?,'customer','tenant',3)", TARGET);
        jdbc.update("INSERT INTO platform_runtime_authority_snapshots VALUES ('candidate','deployment',?,'version',NULL,'authority-fingerprint',?)", TARGET, binding);
        var receipt = cancel();
        assertThat(receipt.evidence().authorityCandidates()).containsExactly(new UnusedAuthorityCandidate("candidate", "authority-fingerprint", 3));
        assertThat(jdbc.queryForObject("SELECT binding_json FROM platform_runtime_authority_snapshots", String.class)).isEqualTo(binding);
        assertThat(json.write(receipt)).doesNotContain("synthetic-secret-ref");
    }

    @Test void cancelledAuthorizationCannotPassTheActualSourcePrerequisiteProvider() {
        cancel();
        var deployments = org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository.class);
        var versions = org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository.class);
        var targets = org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository.class);
        var placement = org.mockito.Mockito.mock(com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService.class);
        var sourceEvidence = org.mockito.Mockito.mock(CustomerSourcePrerequisiteEvidenceStore.class);
        var targetContexts = org.mockito.Mockito.mock(com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService.class);
        var preflight = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider(store, json, mapper, deployments, versions,
            targets, placement, sourceEvidence, targetContexts, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> new TransactionTemplate(new DataSourceTransactionManager(dataSource))
            .execute(tx -> preflight.resolve(sourceScope))).hasMessageContaining("CUSTOMER_CAPACITY_RESERVATION_INVALID");
        org.mockito.Mockito.verifyNoInteractions(deployments, versions, targets, placement, sourceEvidence, targetContexts);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_release_bindings", Long.class)).isZero();
        assertThat(cancel().outcome()).isEqualTo(CancellationOutcome.CANCELLED_UNUSED_NO_EFFECT);
    }

    @Test void configuredActiveRegistryConsumerWithoutReleaseIsStillAnExplicitAssignmentBlocker() {
        jdbc.update("INSERT INTO platform_consumers VALUES ('configured','synthetic-config','customer','ACTIVE','deployment',?,NULL,1,1)", TARGET);
        assertThatThrownBy(this::cancel).hasMessageContaining("UNUSED_CANCELLATION_CONSUMER_ASSIGNED");
        assertHeld();
        assertThat(jdbc.queryForObject("SELECT binding_revision FROM platform_consumers", Long.class)).isEqualTo(1);
    }

    @ParameterizedTest @ValueSource(strings = {"PROVISIONING", "ACTIVE", "OUTCOME_UNKNOWN", "CLEANUP_FAILED"})
    void nonReservedStateNeverCancels(String state) {
        var tree = mapper.valueToTree(reservation); ((com.fasterxml.jackson.databind.node.ObjectNode) tree).put("state", state);
        jdbc.update("UPDATE deployment_capacity_reservations SET state=?,payload_json=?", state, tree.toString());
        assertThatThrownBy(this::cancel).hasMessageContaining("REQUIRES_UNUSED_RESERVATION");
        assertHeld();
    }

    @ParameterizedTest @ValueSource(strings = {"NO_EFFECT", "SUPERSEDED", "QUEUED", "PROVISIONING", "OUTCOME_UNKNOWN", "ACTIVE"})
    void allBindingHistoryBlocksEvenWhenCurrentReleaseLookupHidesIt(String state) {
        bind("release"); store.releaseBindingState("release", state);
        jdbc.update("DELETE FROM platform_deployment_releases");
        assertThatThrownBy(this::cancel).hasMessageContaining("EFFECT_HISTORY_PRESENT");
        assertHeld();
    }

    @ParameterizedTest @ValueSource(strings = {"dependency", "budget", "retirement", "deletion", "consumer", "event", "resource", "release-authority", "null-target"})
    void effectIntentOrUncertainHistoryBlocksWithoutPartialChanges(String kind) {
        externalBinding();
        switch (kind) {
            case "dependency" -> jdbc.update("INSERT INTO deployment_managed_dependency_graphs VALUES ('intent','deployment',?)", TARGET);
            case "budget" -> jdbc.update("INSERT INTO provider_usage_allocations VALUES ('allocation','deployment',?)", TARGET);
            case "retirement" -> jdbc.update("INSERT INTO platform_target_retirement_operations VALUES ('retirement','deployment',?)", TARGET);
            case "deletion" -> jdbc.update("INSERT INTO platform_deployment_deletion_operations VALUES ('deletion','deployment')");
            case "consumer" -> jdbc.update("INSERT INTO platform_consumers(id,bound_deployment_id,bound_target_profile_id) VALUES ('consumer','deployment',NULL)");
            case "event" -> jdbc.update("UPDATE deployment_admission_events SET event_type='PROVISIONING'");
            case "resource" -> jdbc.update("INSERT INTO deployment_provider_resource_handles(id,deployment_id,target_profile_id,resource_kind) VALUES ('owned','deployment',?,'APPLICATION')", TARGET);
            case "release-authority" -> jdbc.update("INSERT INTO platform_runtime_authority_snapshots VALUES ('released','deployment',?,'version','unknown-release','fingerprint','{}')", TARGET);
            case "null-target" -> jdbc.update("INSERT INTO platform_deployment_releases VALUES ('unknown','deployment',NULL)");
            default -> throw new AssertionError(kind);
        }
        assertThatThrownBy(this::cancel).isInstanceOf(AdmissionFailure.class);
        assertHeld();
        assertThat(jdbc.queryForObject("SELECT status FROM deployment_provider_resource_handles WHERE id='external'", String.class)).isEqualTo("ACTIVE");
    }

    @Test void mismatchedScopeStaleFenceAndConflictingReplayFailClosed() {
        assertThatThrownBy(() -> store.cancelUnused("deployment", "foreign-target", command, "staff")).hasMessageContaining("NOT_FOUND");
        var wrong = new CancelUnusedReservation(command.fence(), "cancel", "apply", command.authorizationRequestFingerprint(),
            command.manifestFingerprint(), "wrong-version", command.versionConfigHash(), command.bindingFingerprint());
        assertThatThrownBy(() -> store.cancelUnused("deployment", TARGET, wrong, "staff")).hasMessageContaining("SCOPE_CHANGED");
        var stale = new CancelUnusedReservation(new Fence(reservation.id(), 99, reservation.generation()), "cancel", "apply",
            command.authorizationRequestFingerprint(), command.manifestFingerprint(), "version", "config-hash", command.bindingFingerprint());
        assertThatThrownBy(() -> store.cancelUnused("deployment", TARGET, stale, "staff")).hasMessageContaining("STALE_RESERVATION_FENCE");
        cancel();
        assertThatThrownBy(() -> store.cancelUnused("deployment", TARGET, wrong, "staff")).hasMessageContaining("CANCELLATION_OPERATION_REUSED");
    }


    @Test void concurrentCancellationHasOneStableReceipt() throws Exception {
        try (var workers = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var a = workers.submit(() -> { start.await(); return cancel(); });
            var b = workers.submit(() -> { start.await(); return cancel(); });
            start.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
        }
    }

    @Test void committedApplyBlocksCancellation() {
        bind("release");
        assertThatThrownBy(this::cancel).hasMessageContaining("EFFECT_HISTORY_PRESENT"); assertHeld();
    }

    UnusedReservationCancellationReceipt cancel() { return store.cancelUnused("deployment", TARGET, command, "staff"); }
    void assertHeld() {
        assertThat(store.held()).hasSize(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deployment_admission_unused_cancellations", Long.class)).isZero();
    }

    void bind(String id) {
        new TransactionTemplate(new DataSourceTransactionManager(dataSource)).executeWithoutResult(tx -> bindInTransaction(id));
    }
    void bindInTransaction(String id) {
        jdbc.queryForObject("SELECT id FROM platform_deployments WHERE id='deployment' FOR UPDATE", String.class);
        jdbc.update("INSERT INTO platform_deployment_releases VALUES (?,'deployment',?)", id, TARGET);
        var deployment = new DeploymentEntity(); deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setConfigHash("config-hash");
        var release = new DeploymentReleaseEntity(); release.setId(id); release.setTargetProfileId(TARGET);
        store.bindRelease(deployment, version, release);
    }


    DeploymentProviderResourceHandleEntity externalBinding() {
        String metadata = "{\"customerId\":\"customer\",\"tenantId\":\"tenant\",\"deploymentId\":\"deployment\",\"targetProfileId\":\"" + TARGET
            + "\",\"lifecycle\":\"RETAIN_EXTERNAL\",\"externalResourceOwned\":false,\"approvalReference\":\"same-owner-approval\",\"collectionPrefix\":\"old-prefix\",\"tokenSecretRef\":\"synthetic-secret-ref\"}";
        jdbc.update("""
            INSERT INTO deployment_provider_resource_handles
             (id,deployment_id,target_profile_id,resource_kind,status,metadata_json,provider_type,provider_resource_uuid,created_at,updated_at)
            VALUES ('external','deployment',?,'EXTERNAL_VECTOR_STORAGE_BINDING','ACTIVE',?,'COOLIFY','synthetic-cluster',?,?)
            """, TARGET, metadata, java.sql.Timestamp.from(NOW), java.sql.Timestamp.from(NOW));
        var handle = new DeploymentProviderResourceHandleEntity();
        handle.setId("external"); handle.setDeploymentId("deployment"); handle.setTargetProfileId(TARGET);
        handle.setResourceKind("EXTERNAL_VECTOR_STORAGE_BINDING"); handle.setStatus("ACTIVE"); handle.setMetadataJson(metadata);
        handle.setProviderType(DeploymentProviderType.COOLIFY); handle.setProviderResourceUuid("synthetic-cluster"); handle.setCreatedAt(NOW);
        return handle;
    }
}
