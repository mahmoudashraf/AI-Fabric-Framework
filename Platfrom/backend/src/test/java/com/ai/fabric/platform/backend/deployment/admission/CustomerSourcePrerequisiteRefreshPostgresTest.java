package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntitlementEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerPlacementEvidenceEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Authorization;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.DocumentAudience;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Prerequisite;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceAuthorizationService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerTargetPlacementService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentTargetProfileEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.model.DeploymentTargetReference;
import com.ai.fabric.platform.backend.deployment.service.DeploymentTargetContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Real migrations, capacity ledger, source-evidence storage and refresh transactions; external probes are stubbed. */
@Testcontainers(disabledWithoutDocker = true)
class CustomerSourcePrerequisiteRefreshPostgresTest extends AdmissionFixture {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16-alpine");

    private final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
    private final CustomerSelfServiceAuthorizationService authorization = mock(CustomerSelfServiceAuthorizationService.class);
    private final CustomerOfferEntitlementService entitlements = mock(CustomerOfferEntitlementService.class);
    private final DeploymentTargetContextService targets = mock(DeploymentTargetContextService.class);
    private final DeploymentAdmissionCustomerPrerequisiteEvidenceProvider prerequisites =
        mock(DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.class);
    private final CustomerTargetPlacementService placement = mock(CustomerTargetPlacementService.class);
    private final CustomerSourceConnectorPrerequisiteProbe connector = mock(CustomerSourceConnectorPrerequisiteProbe.class);
    private final DeploymentTargetReference target = new DeploymentTargetReference("deployment", TARGET, "release");
    private CustomerSourcePrerequisiteEvidenceStore evidence;
    private CustomerSourcePrerequisiteRefreshService service;
    private DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot snapshot;
    private CustomerOfferContracts.AdmissionContext sourceContext;
    private TransactionTemplate transaction;
    private Reservation allocation;

    @Override
    void database() {
        String schema = "source_refresh_" + UUID.randomUUID().toString().replace("-", "");
        var admin = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        new JdbcTemplate(admin).execute("CREATE SCHEMA " + schema);
        dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl()
            + (DATABASE.getJdbcUrl().contains("?") ? "&" : "?") + "currentSchema=" + schema,
            DATABASE.getUsername(), DATABASE.getPassword());
        mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        json = new AdmissionJson(mapper);
        new ResourceDatabasePopulator(
            new ClassPathResource("db/migration/V165__durable_capacity_admission_and_staging_expiry.sql"),
            new ClassPathResource("db/migration/V178__admission_allocation_authorizations.sql"),
            new ClassPathResource("db/migration/V180__release_scoped_source_prerequisite_evidence.sql"))
            .execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        var manager = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(manager);
        store = new AdmissionStore(jdbc, json, manager, Clock.fixed(NOW, ZoneOffset.UTC));
        evidence = new CustomerSourcePrerequisiteEvidenceStore(jdbc, json);
        service = new CustomerSourcePrerequisiteRefreshService(associations, authorization, entitlements, targets,
            prerequisites, placement, connector, evidence, new CustomerSelfServiceProperties(null, 60), manager,
            mock(EntityManager.class), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @BeforeEach
    void sourceRelease() {
        database();
        approve(3);
        store.approveManifest(manifest("manifest", "deployment"), "staff", NOW);
        var limits = new CustomerOfferContracts.ReviewedLimits("measurement-proof", 1, 2, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1);
        var admitted = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", null, TARGET,
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-hash", 2, "placement", Operation.APPLY, "apply", limits);
        var admission = context("manifest");
        allocation = store.reserve(admission, new Reserve("apply", "manifest", admitted), evidence(admission, NOW), "owner", NOW);
        var deployment = new DeploymentEntity();
        deployment.setId("deployment"); deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        var version = new DeploymentVersionEntity(); version.setId("version"); version.setConfigHash("config-hash");
        var release = new DeploymentReleaseEntity(); release.setId("release"); release.setTargetProfileId(TARGET);
        transaction.executeWithoutResult(status -> store.bindRelease(deployment, version, release));
        allocation = store.beginReleaseProvisioning(store.releaseBinding("release"), admission,
            evidence(admission, NOW), "worker", NOW);
        var profile = new DeploymentTargetProfileEntity(); profile.setId(TARGET);
        var runtime = new DeploymentTargetContextService.Context(deployment, profile, release, version,
            "https://runtime.example", "https://connector.example");
        sourceContext = new CustomerOfferContracts.AdmissionContext("customer", "tenant", "deployment", "release", TARGET,
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-hash", 2, null, Operation.SOURCE_DISCOVER, null, limits);
        var scope = new CustomerSourcePrerequisiteEvidence.Scope("customer", "tenant", "deployment", TARGET, "release",
            TargetIntent.CUSTOMER_STAGING, "offer", "offer-hash", 2, allocation.id(), allocation.generation(),
            allocation.manifestFingerprint(), allocation.bindingFingerprint(), "version", "config-hash", "profile",
            "document-binding", 1, "credentials", "runtime");
        snapshot = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot(scope, runtime,
            admission.target(), "S3_COMPATIBLE_OBJECT_STORAGE", "storage-handle", Set.of(Prerequisite.STORAGE),
            allocation.expiresAt(), 60);
        when(targets.resolve("deployment", TARGET, "release", false)).thenReturn(runtime);
        when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
            "deployment", "customer", "tenant", "offer", "offer-hash", 2, "bootstrap", NOW)));
        when(authorization.prepareSourcePrerequisiteRefresh(eq("customer"), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return new Authorization(sourceContext, null);
        });
        when(prerequisites.sourceSnapshot(sourceContext)).thenReturn(snapshot);
        var entitlement = new CustomerOfferEntitlementEntity();
        entitlement.setRevision(2); entitlement.setExpiresAt(NOW.plusSeconds(500));
        when(entitlements.requireEntitlement("customer", "offer", null, true)).thenReturn(entitlement);
        var placementEvidence = new CustomerPlacementEvidenceEntity("placement", TARGET, TargetIntent.CUSTOMER_STAGING,
            "profile", STAGING.serverUuid(), STAGING.ip(), STAGING.destinationUuid(), NOW, NOW.plusSeconds(90));
        when(placement.probeAndRecord(profile, TargetIntent.CUSTOMER_STAGING)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return placementEvidence;
        });
        when(placement.requireEvidenceById(profile, TargetIntent.CUSTOMER_STAGING, "placement"))
            .thenReturn(placementEvidence);
        when(connector.probe(snapshot)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return new CustomerSourceConnectorPrerequisiteProbe.Proof("connector-proof", NOW);
        });
    }

    @Test
    void refreshCommitsExactReleaseEvidenceAndReusesItWithoutRenewingCapacity() {
        Reservation before = store.require(allocation.id());
        service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC));
        var persisted = transaction.execute(status -> evidence.latest(snapshot.scope()).orElseThrow());
        assertThat(persisted.scope()).isEqualTo(snapshot.scope());
        assertThat(persisted.checkedAt()).isEqualTo(NOW);
        assertThat(persisted.expiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThat(persisted.connectorProofDigest()).isEqualTo("connector-proof");
        assertThat(store.require(allocation.id())).isEqualTo(before);
        assertThat(store.held()).hasSize(1);

        service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC));

        verify(connector, times(1)).probe(snapshot);
        assertThat(evidenceRows()).isEqualTo(1);
        assertThat(store.require(allocation.id())).isEqualTo(before);
    }

    @Test
    void driftDuringProbeCannotCommitEvidenceOrChangeTheAllocation() throws Exception {
        var changedScope = changedScope("credentialRevisionFingerprint", "rotated-credentials");
        var changed = new DeploymentAdmissionCustomerPrerequisiteEvidenceProvider.SourceSnapshot(changedScope,
            snapshot.runtime(), snapshot.target(), snapshot.connectorType(), snapshot.bindingRef(), snapshot.passed(),
            snapshot.expiresAt(), snapshot.maxAgeSeconds());
        when(prerequisites.sourceSnapshot(sourceContext)).thenReturn(snapshot, changed);

        assertThatThrownBy(() -> service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC)))
            .hasMessageContaining("SOURCE_PREREQUISITE_SCOPE_CHANGED_DURING_PROBE");

        assertThat(evidenceRows()).isZero();
        assertThat(store.require(allocation.id())).isEqualTo(allocation);
    }

    @Test
    void evidenceWritesRollBackAndCannotBeReusedAcrossScopeOrStorageRevisions() throws Exception {
        var proof = new CustomerSourcePrerequisiteEvidence("source-proof", snapshot.scope(), "placement",
            snapshot.passed(), "connector-proof", NOW.plusNanos(123_000), NOW.plusSeconds(60).plusNanos(456_000));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            evidence.save(proof);
            assertThat(evidence.latest(snapshot.scope())).contains(proof);
            throw new IllegalStateException("reject-owning-command");
        })).hasMessageContaining("reject-owning-command");
        assertThat(evidenceRows()).isZero();
        transaction.executeWithoutResult(status -> evidence.save(proof));
        for (String field : Set.of("tenantId", "releaseId", "allocationGeneration", "credentialRevisionFingerprint")) {
            var foreignScope = changedScope(field, "foreign-" + field);
            var foreign = transaction.execute(status -> evidence.latest(foreignScope));
            assertThat(foreign).isEmpty();
        }
        var bindingScope = changedScope("storageBindingRevision", 2L);
        var changedBinding = transaction.execute(status -> evidence.latest(bindingScope));
        var unchanged = transaction.execute(status -> evidence.latest(snapshot.scope()));
        assertThat(changedBinding).isEmpty();
        assertThat(unchanged).contains(proof);
    }

    @Test
    void postgresStoredEvidenceRejectsTimestampAndPayloadTampering() {
        service.ensureFresh(target, Operation.SOURCE_DISCOVER, null, Set.of(DocumentAudience.PUBLIC));
        jdbc.update("UPDATE deployment_source_prerequisite_evidence SET checked_at=checked_at + INTERVAL '1 second'");
        assertThatThrownBy(() -> transaction.execute(status -> evidence.latest(snapshot.scope())))
            .hasMessageContaining("SOURCE_PREREQUISITE_RECORD_MISMATCH");
        jdbc.update("UPDATE deployment_source_prerequisite_evidence SET checked_at=?,payload_fingerprint='tampered'",
            java.sql.Timestamp.from(NOW));
        assertThatThrownBy(() -> transaction.execute(status -> evidence.latest(snapshot.scope())))
            .hasMessageContaining("SOURCE_PREREQUISITE_RECORD_MISMATCH");
    }

    private CustomerSourcePrerequisiteEvidence.Scope changedScope(String field, Object value) throws Exception {
        ObjectNode changed = mapper.valueToTree(snapshot.scope());
        changed.set(field, mapper.valueToTree(value));
        return mapper.treeToValue(changed, CustomerSourcePrerequisiteEvidence.Scope.class);
    }

    private long evidenceRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM deployment_source_prerequisite_evidence", Long.class);
    }
}
