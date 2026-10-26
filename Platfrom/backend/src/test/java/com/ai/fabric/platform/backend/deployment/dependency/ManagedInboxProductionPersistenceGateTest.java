package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotEntity;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.ObjectProvider;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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

    @Test void productionPersistenceRequiresTypedPostgresWithoutRewritingReviewedIdentity() {
        String original = validator.fingerprint(h2);
        assertThatThrownBy(() -> validator.requireProductionInboxPersistence(h2))
            .hasMessageContaining("INBOX_PRODUCTION_POSTGRES_REQUIRED");
        assertThat(validator.fingerprint(h2)).isEqualTo(original);
        assertThatNoException().isThrownBy(() -> validator.requireProductionInboxPersistence(postgres()));
        var http = new ManagedDependencySpec("managed-dependencies/v1",
            List.of(ManagedDependencyFixtures.http("service", false)));
        assertThatNoException().isThrownBy(() -> validator.requireProductionInboxPersistence(http));
    }

    @ParameterizedTest @EnumSource(ManagedDependencyPrerequisites.ProvisioningPhase.class)
    void productionH2CannotEnterEitherProvisioningPhase(ManagedDependencyPrerequisites.ProvisioningPhase phase) throws Exception {
        var fixture = new LifecycleFixture(h2, true);

        assertThatThrownBy(() -> fixture.service.reserve(fixture.scope, h2, phase))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        verifyNoInteractions(fixture.provisioning, fixture.verifier);
    }

    @Test void stagingH2AndProductionPostgresAreBothRetiredBeforeProvisioning() throws Exception {
        var staging = new LifecycleFixture(h2, false);
        assertThatThrownBy(() -> staging.service.reserve(staging.scope, h2, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        var production = new LifecycleFixture(postgres(), true);
        assertThatThrownBy(() -> production.service.reserve(production.scope, postgres(), ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        verifyNoInteractions(staging.provisioning, production.provisioning, staging.verifier, production.verifier);
    }

    @Test void historicalInboxCanStillBeReadAndDisabledWithoutReenablingProvisioning() throws Exception {
        var fixture = new LifecycleFixture(h2, true);
        assertThatThrownBy(() -> fixture.service.initializeAndVerify(fixture.scope, fixture.contract(), fixture.binding(), "initialize"))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        verifyNoInteractions(fixture.provisioning, fixture.verifier);

        fixture.node.disabled = true;
        fixture.service.validateReservation(fixture.scope, "core-release:release");
        fixture.service.disableAndVerifyBinding(fixture.scope, fixture.binding());
        fixture.configureRetirement();
        assertThatThrownBy(() -> fixture.service.requireUnused(fixture.scope, fixture.graph.getRevision(), "retire"))
            .hasMessageContaining("DEPENDENCY_CORE_RETIREMENT_UNSUPPORTED");
        fixture.service.releaseReservation(fixture.scope, "core-release:release");

        verify(fixture.verifier).disableAndVerify(fixture.scope, fixture.contract(), fixture.binding());
        verify(fixture.verifier, never()).requireUnused(any(), any(), any(), any());
        fixture.graph.setCustomerId("different-owner");
        assertThatThrownBy(() -> fixture.service.releaseReservation(fixture.scope, "core-release:release"))
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

    private final class LifecycleFixture {
        final ManagedDependencyCoreReleaseAuthorization core = mock(ManagedDependencyCoreReleaseAuthorization.class);
        final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
        final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
        final RuntimeAuthoritySnapshotRepository snapshots = mock(RuntimeAuthoritySnapshotRepository.class);
        final PlatformManagedProductProvisioningService provisioning = mock(PlatformManagedProductProvisioningService.class);
        final ManagedDependencyInitializationVerifier verifier = mock(ManagedDependencyInitializationVerifier.class);
        final ManagedDependencyGraphEntity graph = new ManagedDependencyGraphEntity();
        final ManagedDependencyState state = new ManagedDependencyState();
        final ManagedDependencyState.Node node = new ManagedDependencyState.Node();
        final ManagedDependencyPrerequisites.Scope scope;
        final ProductionManagedDependencyPrerequisites service;
        final ManagedDependencySpec spec;

        @SuppressWarnings("unchecked")
        LifecycleFixture(ManagedDependencySpec spec, boolean production) throws Exception {
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
            graph.setBindingFingerprint(bindingFingerprint); graph.setReservationRef("core-release:release"); graph.setRevision(7);
            graph.setStatus("READY");
            when(graphs.read("graph")).thenReturn(graph); when(graphs.spec(graph)).thenReturn(spec);
            when(graphs.state(graph)).thenReturn(state);
            node.slot = spec.services().getFirst().slot(); node.serviceRef = "managed-inbox"; node.accountId = "account";
            node.bindingId = "binding"; node.applicationUuid = "application"; node.baseUrl = "https://inbox.example.test";
            node.status = "READY"; state.nodes.add(node);
            when(core.find(scope, graph, spec)).thenReturn(Optional.of(
                new ManagedDependencyCoreReleaseAuthorization.Proof("release", "version-config", production)));
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
            service = new ProductionManagedDependencyPrerequisites(core, graphs,
                mock(DeploymentSourceArtifactRepository.class), new DeploymentSourceCapabilityManifestService(mapper),
                snapshots, releases, mock(DeploymentReleaseEndpointService.class), provisioning,
                verifiers, mapper, mock(ManagedDependencyArtifactPolicy.class));
        }

        String contract() { return spec.services().getFirst().initializationContract(); }
        ManagedDependencyPrerequisites.Binding binding() {
            return new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl,
                node.accountId, node.bindingId, Map.copyOf(node.secrets));
        }
        void configureRetirement() {
            graph.setStatus("RETIRING");
        }
    }
}
