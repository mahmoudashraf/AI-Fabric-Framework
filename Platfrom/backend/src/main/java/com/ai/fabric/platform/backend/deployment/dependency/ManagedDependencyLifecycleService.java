package com.ai.fabric.platform.backend.deployment.dependency;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyEnvVar;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.deployment.service.PlatformManagedProductProvisioningService;
import com.ai.fabric.platform.backend.productservice.repository.PlatformManagedProductServiceRepository;
import com.ai.fabric.platform.backend.productservice.service.PlatformManagedProductServiceService;
import com.ai.fabric.platform.backend.secret.service.PlatformSecretService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Executor;
import static com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencySpecValidator.fail;

@Service
public class ManagedDependencyLifecycleService {
    private final ManagedDependencyGraphStore store;
    private final ManagedDependencySpecRepository specs;
    private final ManagedDependencySpecValidator validator;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final DeploymentTargetProfileRepository targets;
    private final DeploymentAccessService access;
    private final PlatformManagedProductProvisioningService provisioning;
    private final PlatformManagedProductServiceService products;
    private final PlatformManagedProductServiceRepository productRepository;
    private final PlatformSecretService secrets;
    private final PlatformAuditService audit;
    private final ObjectProvider<ManagedDependencyPrerequisites> prerequisites;
    private final Executor executor;

    public ManagedDependencyLifecycleService(ManagedDependencyGraphStore store, ManagedDependencySpecRepository specs,
            ManagedDependencySpecValidator validator, DeploymentRepository deployments, DeploymentVersionRepository versions,
            DeploymentTargetProfileRepository targets, DeploymentAccessService access,
            PlatformManagedProductProvisioningService provisioning, PlatformManagedProductServiceService products,
            PlatformManagedProductServiceRepository productRepository, PlatformSecretService secrets,
            PlatformAuditService audit, ObjectProvider<ManagedDependencyPrerequisites> prerequisites,
            @Qualifier("releaseExecutionExecutor") Executor executor) {
        this.store = store; this.specs = specs; this.validator = validator; this.deployments = deployments;
        this.versions = versions; this.targets = targets; this.access = access; this.provisioning = provisioning;
        this.products = products; this.productRepository = productRepository; this.secrets = secrets;
        this.audit = audit; this.prerequisites = prerequisites; this.executor = executor;
    }

    public record Request(String specRef, String versionId, String bindingFingerprint, String operationKey) {}
    public record NodeSummary(String slot, String status, String baseUrl, String accountId, String bindingId) {}
    public record OperatorSummary(String id, String displayName, String slot, String status, int credentialRevision,
                                  boolean delivered, Instant effectiveAt) {}
    public record Summary(String graphId, String deploymentId, String targetProfileId, String versionId,
                          String specFingerprint, String bindingFingerprint, long revision, String status,
                          String step, String failureCode, int attempt, long configurationRevision,
                          long effectiveConfigurationRevision, List<NodeSummary> services, List<OperatorSummary> operators) {}

    // Called by approved bootstrap/apply. The request selects a reviewed specification, not its contents.
    public Summary submit(String deploymentId, String targetProfileId, Request request) {
        requireScope(deploymentId, targetProfileId, true);
        required(request.operationKey(), 128); required(request.specRef(), 64); required(request.versionId(), 64);
        if (request.bindingFingerprint() == null || !request.bindingFingerprint().matches("[a-f0-9]{64}")) fail("BINDING_FINGERPRINT_REQUIRED");
        var version = versions.findById(request.versionId()).orElseThrow(() -> conflict("DEPENDENCY_CANDIDATE_MISSING"));
        if (!deploymentId.equals(version.getDeploymentId())) fail("DEPENDENCY_CANDIDATE_MISMATCH");
        var reviewed = specs.findById(request.specRef()).filter(ManagedDependencySpecEntity::isActive)
            .orElseThrow(() -> conflict("REVIEWED_DEPENDENCY_SPEC_MISSING"));
        var spec = store.parse(reviewed.getSpecJson(), ManagedDependencySpec.class);
        validator.validate(spec);
        if (reviewed.getReviewedAt() == null || reviewed.getReviewedBy() == null || reviewed.getReviewedBy().isBlank()
            || !validator.fingerprint(spec).equals(reviewed.getFingerprint())) fail("DEPENDENCY_SPEC_REVIEW_INVALID");
        String fingerprint = validator.fingerprint(request);
        String graphId = store.transaction(repository -> {
            var deployment = deployments.findByIdForUpdate(deploymentId).orElseThrow();
            var existing = repository.findByDeploymentIdAndTargetProfileId(deploymentId, targetProfileId).orElse(null);
            if (existing != null) {
                var graph = repository.lockById(existing.getId()).orElseThrow();
                var state = store.state(graph);
                if (!request.versionId().equals(graph.getVersionId()) || !reviewed.getFingerprint().equals(graph.getSpecFingerprint())
                    || !request.bindingFingerprint().equals(graph.getBindingFingerprint()) || "RETIRED".equals(graph.getStatus())) {
                    fail("DEPENDENCY_IMMUTABLE_GRAPH_CONFLICT");
                }
                command(state, request.operationKey(), fingerprint, graph.getId());
                graph.setStateJson(store.json(state));
                repository.saveAndFlush(graph);
                return graph.getId();
            }
            var graph = new ManagedDependencyGraphEntity();
            graph.setId(id("dpg")); graph.setDeploymentId(deploymentId); graph.setTargetProfileId(targetProfileId);
            graph.setCustomerId(deployment.getCustomerId()); graph.setTenantId(deployment.getTenantId());
            graph.setVersionId(request.versionId()); graph.setSpecRef(reviewed.getId());
            graph.setSpecFingerprint(reviewed.getFingerprint()); graph.setBindingFingerprint(request.bindingFingerprint());
            graph.setSpecJson(store.json(spec)); graph.setStatus("PLANNED"); graph.setStep("ADMISSION");
            graph.setCreatedAt(Instant.now()); graph.setUpdatedAt(graph.getCreatedAt());
            var state = new ManagedDependencyState();
            for (var service : spec.services()) {
                var node = new ManagedDependencyState.Node(); node.slot = service.slot();
                node.serviceRef = "dep-" + graph.getId().substring(4) + "-" + service.slot();
                node.accountId = id("account"); node.bindingId = id("binding");
                for (String purpose : service.secretPurposes()) node.secrets.put(purpose, managedSecretName(graph.getId(), service.slot(), purpose));
                state.nodes.add(node);
            }
            command(state, request.operationKey(), fingerprint, graph.getId());
            graph.setStateJson(store.json(state)); repository.saveAndFlush(graph);
            return graph.getId();
        });
        audit(graphId, "DEPENDENCY_GRAPH_REQUESTED", Map.of("operationKey", request.operationKey()));
        // No public endpoint calls submit. Admission and reviewed bindings remain parent-controlled.
        return summary(store.read(graphId));
    }

    public Summary status(String deploymentId, String targetProfileId) {
        requireScope(deploymentId, targetProfileId, false);
        return summary(find(deploymentId, targetProfileId));
    }

    /** Explicit new candidate/binding revision, retaining the same immutable dependency topology and fixtures. */
    public Summary bindCandidate(String deploymentId, String targetProfileId, long expectedGraphRevision, Request request) {
        requireScope(deploymentId, targetProfileId, true);
        required(request.operationKey(), 128);
        var version = versions.findById(request.versionId()).orElseThrow(() -> conflict("DEPENDENCY_CANDIDATE_MISSING"));
        if (!deploymentId.equals(version.getDeploymentId()) || request.bindingFingerprint() == null
            || !request.bindingFingerprint().matches("[a-f0-9]{64}")) fail("DEPENDENCY_CANDIDATE_MISMATCH");
        var graph = find(deploymentId, targetProfileId);
        store.mutate(graph.getId(), null, (current, state) -> {
            if (!command(state, request.operationKey(), validator.fingerprint(List.of("BIND_CANDIDATE", expectedGraphRevision, request)), current.getId())) return;
            if (current.getWorkerToken() != null || current.getStatus().startsWith("RETIR")
                || current.getRevision() != expectedGraphRevision) fail("DEPENDENCY_OPERATION_IN_PROGRESS");
            if (!current.getSpecRef().equals(request.specRef())) fail("DEPENDENCY_IMMUTABLE_SPEC_CONFLICT");
            current.setVersionId(request.versionId()); current.setBindingFingerprint(request.bindingFingerprint());
            state.callbacks.clear(); state.origins.clear(); state.targetValues.clear(); state.targetSecretReferences.clear();
            state.configurationRevision++; state.effectiveConfigurationRevision = -1;
            for (var node : state.nodes) { node.initialized = false; node.status = "PLANNED"; }
            current.setStatus("PLANNED"); current.setStep("ADMISSION"); current.setFailureCode(null);
        });
        audit(graph.getId(), "DEPENDENCY_CANDIDATE_BOUND", Map.of("versionId", request.versionId(), "operationKey", request.operationKey()));
        return summary(store.read(graph.getId()));
    }

    public Summary reconcile(String deploymentId, String targetProfileId, String operationKey) {
        return schedule(deploymentId, targetProfileId, operationKey, true);
    }

    public Summary provision(String deploymentId, String targetProfileId, String operationKey) {
        return schedule(deploymentId, targetProfileId, operationKey, false);
    }

    private Summary schedule(String deploymentId, String targetProfileId, String operationKey, boolean initialize) {
        requireScope(deploymentId, targetProfileId, true);
        var graph = find(deploymentId, targetProfileId);
        required(operationKey, 128);
        boolean[] accepted = {false};
        store.mutate(graph.getId(), null, (current, state) -> {
            if ("RETIRING".equals(current.getStatus()) || "RETIRED".equals(current.getStatus())) fail("DEPENDENCY_RETIREMENT_IN_PROGRESS");
            accepted[0] = command(state, operationKey, validator.fingerprint(List.of(initialize ? "RECONCILE" : "PROVISION", current.getSpecFingerprint(),
                current.getBindingFingerprint(), state.configurationRevision)), current.getId());
        });
        if (!accepted[0]) return summary(store.read(graph.getId()));
        String token = id("worker");
        boolean claimed = store.transaction(repository -> {
            var current = repository.lockById(graph.getId()).orElseThrow();
            if (current.getWorkerToken() != null) return false;
            if ("RETIRING".equals(current.getStatus()) || "RETIRED".equals(current.getStatus())) fail("DEPENDENCY_RETIREMENT_IN_PROGRESS");
            current.setWorkerToken(token); current.setStatus("RUNNING"); current.setStep("ADMISSION");
            current.setFailureCode(null); current.setAttempt(current.getAttempt() + 1);
            current.setUpdatedAt(Instant.now()); current.setRevision(current.getRevision() + 1);
            repository.saveAndFlush(current); return true;
        });
        if (claimed) {
            try { executor.execute(() -> execute(graph.getId(), token, initialize)); }
            catch (RuntimeException error) { failure(graph.getId(), token, "DEPENDENCY_EXECUTOR_REJECTED"); }
        }
        return summary(store.read(graph.getId()));
    }

    void execute(String graphId, String token, boolean initialize) {
        try {
            var graph = store.read(graphId); var spec = store.spec(graph); validator.validate(spec);
            if (!validator.fingerprint(spec).equals(graph.getSpecFingerprint())) fail("DEPENDENCY_SPEC_DRIFT");
            var gate = gate(); var scope = scope(graph);
            var admission = gate.reserve(scope, spec);
            validateAdmission(graph, admission);
            store.mutate(graphId, token, (current, state) -> {
                if (current.getReservationRef() != null && !current.getReservationRef().equals(admission.reservationRef())) fail("DEPENDENCY_RESERVATION_DRIFT");
                if (!state.callbacks.isEmpty() && (!state.callbacks.equals(admission.callbacks()) || !state.origins.equals(admission.origins()))) fail("DEPENDENCY_BINDING_DRIFT");
                if (!state.targetValues.isEmpty() && !state.targetValues.equals(admission.targetValues())
                    || !state.targetSecretReferences.isEmpty() && !state.targetSecretReferences.equals(admission.targetSecretReferences())) fail("DEPENDENCY_BINDING_DRIFT");
                current.setReservationRef(admission.reservationRef()); state.callbacks = new TreeMap<>(admission.callbacks());
                state.origins = List.copyOf(admission.origins());
                state.targetValues = new TreeMap<>(admission.targetValues()); state.targetSecretReferences = new TreeMap<>(admission.targetSecretReferences());
            });
            for (var service : spec.services()) gate.validateArtifact(service);
            // All prerequisites and operator enrollment must be available before creating any provider resource.
            var initial = store.state(store.read(graphId));
            for (var service : spec.services()) {
                if (service.contract() == ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1
                    && !node(initial, service.slot()).disabled
                    && initial.operators.stream().noneMatch(o -> service.slot().equals(o.slot) && "ENROLLED".equals(o.status))) fail("INBOX_OPERATOR_ENROLLMENT_REQUIRED");
                for (var value : service.environment().values()) {
                    if (value.kind() == ManagedDependencySpec.ValueKind.CALLBACK_URL && !initial.callbacks.containsKey(value.reference())) fail("DEPENDENCY_CALLBACK_MISSING");
                    if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_VALUE && !initial.targetValues.containsKey(value.reference())) fail("DEPENDENCY_TARGET_VALUE_MISSING");
                    if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET && !initial.targetSecretReferences.containsKey(value.reference())) fail("DEPENDENCY_TARGET_SECRET_MISSING");
                    if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET) secret(initial.targetSecretReferences.get(value.reference()));
                }
            }
            CoolifyConnection connection = provisioning.ownedDependencyConnection(graph.getTargetProfileId(), environment(graph));
            boolean[] disabled = {false};
            for (var service : spec.services()) {
                guard(graphId, token);
                store.mutate(graphId, token, (current, state) -> current.setStep("PROVISION:" + service.slot()));
                var node = node(store.state(store.read(graphId)), service.slot());
                if (node.disabled) {
                    gate.disableAndVerifyBinding(scope, binding(node));
                    provisioning.disableOwnedDependency(connection, graphId, node.serviceRef, service.slot(), node.applicationUuid,
                        checkpoint(graphId, token, service.slot()));
                    store.mutate(graphId, token, (current, state) -> node(state, service.slot()).status = "DISABLED");
                    if (node.applicationUuid != null) updateProduct(node.serviceRef, node.baseUrl, "DISABLED");
                    disabled[0] = true;
                    continue;
                }
                products.ensureOwnedDependency(graphId, graph.getDeploymentId(), graph.getTargetProfileId(),
                    node.serviceRef, service.slot(), service.image(), graph.getSpecFingerprint());
                for (var secret : node.secrets.entrySet()) {
                    if (!secrets.isSecretPresent(secret.getValue())) {
                        if (node.applicationUuid != null || node.databaseUuid != null) fail("DEPENDENCY_SECRET_LOST");
                        secrets.upsertManagedSecret(secret.getValue(), credential(), Map.of("dependencyGraphId", graphId,
                            "deploymentId", graph.getDeploymentId(), "targetProfileId", graph.getTargetProfileId(), "purpose", secret.getKey()));
                    }
                }
                var result = provisioning.reconcileOwnedDependency(connection, graphId, node.serviceRef, service,
                    resources(node), databaseUuid -> environment(graphId, service, connection, databaseUuid), checkpoint(graphId, token, service.slot()));
                store.mutate(graphId, token, (current, state) -> {
                    var owned = node(state, service.slot()); owned.baseUrl = result.baseUrl(); owned.status = "PROVISIONED";
                    owned.provisionedConfigurationRevision = state.configurationRevision;
                });
                updateProduct(node.serviceRef, result.baseUrl(), "PROVISIONED");
            }
            if (!initialize) {
                store.mutate(graphId, token, (current, state) -> {
                    current.setStatus(disabled[0] ? "BLOCKED" : "PROVISIONED"); current.setStep("RUNTIME_INITIALIZATION_REQUIRED");
                    current.setFailureCode(disabled[0] ? "INBOX_DISABLED" : null); current.setWorkerToken(null);
                });
                audit(graphId, "DEPENDENCY_RESOURCES_PROVISIONED", Map.of());
                return;
            }
            for (var service : spec.services()) {
                var readyNode = node(store.state(store.read(graphId)), service.slot());
                if (readyNode.disabled) continue;
                store.mutate(graphId, token, (current, state) -> {
                    node(state, service.slot()).status = "INITIALIZING"; current.setStep("INITIALIZE:" + service.slot());
                });
                guard(graphId, token);
                gate.initializeAndVerify(scope, service.initializationContract(), binding(readyNode));
                store.mutate(graphId, token, (current, state) -> {
                    var owned = node(state, service.slot()); owned.initialized = true; owned.status = "READY";
                });
                updateProduct(readyNode.serviceRef, readyNode.baseUrl, "ACTIVE");
            }
            for (String reference : store.state(store.read(graphId)).retiredOperatorSecretNames) {
                secrets.clearManagedSecret(reference, Map.of("dependencyGraphId", graphId, "deploymentId", graph.getDeploymentId(), "purpose", "RETIRED_OPERATOR_KEY"));
            }
            store.mutate(graphId, token, (current, state) -> {
                current.setStatus(disabled[0] ? "BLOCKED" : "READY"); current.setStep("COMPLETE");
                current.setFailureCode(disabled[0] ? "INBOX_DISABLED" : null); current.setWorkerToken(null);
                state.effectiveConfigurationRevision = state.configurationRevision;
                for (var operator : state.operators) operator.effectiveAt = Instant.now();
                state.retiredOperatorSecretNames.clear();
            });
            audit(graphId, disabled[0] ? "DEPENDENCY_GRAPH_DISABLED" : "DEPENDENCY_GRAPH_READY", Map.of());
        } catch (RuntimeException error) {
            String code = error instanceof ResponseStatusException response && response.getReason() != null
                && response.getReason().matches("[A-Z][A-Z0-9_]{1,63}") ? response.getReason() : "DEPENDENCY_RECONCILE_FAILED";
            if (token.equals(store.read(graphId).getWorkerToken())) failure(graphId, token, code);
        }
    }

    public Summary recoverInterrupted(String deploymentId, String targetProfileId, String expectedWorkerToken, String operationKey) {
        requireScope(deploymentId, targetProfileId, true);
        var graph = find(deploymentId, targetProfileId);
        if (expectedWorkerToken == null || !expectedWorkerToken.equals(graph.getWorkerToken())) fail("DEPENDENCY_WORKER_GENERATION_CONFLICT");
        gate().requireInterruptedWorkerTerminated(scope(graph), expectedWorkerToken);
        store.mutate(graph.getId(), expectedWorkerToken, (current, state) -> {
            current.setWorkerToken(null); current.setStatus("BLOCKED"); current.setFailureCode("DEPENDENCY_INTERRUPTED");
        });
        return reconcile(deploymentId, targetProfileId, operationKey);
    }

    private List<CoolifyEnvVar> environment(String graphId, ManagedDependencySpec.Service service,
                                           CoolifyConnection connection, String databaseUuid) {
        var graph = store.read(graphId); var state = store.state(graph); var node = node(state, service.slot());
        List<CoolifyEnvVar> values = new ArrayList<>();
        // Database creation precedes the application's database URL resolution.
        if (service.database() != null) values.add(env("__DATABASE_PASSWORD", secret(node.secrets.get(service.database().passwordPurpose()))));
        if (service.database() != null && databaseUuid == null) return values;
        for (var entry : service.environment().entrySet()) {
            var value = entry.getValue();
            String resolved = switch (value.kind()) {
                case LITERAL -> value.reference();
                case SECRET -> secret(node.secrets.get(value.reference()));
                case SECRET_BCRYPT_BASE64 -> Base64.getEncoder().encodeToString(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
                    .encode(secret(node.secrets.get(value.reference()))).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                case SECRET_STANDARD_BASE64 -> Base64.getEncoder().encodeToString(Base64.getUrlDecoder().decode(secret(node.secrets.get(value.reference()))));
                case SERVICE_SECRET -> secret(node(state, value.serviceSlot()).secrets.get(value.reference()));
                case TARGET_VALUE -> state.targetValues.get(value.reference());
                case TARGET_SECRET -> secret(state.targetSecretReferences.get(value.reference()));
                case ACCOUNT_ID -> node.accountId;
                case BINDING_ID -> node.bindingId;
                case CUSTOMER_ID -> graph.getCustomerId();
                case TENANT_ID -> graph.getTenantId();
                case DEPLOYMENT_ID -> graph.getDeploymentId();
                case TARGET_ID -> graph.getTargetProfileId();
                case CALLBACK_URL -> state.callbacks.get(value.reference());
                case CALLBACK_HOSTS -> String.join(",", state.callbacks.values().stream().map(v -> URI.create(v).getHost()).distinct().sorted().toList());
                case ORIGINS -> String.join(",", state.origins);
                case PUBLIC_URL -> "https://" + node.serviceRef + "." + connection.config().defaultPublicDomainSuffix().replaceFirst("^\\.", "");
                case SERVICE_URL -> node(state, value.reference()).baseUrl;
                case DATABASE_JDBC -> provisioning.ownedDependencyDatabaseJdbc(connection, databaseUuid);
                case DATABASE_USERNAME -> "dependency";
                case OPERATORS -> operatorConfiguration(state, service.slot(), node.bindingId);
            };
            if (resolved == null || resolved.isBlank()) fail("DEPENDENCY_ENVIRONMENT_UNRESOLVED");
            values.add(env(entry.getKey(), resolved));
        }
        return values;
    }

    private String operatorConfiguration(ManagedDependencyState state, String slot, String bindingId) {
        return String.join(";", state.operators.stream().filter(o -> slot.equals(o.slot) && "ENROLLED".equals(o.status))
            .sorted(java.util.Comparator.comparing(o -> o.id)).map(o -> o.id + "|" + o.displayName + "|" + secret(o.secretName) + "|" + bindingId).toList());
    }
    private CoolifyEnvVar env(String name, String value) { return new CoolifyEnvVar(name, value, false, true, value.contains("\n"), true); }
    private String secret(String reference) {
        String value = reference == null ? null : secrets.resolveSecret(reference);
        if (value == null || value.length() < 32) fail("DEPENDENCY_SECRET_MISSING");
        return value;
    }
    void guard(String graphId, String token) {
        var graph = store.read(graphId);
        if (!token.equals(graph.getWorkerToken())) fail("DEPENDENCY_WORKER_FENCED");
        var deployment = deployments.findById(graph.getDeploymentId()).orElseThrow(() -> conflict("DEPENDENCY_DEPLOYMENT_MISSING"));
        if (!graph.getCustomerId().equals(deployment.getCustomerId()) || !graph.getTenantId().equals(deployment.getTenantId())
            || deployment.getArchivedAt() != null) fail("DEPENDENCY_OWNER_DRIFT");
        var reviewed = specs.findById(graph.getSpecRef()).orElseThrow(() -> conflict("REVIEWED_DEPENDENCY_SPEC_MISSING"));
        if (!reviewed.isActive() || !graph.getSpecFingerprint().equals(reviewed.getFingerprint())) fail("DEPENDENCY_SPEC_REVIEW_REVOKED");
        gate().validateReservation(scope(graph), graph.getReservationRef());
    }
    PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint(String graphId, String token, String slot) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint() {
            public void guard() { ManagedDependencyLifecycleService.this.guard(graphId, token); }
            public void creating(String kind) {
                store.mutate(graphId, token, (graph, state) -> {
                    var node = node(state, slot);
                    if ("APPLICATION".equals(kind)) node.applicationCreateIntent = true;
                    else node.databaseCreateIntent = true;
                });
            }
            public void created(String kind, String uuid) {
                required(uuid, 255);
                store.mutate(graphId, token, (graph, state) -> {
                    var node = node(state, slot);
                    if ("APPLICATION".equals(kind)) node.applicationUuid = uuid;
                    else node.databaseUuid = uuid;
                });
            }
            public void volume(String mountPath, String uuid) {
                store.mutate(graphId, token, (graph, state) -> {
                    String existing = node(state, slot).volumes.putIfAbsent(mountPath, uuid);
                    if (existing != null && !existing.equals(uuid)) fail("DEPENDENCY_VOLUME_GENERATION_CONFLICT");
                });
            }
        };
    }
    public List<ManagedDependencyPrerequisites.Binding> bindings(String deploymentId, String targetProfileId,
            String expectedSpecFingerprint, String expectedBindingFingerprint) {
        return bindingProjection(deploymentId, targetProfileId, expectedSpecFingerprint, expectedBindingFingerprint, true);
    }

    public List<ManagedDependencyPrerequisites.Binding> provisionedBindings(String deploymentId, String targetProfileId,
            String expectedSpecFingerprint, String expectedBindingFingerprint) {
        return bindingProjection(deploymentId, targetProfileId, expectedSpecFingerprint, expectedBindingFingerprint, false);
    }

    public record RuntimeGenerationEvidence(Map<String, String> readbacks) {
        public RuntimeGenerationEvidence {
            readbacks = readbacks == null ? Map.of() : Map.copyOf(readbacks);
        }
    }

    /** Internal apply integration. It fences only dependency processes that call back into the runtime. */
    public RuntimeGenerationEvidence quiesceForRuntimeGeneration(String deploymentId, String targetProfileId,
            String previousReleaseId, String nextReleaseId, String nextVersionId) {
        var graph = generationGraph(deploymentId, targetProfileId, nextVersionId);
        if (graph == null) return new RuntimeGenerationEvidence(Map.of());
        var spec = store.spec(graph);
        var inboxServices = spec.services().stream()
            .filter(service -> service.contract() == ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1)
            .toList();
        if (inboxServices.isEmpty()) return new RuntimeGenerationEvidence(Map.of());
        if (inboxServices.size() != 1) fail("DEPENDENCY_GENERATION_INBOX_AMBIGUOUS");
        String operationKey = generationOperationKey(graph, previousReleaseId, nextReleaseId);
        beginGenerationTransition(graph.getId(), operationKey);
        var connection = provisioning.ownedDependencyConnection(graph.getTargetProfileId(), environment(graph));
        var service = inboxServices.getFirst();
        var state = store.state(store.read(graph.getId()));
        var node = node(state, service.slot());
        String reference = provisioning.quiesceOwnedDependencyForRuntimeGeneration(
            connection, graph.getId(), node.serviceRef, service.slot(), node.applicationUuid,
            generationCheckpoint(graph.getId(), operationKey)
        );
        store.mutate(graph.getId(), null, (current, currentState) -> {
            requireGenerationOperation(current, currentState, operationKey);
            if (!currentState.runtimeGenerationStoppedSlots.contains(service.slot())) {
                currentState.runtimeGenerationStoppedSlots.add(service.slot());
                currentState.runtimeGenerationStoppedSlots.sort(String::compareTo);
            }
            currentState.runtimeGenerationState = "QUIESCED";
        });
        return new RuntimeGenerationEvidence(Map.of("HUMAN_INBOX", reference));
    }

    public void resumeAfterRuntimeGeneration(String deploymentId, String targetProfileId,
            String previousReleaseId, String nextReleaseId, String nextVersionId) {
        var graph = generationGraph(deploymentId, targetProfileId, nextVersionId);
        if (graph == null) return;
        String operationKey = generationOperationKey(graph, previousReleaseId, nextReleaseId);
        var state = store.state(graph);
        if (state.runtimeGenerationOperationKey == null) return;
        requireGenerationOperation(graph, state, operationKey);
        if ("RESUMED".equals(state.runtimeGenerationState)) return;
        if (!"QUIESCED".equals(state.runtimeGenerationState)) fail("DEPENDENCY_GENERATION_NOT_QUIESCED");
        var spec = store.spec(graph);
        var connection = provisioning.ownedDependencyConnection(graph.getTargetProfileId(), environment(graph));
        for (String slot : List.copyOf(state.runtimeGenerationStoppedSlots)) {
            var service = spec.services().stream().filter(value -> slot.equals(value.slot()))
                .findFirst().orElseThrow(() -> conflict("DEPENDENCY_GENERATION_SLOT_MISSING"));
            if (service.contract() != ManagedDependencySpec.Contract.BASIC_INBOX_KEYS_V1) {
                fail("DEPENDENCY_GENERATION_SLOT_INVALID");
            }
            var current = store.state(store.read(graph.getId()));
            var node = node(current, slot);
            provisioning.resumeOwnedDependencyAfterRuntimeGeneration(
                connection, graph.getId(), node.serviceRef, slot, node.applicationUuid,
                generationCheckpoint(graph.getId(), operationKey)
            );
            store.mutate(graph.getId(), null, (owned, currentState) -> {
                requireGenerationOperation(owned, currentState, operationKey);
                currentState.runtimeGenerationStoppedSlots.remove(slot);
            });
        }
        store.mutate(graph.getId(), null, (current, currentState) -> {
            requireGenerationOperation(current, currentState, operationKey);
            if (!currentState.runtimeGenerationStoppedSlots.isEmpty()) fail("DEPENDENCY_GENERATION_RESUME_INCOMPLETE");
            currentState.runtimeGenerationState = "RESUMED";
        });
    }

    private ManagedDependencyGraphEntity generationGraph(String deploymentId, String targetProfileId, String nextVersionId) {
        var graph = store.find(deploymentId, targetProfileId).orElse(null);
        if (graph == null) return null;
        if (!deploymentId.equals(graph.getDeploymentId()) || !targetProfileId.equals(graph.getTargetProfileId())
            || !nextVersionId.equals(graph.getVersionId()) || graph.getWorkerToken() != null
            || !"READY".equals(graph.getStatus())) fail("DEPENDENCY_GENERATION_GRAPH_NOT_READY");
        return graph;
    }

    private void beginGenerationTransition(String graphId, String operationKey) {
        store.mutate(graphId, null, (graph, state) -> {
            if (state.runtimeGenerationOperationKey != null && !state.runtimeGenerationOperationKey.equals(operationKey)
                && !"RESUMED".equals(state.runtimeGenerationState)) fail("DEPENDENCY_GENERATION_OPERATION_CONFLICT");
            if (!operationKey.equals(state.runtimeGenerationOperationKey)) {
                state.runtimeGenerationOperationKey = operationKey;
                state.runtimeGenerationState = "QUIESCING";
                state.runtimeGenerationStoppedSlots.clear();
            }
        });
    }

    private void requireGenerationOperation(ManagedDependencyGraphEntity graph, ManagedDependencyState state,
            String operationKey) {
        if (!operationKey.equals(state.runtimeGenerationOperationKey) || graph.getWorkerToken() != null
            || !"READY".equals(graph.getStatus())) fail("DEPENDENCY_GENERATION_OPERATION_CHANGED");
    }

    private PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint generationCheckpoint(
            String graphId, String operationKey) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint() {
            @Override public void guard() {
                var current = store.read(graphId);
                requireGenerationOperation(current, store.state(current), operationKey);
            }
            @Override public void creating(String kind) { fail("DEPENDENCY_GENERATION_RESOURCE_CREATION_FORBIDDEN"); }
            @Override public void created(String kind, String uuid) { fail("DEPENDENCY_GENERATION_RESOURCE_CREATION_FORBIDDEN"); }
            @Override public void volume(String mountPath, String uuid) { fail("DEPENDENCY_GENERATION_RESOURCE_CREATION_FORBIDDEN"); }
        };
    }

    private String generationOperationKey(ManagedDependencyGraphEntity graph, String previousReleaseId,
            String nextReleaseId) {
        required(previousReleaseId, 64);
        required(nextReleaseId, 64);
        return validator.fingerprint(List.of("RUNTIME_GENERATION", graph.getId(), graph.getTargetProfileId(),
            previousReleaseId, nextReleaseId, graph.getSpecFingerprint(), graph.getBindingFingerprint()));
    }

    private List<ManagedDependencyPrerequisites.Binding> bindingProjection(String deploymentId, String targetProfileId,
            String expectedSpecFingerprint, String expectedBindingFingerprint, boolean readyRequired) {
        requireScope(deploymentId, targetProfileId, false);
        var graph = find(deploymentId, targetProfileId);
        if (!Objects.equals(expectedSpecFingerprint, graph.getSpecFingerprint())
            || !Objects.equals(expectedBindingFingerprint, graph.getBindingFingerprint())
            || graph.getWorkerToken() != null || graph.getStatus().startsWith("RETIR")
            || readyRequired && !"READY".equals(graph.getStatus())) fail("DEPENDENCY_BINDINGS_NOT_READY");
        gate().validateReservation(scope(graph), graph.getReservationRef());
        var state = store.state(graph);
        if (state.nodes.stream().anyMatch(n -> n.disabled || n.baseUrl == null || n.applicationUuid == null
            || n.provisionedConfigurationRevision != state.configurationRevision)) fail("DEPENDENCY_BINDINGS_NOT_READY");
        return state.nodes.stream().map(this::binding).toList();
    }
    ManagedDependencyPrerequisites.Binding binding(ManagedDependencyState.Node node) {
        return new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl, node.accountId, node.bindingId, Map.copyOf(node.secrets));
    }
    ManagedDependencyPrerequisites gate() {
        var value = prerequisites.getIfAvailable();
        if (value == null) fail("DEPENDENCY_ADMISSION_ADAPTER_REQUIRED");
        return value;
    }
    ManagedDependencyPrerequisites.Scope scope(ManagedDependencyGraphEntity graph) {
        return new ManagedDependencyPrerequisites.Scope(graph.getId(), graph.getCustomerId(), graph.getTenantId(),
            graph.getDeploymentId(), graph.getTargetProfileId(), graph.getVersionId(), graph.getSpecFingerprint(), graph.getBindingFingerprint());
    }
    ManagedDependencyGraphEntity find(String deploymentId, String targetProfileId) {
        return store.transaction(repository -> repository.findByDeploymentIdAndTargetProfileId(deploymentId, targetProfileId)
            .orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "Dependency graph not found.")));
    }
    void requireScope(String deploymentId, String targetProfileId, boolean mutate) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> conflict("DEPENDENCY_DEPLOYMENT_MISSING"));
        if (mutate) access.requireDeploymentAdminAccess(deployment); else access.requireDeploymentAccess(deployment);
        if (deployment.getCustomerId() == null || deployment.getCustomerId().isBlank() || deployment.getTenantId() == null
            || deployment.getTenantId().isBlank() || deployment.getArchivedAt() != null) fail("DEPENDENCY_OWNER_REQUIRED");
        var target = targets.findById(targetProfileId).orElseThrow(() -> conflict("DEPENDENCY_TARGET_MISSING"));
        if (!target.isActive()) fail("DEPENDENCY_TARGET_INACTIVE");
    }
    String environment(ManagedDependencyGraphEntity graph) {
        return targets.findById(graph.getTargetProfileId()).orElseThrow().getEnvironmentName();
    }
    void updateProduct(String serviceRef, String baseUrl, String status) {
        var product = products.requireService(serviceRef);
        product.setBaseUrl(baseUrl); product.setStatus(status);
        product.setActualReplicas(java.util.Set.of("ACTIVE", "PROVISIONED").contains(status) ? 1 : 0);
        product.setUpdatedAt(Instant.now()); productRepository.save(product);
    }
    private void failure(String graphId, String token, String code) {
        store.mutate(graphId, token, (graph, state) -> {
            graph.setStatus("BLOCKED"); graph.setFailureCode(code); graph.setWorkerToken(null);
        });
        audit(graphId, "DEPENDENCY_GRAPH_BLOCKED", Map.of("code", code));
    }
    void audit(String graphId, String action, Map<String, ?> details) { audit.record(action, "DEPENDENCY_GRAPH", graphId, details); }
    private void validateAdmission(ManagedDependencyGraphEntity graph, ManagedDependencyPrerequisites.Admission admission) {
        if (admission == null || admission.reservationRef() == null || admission.reservationRef().isBlank()
            || !graph.getBindingFingerprint().equals(admission.bindingFingerprint()) || admission.callbacks() == null
            || admission.origins() == null || admission.origins().isEmpty() || admission.targetValues() == null
            || admission.targetSecretReferences() == null) fail("DEPENDENCY_ADMISSION_INVALID");
        admission.callbacks().values().forEach(ManagedDependencyLifecycleService::https);
        admission.origins().forEach(ManagedDependencyLifecycleService::https);
    }
    static void https(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || uri.getRawQuery() != null) fail("DEPENDENCY_TRUSTED_HTTPS_REQUIRED");
        } catch (IllegalArgumentException error) { fail("DEPENDENCY_TRUSTED_HTTPS_REQUIRED"); }
    }
    Summary summary(ManagedDependencyGraphEntity graph) {
        var state = store.state(graph);
        return new Summary(graph.getId(), graph.getDeploymentId(), graph.getTargetProfileId(), graph.getVersionId(),
            graph.getSpecFingerprint(), graph.getBindingFingerprint(), graph.getRevision(), graph.getStatus(), graph.getStep(),
            graph.getFailureCode(), graph.getAttempt(), state.configurationRevision, state.effectiveConfigurationRevision,
            state.nodes.stream().map(n -> new NodeSummary(n.slot, n.status, n.baseUrl, n.accountId, n.bindingId)).toList(),
            state.operators.stream().map(o -> new OperatorSummary(o.id, o.displayName, o.slot, o.status, o.credentialRevision, o.delivered, o.effectiveAt)).toList());
    }
    static PlatformManagedProductProvisioningService.OwnedDependencyResources resources(ManagedDependencyState.Node node) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(node.applicationUuid, node.databaseUuid,
            node.applicationCreateIntent, node.databaseCreateIntent, Map.copyOf(node.volumes));
    }
    static ManagedDependencyState.Node node(ManagedDependencyState state, String slot) {
        return state.nodes.stream().filter(n -> slot.equals(n.slot)).findFirst().orElseThrow(() -> conflict("DEPENDENCY_SLOT_MISSING"));
    }
    static boolean command(ManagedDependencyState state, String key, String fingerprint, String resultId) {
        required(key, 128);
        var existing = state.commands.get(key);
        if (existing != null) {
            if (!fingerprint.equals(existing.fingerprint())) fail("DEPENDENCY_OPERATION_KEY_CONFLICT");
            return false;
        }
        if (state.commands.size() >= 10000) fail("DEPENDENCY_OPERATION_LEDGER_FULL");
        state.commands.put(key, new ManagedDependencyState.Command(fingerprint, resultId, Instant.now(),
            com.ai.fabric.platform.backend.security.PlatformSecurityContext.actorIdOrSystem(),
            com.ai.fabric.platform.backend.security.PlatformSecurityContext.actorRoleOrSystem())); return true;
    }
    static void required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max) fail("DEPENDENCY_REQUEST_INVALID");
    }
    static String id(String prefix) { return prefix + "-" + UUID.randomUUID().toString().replace("-", ""); }
    static String credential() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    String managedSecretName(String graphId, String slot, String purpose) {
        return ("MANAGED_DEP_" + graphId.substring(4) + "_" + validator.fingerprint(List.of(slot, purpose)).substring(0, 24))
            .toUpperCase(java.util.Locale.ROOT);
    }
    static ResponseStatusException conflict(String code) { return new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, code); }
}
