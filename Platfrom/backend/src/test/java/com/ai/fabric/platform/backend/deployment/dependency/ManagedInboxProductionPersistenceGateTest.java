package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferDeploymentEntity;
import com.ai.fabric.platform.backend.customerselfservice.entity.CustomerOfferEntity;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.*;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferRepository;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferCodec;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts;
import com.ai.fabric.platform.backend.deployment.admission.ManagedDependencyAdmissionLedger;
import com.ai.fabric.platform.backend.deployment.admission.ReviewedManagedDependencyEvidenceService;
import com.ai.fabric.platform.backend.deployment.admission.AdmissionJson;
import com.ai.fabric.platform.backend.deployment.admission.CustomerAdmissionManifestService;
import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotEntity;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.retirement.*;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentDocumentBindingResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManagedInboxProductionPersistenceGateTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
    private final ManagedDependencySpec h2 = new ManagedDependencySpec("managed-dependencies/v1",
        List.of(ManagedDependencyFixtures.topology().services().getLast()));

    @Test void productionAdmissionRejectsH2ButStagingRetainsTheExactReviewedIdentity() throws Exception {
        var fixture = new ReviewedFixture(h2, 1);
        String fingerprint = validator.fingerprint(h2);

        assertThatThrownBy(() -> fixture.service.resolveForProduction(fixture.definition, envelope()))
            .hasMessageContaining("INBOX_PRODUCTION_POSTGRES_REQUIRED");
        assertThat(fixture.service.resolve(fixture.definition, envelope()).orElseThrow().fingerprint())
            .isEqualTo(fingerprint);
        assertThat(fixture.record.getFingerprint()).isEqualTo(fingerprint);
    }

    @Test void productionReadbackRechecksTheExactDeploymentOfferAndPersistence() throws Exception {
        var fixture = new ReviewedFixture(h2, 1);
        fixture.associate();

        assertThatThrownBy(() -> fixture.service.resolveForProductionDeployment("customer", "tenant", "deployment",
            "offer-topology:offer:offer-fingerprint", envelope()))
            .hasMessageContaining("INBOX_PRODUCTION_POSTGRES_REQUIRED");
        assertThat(fixture.service.resolveForDeployment("customer", "tenant", "deployment",
            "offer-topology:offer:offer-fingerprint", envelope())).isPresent();
        assertThatThrownBy(() -> fixture.service.resolveForProductionDeployment("other", "tenant", "deployment",
            "offer-topology:offer:offer-fingerprint", envelope()))
            .hasMessageContaining("REVIEWED_DEPENDENCY_OFFER_SCOPE_CHANGED");
        assertThatThrownBy(() -> fixture.service.resolveForProductionDeployment("customer", "tenant", "deployment",
            "offer-topology:offer:stale", envelope()))
            .hasMessageContaining("REVIEWED_DEPENDENCY_OFFER_CHANGED");
    }

    @Test void productionManifestRejectsH2BeforePolicyStorageAuthorityOrManifestEffects() throws Exception {
        var fixture = new ReviewedFixture(h2, 1);
        var deployment = new DeploymentEntity(); deployment.setId("deployment");
        deployment.setCustomerId("customer"); deployment.setTenantId("tenant");
        var version = new DeploymentVersionEntity(); version.setId("version");
        version.setDeploymentId("deployment"); version.setStatus("PUBLISHED");
        var context = mock(AdmissionContext.class);
        when(context.deploymentId()).thenReturn("deployment"); when(context.customerId()).thenReturn("customer");
        when(context.tenantId()).thenReturn("tenant"); when(context.topology()).thenReturn(fixture.definition.topology());
        when(context.intent()).thenReturn(TargetIntent.CUSTOMER_PRODUCTION);
        var deployments = mock(DeploymentService.class);
        var documents = mock(DeploymentDocumentBindingResolver.class);
        var audit = mock(PlatformAuditService.class);
        var manifests = new CustomerAdmissionManifestService(null, new AdmissionJson(mapper), deployments,
            documents, fixture.service, audit);

        assertThatThrownBy(() -> manifests.prepare(deployment, version, new Authorization(context, fixture.definition)))
            .hasMessageContaining("INBOX_PRODUCTION_POSTGRES_REQUIRED");
        verifyNoInteractions(deployments, documents, audit);
    }

    @Test void productionAcceptsTypedOwnedPostgresWithoutAnApplicationVolume() throws Exception {
        var fixture = new ReviewedFixture(postgres(), 1);
        assertThat(fixture.service.resolveForProduction(fixture.definition, envelope())).isPresent();
        assertThatNoException().isThrownBy(() -> fixture.service.requireProductionPersistence(fixture.definition));
    }

    @Test void externalInboxWithoutManagedSpecAndNonInboxServicesAreUnaffected() throws Exception {
        var external = new ReviewedFixture(h2, 0);
        when(external.specs.findByTemplateVersionIdAndActiveTrue("template-version")).thenReturn(List.of());
        assertThat(external.service.resolveForProduction(external.definition, envelope())).isEmpty();
        assertThatNoException().isThrownBy(() -> external.service.requireProductionPersistence(external.definition));

        var http = new ReviewedFixture(new ManagedDependencySpec("managed-dependencies/v1",
            List.of(ManagedDependencyFixtures.http("service", false))), 1);
        assertThat(http.service.resolveForProduction(http.definition, envelope())).isPresent();
    }

    @Test void persistenceDoesNotBypassReviewedFingerprintOwnershipOrEnvelope() throws Exception {
        var fixture = new ReviewedFixture(postgres(), 1);
        fixture.record.setFingerprint("f".repeat(64));
        assertThatThrownBy(() -> fixture.service.resolveForProduction(fixture.definition, envelope()))
            .hasMessageContaining("REVIEWED_DEPENDENCY_SPEC_FINGERPRINT_CHANGED");
        fixture.record.setFingerprint(validator.fingerprint(postgres()));
        assertThatThrownBy(() -> fixture.service.resolveForProduction(fixture.definition,
            new AdmissionContracts.Vector(Map.of())))
            .hasMessageContaining("REVIEWED_DEPENDENCY_CAPACITY_EXCEEDS_ADMISSION_ENVELOPE");
        when(fixture.artifacts.findById(anyString())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> fixture.service.requireProductionPersistence(fixture.definition))
            .hasMessageContaining("REVIEWED_DEPENDENCY_OWNERSHIP_UNPROVEN");
    }

    @ParameterizedTest @EnumSource(ManagedDependencyPrerequisites.ProvisioningPhase.class)
    void productionH2CannotEnterEitherProvisioningPhase(ManagedDependencyPrerequisites.ProvisioningPhase phase) throws Exception {
        var fixture = new LifecycleFixture(h2, AdmissionContracts.Intent.CUSTOMER_PRODUCTION);

        assertThatThrownBy(() -> fixture.service.reserve(fixture.scope, h2, phase))
            .hasMessageContaining("INBOX_PRODUCTION_POSTGRES_REQUIRED");
        verifyNoInteractions(fixture.provisioning, fixture.releases, fixture.verifier);
    }

    @Test void stagingH2AndProductionPostgresCanEnterFoundationProvisioning() throws Exception {
        var staging = new LifecycleFixture(h2, AdmissionContracts.Intent.CUSTOMER_STAGING);
        assertThat(staging.service.reserve(staging.scope, h2, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION)
            .reservationRef()).isEqualTo("reservation");
        var production = new LifecycleFixture(postgres(), AdmissionContracts.Intent.CUSTOMER_PRODUCTION);
        assertThat(production.service.reserve(production.scope, postgres(), ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION)
            .reservationRef()).isEqualTo("reservation");
    }

    @Test void historicalProductionH2CannotInitializeButCanStillDisableRevokeRetireAndRelease() throws Exception {
        var fixture = new LifecycleFixture(h2, AdmissionContracts.Intent.CUSTOMER_PRODUCTION);
        assertThatThrownBy(() -> fixture.service.initializeAndVerify(fixture.scope, fixture.contract(), fixture.binding(), "initialize"))
            .hasMessageContaining("INBOX_PRODUCTION_POSTGRES_REQUIRED");
        verifyNoInteractions(fixture.provisioning, fixture.verifier);

        fixture.node.disabled = true;
        fixture.service.validateReservation(fixture.scope, "reservation");
        fixture.service.disableAndVerifyBinding(fixture.scope, fixture.binding());
        fixture.service.verifyOperatorRevocation(fixture.scope, fixture.binding(), List.of("retired-secret"), "revoke", true);
        fixture.configureRetirement();
        fixture.service.requireUnused(fixture.scope, fixture.graph.getRevision(), "retire");
        fixture.service.releaseReservation(fixture.scope, "reservation");

        verify(fixture.verifier).disableAndVerify(fixture.scope, fixture.contract(), fixture.binding());
        verify(fixture.verifier).verifyOperatorRevocation(fixture.scope, fixture.contract(), fixture.binding(),
            List.of("retired-secret"), "revoke", true);
        verify(fixture.verifier).requireUnused(fixture.scope, fixture.contract(), fixture.binding(), "retire");
        fixture.graph.setCustomerId("different-owner");
        assertThatThrownBy(() -> fixture.service.releaseReservation(fixture.scope, "reservation"))
            .hasMessageContaining("DEPENDENCY_SCOPE_CHANGED");
    }

    private ManagedDependencySpec postgres() {
        var original = h2.services().getFirst();
        var environment = new TreeMap<>(original.environment());
        environment.put("BASIC_INBOX_JDBC_URL", new Value(ValueKind.DATABASE_JDBC, null));
        environment.put("BASIC_INBOX_JDBC_USERNAME", new Value(ValueKind.DATABASE_USERNAME, null));
        environment.put("BASIC_INBOX_JDBC_PASSWORD", new Value(ValueKind.SECRET, "inbox-db"));
        var purposes = new HashSet<>(original.secretPurposes()); purposes.add("inbox-db");
        var database = new Database(ManagedDependencyFixtures.IMAGE, "inbox-db", new Capacity(250, 512, 4096, 1));
        return new ManagedDependencySpec(h2.schemaVersion(), List.of(new Service(original.slot(), original.contract(),
            original.image(), original.sourceArtifactId(), original.sourceCommit(), original.port(), original.healthPath(),
            original.capacity(), purposes, environment, database, List.of(), original.dependsOn(), original.initializationContract())));
    }

    private AdmissionContracts.Vector envelope() {
        var values = new EnumMap<AdmissionContracts.Dimension, Long>(AdmissionContracts.Dimension.class);
        for (var dimension : AdmissionContracts.Dimension.values()) values.put(dimension, 0L);
        values.put(AdmissionContracts.Dimension.CPU_MILLICORES, 4000L);
        values.put(AdmissionContracts.Dimension.MEMORY_BYTES, 6144L * 1024 * 1024);
        values.put(AdmissionContracts.Dimension.DISK_BYTES, 16384L * 1024 * 1024);
        values.put(AdmissionContracts.Dimension.DATABASES, 2L);
        values.put(AdmissionContracts.Dimension.DEPENDENCIES, 4L);
        return new AdmissionContracts.Vector(values);
    }

    private final class ReviewedFixture {
        final ManagedDependencySpecRepository specs = mock(ManagedDependencySpecRepository.class);
        final CustomerOfferDeploymentRepository associations = mock(CustomerOfferDeploymentRepository.class);
        final CustomerOfferRepository offers = mock(CustomerOfferRepository.class);
        final DeploymentSourceArtifactRepository artifacts = mock(DeploymentSourceArtifactRepository.class);
        final CustomerOfferCodec codec = new CustomerOfferCodec(mapper);
        final ManagedDependencySpecEntity record = new ManagedDependencySpecEntity();
        final Definition definition;
        final ReviewedManagedDependencyEvidenceService service = new ReviewedManagedDependencyEvidenceService(
            specs, validator, associations, offers, codec, mapper,
            new ManagedDependencyArtifactPolicy(artifacts, new DeploymentSourceCapabilityManifestService(mapper)));

        ReviewedFixture(ManagedDependencySpec spec, int dependencies) throws Exception {
            record.setTemplateVersionId("template-version"); record.setSpecJson(mapper.writeValueAsString(spec));
            record.setFingerprint(validator.fingerprint(spec)); record.setActive(true);
            when(specs.findByTemplateVersionIdAndActiveTrue("template-version")).thenReturn(List.of(record));
            for (var service : spec.services()) when(artifacts.findById(service.sourceArtifactId()))
                .thenReturn(Optional.of(ManagedDependencyFixtures.ownedAiArtifact(service)));
            definition = new Definition("1", "Reviewed offer", "template",
                new PluginPin("template", "template-version", "manifest"), List.of(), List.of(),
                List.of(new TargetPermission("target", TargetIntent.CUSTOMER_PRODUCTION, "profile")),
                EnumSet.of(Operation.APPLY), List.of(), List.of(), Set.of(DocumentAudience.PUBLIC),
                EnumSet.allOf(Prerequisite.class),
                new ReviewedLimits("measurement", 1, 1, 4000, 6144, 16384, 2, 2, 4, 1, 24, 24, 24),
                new ReviewedTopology(1, 3750, 5888, 16384, 2, 2, dependencies, 1),
                "approved", "operating", "identity", "storage");
        }

        void associate() {
            when(associations.findById("deployment")).thenReturn(Optional.of(new CustomerOfferDeploymentEntity(
                "deployment", "customer", "tenant", "offer", "target", "offer-fingerprint", 1, "operation", Instant.now())));
            when(offers.findById("offer")).thenReturn(Optional.of(new CustomerOfferEntity("offer", codec.write(definition),
                "offer-fingerprint", "review", "operator", Instant.now())));
        }
    }

    private final class LifecycleFixture {
        final ManagedDependencyAdmissionLedger admission = mock(ManagedDependencyAdmissionLedger.class);
        final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
        final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
        final RuntimeAuthoritySnapshotRepository snapshots = mock(RuntimeAuthoritySnapshotRepository.class);
        final PlatformManagedProductProvisioningService provisioning = mock(PlatformManagedProductProvisioningService.class);
        final TargetRetirementOperationRepository operations = mock(TargetRetirementOperationRepository.class);
        final TargetRetirementGateRepository gates = mock(TargetRetirementGateRepository.class);
        final ManagedDependencyInitializationVerifier verifier = mock(ManagedDependencyInitializationVerifier.class);
        final ManagedDependencyGraphEntity graph = new ManagedDependencyGraphEntity();
        final ManagedDependencyState state = new ManagedDependencyState();
        final ManagedDependencyState.Node node = new ManagedDependencyState.Node();
        final ManagedDependencyPrerequisites.Scope scope;
        final ProductionManagedDependencyPrerequisites service;
        final ManagedDependencySpec spec;

        @SuppressWarnings("unchecked")
        LifecycleFixture(ManagedDependencySpec spec, AdmissionContracts.Intent intent) throws Exception {
            this.spec = spec;
            var authority = new RuntimeAuthorityBinding("customer", "tenant", "deployment", "target", "version",
                "version-config", 3, "private-issuer", "private-audience", "public-issuer", "public-audience",
                true, true, List.of("https://customer.example.test"), Map.of());
            String bindingJson = mapper.writeValueAsString(authority);
            String bindingFingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(bindingJson.getBytes(StandardCharsets.UTF_8)));
            scope = new ManagedDependencyPrerequisites.Scope("graph", "customer", "tenant", "deployment", "target",
                "version", "release", validator.fingerprint(spec), bindingFingerprint);
            graph.setId("graph"); graph.setCustomerId("customer"); graph.setTenantId("tenant");
            graph.setDeploymentId("deployment"); graph.setTargetProfileId("target"); graph.setVersionId("version");
            graph.setReleaseId("release"); graph.setSpecFingerprint(scope.specFingerprint());
            graph.setBindingFingerprint(bindingFingerprint); graph.setReservationRef("reservation"); graph.setRevision(7);
            when(graphs.read("graph")).thenReturn(graph); when(graphs.spec(graph)).thenReturn(spec);
            when(graphs.state(graph)).thenReturn(state);
            node.slot = spec.services().getFirst().slot(); node.serviceRef = "managed-inbox"; node.accountId = "account";
            node.bindingId = "binding"; node.applicationUuid = "application"; node.baseUrl = "https://inbox.example.test";
            node.status = "READY"; state.nodes.add(node);
            var evidence = new ManagedDependencyAdmissionLedger.Evidence(
                "reservation", "generation", 2, intent, "manifest", "a".repeat(64), bindingFingerprint,
                new AdmissionContracts.Placement("server", "destination", "127.0.0.1"), "evidence", Instant.now().plusSeconds(300));
            when(admission.require(any(), any())).thenReturn(evidence);
            when(admission.requireForExecution(any(), any())).thenReturn(evidence);
            var snapshot = new RuntimeAuthoritySnapshotEntity(); snapshot.setReleaseId("release");
            snapshot.setFingerprint(bindingFingerprint); snapshot.setBindingJson(bindingJson);
            when(snapshots.findByReleaseId("release")).thenReturn(Optional.of(snapshot));
            var release = new DeploymentReleaseEntity(); release.setId("release"); release.setDeploymentId("deployment");
            release.setTargetProfileId("target"); release.setDeploymentVersionId("version");
            release.setRuntimeAuthorityFingerprint(bindingFingerprint);
            when(releases.findById("release")).thenReturn(Optional.of(release));
            ObjectProvider<ManagedDependencyInitializationVerifier> verifiers = mock(ObjectProvider.class);
            when(verifiers.orderedStream()).thenAnswer(ignored -> Stream.of(verifier));
            when(verifier.supports(contract())).thenReturn(true);
            service = new ProductionManagedDependencyPrerequisites(admission,
                mock(ManagedDependencyCoreReleaseAuthorization.class), graphs,
                mock(DeploymentSourceArtifactRepository.class), new DeploymentSourceCapabilityManifestService(mapper),
                snapshots, releases, mock(DeploymentReleaseEndpointService.class), provisioning, operations, gates,
                verifiers, mapper, mock(ManagedDependencyArtifactPolicy.class));
        }

        String contract() { return spec.services().getFirst().initializationContract(); }
        ManagedDependencyPrerequisites.Binding binding() {
            return new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl,
                node.accountId, node.bindingId, Map.copyOf(node.secrets));
        }
        void configureRetirement() {
            graph.setStatus("RETIRING");
            var operation = new TargetRetirementOperationEntity(); operation.setId("retirement");
            operation.setCustomerId("customer"); operation.setState(TargetRetirementContracts.State.RUNNING);
            operation.setStep(TargetRetirementContracts.Step.DEPENDENCIES); operation.setWorkerToken("worker");
            operation.setLeaseExpiresAt(Instant.now().plusSeconds(60)); operation.setDestructiveStarted(true); operation.setFenceEpoch(4);
            when(operations.findByDeploymentIdAndTargetProfileIdAndOperationKey("deployment", "target", "retire"))
                .thenReturn(Optional.of(operation));
            var gate = new TargetRetirementGateEntity(); gate.setState("RETIRING"); gate.setOperationId("retirement"); gate.setEpoch(4);
            when(gates.findByDeploymentIdAndTargetProfileId("deployment", "target")).thenReturn(Optional.of(gate));
        }
    }
}
