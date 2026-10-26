package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotEntity;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileConfig;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProductionManagedDependencyPrerequisitesTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ManagedDependencyCoreReleaseAuthorization core = mock(ManagedDependencyCoreReleaseAuthorization.class);
    private final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
    private final DeploymentSourceArtifactRepository artifacts = mock(DeploymentSourceArtifactRepository.class);
    private final RuntimeAuthoritySnapshotRepository snapshots = mock(RuntimeAuthoritySnapshotRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentReleaseEndpointService endpoints = mock(DeploymentReleaseEndpointService.class);
    private final PlatformManagedProductProvisioningService provisioning = mock(PlatformManagedProductProvisioningService.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ManagedDependencyInitializationVerifier> verifiers = mock(ObjectProvider.class);
    private final DeploymentSourceCapabilityManifestService manifests =
        new DeploymentSourceCapabilityManifestService(mapper);
    private final ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
    private ManagedDependencyGraphEntity graph;
    private ManagedDependencyState state;
    private ManagedDependencySpec spec;
    private ManagedDependencyPrerequisites.Scope scope;
    private RuntimeAuthorityBinding authority;
    private ProductionManagedDependencyPrerequisites prerequisites;

    @BeforeEach
    void setUp() throws Exception {
        Map<String, ManagedDependencySpec.Value> environment = new java.util.TreeMap<>();
        environment.put("API_KEY", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.SECRET, "api"));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_KEY", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.SECRET, "management"));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_CUSTOMER_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.CUSTOMER_ID, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_TENANT_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.TENANT_ID, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_DEPLOYMENT_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.DEPLOYMENT_ID, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_TARGET_PROFILE_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.TARGET_ID, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_VERSION_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.VERSION_ID, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_SLOT", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.LITERAL, "service"));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_SERVICE_REF", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.SERVICE_REF, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_ACCOUNT_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.ACCOUNT_ID, null));
        environment.put("LOOMAI_MANAGED_DEPENDENCY_BINDING_ID", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.BINDING_ID, null));
        var service = new ManagedDependencySpec.Service("service", ManagedDependencySpec.Contract.HTTP_SERVICE,
            "registry.example.test/service@sha256:" + "1".repeat(64), "artifact-service", "2".repeat(40),
            8080, "/actuator/health", new ManagedDependencySpec.Capacity(250, 512, 128, 1),
            Set.of("api", "management"), environment, null, List.of(), List.of(), "test-init/v1");
        spec = new ManagedDependencySpec("managed-dependencies/v1", List.of(service));
        String specFingerprint = validator.fingerprint(spec);
        authority = new RuntimeAuthorityBinding("customer", "tenant", "deployment", "target", "version",
            "version-config", 3, "private-issuer", "private-audience", "public-issuer", "public-audience",
            true, true, List.of("https://customer.example.test"), Map.of());
        String bindingJson = mapper.writeValueAsString(authority);
        String bindingFingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(bindingJson.getBytes(StandardCharsets.UTF_8)));
        scope = new ManagedDependencyPrerequisites.Scope("graph", "customer", "tenant", "deployment",
            "target", "version", "release", specFingerprint, bindingFingerprint);

        graph = new ManagedDependencyGraphEntity();
        graph.setId("graph"); graph.setCustomerId("customer"); graph.setTenantId("tenant");
        graph.setDeploymentId("deployment"); graph.setTargetProfileId("target"); graph.setVersionId("version");
        graph.setReleaseId("release");
        graph.setSpecFingerprint(specFingerprint); graph.setBindingFingerprint(bindingFingerprint);
        graph.setReservationRef("core-release:release"); graph.setStatus("READY"); graph.setRevision(7);
        state = new ManagedDependencyState();
        var node = new ManagedDependencyState.Node();
        node.slot = "service"; node.serviceRef = "managed-service"; node.accountId = "account";
        node.bindingId = "binding"; node.applicationUuid = "application";
        node.baseUrl = "https://managed-service.example.test"; node.secrets.put("api", "MANAGED_API");
        node.status = "READY"; node.initialized = true;
        state.nodes.add(node);
        when(graphs.read("graph")).thenReturn(graph);
        when(graphs.spec(graph)).thenReturn(spec);
        when(graphs.state(graph)).thenReturn(state);

        var snapshot = new RuntimeAuthoritySnapshotEntity();
        snapshot.setId("snapshot"); snapshot.setDeploymentId("deployment"); snapshot.setTargetProfileId("target");
        snapshot.setVersionId("version"); snapshot.setFingerprint(bindingFingerprint);
        snapshot.setReleaseId("release");
        snapshot.setBindingJson(bindingJson); snapshot.setCreatedAt(Instant.now());
        when(snapshots.findByReleaseId("release")).thenReturn(Optional.of(snapshot));
        var release = new DeploymentReleaseEntity();
        release.setId("release"); release.setDeploymentId("deployment"); release.setDeploymentVersionId("version");
        release.setTargetProfileId("target"); release.setRuntimeAuthorityFingerprint(bindingFingerprint);
        when(releases.findById("release")).thenReturn(Optional.of(release));
        when(verifiers.orderedStream()).thenReturn(Stream.empty());

        prerequisites = new ProductionManagedDependencyPrerequisites(core, graphs, artifacts, manifests,
            snapshots, releases, endpoints, provisioning, verifiers, mapper,
            new ManagedDependencyArtifactPolicy(artifacts, manifests));
        when(artifacts.findById(service.sourceArtifactId()))
            .thenReturn(Optional.of(ManagedDependencyFixtures.ownedAiArtifact(service)));
        useCoreAuthorization(false);
    }

    @Test
    void workerRejectsUnprovenOwnershipBeforeAdmissionOrRemoteEffects() {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(spec.services().getFirst());
        artifact.setCapabilityManifestJson("{}");
        artifact.setCapabilityManifestHash(null);
        when(artifacts.findById(artifact.getId())).thenReturn(Optional.of(artifact));
        clearInvocations(core, provisioning);
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec,
            ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_OWNERSHIP_UNPROVEN");
        verifyNoInteractions(provisioning);
    }

    @Test
    void reserveReplaysCoreAuthorizationAndReturnsOnlyFrozenAuthorityData() {
        var first = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION);
        var replay = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION);
        assertThat(replay).isEqualTo(first);
        assertThat(first.reservationRef()).isNull();
        assertThat(first.authorityReference()).isEqualTo("core-release:release");
        assertThat(first.origins()).containsExactly("https://customer.example.test");
        assertThat(first.callbacks()).isEmpty();
        verify(core, times(2)).find(scope, graph, spec);
    }

    @Test
    void historicalReservationCannotReplaceMissingCoreAuthorization() {
        graph.setReservationRef("historical-reservation");
        when(core.find(scope, graph, spec)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_CORE_RELEASE_AUTHORIZATION_REQUIRED");
        assertThatThrownBy(() -> prerequisites.validateReservation(scope, "historical-reservation"))
            .hasMessageContaining("DEPENDENCY_CORE_RELEASE_AUTHORIZATION_REQUIRED");
        verifyNoInteractions(provisioning);
    }
    @Test
    void coreReleaseAuthorizationDoesNotManufactureOrReadACapacityReservation() {
        useCoreAuthorization(false);
        graph.setReservationRef(null);

        var result = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION);

        assertThat(result.reservationRef()).isNull();
        assertThat(result.coreReleaseAuthorizationRef()).isEqualTo("release");
        assertThat(result.authorityReference()).isEqualTo("core-release:release");
        graph.setReservationRef(result.authorityReference());
        prerequisites.validateReservation(scope, graph.getReservationRef());
        prerequisites.validateExecutionReservation(scope, graph.getReservationRef());
        prerequisites.validateArtifact(scope, spec.services().getFirst());
        verifyNoInteractions(provisioning);
    }

    @Test
    void coreReleaseCannotUseAnotherAuthorityReferenceOrFrozenConfig() {
        useCoreAuthorization(false);
        assertThatThrownBy(() -> prerequisites.validateExecutionReservation(scope, "core-release:other"))
            .hasMessageContaining("DEPENDENCY_AUTHORIZATION_DRIFT");
        when(core.find(scope, graph, spec)).thenReturn(Optional.of(
            new ManagedDependencyCoreReleaseAuthorization.Proof("release", "changed-config", false)));
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec,
            ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_AUTHORITY_CONFIG_CHANGED");
        verifyNoInteractions(provisioning);
    }



    @Test
    void coreAuthorizationDoesNotSupplyMissingSecretsOrUnprovenCallbacks() throws Exception {
        configureSpecAndSecrets(new ManagedDependencySpec(spec.schemaVersion(), spec.services(), List.of(
            new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_INBOX_CALLBACK_SIGNING_SECRET", new ManagedDependencySpec.Value(
                    ManagedDependencySpec.ValueKind.TARGET_SECRET, "conversation-handover-callback-signing")))), Map.of());
        useCoreAuthorization(false);
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_TARGET_SECRET_UNAVAILABLE");

        existingProductionInbox();
        useCoreAuthorization(true);
        when(endpoints.connectorBaseUrl(releases.findById("release").orElseThrow())).thenReturn(null);
        clearInvocations(core);
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        verifyNoInteractions(provisioning);
    }

    private void useCoreAuthorization(boolean production) {
        graph.setReservationRef("core-release:release");
        when(core.find(scope, graph, spec)).thenReturn(Optional.of(
            new ManagedDependencyCoreReleaseAuthorization.Proof("release", authority.versionConfigHash(), production)));
        clearInvocations(core);
    }

    @Test
    void runtimeOnlyTargetSecretIsAvailableBeforeRuntimePlanningInBothPhases() throws Exception {
        var reference = "conversation-handover-callback-signing";
        var value = new ManagedDependencySpec.Value(ManagedDependencySpec.ValueKind.TARGET_SECRET, reference);
        var bindings = List.of(
            new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_INBOX_CALLBACK_SIGNING_SECRET", value),
            new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.RUNTIME,
                "CALLBACK_SIGNING_SECRET", value));
        configureSpecAndSecrets(new ManagedDependencySpec(spec.schemaVersion(), spec.services(), bindings), Map.of(
            RuntimeAuthorityPurpose.CONVERSATION_HANDOVER_CALLBACK_SIGNING,
            new RuntimeAuthorityBinding.SecretRevision("FROZEN_TARGET_CALLBACK", "revision-1"),
            RuntimeAuthorityPurpose.TRUSTED_BACKEND_API_KEY,
            new RuntimeAuthorityBinding.SecretRevision("UNREQUESTED_TARGET_KEY", "revision-2")));

        for (var phase : ManagedDependencyPrerequisites.ProvisioningPhase.values()) {
            var result = prerequisites.reserve(scope, spec, phase);
            assertThat(result.targetSecretReferences()).containsExactlyEntriesOf(
                Map.of(reference, "FROZEN_TARGET_CALLBACK"));
        }
        verifyNoInteractions(provisioning);
    }

    @Test
    void missingRuntimeOnlyTargetSecretFailsBeforeProviderEffects() throws Exception {
        configureSpecAndSecrets(new ManagedDependencySpec(spec.schemaVersion(), spec.services(), List.of(
            new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_INBOX_CALLBACK_SIGNING_SECRET", new ManagedDependencySpec.Value(
                    ManagedDependencySpec.ValueKind.TARGET_SECRET, "conversation-handover-callback-signing")))), Map.of());

        assertThatThrownBy(() -> prerequisites.reserve(scope, spec,
            ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_TARGET_SECRET_UNAVAILABLE");
        verifyNoInteractions(provisioning);
    }

    @Test
    void serviceAndRuntimeTargetSecretRequirementsAreBothPreserved() throws Exception {
        var original = spec.services().getFirst();
        var environment = new java.util.TreeMap<>(original.environment());
        environment.put("TRUSTED_TARGET_KEY", new ManagedDependencySpec.Value(
            ManagedDependencySpec.ValueKind.TARGET_SECRET, "trusted-backend-api-key"));
        var service = new ManagedDependencySpec.Service(original.slot(), original.contract(), original.image(),
            original.sourceArtifactId(), original.sourceCommit(), original.port(), original.healthPath(), original.capacity(),
            original.secretPurposes(), environment, original.database(), original.volumes(), original.dependsOn(),
            original.initializationContract());
        configureSpecAndSecrets(new ManagedDependencySpec(spec.schemaVersion(), List.of(service), List.of(
            new ManagedDependencySpec.RuntimeEnvironmentBinding(ManagedDependencySpec.RuntimeTarget.CONNECTOR,
                "MANAGED_INBOX_CALLBACK_SIGNING_SECRET", new ManagedDependencySpec.Value(
                    ManagedDependencySpec.ValueKind.TARGET_SECRET, "conversation-handover-callback-signing")))), Map.of(
            RuntimeAuthorityPurpose.CONVERSATION_HANDOVER_CALLBACK_SIGNING,
            new RuntimeAuthorityBinding.SecretRevision("FROZEN_TARGET_CALLBACK", "revision-1"),
            RuntimeAuthorityPurpose.TRUSTED_BACKEND_API_KEY,
            new RuntimeAuthorityBinding.SecretRevision("FROZEN_TRUSTED_KEY", "revision-2")));

        var result = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION);

        assertThat(result.targetSecretReferences()).containsExactlyInAnyOrderEntriesOf(Map.of(
            "conversation-handover-callback-signing", "FROZEN_TARGET_CALLBACK",
            "trusted-backend-api-key", "FROZEN_TRUSTED_KEY"));
        verifyNoInteractions(provisioning);
    }

    @Test
    void artifactMustMatchPromotedDigestCommitChannelAndManifestHash() throws Exception {
        DeploymentSourceArtifactEntity artifact = new DeploymentSourceArtifactEntity();
        artifact.setId("artifact-service"); artifact.setArtifactType("DOCKER_IMAGE");
        artifact.setArtifactRole(DeploymentSourceArtifactRole.MANAGED_DEPENDENCY.name());
        artifact.setImageRepository("registry.example.test/service");
        artifact.setImageDigest("sha256:" + "1".repeat(64)); artifact.setGitCommitSha("2".repeat(40));
        artifact.setPromotionChannel("production"); artifact.setPromotedAt(Instant.now());
        var candidate = mapper.readTree(ManagedDependencyFixtures.ownedAiArtifact(spec.services().getFirst())
            .getCapabilityManifestJson());
        var normalized = manifests.normalize(candidate);
        artifact.setCapabilityManifestJson(mapper.writeValueAsString(normalized.manifest()));
        artifact.setCapabilityManifestHash(normalized.hash());
        when(artifacts.findById("artifact-service")).thenReturn(Optional.of(artifact));

        // A production-reviewed dependency is valid in both customer staging and production.
        prerequisites.validateArtifact(scope, spec.services().getFirst());
        artifact.setPromotionChannel("staging");
        prerequisites.validateArtifact(scope, spec.services().getFirst());

        useCoreAuthorization(true);
        assertThatThrownBy(() -> prerequisites.validateArtifact(scope, spec.services().getFirst()))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_PROVENANCE_INVALID");
        artifact.setPromotionChannel("production");
        prerequisites.validateArtifact(scope, spec.services().getFirst());

        artifact.setGitCommitSha("3".repeat(40));
        assertThatThrownBy(() -> prerequisites.validateArtifact(scope, spec.services().getFirst()))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_PROVENANCE_INVALID");
    }

    @Test
    void initializationRequiresExactlyOneContractVerifierAndExactOwnedReadback() {
        var binding = binding();
        var connection = new CoolifyConnection("https://coolify.example.test", "token",
            new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging", "environment",
                "server", "destination", "example.test", null, 1, 10, false, true,
                "8080", "/actuator/health", "8080"));
        when(provisioning.ownedDependencyConnection("target", "staging")).thenReturn(connection);

        assertThatThrownBy(() -> prerequisites.initializeAndVerify(scope, "test-init/v1", binding, "init-1"))
            .hasMessageContaining("DEPENDENCY_INITIALIZATION_VERIFIER_REQUIRED");
        verify(provisioning, never()).verifyOwnedDependencyReady(any(), anyString(), anyString(), anyString(),
            anyString(), any(), anyMap());

        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports("test-init/v1")).thenReturn(true);
        when(verifiers.orderedStream()).thenReturn(Stream.of(verifier));
        prerequisites.initializeAndVerify(scope, "test-init/v1", binding, "init-1");
        verify(provisioning).verifyOwnedDependencyReady(connection, "graph", "managed-service", "service",
            "application", null, Map.of());
        verify(verifier).initializeAndVerify(scope, "test-init/v1", binding, "init-1");

        var duplicate = mock(ManagedDependencyInitializationVerifier.class);
        when(duplicate.supports("test-init/v1")).thenReturn(true);
        when(verifiers.orderedStream()).thenReturn(Stream.of(verifier, duplicate));
        assertThatThrownBy(() -> prerequisites.initializeAndVerify(scope, "test-init/v1", binding, "init-1"))
            .hasMessageContaining("DEPENDENCY_INITIALIZATION_VERIFIER_REQUIRED");
    }

    @Test
    void foreignTargetOrChangedBindingIsRejectedBeforeProviderCalls() {
        var foreign = new ManagedDependencyPrerequisites.Scope("graph", "customer", "tenant", "deployment",
            "other-target", "version", "release", scope.specFingerprint(), scope.bindingFingerprint());
        assertThatThrownBy(() -> prerequisites.reserve(foreign, spec,
            ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_SCOPE_CHANGED");

        var changed = new ManagedDependencyPrerequisites.Binding("service", "managed-service",
            "https://other.example.test", "account", "binding", Map.of("api", "MANAGED_API"));
        assertThatThrownBy(() -> prerequisites.initializeAndVerify(scope, "test-init/v1", changed, "init-1"))
            .hasMessageContaining("DEPENDENCY_BINDING_CHANGED");
        verifyNoInteractions(provisioning);
    }

    @Test
    void historicalInboxCannotReserveOrInitializeNewWork() throws Exception {
        existingProductionInbox();
        var node = state.nodes.getFirst();
        var binding = new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl,
            node.accountId, node.bindingId, Map.copyOf(node.secrets));

        for (var phase : ManagedDependencyPrerequisites.ProvisioningPhase.values()) {
            assertThatThrownBy(() -> prerequisites.reserve(scope, spec, phase))
                .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        }
        assertThatThrownBy(() -> prerequisites.initializeAndVerify(scope,
            spec.services().getFirst().initializationContract(), binding, "initialize"))
            .hasMessageContaining(ManagedDependencySpecValidator.RETIRED_INBOX);
        assertThat(graphs.spec(graph).services().getFirst().contract())
            .isEqualTo(ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1);
        verifyNoInteractions(provisioning, verifiers);
    }

    @Test
    void interruptedWorkerAndPersistentVolumeReclamationNeverGuessSuccess() {
        assertThatThrownBy(() -> prerequisites.requireInterruptedWorkerTerminated(scope, "worker"))
            .hasMessageContaining("DEPENDENCY_WORKER_TERMINATION_UNPROVEN");
        var connection = new CoolifyConnection("https://coolify.example.test", "token",
            new CoolifyTargetProfileConfig("https://coolify.example.test", "project", "staging", "environment",
                "server", "destination", "example.test", null, 1, 10, false, true,
                "8080", "/actuator/health", "8080"));
        when(provisioning.ownedDependencyConnection("target", "staging")).thenReturn(connection);
        var resources = List.of(new ManagedDependencyResource("service", "managed-service",
            "application", null, Map.of("/app/data", "volume")));
        prerequisites.verifyReclaimed(scope, resources);
        verify(provisioning).verifyOwnedDependenciesReclaimed(connection, "graph", resources);
    }

    @Test
    void retirementRejectsUnsupportedCoreCommandEvenForHistoricalNodeCheckpoints() {
        graph.setStatus("RETIRING");
        for (String status : List.of("READY", "DISABLED", "RETIRING", "RETIRED")) {
            state.nodes.getFirst().status = status;
            state.nodes.getFirst().retirementOperationKey = "retire-1";
            assertThatThrownBy(() -> prerequisites.requireUnused(scope, graph.getRevision(), "retire-1"))
                .hasMessageContaining("DEPENDENCY_CORE_RETIREMENT_UNSUPPORTED");
        }
        assertThatThrownBy(() -> prerequisites.requireUnused(scope, graph.getRevision() + 1, "retire-1"))
            .hasMessageContaining("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
        verifyNoInteractions(provisioning, verifiers);
    }




    private void existingProductionInbox() throws Exception {
        var inbox = ManagedDependencyFixtures.productionInbox();
        configureSpecAndSecrets(new ManagedDependencySpec("managed-dependencies/v1", List.of(inbox)), Map.of());
        var node = state.nodes.getFirst(); node.slot = "inbox"; node.databaseUuid = "database"; node.volumes.put("/app/data", "volume");
        node.provisionedConfigurationRevision = 0;
        state.effectiveConfigurationRevision = 0; state.configurationRevision = 1;
        var release = releases.findById("release").orElseThrow();
        release.setProvisioningStatus("ACTIVE"); release.setStatus("APPLIED_VERIFIED");
        when(endpoints.connectorBaseUrl(release)).thenReturn("https://connector.example.test");
        when(artifacts.findById(inbox.sourceArtifactId())).thenReturn(Optional.of(ManagedDependencyFixtures.ownedAiArtifact(inbox)));
        useCoreAuthorization(true);
        clearInvocations(provisioning);
    }

    private void configureSpecAndSecrets(ManagedDependencySpec replacement,
                                        Map<RuntimeAuthorityPurpose, RuntimeAuthorityBinding.SecretRevision> secrets) throws Exception {
        spec = replacement;
        authority = new RuntimeAuthorityBinding(authority.customerId(), authority.tenantId(), authority.deploymentId(),
            authority.targetProfileId(), authority.versionId(), authority.versionConfigHash(), authority.generation(),
            authority.privateIssuer(), authority.privateAudience(), authority.publicIssuer(), authority.publicAudience(),
            authority.anonymousBootstrapEnabled(), authority.allowAnonymousSessions(), authority.allowedOrigins(), secrets);
        String bindingJson = mapper.writeValueAsString(authority);
        String bindingFingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(bindingJson.getBytes(StandardCharsets.UTF_8)));
        String specFingerprint = validator.fingerprint(spec);
        scope = new ManagedDependencyPrerequisites.Scope("graph", "customer", "tenant", "deployment", "target",
            "version", "release", specFingerprint, bindingFingerprint);
        graph.setSpecFingerprint(specFingerprint); graph.setBindingFingerprint(bindingFingerprint);
        when(graphs.spec(graph)).thenReturn(spec);
        var snapshot = snapshots.findByReleaseId("release").orElseThrow();
        snapshot.setFingerprint(bindingFingerprint); snapshot.setBindingJson(bindingJson);
        releases.findById("release").orElseThrow().setRuntimeAuthorityFingerprint(bindingFingerprint);
        useCoreAuthorization(false);
    }

    private ManagedDependencyPrerequisites.Binding binding() {
        return new ManagedDependencyPrerequisites.Binding("service", "managed-service",
            "https://managed-service.example.test", "account", "binding", Map.of("api", "MANAGED_API"));
    }
}
