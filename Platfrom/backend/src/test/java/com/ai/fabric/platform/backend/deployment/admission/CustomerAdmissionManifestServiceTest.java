package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class CustomerAdmissionManifestServiceTest extends AdmissionFixture {
    private final DeploymentService deployments = mock(DeploymentService.class);
    private final DeploymentDocumentBindingResolver documentBindings = mock(DeploymentDocumentBindingResolver.class);
    private final ReviewedManagedDependencyEvidenceService dependencyEvidence =
        mock(ReviewedManagedDependencyEvidenceService.class);
    private final PlatformAuditService audit = mock(PlatformAuditService.class);
    private CustomerAdmissionManifestService service;
    private DeploymentEntity deployment;
    private DeploymentVersionEntity version;
    private Authorization authorization;

    @BeforeEach
    void setup() {
        Budget budget = new Budget(capacity(8), vector(0), vector(0), "reviewed-measurement",
            NOW.minusSeconds(1), NOW.plusSeconds(10_000));
        FleetPolicy policy = new FleetPolicy("policy1", budget,
            Map.of(TARGET, new TargetBudget(TARGET, Intent.CUSTOMER_STAGING, STAGING, "profile-fingerprint", budget)),
            policy(8).lifecycle(), "restore-proof", "privacy-proof", "support-proof");
        store.approvePolicy(policy, "operator", NOW);
        store.approveGrant(new CustomerGrant("customer", capacity(8), Map.of(TARGET, capacity(8)),
            NOW.plusSeconds(5_000), "verified-owner", "payer", "keep-entitlement"), "operator", NOW);

        deployment = new DeploymentEntity();
        deployment.setId("deployment1");
        deployment.setCustomerId("customer");
        deployment.setTenantId("tenant");
        version = new DeploymentVersionEntity();
        version.setId("version");
        version.setDeploymentId("deployment1");
        version.setStatus("PUBLISHED");
        version.setConfigHash("config-hash");

        ReviewedTopology topology = new ReviewedTopology(1, 3_750, 5_888, 16_384, 2, 2, 4, 1);
        Definition definition = definition(topology);
        var context = new com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.AdmissionContext(
            "customer", "tenant", "deployment1", null, TARGET, TargetIntent.CUSTOMER_STAGING,
            "offer", "offer-fingerprint", 2, "placement", Operation.APPLY, "operation",
            definition.limits(), topology);
        authorization = new Authorization(context, definition);

        var documentSnapshot = mapper.createObjectNode();
        documentSnapshot.put("schema", "DOCUMENT_STORAGE_BINDING_V1");
        documentSnapshot.put("resourceHandleId", "binding");
        when(documentBindings.snapshot(deployment, version, TARGET)).thenReturn(documentSnapshot);
        when(deployments.prepareAuthorizedRuntimeAuthorityCandidate(deployment, version, TARGET))
            .thenReturn(new RuntimeAuthorityCandidateEvidence("deployment1", TARGET, "version", "snapshot",
                "a".repeat(64), 1, List.of("https://app.example.test")));
        when(dependencyEvidence.resolve(any(), any())).thenReturn(java.util.Optional.of(
            new ReviewedManagedDependencyEvidenceService.Evidence(
                "template-version", "d".repeat(64), "managed-dependency-spec:" + "d".repeat(64))));
        service = new CustomerAdmissionManifestService(store, json, deployments, documentBindings,
            dependencyEvidence, audit);
    }

    @Test
    void derivesAndPersistsExactManifestWithoutCallerSelectedCapacityOrReferences() {
        Manifest first = service.prepare(deployment, version, authorization);
        Manifest replay = service.prepare(deployment, version, authorization);

        assertThat(replay).isEqualTo(first);
        assertThat(first.id()).startsWith("amf-").hasSize(64);
        assertThat(first.bindingFingerprint()).isEqualTo("a".repeat(64));
        assertThat(first.demand().amounts())
            .containsEntry(Dimension.CPU_MILLICORES, 3_750L)
            .containsEntry(Dimension.MEMORY_BYTES, 5_888L * 1024L * 1024L)
            .containsEntry(Dimension.DISK_BYTES, 16_384L * 1024L * 1024L)
            .containsEntry(Dimension.DATABASES, 2L)
            .containsEntry(Dimension.INDEXES, 2L)
            .containsEntry(Dimension.DEPENDENCIES, 4L);
        assertThat(first.customerPrerequisiteReferences()).containsKeys(
            Prerequisite.APPROVED_POLICY, Prerequisite.IMMUTABLE_ARTIFACTS, Prerequisite.STORAGE,
            Prerequisite.IDENTITY, Prerequisite.NOTIFICATIONS, Prerequisite.OPERATING_POLICY);
        assertThat(first.customerPrerequisiteReferences()).doesNotContainKey(Prerequisite.CAPACITY);
        assertThat(first.customerPrerequisiteReferences().get(Prerequisite.STORAGE)).startsWith("document-binding:");
        assertThat(first.prerequisiteReferences()).contains("managed-dependency-spec:" + "d".repeat(64));
        assertThat(store.reviewed("MANIFEST", first.id(), Manifest.class)).isEqualTo(first);
    }

    @Test
    void generatedManifestIsConsumableByManagedDependencyLedger() {
        Manifest manifest = service.prepare(deployment, version, authorization);
        var context = store.context("customer", "tenant", deployment.getId(), manifest.id());
        var reservation = store.reserve(context, new Reserve("apply", manifest.id()),
            evidence(context, NOW), "owner", NOW);
        var ledger = new ManagedDependencyAdmissionLedger(store, json,
            java.time.Clock.fixed(NOW, java.time.ZoneOffset.UTC));

        var result = ledger.require(new ManagedDependencyAdmissionLedger.Requirement(
            "customer", "tenant", deployment.getId(), TARGET, version.getId(), "a".repeat(64),
            "d".repeat(64), 1_000, 2L * 1024L * 1024L * 1024L,
            6L * 1024L * 1024L * 1024L, 1, 4), reservation.id());

        assertThat(result.reservationId()).isEqualTo(reservation.id());
    }

    @Test
    void missingRequiredStorageFailsBeforeAuthorityMaterialIsGenerated() {
        when(documentBindings.snapshot(deployment, version, TARGET)).thenReturn(mapper.createObjectNode());

        assertThatThrownBy(() -> service.prepare(deployment, version, authorization))
            .hasMessageContaining("CUSTOMER_ADMISSION_STORAGE_BINDING_REQUIRED");
        verifyNoInteractions(deployments);
    }

    private Definition definition(ReviewedTopology topology) {
        return new Definition("1", "Reviewed offer", "template",
            new PluginPin("template-plugin", "template-version", "manifest-hash"), List.of(),
            List.of(new ArtifactPin("runtime", "artifact-hash")),
            List.of(new TargetPermission(TARGET, TargetIntent.CUSTOMER_STAGING, "profile-fingerprint")),
            EnumSet.of(Operation.APPLY), List.of(),
            List.of(new BindingSlot("document-storage", BindingKind.CUSTOMER_S3, Set.of("reviewed-s3"))),
            Set.of(DocumentAudience.PUBLIC), EnumSet.allOf(Prerequisite.class),
            new ReviewedLimits("reviewed-measurement", 1, 1, 4_000, 6_144, 16_384, 2, 2, 4,
                1, 24, 24, 24), topology, "approved-policy", "operating-policy", "identity-policy", "storage-policy");
    }

    private Vector capacity(long value) {
        return with(with(with(vector(value), Dimension.CPU_MILLICORES, 20_000),
            Dimension.MEMORY_BYTES, 64L * 1024L * 1024L * 1024L),
            Dimension.DISK_BYTES, 256L * 1024L * 1024L * 1024L);
    }
}
