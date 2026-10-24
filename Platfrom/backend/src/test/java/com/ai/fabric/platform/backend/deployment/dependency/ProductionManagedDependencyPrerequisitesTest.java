package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts;
import com.ai.fabric.platform.backend.deployment.admission.ManagedDependencyAdmissionLedger;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityPurpose;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotEntity;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentSourceArtifactEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
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
    private final ManagedDependencyAdmissionLedger admission = mock(ManagedDependencyAdmissionLedger.class);
    private final ManagedDependencyCoreReleaseAuthorization core = mock(ManagedDependencyCoreReleaseAuthorization.class);
    private final ManagedDependencyGraphStore graphs = mock(ManagedDependencyGraphStore.class);
    private final DeploymentSourceArtifactRepository artifacts = mock(DeploymentSourceArtifactRepository.class);
    private final RuntimeAuthoritySnapshotRepository snapshots = mock(RuntimeAuthoritySnapshotRepository.class);
    private final DeploymentReleaseRepository releases = mock(DeploymentReleaseRepository.class);
    private final DeploymentReleaseEndpointService endpoints = mock(DeploymentReleaseEndpointService.class);
    private final PlatformManagedProductProvisioningService provisioning = mock(PlatformManagedProductProvisioningService.class);
    private final TargetRetirementOperationRepository retirementOperations = mock(TargetRetirementOperationRepository.class);
    private final TargetRetirementGateRepository retirementGates = mock(TargetRetirementGateRepository.class);
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
        graph.setReservationRef("reservation"); graph.setStatus("READY"); graph.setRevision(7);
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

        var evidence = new ManagedDependencyAdmissionLedger.Evidence("reservation", "generation", 2,
            AdmissionContracts.Intent.CUSTOMER_STAGING, "manifest", "a".repeat(64), bindingFingerprint,
            new AdmissionContracts.Placement("server", "destination", "127.0.0.1"), "evidence",
            Instant.now().plusSeconds(300));
        when(admission.require(any(), any())).thenReturn(evidence);
        when(admission.requireForExecution(any(), any())).thenReturn(evidence);

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

        prerequisites = new ProductionManagedDependencyPrerequisites(admission, core, graphs, artifacts, manifests,
            snapshots, releases, endpoints, provisioning, retirementOperations, retirementGates, verifiers, mapper,
            new ManagedDependencyArtifactPolicy(artifacts, manifests));
        when(artifacts.findById(service.sourceArtifactId()))
            .thenReturn(Optional.of(ManagedDependencyFixtures.ownedAiArtifact(service)));
    }

    @Test
    void workerRejectsUnprovenOwnershipBeforeAdmissionOrRemoteEffects() {
        var artifact = ManagedDependencyFixtures.ownedAiArtifact(spec.services().getFirst());
        artifact.setCapabilityManifestJson("{}");
        artifact.setCapabilityManifestHash(null);
        when(artifacts.findById(artifact.getId())).thenReturn(Optional.of(artifact));
        clearInvocations(admission, provisioning);
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec,
            ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION))
            .hasMessageContaining("DEPENDENCY_ARTIFACT_OWNERSHIP_UNPROVEN");
        verifyNoInteractions(admission, provisioning);
    }

    @Test
    void reserveReplaysTheParentAdmissionAndReturnsOnlyFrozenAuthorityData() {
        var first = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION);
        var replay = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION);

        assertThat(replay).isEqualTo(first);
        assertThat(first.reservationRef()).isEqualTo("reservation");
        assertThat(first.origins()).containsExactly("https://customer.example.test");
        assertThat(first.callbacks()).isEmpty();
        verify(admission, times(2)).requireForExecution(any(), isNull());
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
        verifyNoInteractions(admission, provisioning);
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
        verifyNoInteractions(admission, provisioning);
    }

    @Test
    void effectiveCoreInboxRechecksOwnedResourcesAndCallbackCanaryWithoutAdmissionFreshness() throws Exception {
        existingProductionInbox();
        useCoreAuthorization(true);
        var service = spec.services().getFirst();
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports(service.initializationContract())).thenReturn(true);
        when(verifiers.orderedStream()).thenAnswer(ignored -> Stream.of(verifier));
        clearInvocations(admission, provisioning);

        var result = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND);
        assertThat(result.existingConfiguration()).isTrue();
        assertThat(result.reservationRef()).isNull();
        assertThat(result.callbacks()).containsEntry("inbox",
            "https://connector.example.test/integrations/conversation-channel/events");
        prerequisites.validateExecutionReservation(scope, result.authorityReference());
        prerequisites.validateArtifact(scope, service);
        var node = state.nodes.getFirst();
        var binding = new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl,
            node.accountId, node.bindingId, Map.copyOf(node.secrets));
        prerequisites.initializeAndVerify(scope, service.initializationContract(), binding, "core-init");

        verifyNoInteractions(admission);
        verify(provisioning).ownedDependencyConnection("target", "production");
        verify(provisioning).verifyOwnedDependencyReady(any(), eq("graph"), eq("managed-service"), eq("inbox"),
            eq("application"), eq("database"), eq(Map.of("/app/data", "volume")));
        verify(verifier).initializeAndVerify(scope, service.initializationContract(), binding, "core-init");
    }

    @Test
    void coreInboxMissingOwnedResourcesOrProviderReadbackCannotClaimInitialization() throws Exception {
        existingProductionInbox();
        useCoreAuthorization(true);
        var node = state.nodes.getFirst();
        node.applicationUuid = null;
        clearInvocations(admission);
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("DEPENDENCY_EXISTING_RESOURCES_REQUIRED");
        verifyNoInteractions(provisioning);

        node.applicationUuid = "application";
        var service = spec.services().getFirst();
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports(service.initializationContract())).thenReturn(true);
        when(verifiers.orderedStream()).thenAnswer(ignored -> Stream.of(verifier));
        doThrow(new IllegalStateException("provider-readback-failed")).when(provisioning)
            .verifyOwnedDependencyReady(any(), anyString(), anyString(), anyString(), anyString(), anyString(), anyMap());
        assertThatThrownBy(() -> prerequisites.initializeAndVerify(scope, service.initializationContract(),
            new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl,
                node.accountId, node.bindingId, Map.copyOf(node.secrets)), "core-init"))
            .hasMessageContaining("provider-readback-failed");
        verify(verifier, never()).initializeAndVerify(any(), any(), any(), any());
        verifyNoInteractions(admission);
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
        clearInvocations(admission);
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("DEPENDENCY_CALLBACK_ENDPOINT_MISSING");
        verifyNoInteractions(admission, provisioning);
    }

    private void useCoreAuthorization(boolean production) {
        graph.setReservationRef("core-release:release");
        when(core.find(scope, graph, spec)).thenReturn(Optional.of(
            new ManagedDependencyCoreReleaseAuthorization.Proof("release", authority.versionConfigHash(), production)));
        clearInvocations(admission);
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

        var productionEvidence = new ManagedDependencyAdmissionLedger.Evidence("reservation", "generation", 2,
            AdmissionContracts.Intent.CUSTOMER_PRODUCTION, "manifest", "a".repeat(64), scope.bindingFingerprint(),
            new AdmissionContracts.Placement("server", "destination", "127.0.0.1"), "evidence",
            Instant.now().plusSeconds(300));
        when(admission.require(any(), any())).thenReturn(productionEvidence);
        when(admission.requireForExecution(any(), any())).thenReturn(productionEvidence);
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
    void exactInboxRevocationDoesNotDependOnExpiredProvisioningEvidence() {
        var inbox = ManagedDependencyFixtures.topology().services().stream().filter(value -> value.slot().equals("inbox")).findFirst().orElseThrow();
        spec = new ManagedDependencySpec("managed-dependencies/v1", List.of(inbox));
        String fingerprint = validator.fingerprint(spec); graph.setSpecFingerprint(fingerprint);
        scope = new ManagedDependencyPrerequisites.Scope("graph", "customer", "tenant", "deployment", "target", "version", "release",
            fingerprint, scope.bindingFingerprint());
        state.nodes.getFirst().slot = "inbox";
        when(graphs.spec(graph)).thenReturn(spec);
        var binding = new ManagedDependencyPrerequisites.Binding("inbox", "managed-service", "https://managed-service.example.test",
            "account", "binding", Map.of("api", "MANAGED_API"));
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports("inbox-callback-canary/v1")).thenReturn(true);
        when(verifiers.orderedStream()).thenReturn(Stream.of(verifier));
        clearInvocations(admission);
        when(admission.require(any(), any())).thenThrow(new IllegalStateException("expired admission"));
        when(admission.requireForExecution(any(), any())).thenThrow(new IllegalStateException("expired admission"));

        prerequisites.verifyOperatorRevocation(scope, binding, List.of("RETIRED_KEY"), "revoke", false);

        verifyNoInteractions(admission, provisioning);
        verify(verifier).verifyOperatorRevocation(scope, "inbox-callback-canary/v1", binding,
            List.of("RETIRED_KEY"), "revoke", false);
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
        var resources = List.of(new ManagedDependencyRetirementAdapter.Resource("service", "managed-service",
            "application", null, Map.of("/app/data", "volume")));
        prerequisites.verifyReclaimed(scope, resources);
        verify(provisioning).verifyOwnedDependenciesReclaimed(connection, "graph", resources);
    }

    @Test
    void retirementProbesOnlyLiveNodesAndAcceptsExactSameOperationCheckpoints() {
        configureRetirement("retire-1");
        graph.setStatus("RETIRING");
        var retired = new ManagedDependencyState.Node();
        retired.slot = "removed"; retired.serviceRef = "removed-service";
        retired.status = "RETIRED"; retired.retirementOperationKey = "retire-1";
        state.nodes.add(retired);
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports("test-init/v1")).thenReturn(true);
        when(verifiers.orderedStream()).thenReturn(Stream.of(verifier));

        prerequisites.requireUnused(scope, graph.getRevision(), "retire-1");

        verify(verifier).requireUnused(eq(scope), eq("test-init/v1"), any(), eq("retire-1"));
    }

    @Test
    void retirementRejectsNodeCheckpointFromAnotherOperation() {
        configureRetirement("retire-1");
        graph.setStatus("RETIRING");
        state.nodes.getFirst().status = "RETIRING";
        state.nodes.getFirst().retirementOperationKey = "retire-other";

        assertThatThrownBy(() -> prerequisites.requireUnused(scope, graph.getRevision(), "retire-1"))
            .hasMessageContaining("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
    }

    @Test
    void previouslyDisabledNodeDoesNotRequireAStoppedHttpEndpoint() {
        configureRetirement("retire-1");
        graph.setStatus("RETIRING");
        state.nodes.getFirst().status = "DISABLED";
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports("test-init/v1")).thenReturn(true);
        when(verifiers.orderedStream()).thenReturn(Stream.of(verifier));

        prerequisites.requireUnused(scope, graph.getRevision(), "retire-1");

        verifyNoInteractions(verifier);
    }

    @Test void previouslyEffectiveActiveInboxUsesConfigurationAuthorityAtEveryReconcileBoundary() throws Exception {
        existingProductionInbox();
        var result = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND);
        assertThat(result.existingConfiguration()).isTrue();
        prerequisites.validateReservation(scope, "reservation");
        prerequisites.validateExecutionReservation(scope, "reservation");
        var service = spec.services().getFirst();
        prerequisites.validateArtifact(scope, service);
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports(service.initializationContract())).thenReturn(true);
        when(verifiers.orderedStream()).thenAnswer(ignored -> Stream.of(verifier));
        var node = state.nodes.getFirst();
        prerequisites.initializeAndVerify(scope, service.initializationContract(),
            new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl, node.accountId, node.bindingId,
                Map.copyOf(node.secrets)), "initialize");
        verify(admission, atLeastOnce()).requireConfigurationForExecution(any(), eq("reservation"), eq("release"));
        verify(admission, atLeastOnce()).requireConfiguration(any(), eq("reservation"), eq("release"));
        verify(admission, never()).require(any(), any());
        verify(admission, never()).requireForExecution(any(), any());
        verify(verifier).initializeAndVerify(eq(scope), eq(service.initializationContract()), any(), eq("initialize"));
    }

    @Test void missingExistingResourceOrWrongReleaseNeverFallsBackToInitialProvisioning() throws Exception {
        existingProductionInbox();
        var node = state.nodes.getFirst();
        node.applicationUuid = null;
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("DEPENDENCY_EXISTING_RESOURCES_REQUIRED");
        node.applicationUuid = "application"; node.databaseUuid = null;
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("DEPENDENCY_EXISTING_RESOURCES_REQUIRED");
        node.databaseUuid = "database"; node.volumes.clear();
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("DEPENDENCY_EXISTING_RESOURCES_REQUIRED");
        node.volumes.put("/app/data", "volume");
        releases.findById("release").orElseThrow().setProvisioningStatus("SUPERSEDED");
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("DEPENDENCY_EXISTING_CONFIGURATION_REQUIRED");
        verify(admission, never()).requireForExecution(any(), any());
        verifyNoInteractions(provisioning);
    }

    @Test void previouslyEffectiveStagingInboxUsesOrdinaryFreshAdmissionForConfigurationUpdates() throws Exception {
        existingProductionInbox();
        when(admission.reviewedIntent(any(), eq("reservation"))).thenReturn(AdmissionContracts.Intent.CUSTOMER_STAGING);
        clearInvocations(admission);

        var result = prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND);
        assertThat(result.existingConfiguration()).isFalse();
        prerequisites.validateReservation(scope, "reservation");
        prerequisites.validateExecutionReservation(scope, "reservation");
        var service = spec.services().getFirst();
        prerequisites.validateArtifact(scope, service);
        var verifier = mock(ManagedDependencyInitializationVerifier.class);
        when(verifier.supports(service.initializationContract())).thenReturn(true);
        when(verifiers.orderedStream()).thenAnswer(ignored -> Stream.of(verifier));
        var node = state.nodes.getFirst();
        prerequisites.initializeAndVerify(scope, service.initializationContract(),
            new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl, node.accountId, node.bindingId,
                Map.copyOf(node.secrets)), "staging-update");

        verify(admission, atLeastOnce()).requireForExecution(any(), any());
        verify(admission, atLeastOnce()).require(any(), eq("reservation"));
        verify(admission, never()).requireConfigurationForExecution(any(), any(), any());
        verify(admission, never()).requireConfiguration(any(), any(), any());
        verify(verifier).initializeAndVerify(eq(scope), eq(service.initializationContract()), any(), eq("staging-update"));
    }

    @Test void firstConfigurationAndRetirementKeepOriginalAdmissionBoundary() throws Exception {
        existingProductionInbox();
        state.effectiveConfigurationRevision = -1;
        when(admission.requireForExecution(any(), any())).thenThrow(new IllegalStateException("fresh admission required"));
        assertThatThrownBy(() -> prerequisites.reserve(scope, spec, ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND))
            .hasMessageContaining("fresh admission required");
        state.effectiveConfigurationRevision = 0; graph.setStatus("RETIRING");
        when(admission.require(any(), any())).thenThrow(new IllegalStateException("fresh admission required"));
        assertThatThrownBy(() -> prerequisites.validateReservation(scope, "reservation")).hasMessageContaining("fresh admission required");
        verify(admission, never()).requireConfigurationForExecution(any(), any(), any());
        verify(admission, never()).requireConfiguration(any(), any(), any());
        verifyNoInteractions(provisioning);
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
        var evidence = new ManagedDependencyAdmissionLedger.Evidence("reservation", "generation", 2,
            AdmissionContracts.Intent.CUSTOMER_PRODUCTION, "manifest", "a".repeat(64), scope.bindingFingerprint(),
            new AdmissionContracts.Placement("server", "destination", "127.0.0.1"), "evidence", Instant.now().plusSeconds(60), true);
        when(admission.requireConfigurationForExecution(any(), eq("reservation"), eq("release"))).thenReturn(evidence);
        when(admission.requireConfiguration(any(), eq("reservation"), eq("release"))).thenReturn(evidence);
        when(admission.reviewedIntent(any(), eq("reservation"))).thenReturn(AdmissionContracts.Intent.CUSTOMER_PRODUCTION);
        clearInvocations(admission, provisioning);
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
        var evidence = new ManagedDependencyAdmissionLedger.Evidence(
            "reservation", "generation", 2, AdmissionContracts.Intent.CUSTOMER_STAGING, "manifest", "a".repeat(64),
            bindingFingerprint, new AdmissionContracts.Placement("server", "destination", "127.0.0.1"),
            "evidence", Instant.now().plusSeconds(300));
        when(admission.require(any(), any())).thenReturn(evidence);
        when(admission.requireForExecution(any(), any())).thenReturn(evidence);
    }

    private void configureRetirement(String operationKey) {
        var operation = new TargetRetirementOperationEntity();
        operation.setId("retirement"); operation.setCustomerId("customer");
        operation.setDeploymentId("deployment"); operation.setTargetProfileId("target");
        operation.setOperationKey(operationKey); operation.setState(TargetRetirementContracts.State.RUNNING);
        operation.setStep(TargetRetirementContracts.Step.DEPENDENCIES); operation.setWorkerToken("worker");
        operation.setLeaseExpiresAt(Instant.now().plusSeconds(60)); operation.setDestructiveStarted(true);
        operation.setFenceEpoch(4);
        when(retirementOperations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
            "deployment", "target", operationKey)).thenReturn(Optional.of(operation));
        var gate = new TargetRetirementGateEntity();
        gate.setId("gate"); gate.setDeploymentId("deployment"); gate.setTargetProfileId("target");
        gate.setState("RETIRING"); gate.setOperationId("retirement"); gate.setEpoch(4);
        when(retirementGates.findByDeploymentIdAndTargetProfileId("deployment", "target"))
            .thenReturn(Optional.of(gate));
    }

    private ManagedDependencyPrerequisites.Binding binding() {
        return new ManagedDependencyPrerequisites.Binding("service", "managed-service",
            "https://managed-service.example.test", "account", "binding", Map.of("api", "MANAGED_API"));
    }
}
