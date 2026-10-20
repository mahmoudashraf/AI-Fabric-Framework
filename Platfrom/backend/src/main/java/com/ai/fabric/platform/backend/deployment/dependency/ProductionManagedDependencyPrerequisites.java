package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts;
import com.ai.fabric.platform.backend.deployment.admission.ManagedDependencyAdmissionLedger;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthorityBinding;
import com.ai.fabric.platform.backend.deployment.authority.RuntimeAuthoritySnapshotRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentSourceArtifactRepository;
import com.ai.fabric.platform.backend.deployment.model.DeploymentSourceArtifactRole;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import com.ai.fabric.platform.backend.deployment.service.DeploymentSourceCapabilityManifestService;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpec.ValueKind.*;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;
import static org.springframework.http.HttpStatus.CONFLICT;

/** Production R5 adapter over the existing R7 ledger and owned Coolify lifecycle. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class ProductionManagedDependencyPrerequisites implements ManagedDependencyPrerequisites {
    private final ManagedDependencyAdmissionLedger admission;
    private final ManagedDependencyGraphStore graphs;
    private final DeploymentSourceArtifactRepository artifacts;
    private final DeploymentSourceCapabilityManifestService capabilityManifests;
    private final RuntimeAuthoritySnapshotRepository authoritySnapshots;
    private final DeploymentReleaseRepository releases;
    private final DeploymentReleaseEndpointService endpoints;
    private final PlatformManagedProductProvisioningService provisioning;
    private final TargetRetirementOperationRepository retirementOperations;
    private final TargetRetirementGateRepository retirementGates;
    private final ObjectProvider<ManagedDependencyInitializationVerifier> initializationVerifiers;
    private final ObjectMapper mapper;
    private final ManagedDependencyArtifactPolicy ownership;

    public ProductionManagedDependencyPrerequisites(ManagedDependencyAdmissionLedger admission,
            ManagedDependencyGraphStore graphs, DeploymentSourceArtifactRepository artifacts,
            DeploymentSourceCapabilityManifestService capabilityManifests,
            RuntimeAuthoritySnapshotRepository authoritySnapshots, DeploymentReleaseRepository releases,
            DeploymentReleaseEndpointService endpoints, PlatformManagedProductProvisioningService provisioning,
            TargetRetirementOperationRepository retirementOperations,
            TargetRetirementGateRepository retirementGates,
            ObjectProvider<ManagedDependencyInitializationVerifier> initializationVerifiers,
            ObjectMapper mapper, ManagedDependencyArtifactPolicy ownership) {
        this.admission = admission;
        this.graphs = graphs;
        this.artifacts = artifacts;
        this.capabilityManifests = capabilityManifests;
        this.authoritySnapshots = authoritySnapshots;
        this.releases = releases;
        this.endpoints = endpoints;
        this.provisioning = provisioning;
        this.retirementOperations = retirementOperations;
        this.retirementGates = retirementGates;
        this.initializationVerifiers = initializationVerifiers;
        this.mapper = mapper;
        this.ownership = ownership;
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Admission reserve(Scope scope, ManagedDependencySpec spec, ProvisioningPhase phase) {
        requireScope(scope);
        if (phase == null) fail("DEPENDENCY_PROVISIONING_PHASE_REQUIRED");
        graph(scope);
        ManagedDependencySpecValidator validator = new ManagedDependencySpecValidator(mapper);
        validator.validate(spec);
        if (!scope.specFingerprint().equals(validator.fingerprint(spec))) fail("DEPENDENCY_SPEC_DRIFT");
        ownership.requireAllowed(spec);
        var evidence = admission.requireForExecution(requirement(scope, spec), null);
        if (evidence.intent() == AdmissionContracts.Intent.CUSTOMER_PRODUCTION) {
            validator.requireProductionInboxPersistence(spec);
        }
        RuntimeAuthorityBinding authority = authority(scope);

        Map<String, String> callbacks = phase == ProvisioningPhase.RUNTIME_BOUND
            ? callbacks(scope, spec) : Map.of();
        Map<String, String> targetValues = targetValues(scope, spec, authority,
            phase == ProvisioningPhase.RUNTIME_BOUND);
        Map<String, String> targetSecrets = targetSecrets(spec, authority);
        return new Admission(evidence.reservationId(), evidence.bindingFingerprint(), callbacks,
            authority.allowedOrigins(), targetValues, targetSecrets);
    }

    @Override
    public void validateReservation(Scope scope, String reservationRef) {
        requireScope(scope);
        var graph = graph(scope);
        ManagedDependencySpec spec = graphs.spec(graph);
        ownership.requireAllowed(spec);
        admission.require(requirement(scope, spec), required(reservationRef, "DEPENDENCY_RESERVATION_REQUIRED"));
        authority(scope);
    }

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public void validateExecutionReservation(Scope scope, String reservationRef) {
        requireScope(scope);
        var graph = graph(scope); var spec = graphs.spec(graph);
        ownership.requireAllowed(spec);
        admission.requireForExecution(requirement(scope, spec), required(reservationRef, "DEPENDENCY_RESERVATION_REQUIRED"));
        authority(scope);
    }

    @Override
    public void validateArtifact(Scope scope, ManagedDependencySpec.Service service) {
        validateReservation(scope, graph(scope).getReservationRef());
        var artifact = artifacts.findById(required(service.sourceArtifactId(), "DEPENDENCY_ARTIFACT_REQUIRED"))
            .orElseThrow(() -> conflict("DEPENDENCY_ARTIFACT_MISSING"));
        String expectedImage = artifact.getImageRepository() + "@" + artifact.getImageDigest();
        var evidence = admission.require(requirement(scope, graphs.spec(graph(scope))), graph(scope).getReservationRef());
        boolean productionIntent = evidence.intent() == AdmissionContracts.Intent.CUSTOMER_PRODUCTION;
        boolean acceptedChannel = "production".equals(artifact.getPromotionChannel())
            || !productionIntent && "staging".equals(artifact.getPromotionChannel());
        if (!"DOCKER_IMAGE".equalsIgnoreCase(artifact.getArtifactType())
            || !DeploymentSourceArtifactRole.MANAGED_DEPENDENCY.name().equals(artifact.getArtifactRole())
            || artifact.getPromotedAt() == null
            || artifact.getImageRepository() == null || artifact.getImageRepository().isBlank()
            || artifact.getImageDigest() == null || !artifact.getImageDigest().matches("sha256:[a-f0-9]{64}")
            || !expectedImage.equals(service.image())
            || !Objects.equals(artifact.getGitCommitSha(), service.sourceCommit())
            || !acceptedChannel) {
            fail("DEPENDENCY_ARTIFACT_PROVENANCE_INVALID");
        }
        ownership.requireAllowed(service, artifact);
    }

    @Override
    public void initializeAndVerify(Scope scope, String initializationContract, Binding binding,
                                    String operationKey) {
        validateReservation(scope, graph(scope).getReservationRef());
        var graph = graph(scope);
        var evidence = admission.require(requirement(scope, graphs.spec(graph)), graph.getReservationRef());
        if (evidence.intent() == AdmissionContracts.Intent.CUSTOMER_PRODUCTION) {
            new ManagedDependencySpecValidator(mapper).requireProductionInboxPersistence(graphs.spec(graph));
        }
        var node = exactNode(graph, binding);
        var verifier = verifier(initializationContract);
        provisioning.verifyOwnedDependencyReady(
            provisioning.ownedDependencyConnection(scope.targetProfileId(), environment(scope)),
            scope.graphId(), node.serviceRef, node.slot, node.applicationUuid, node.databaseUuid,
            Map.copyOf(node.volumes));
        verifier.initializeAndVerify(scope, initializationContract, binding, operationKey);
    }

    @Override
    public void disableAndVerifyBinding(Scope scope, Binding binding) {
        validateReservation(scope, graph(scope).getReservationRef());
        var graph = graph(scope);
        var state = graphs.state(graph);
        var node = exactNode(graph, binding);
        if (!node.disabled || state.operators.stream().anyMatch(value -> node.slot.equals(value.slot)
            && "ENROLLED".equals(value.status))) fail("DEPENDENCY_BINDING_DISABLE_UNPROVEN");
        String contract = service(graph, node.slot).initializationContract();
        verifier(contract).disableAndVerify(scope, contract, binding);
    }

    @Override
    public void validateOperatorRevocationScope(Scope scope, Binding binding) {
        requireScope(scope);
        var graph = graph(scope);
        var node = exactNode(graph, binding);
        var service = service(graph, node.slot);
        if (service.contract() != ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1
            || node.applicationUuid == null || node.baseUrl == null) {
            fail("INBOX_OPERATOR_EXISTING_RESOURCES_REQUIRED");
        }
        authority(scope);
    }

    @Override
    public void verifyOperatorRevocation(Scope scope, Binding binding, List<String> retiredCredentialReferences,
                                        String operationKey, boolean disabled) {
        validateOperatorRevocationScope(scope, binding);
        var graph = graph(scope);
        var node = exactNode(graph, binding);
        String contract = service(graph, node.slot).initializationContract();
        verifier(contract).verifyOperatorRevocation(scope, contract, binding, retiredCredentialReferences,
            operationKey, disabled);
    }

    @Override
    public void requireInterruptedWorkerTerminated(Scope scope, String workerToken) {
        requireScope(scope);
        required(workerToken, "DEPENDENCY_WORKER_TOKEN_REQUIRED");
        // There is no durable process-termination receipt in the current lifecycle. Recovery must
        // remain blocked instead of treating an absent heartbeat or a process restart as proof.
        fail("DEPENDENCY_WORKER_TERMINATION_UNPROVEN");
    }

    @Override
    public void requireUnused(Scope scope, long graphRevision, String retirementOperationKey) {
        requireScope(scope);
        var graph = graph(scope);
        if (graph.getRevision() != graphRevision || !"RETIRING".equals(graph.getStatus())) {
            fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
        }
        var operation = retirementOperations.findByDeploymentIdAndTargetProfileIdAndOperationKey(
                scope.deploymentId(), scope.targetProfileId(), required(retirementOperationKey,
                    "DEPENDENCY_RETIREMENT_OPERATION_REQUIRED"))
            .orElseThrow(() -> conflict("DEPENDENCY_TARGET_RETIREMENT_REQUIRED"));
        var gate = retirementGates.findByDeploymentIdAndTargetProfileId(scope.deploymentId(), scope.targetProfileId())
            .orElseThrow(() -> conflict("DEPENDENCY_TARGET_RETIREMENT_REQUIRED"));
        if (!scope.customerId().equals(operation.getCustomerId())
            || operation.getState() != TargetRetirementContracts.State.RUNNING
            || operation.getStep() != TargetRetirementContracts.Step.DEPENDENCIES
            || operation.getWorkerToken() == null || operation.getLeaseExpiresAt() == null
            || !operation.getLeaseExpiresAt().isAfter(Instant.now()) || !operation.isDestructiveStarted()
            || !"RETIRING".equals(gate.getState()) || !operation.getId().equals(gate.getOperationId())
            || operation.getFenceEpoch() != gate.getEpoch()) {
            fail("DEPENDENCY_TARGET_RETIREMENT_UNPROVEN");
        }
        validateReservation(scope, graph.getReservationRef());
        for (var node : graphs.state(graph).nodes) {
            if ("DISABLED".equals(node.status)) continue;
            if ("RETIRED".equals(node.status)) {
                if (!retirementOperationKey.equals(node.retirementOperationKey)) {
                    fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
                }
                continue;
            }
            if ("RETIRING".equals(node.status)) {
                if (!retirementOperationKey.equals(node.retirementOperationKey)) {
                    fail("DEPENDENCY_RETIREMENT_GENERATION_CHANGED");
                }
                continue;
            }
            String contract = service(graph, node.slot).initializationContract();
            verifier(contract).requireUnused(scope, contract,
                new Binding(node.slot, node.serviceRef, node.baseUrl, node.accountId, node.bindingId,
                    Map.copyOf(node.secrets)), retirementOperationKey);
        }
    }

    @Override
    public void verifyReclaimed(Scope scope, List<ManagedDependencyRetirementAdapter.Resource> resources) {
        requireScope(scope);
        if (resources == null) fail("DEPENDENCY_RECLAMATION_EVIDENCE_REQUIRED");
        provisioning.verifyOwnedDependenciesReclaimed(
            provisioning.ownedDependencyConnection(scope.targetProfileId(), environment(scope)),
            scope.graphId(), resources);
    }

    @Override
    public void releaseReservation(Scope scope, String reservationRef) {
        // R5 owns no separate capacity lease. R3 releases the parent R7 reservation only after
        // every target participant has supplied authoritative reclamation evidence.
        validateReservation(scope, reservationRef);
    }

    private ManagedDependencyAdmissionLedger.Requirement requirement(Scope scope, ManagedDependencySpec spec) {
        ManagedDependencySpec.Capacity capacity = spec.totalCapacity();
        long databases = spec.services().stream().filter(value -> value.database() != null).count();
        return new ManagedDependencyAdmissionLedger.Requirement(scope.customerId(), scope.tenantId(),
            scope.deploymentId(), scope.targetProfileId(), scope.versionId(), scope.bindingFingerprint(),
            scope.specFingerprint(), capacity.milliCpu(), mib(capacity.memoryMiB()), mib(capacity.diskMiB()),
            databases, capacity.resources());
    }

    private RuntimeAuthorityBinding authority(Scope scope) {
        String releaseId = required(scope.releaseId(), "DEPENDENCY_RELEASE_BINDING_REQUIRED");
        var release = releases.findById(releaseId)
            .orElseThrow(() -> conflict("DEPENDENCY_RELEASE_BINDING_MISSING"));
        if (!scope.deploymentId().equals(release.getDeploymentId())
            || !scope.targetProfileId().equals(release.getTargetProfileId())
            || !scope.versionId().equals(release.getDeploymentVersionId())
            || !scope.bindingFingerprint().equals(release.getRuntimeAuthorityFingerprint())) {
            fail("DEPENDENCY_RELEASE_BINDING_CHANGED");
        }
        var snapshot = authoritySnapshots.findByReleaseId(releaseId)
            .orElseThrow(() -> conflict("DEPENDENCY_AUTHORITY_BINDING_AMBIGUOUS_OR_MISSING"));
        try {
            RuntimeAuthorityBinding binding = mapper.readValue(snapshot.getBindingJson(), RuntimeAuthorityBinding.class);
            String fingerprint = sha256(snapshot.getBindingJson());
            if (!snapshot.getFingerprint().equals(fingerprint)
                || !releaseId.equals(snapshot.getReleaseId())
                || !scope.customerId().equals(binding.customerId()) || !scope.tenantId().equals(binding.tenantId())
                || !scope.deploymentId().equals(binding.deploymentId())
                || !scope.targetProfileId().equals(binding.targetProfileId())
                || !scope.versionId().equals(binding.versionId())) {
                fail("DEPENDENCY_AUTHORITY_BINDING_INVALID");
            }
            binding.allowedOrigins().forEach(ManagedDependencyLifecycleService::https);
            return binding;
        } catch (ResponseStatusException error) { throw error;
        } catch (Exception error) { throw conflict("DEPENDENCY_AUTHORITY_BINDING_INVALID"); }
    }

    private Map<String, String> callbacks(Scope scope, ManagedDependencySpec spec) {
        var references = spec.services().stream().flatMap(value -> value.environment().values().stream())
            .filter(value -> value.kind() == CALLBACK_URL).map(ManagedDependencySpec.Value::reference)
            .distinct().toList();
        if (references.isEmpty()) return Map.of();
        if (references.size() != 1 || !"inbox".equals(references.getFirst())) {
            fail("DEPENDENCY_CALLBACK_BINDING_UNSUPPORTED");
        }
        var release = releases.findById(required(scope.releaseId(), "DEPENDENCY_RELEASE_BINDING_REQUIRED"))
            .orElseThrow(() -> conflict("DEPENDENCY_CALLBACK_RELEASE_MISSING"));
        if (!scope.deploymentId().equals(release.getDeploymentId())
            || !scope.versionId().equals(release.getDeploymentVersionId())
            || !scope.targetProfileId().equals(release.getTargetProfileId())) {
            fail("DEPENDENCY_CALLBACK_RELEASE_CHANGED");
        }
        if (!scope.bindingFingerprint().equals(release.getRuntimeAuthorityFingerprint())) {
            fail("DEPENDENCY_CALLBACK_BINDING_CHANGED");
        }
        String connector = endpoints.connectorBaseUrl(release);
        if (connector == null) fail("DEPENDENCY_CALLBACK_ENDPOINT_MISSING");
        String callback = connector + "/integrations/conversation-channel/events";
        ManagedDependencyLifecycleService.https(callback);
        return Map.of("inbox", callback);
    }

    private Map<String, String> targetValues(Scope scope, ManagedDependencySpec spec,
            RuntimeAuthorityBinding authority, boolean requireRuntimeEndpoints) {
        var requested = spec.services().stream().flatMap(value -> value.environment().values().stream())
            .filter(value -> value.kind() == TARGET_VALUE).map(ManagedDependencySpec.Value::reference)
            .distinct().toList();
        if (requested.isEmpty()) return Map.of();
        Map<String, String> supported = new LinkedHashMap<>();
        supported.put("private-issuer", authority.privateIssuer());
        supported.put("private-audience", authority.privateAudience());
        supported.put("public-issuer", authority.publicIssuer());
        supported.put("public-audience", authority.publicAudience());
        var release = scope.releaseId() == null ? null : releases.findById(scope.releaseId()).orElse(null);
        if (release != null && scope.deploymentId().equals(release.getDeploymentId())
            && scope.targetProfileId().equals(release.getTargetProfileId())
            && scope.versionId().equals(release.getDeploymentVersionId())
            && scope.bindingFingerprint().equals(release.getRuntimeAuthorityFingerprint())) {
            supported.put("runtime-base-url", endpoints.runtimeBaseUrl(release));
            String connector = endpoints.connectorBaseUrl(release);
            if (connector != null) supported.put("connector-base-url", connector);
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (String reference : requested) {
            String value = supported.get(reference);
            if (value == null || value.isBlank()) {
                if (!requireRuntimeEndpoints && ("runtime-base-url".equals(reference)
                    || "connector-base-url".equals(reference))) continue;
                fail("DEPENDENCY_TARGET_VALUE_UNAVAILABLE");
            }
            result.put(reference, value);
        }
        return Map.copyOf(result);
    }

    private Map<String, String> targetSecrets(ManagedDependencySpec spec, RuntimeAuthorityBinding authority) {
        var requested = Stream.concat(
                spec.services().stream().flatMap(value -> value.environment().values().stream()),
                spec.runtimeEnvironment().stream().map(ManagedDependencySpec.RuntimeEnvironmentBinding::value))
            .filter(value -> value.kind() == TARGET_SECRET).map(ManagedDependencySpec.Value::reference)
            .distinct().toList();
        if (requested.isEmpty()) return Map.of();
        Map<String, String> available = new LinkedHashMap<>();
        authority.secrets().forEach((purpose, revision) -> available.put(
            purpose.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-'), revision.reference()));
        Map<String, String> result = new LinkedHashMap<>();
        for (String reference : requested) {
            String value = available.get(reference);
            if (value == null || value.isBlank()) fail("DEPENDENCY_TARGET_SECRET_UNAVAILABLE");
            result.put(reference, value);
        }
        return Map.copyOf(result);
    }

    private ManagedDependencyInitializationVerifier verifier(String contract) {
        var matches = initializationVerifiers.orderedStream()
            .filter(value -> value.supports(contract)).toList();
        if (matches.size() != 1) fail("DEPENDENCY_INITIALIZATION_VERIFIER_REQUIRED");
        return matches.getFirst();
    }

    private ManagedDependencyGraphEntity graph(Scope scope) {
        var graph = graphs.read(scope.graphId());
        if (!scope.customerId().equals(graph.getCustomerId()) || !scope.tenantId().equals(graph.getTenantId())
            || !scope.deploymentId().equals(graph.getDeploymentId())
            || !scope.targetProfileId().equals(graph.getTargetProfileId())
            || !scope.versionId().equals(graph.getVersionId())
            || !scope.specFingerprint().equals(graph.getSpecFingerprint())
            || !scope.bindingFingerprint().equals(graph.getBindingFingerprint())) {
            fail("DEPENDENCY_SCOPE_CHANGED");
        }
        return graph;
    }

    private ManagedDependencyState.Node exactNode(ManagedDependencyGraphEntity graph, Binding binding) {
        if (binding == null) fail("DEPENDENCY_BINDING_REQUIRED");
        var matches = graphs.state(graph).nodes.stream()
            .filter(value -> binding.slot().equals(value.slot))
            .filter(value -> binding.serviceRef().equals(value.serviceRef))
            .filter(value -> binding.accountId().equals(value.accountId))
            .filter(value -> binding.bindingId().equals(value.bindingId))
            .filter(value -> Objects.equals(binding.baseUrl(), value.baseUrl))
            .filter(value -> binding.secretReferences().equals(value.secrets)).toList();
        if (matches.size() != 1) fail("DEPENDENCY_BINDING_CHANGED");
        return matches.getFirst();
    }

    private ManagedDependencySpec.Service service(ManagedDependencyGraphEntity graph, String slot) {
        var matches = graphs.spec(graph).services().stream().filter(value -> slot.equals(value.slot())).toList();
        if (matches.size() != 1) fail("DEPENDENCY_SLOT_MISSING");
        return matches.getFirst();
    }

    private String environment(Scope scope) {
        return admission.require(requirement(scope, graphs.spec(graph(scope))), graph(scope).getReservationRef()).intent()
            == AdmissionContracts.Intent.CUSTOMER_PRODUCTION ? "production" : "staging";
    }

    private long mib(long value) {
        try { return Math.multiplyExact(value, 1024L * 1024L); }
        catch (ArithmeticException error) { fail("DEPENDENCY_CAPACITY_OVERFLOW"); return 0; }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable.", error);
        }
    }

    private void requireScope(Scope scope) {
        if (scope == null) fail("DEPENDENCY_SCOPE_REQUIRED");
        required(scope.graphId(), "DEPENDENCY_SCOPE_REQUIRED");
        required(scope.customerId(), "DEPENDENCY_SCOPE_REQUIRED");
        required(scope.tenantId(), "DEPENDENCY_SCOPE_REQUIRED");
        required(scope.deploymentId(), "DEPENDENCY_SCOPE_REQUIRED");
        required(scope.targetProfileId(), "DEPENDENCY_SCOPE_REQUIRED");
        required(scope.versionId(), "DEPENDENCY_SCOPE_REQUIRED");
        if (scope.specFingerprint() == null || !scope.specFingerprint().matches("[a-f0-9]{64}")
            || scope.bindingFingerprint() == null || !scope.bindingFingerprint().matches("[a-f0-9]{64}")) {
            fail("DEPENDENCY_SCOPE_REQUIRED");
        }
    }

    private String required(String value, String code) {
        if (value == null || value.isBlank()) fail(code);
        return value;
    }

    private ResponseStatusException conflict(String code) {
        return new ResponseStatusException(CONFLICT, code);
    }
}
