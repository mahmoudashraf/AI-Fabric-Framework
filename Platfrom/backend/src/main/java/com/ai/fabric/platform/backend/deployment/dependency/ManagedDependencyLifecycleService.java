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
import java.util.Set;
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
    private final ManagedDependencyCustomerOfferGuard customerOfferGuard;

    public ManagedDependencyLifecycleService(ManagedDependencyGraphStore store, ManagedDependencySpecRepository specs,
            ManagedDependencySpecValidator validator, DeploymentRepository deployments, DeploymentVersionRepository versions,
            DeploymentTargetProfileRepository targets, DeploymentAccessService access,
            PlatformManagedProductProvisioningService provisioning, PlatformManagedProductServiceService products,
            PlatformManagedProductServiceRepository productRepository, PlatformSecretService secrets,
            PlatformAuditService audit, ObjectProvider<ManagedDependencyPrerequisites> prerequisites,
            @Qualifier("releaseExecutionExecutor") Executor executor,
            ManagedDependencyCustomerOfferGuard customerOfferGuard) {
        this.store = store; this.specs = specs; this.validator = validator; this.deployments = deployments;
        this.versions = versions; this.targets = targets; this.access = access; this.provisioning = provisioning;
        this.products = products; this.productRepository = productRepository; this.secrets = secrets;
        this.audit = audit; this.prerequisites = prerequisites; this.executor = executor;
        this.customerOfferGuard = customerOfferGuard;
    }

    public record Request(String specRef, String versionId, String releaseId, String bindingFingerprint,
                          String operationKey) {
        public Request(String specRef, String versionId, String bindingFingerprint, String operationKey) {
            this(specRef, versionId, null, bindingFingerprint, operationKey);
        }
    }
    public record NodeSummary(String slot, String status, String baseUrl, String accountId, String bindingId) {}
    public record OperatorSummary(String id, String displayName, String slot, String status, int credentialRevision,
                                  boolean delivered, Instant effectiveAt) {}
    public record Summary(String graphId, String deploymentId, String targetProfileId, String versionId,
                          String releaseId,
                          String specFingerprint, String bindingFingerprint, long revision, String status,
                          String step, String failureCode, int attempt, long configurationRevision,
                          long effectiveConfigurationRevision, List<NodeSummary> services, List<OperatorSummary> operators) {}

    // Called by approved bootstrap/apply. The request selects a reviewed specification, not its contents.
    public Summary submit(String deploymentId, String targetProfileId, Request request) {
        requireScope(deploymentId, targetProfileId, true);
        return submitInternal(deploymentId, targetProfileId, request);
    }

    /** Release-worker entry point. The caller must already have frozen the exact release authority. */
    Summary submitForRelease(String deploymentId, String targetProfileId, Request request) {
        requireReleaseScope(deploymentId, targetProfileId, request);
        return submitInternal(deploymentId, targetProfileId, request);
    }

    private Summary submitInternal(String deploymentId, String targetProfileId, Request request) {
        required(request.operationKey(), 128); required(request.specRef(), 64); required(request.versionId(), 64);
        if (request.bindingFingerprint() == null || !request.bindingFingerprint().matches("[a-f0-9]{64}")) fail("BINDING_FINGERPRINT_REQUIRED");
        var version = versions.findById(request.versionId()).orElseThrow(() -> conflict("DEPENDENCY_CANDIDATE_MISSING"));
        if (!deploymentId.equals(version.getDeploymentId())) fail("DEPENDENCY_CANDIDATE_MISMATCH");
        var reviewed = specs.findById(request.specRef()).filter(ManagedDependencySpecEntity::isActive)
            .orElseThrow(() -> conflict("REVIEWED_DEPENDENCY_SPEC_MISSING"));
        var spec = store.parse(reviewed.getSpecJson(), ManagedDependencySpec.class);
        validator.validate(spec);
        ManagedDependencySpecValidator.requireProvisionable(spec);
        if (reviewed.getReviewedAt() == null || reviewed.getReviewedBy() == null || reviewed.getReviewedBy().isBlank()
            || !validator.fingerprint(spec).equals(reviewed.getFingerprint())) fail("DEPENDENCY_SPEC_REVIEW_INVALID");
        String fingerprint = validator.fingerprint(request);
        String graphId = store.transaction(repository -> {
            var deployment = deployments.findByIdForUpdate(deploymentId).orElseThrow();
            var existing = repository.findByDeploymentIdAndTargetProfileId(deploymentId, targetProfileId).orElse(null);
            if (existing != null) {
                var graph = repository.lockById(existing.getId()).orElseThrow();
                var state = store.state(graph);
                if (!reviewed.getFingerprint().equals(graph.getSpecFingerprint()) || "RETIRED".equals(graph.getStatus())) {
                    fail("DEPENDENCY_IMMUTABLE_GRAPH_CONFLICT");
                }
                boolean releaseTransition = !request.versionId().equals(graph.getVersionId())
                    || !request.bindingFingerprint().equals(graph.getBindingFingerprint())
                    || !Objects.equals(request.releaseId(), graph.getReleaseId())
                    || !reviewed.getId().equals(graph.getSpecRef());
                if (releaseTransition) {
                    if (request.releaseId() == null) fail("DEPENDENCY_IMMUTABLE_GRAPH_CONFLICT");
                    if (graph.getWorkerToken() != null || graph.getStatus().startsWith("RETIR")) {
                        fail("DEPENDENCY_OPERATION_IN_PROGRESS");
                    }
                    graph.setVersionId(request.versionId());
                    graph.setSpecRef(reviewed.getId());
                    graph.setReleaseId(request.releaseId());
                    graph.setBindingFingerprint(request.bindingFingerprint());
                    graph.setReservationRef(null);
                    state.callbacks.clear(); state.origins.clear(); state.targetValues.clear();
                    state.targetSecretReferences.clear();
                    state.configurationRevision++;
                    state.effectiveConfigurationRevision = -1;
                    for (var node : state.nodes) { node.initialized = false; node.status = "PLANNED"; }
                    graph.setStatus("PLANNED"); graph.setStep("ADMISSION"); graph.setFailureCode(null);
                }
                command(state, request.operationKey(), fingerprint, graph.getId());
                graph.setStateJson(store.json(state));
                repository.saveAndFlush(graph);
                return graph.getId();
            }
            var graph = new ManagedDependencyGraphEntity();
            graph.setId(id("dpg")); graph.setDeploymentId(deploymentId); graph.setTargetProfileId(targetProfileId);
            graph.setCustomerId(deployment.getCustomerId()); graph.setTenantId(deployment.getTenantId());
            graph.setVersionId(request.versionId()); graph.setReleaseId(request.releaseId()); graph.setSpecRef(reviewed.getId());
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
        ManagedDependencySpecValidator.requireProvisionable(store.spec(graph));
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


    Summary provisionForRelease(String deploymentId, String targetProfileId, String releaseId,
                                String operationKey) {
        return runForRelease(deploymentId, targetProfileId, releaseId, operationKey, false);
    }

    Summary reconcileForRelease(String deploymentId, String targetProfileId, String releaseId,
                                String operationKey) {
        return runForRelease(deploymentId, targetProfileId, releaseId, operationKey, true);
    }

    private Summary schedule(String deploymentId, String targetProfileId, String operationKey, boolean initialize) {
        requireScope(deploymentId, targetProfileId, true);
        var graph = find(deploymentId, targetProfileId);
        return schedule(graph, operationKey, initialize, false);
    }

    private Summary runForRelease(String deploymentId, String targetProfileId, String releaseId,
                                  String operationKey, boolean initialize) {
        var graph = find(deploymentId, targetProfileId);
        if (releaseId == null || !releaseId.equals(graph.getReleaseId())) fail("DEPENDENCY_RELEASE_BINDING_CHANGED");
        schedule(graph, operationKey, initialize, true);
        var completed = find(deploymentId, targetProfileId);
        String expected = initialize ? "READY" : "PROVISIONED";
        if (!expected.equals(completed.getStatus())) {
            fail(completed.getFailureCode() == null ? "DEPENDENCY_RELEASE_RECONCILE_INCOMPLETE" : completed.getFailureCode());
        }
        return summary(completed);
    }

    private Summary schedule(ManagedDependencyGraphEntity graph, String operationKey, boolean initialize,
                             boolean synchronous) {
        ManagedDependencySpecValidator.requireProvisionable(store.spec(graph));
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
            if (synchronous) {
                execute(graph.getId(), token, initialize);
            } else {
                try { executor.execute(() -> execute(graph.getId(), token, initialize)); }
                catch (RuntimeException error) { failure(graph.getId(), token, "DEPENDENCY_EXECUTOR_REJECTED"); }
            }
        }
        return summary(store.read(graph.getId()));
    }

    void execute(String graphId, String token, boolean initialize) {
        try {
            var graph = store.read(graphId); var spec = store.spec(graph); validator.validate(spec);
            ManagedDependencySpecValidator.requireProvisionable(spec);
            if (!validator.fingerprint(spec).equals(graph.getSpecFingerprint())) fail("DEPENDENCY_SPEC_DRIFT");
            customerOfferGuard.requireActiveExecution(graph);
            var gate = gate(); var scope = scope(graph);
            var phase = initialize
                ? ManagedDependencyPrerequisites.ProvisioningPhase.RUNTIME_BOUND
                : ManagedDependencyPrerequisites.ProvisioningPhase.FOUNDATION;
            var admission = gate.reserve(scope, spec, phase);
            validateAdmission(graph, admission);
            store.mutate(graphId, token, (current, state) -> {
                if (current.getReservationRef() != null && !current.getReservationRef().equals(admission.authorityReference())) fail("DEPENDENCY_AUTHORIZATION_DRIFT");
                if (!state.callbacks.isEmpty() && !compatible(state.callbacks, admission.callbacks())) fail("DEPENDENCY_BINDING_DRIFT");
                if (!state.origins.isEmpty() && !state.origins.equals(admission.origins())) fail("DEPENDENCY_BINDING_DRIFT");
                if (!state.targetValues.isEmpty() && !compatible(state.targetValues, admission.targetValues())
                    || !state.targetSecretReferences.isEmpty() && !compatible(state.targetSecretReferences, admission.targetSecretReferences())) fail("DEPENDENCY_BINDING_DRIFT");
                current.setReservationRef(admission.authorityReference()); state.callbacks = new TreeMap<>(admission.callbacks());
                state.origins = List.copyOf(admission.origins());
                state.targetValues = new TreeMap<>(admission.targetValues()); state.targetSecretReferences = new TreeMap<>(admission.targetSecretReferences());
            });
            for (var service : spec.services()) gate.validateArtifact(scope, service);
            // Resolve all prerequisites before creating any provider resource.
            var initial = store.state(store.read(graphId));
            List<ManagedDependencySpec.Service> phaseServices = spec.services().stream()
                .filter(service -> initialize || foundationService(service, spec))
                .toList();
            for (var service : phaseServices) {
                for (var value : service.environment().values()) {
                    if (value.kind() == ManagedDependencySpec.ValueKind.CALLBACK_URL && !initial.callbacks.containsKey(value.reference())) fail("DEPENDENCY_CALLBACK_MISSING");
                    if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_VALUE && !initial.targetValues.containsKey(value.reference())) fail("DEPENDENCY_TARGET_VALUE_MISSING");
                    if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET && !initial.targetSecretReferences.containsKey(value.reference())) fail("DEPENDENCY_TARGET_SECRET_MISSING");
                    if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET) secret(initial.targetSecretReferences.get(value.reference()));
                }
            }
            CoolifyConnection connection = provisioning.ownedDependencyConnection(graph.getTargetProfileId(), environment(graph));
            for (var service : spec.services()) {
                guard(graphId, token);
                var owned = node(store.state(store.read(graphId)), service.slot());
                products.ensureOwnedDependency(graphId, graph.getDeploymentId(), graph.getTargetProfileId(),
                    owned.serviceRef, service.slot(), service.image(), graph.getSpecFingerprint());
                for (var secret : owned.secrets.entrySet()) {
                    if (!secrets.isSecretPresent(secret.getValue())) {
                        if (owned.applicationUuid != null || owned.databaseUuid != null) fail("DEPENDENCY_SECRET_LOST");
                        guard(graphId, token);
                        secrets.upsertManagedSecret(secret.getValue(), credential(), Map.of("dependencyGraphId", graphId,
                            "deploymentId", graph.getDeploymentId(), "targetProfileId", graph.getTargetProfileId(), "purpose", secret.getKey()));
                    }
                }
            }
            boolean[] disabled = {false};
            for (var service : phaseServices) {
                guard(graphId, token);
                store.mutate(graphId, token, (current, state) -> current.setStep("PROVISION:" + service.slot()));
                var node = node(store.state(store.read(graphId)), service.slot());
                if (node.disabled) {
                    if (admission.existingConfiguration()) fail("DEPENDENCY_EXISTING_RESOURCES_REQUIRED");
                    gate.disableAndVerifyBinding(scope, binding(node));
                    provisioning.disableOwnedDependency(connection, graphId, node.serviceRef, service.slot(), node.applicationUuid,
                        checkpoint(graphId, token, service.slot()));
                    store.mutate(graphId, token, (current, state) -> node(state, service.slot()).status = "DISABLED");
                    if (node.applicationUuid != null) updateProduct(node.serviceRef, node.baseUrl, "DISABLED");
                    disabled[0] = true;
                    continue;
                }
                var result = admission.existingConfiguration()
                    ? provisioning.reconcileExistingOwnedDependency(connection, graphId, node.serviceRef, service,
                        resources(node), databaseUuid -> environment(graphId, service, connection, databaseUuid),
                        checkpoint(graphId, token, service.slot(), true))
                    : provisioning.reconcileOwnedDependency(connection, graphId, node.serviceRef, service,
                        resources(node), databaseUuid -> environment(graphId, service, connection, databaseUuid), checkpoint(graphId, token, service.slot()));
                store.mutate(graphId, token, (current, state) -> {
                    var owned = node(state, service.slot()); owned.baseUrl = result.baseUrl(); owned.status = "PROVISIONED";
                    owned.provisionedConfigurationRevision = state.configurationRevision;
                });
                updateProduct(node.serviceRef, result.baseUrl(), "PROVISIONED");
            }
            if (!initialize) {
                store.mutate(graphId, token, (current, state) -> {
                    customerOfferGuard.requireActiveExecution(current);
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
                var initializationGraph = store.read(graphId);
                var initializationState = store.state(initializationGraph);
                String initializationKey = validator.fingerprint(List.of("INITIALIZE", graphId,
                    service.slot(), initializationState.configurationRevision,
                    Objects.toString(initializationGraph.getReleaseId(), ""),
                    initializationGraph.getSpecFingerprint(), initializationGraph.getBindingFingerprint()));
                gate.initializeAndVerify(scope, service.initializationContract(), binding(readyNode),
                    initializationKey);
                store.mutate(graphId, token, (current, state) -> {
                    var owned = node(state, service.slot()); owned.initialized = true; owned.status = "READY";
                });
                updateProduct(readyNode.serviceRef, readyNode.baseUrl, "ACTIVE");
            }
            store.mutate(graphId, token, (current, state) -> {
                customerOfferGuard.requireActiveExecution(current);
                current.setStatus(disabled[0] ? "BLOCKED" : "READY"); current.setStep("COMPLETE");
                current.setFailureCode(disabled[0] ? "INBOX_DISABLED" : null); current.setWorkerToken(null);
                state.effectiveConfigurationRevision = state.configurationRevision;
            });
            audit(graphId, disabled[0] ? "DEPENDENCY_GRAPH_DISABLED" : "DEPENDENCY_GRAPH_READY", Map.of());
        } catch (RuntimeException error) {
            String code = error instanceof ResponseStatusException response && response.getReason() != null
                && response.getReason().matches("[A-Z][A-Z0-9_]{1,63}") ? response.getReason() : "DEPENDENCY_RECONCILE_FAILED";
            if (error instanceof com.ai.fabric.platform.backend.deployment.service.CoolifyApiException providerError
                && providerError.statusCode() >= 100 && providerError.statusCode() <= 599) {
                code = "DEPENDENCY_PROVIDER_HTTP_" + providerError.statusCode();
            }
            if (token.equals(store.read(graphId).getWorkerToken())) failure(graphId, token, code);
        }
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
                case VERSION_ID -> graph.getVersionId();
                case SERVICE_REF -> node.serviceRef;
                case CALLBACK_URL -> state.callbacks.get(value.reference());
                case CALLBACK_HOSTS -> String.join(",", state.callbacks.values().stream().map(v -> URI.create(v).getHost()).distinct().sorted().toList());
                case ORIGINS -> String.join(",", state.origins);
                case PUBLIC_URL -> "https://" + node.serviceRef + "." + connection.config().defaultPublicDomainSuffix().replaceFirst("^\\.", "");
                case SERVICE_URL -> node(state, value.reference()).baseUrl;
                case SERVICE_URL_PATH -> joinServiceUrl(node(state, value.serviceSlot()).baseUrl, value.reference());
                case SERVICE_HOST -> serviceHost(node(state, value.reference()).baseUrl);
                case DATABASE_JDBC -> provisioning.ownedDependencyDatabaseJdbc(connection, databaseUuid);
                case DATABASE_USERNAME -> "dependency";
                case OPERATORS -> throw conflict(ManagedDependencySpecValidator.RETIRED_INBOX);
            };
            if (resolved == null || resolved.isBlank()) fail("DEPENDENCY_ENVIRONMENT_UNRESOLVED");
            values.add(env(entry.getKey(), resolved));
        }
        return values;
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
        customerOfferGuard.requireActiveExecution(graph);
        var deployment = deployments.findById(graph.getDeploymentId()).orElseThrow(() -> conflict("DEPENDENCY_DEPLOYMENT_MISSING"));
        if (!graph.getCustomerId().equals(deployment.getCustomerId()) || !graph.getTenantId().equals(deployment.getTenantId())
            || deployment.getArchivedAt() != null) fail("DEPENDENCY_OWNER_DRIFT");
        var reviewed = specs.findById(graph.getSpecRef()).orElseThrow(() -> conflict("REVIEWED_DEPENDENCY_SPEC_MISSING"));
        if (!reviewed.isActive() || !graph.getSpecFingerprint().equals(reviewed.getFingerprint())) fail("DEPENDENCY_SPEC_REVIEW_REVOKED");
        gate().validateExecutionReservation(scope(graph), graph.getReservationRef());
    }
    PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint(String graphId, String token, String slot) {
        return checkpoint(graphId, token, slot, false);
    }
    private PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint checkpoint(String graphId, String token, String slot,
                                                                                          boolean existingOnly) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyCheckpoint() {
            public void guard() { ManagedDependencyLifecycleService.this.guard(graphId, token); }
            public void creating(String kind) {
                if (existingOnly) fail("DEPENDENCY_CONFIGURATION_CANNOT_CREATE");
                store.mutate(graphId, token, (graph, state) -> {
                    var node = node(state, slot);
                    if ("APPLICATION".equals(kind)) node.applicationCreateIntent = true;
                    else node.databaseCreateIntent = true;
                });
            }
            public void created(String kind, String uuid) {
                if (existingOnly) fail("DEPENDENCY_CONFIGURATION_CANNOT_CREATE");
                required(uuid, 255);
                store.mutate(graphId, token, (graph, state) -> {
                    var node = node(state, slot);
                    if ("APPLICATION".equals(kind)) {
                        node.applicationUuid = uuid;
                        node.volumeReclamationPlan.clear();
                        node.reclaimedVolumeUuids.clear();
                    }
                    else node.databaseUuid = uuid;
                });
            }
            public void volume(String mountPath, String uuid) {
                if (existingOnly) fail("DEPENDENCY_CONFIGURATION_CANNOT_CREATE");
                store.mutate(graphId, token, (graph, state) -> {
                    String existing = node(state, slot).volumes.putIfAbsent(mountPath, uuid);
                    if (existing != null && !existing.equals(uuid)) fail("DEPENDENCY_VOLUME_GENERATION_CONFLICT");
                });
            }
            public void volumeReclamationPlanned(Map<String, String> volumeNamesByUuid) { fail("DEPENDENCY_PROVISIONING_CANNOT_RECLAIM"); }
            public void volumesReclaimed(Set<String> volumeUuids) { fail("DEPENDENCY_PROVISIONING_CANNOT_RECLAIM"); }
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

    public record RuntimeEnvironmentProjection(Map<String, String> runtime, Map<String, String> connector) {
        public RuntimeEnvironmentProjection {
            runtime = runtime == null ? Map.of() : Map.copyOf(runtime);
            connector = connector == null ? Map.of() : Map.copyOf(connector);
        }
    }

    /** Internal release planning projection. Values come only from the reviewed immutable specification. */
    public RuntimeEnvironmentProjection runtimeEnvironment(String deploymentId, String targetProfileId,
            String versionId, String expectedBindingFingerprint) {
        var graph = find(deploymentId, targetProfileId);
        if (!versionId.equals(graph.getVersionId()) || !expectedBindingFingerprint.equals(graph.getBindingFingerprint())
            || graph.getWorkerToken() != null || !java.util.Set.of("PROVISIONED", "READY").contains(graph.getStatus())) {
            fail("DEPENDENCY_RUNTIME_BINDINGS_NOT_READY");
        }
        gate().validateReservation(scope(graph), graph.getReservationRef());
        var state = store.state(graph);
        var spec = store.spec(graph);
        var connection = provisioning.ownedDependencyConnection(graph.getTargetProfileId(), environment(graph));
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> connector = new TreeMap<>();
        for (var binding : spec.runtimeEnvironment()) {
            var value = binding.value();
            if (value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET) {
                String secretReference = state.targetSecretReferences.get(value.reference());
                if (secretReference == null || secretReference.isBlank() || !secrets.isSecretPresent(secretReference)) {
                    fail("DEPENDENCY_TARGET_SECRET_MISSING");
                }
            }
            var owned = value.kind() == ManagedDependencySpec.ValueKind.TARGET_SECRET
                ? null
                : node(state, value.kind() == ManagedDependencySpec.ValueKind.SERVICE_URL
                    || value.kind() == ManagedDependencySpec.ValueKind.SERVICE_HOST
                    ? value.reference() : value.serviceSlot());
            String resolved = switch (value.kind()) {
                case SERVICE_URL -> owned.baseUrl == null ? plannedPublicUrl(owned, connection) : owned.baseUrl;
                case SERVICE_URL_PATH -> joinServiceUrl(
                    owned.baseUrl == null ? plannedPublicUrl(owned, connection) : owned.baseUrl,
                    value.reference()
                );
                case SERVICE_HOST -> serviceHost(owned.baseUrl == null ? plannedPublicUrl(owned, connection) : owned.baseUrl);
                case SERVICE_SECRET -> "${secret:" + owned.secrets.get(value.reference()) + "}";
                case TARGET_SECRET -> "${secret:" + state.targetSecretReferences.get(value.reference()) + "}";
                case ACCOUNT_ID -> owned.accountId;
                case BINDING_ID -> owned.bindingId;
                default -> throw conflict("DEPENDENCY_RUNTIME_BINDING_INVALID");
            };
            if (resolved == null || resolved.isBlank()) fail("DEPENDENCY_RUNTIME_BINDING_UNRESOLVED");
            (binding.target() == ManagedDependencySpec.RuntimeTarget.RUNTIME ? runtime : connector)
                .put(binding.name(), resolved);
        }
        return new RuntimeEnvironmentProjection(runtime, connector);
    }

    private String joinServiceUrl(String baseUrl, String relativePath) {
        if (baseUrl == null || baseUrl.isBlank() || relativePath == null || !relativePath.startsWith("/")) {
            fail("DEPENDENCY_SERVICE_URL_PATH_INVALID");
        }
        return baseUrl.replaceFirst("/+$", "") + relativePath;
    }

    private String serviceHost(String baseUrl) {
        try {
            String host = baseUrl == null ? null : URI.create(baseUrl).getHost();
            if (host == null || host.isBlank()) fail("DEPENDENCY_SERVICE_HOST_INVALID");
            return host;
        } catch (IllegalArgumentException error) {
            fail("DEPENDENCY_SERVICE_HOST_INVALID");
            return null;
        }
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
        var available = state.nodes.stream()
            .filter(n -> !n.disabled && n.baseUrl != null && n.applicationUuid != null
                && n.provisionedConfigurationRevision == state.configurationRevision)
            .toList();
        if (readyRequired && available.size() != state.nodes.size()) fail("DEPENDENCY_BINDINGS_NOT_READY");
        return available.stream().map(this::binding).toList();
    }
    ManagedDependencyPrerequisites.Binding binding(ManagedDependencyState.Node node) {
        return new ManagedDependencyPrerequisites.Binding(node.slot, node.serviceRef, node.baseUrl, node.accountId, node.bindingId, Map.copyOf(node.secrets));
    }
    ManagedDependencyPrerequisites gate() {
        var values = prerequisites.orderedStream().toList();
        if (values.size() != 1) fail("DEPENDENCY_ADMISSION_ADAPTER_REQUIRED");
        return values.getFirst();
    }
    ManagedDependencyPrerequisites.Scope scope(ManagedDependencyGraphEntity graph) {
        return new ManagedDependencyPrerequisites.Scope(graph.getId(), graph.getCustomerId(), graph.getTenantId(),
            graph.getDeploymentId(), graph.getTargetProfileId(), graph.getVersionId(), graph.getReleaseId(),
            graph.getSpecFingerprint(), graph.getBindingFingerprint());
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

    private void requireReleaseScope(String deploymentId, String targetProfileId, Request request) {
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> conflict("DEPENDENCY_DEPLOYMENT_MISSING"));
        if (deployment.getCustomerId() == null || deployment.getCustomerId().isBlank()
            || deployment.getTenantId() == null || deployment.getTenantId().isBlank()
            || deployment.getArchivedAt() != null) fail("DEPENDENCY_OWNER_REQUIRED");
        var target = targets.findById(targetProfileId).orElseThrow(() -> conflict("DEPENDENCY_TARGET_MISSING"));
        if (!target.isActive()) fail("DEPENDENCY_TARGET_INACTIVE");
        var version = versions.findById(request.versionId()).orElseThrow(() -> conflict("DEPENDENCY_CANDIDATE_MISSING"));
        if (!deploymentId.equals(version.getDeploymentId())) fail("DEPENDENCY_CANDIDATE_MISMATCH");
        required(request.releaseId(), 64);
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
        if (admission == null || admission.authorityReference() == null || admission.authorityReference().isBlank()
            || admission.coreReleaseAuthorizationRef() != null
                && !admission.coreReleaseAuthorizationRef().equals(graph.getReleaseId())
            || !graph.getBindingFingerprint().equals(admission.bindingFingerprint()) || admission.callbacks() == null
            || admission.origins() == null || admission.targetValues() == null
            || admission.targetSecretReferences() == null) fail("DEPENDENCY_AUTHORIZATION_INVALID");
        admission.callbacks().values().forEach(ManagedDependencyLifecycleService::https);
        admission.origins().forEach(ManagedDependencyLifecycleService::https);
    }
    private boolean foundationService(ManagedDependencySpec.Service service, ManagedDependencySpec spec) {
        boolean runtimeBound = service.environment().values().stream().anyMatch(value ->
            value.kind() == ManagedDependencySpec.ValueKind.CALLBACK_URL
                || value.kind() == ManagedDependencySpec.ValueKind.CALLBACK_HOSTS
                || value.kind() == ManagedDependencySpec.ValueKind.TARGET_VALUE
                && ("runtime-base-url".equals(value.reference()) || "connector-base-url".equals(value.reference())));
        if (runtimeBound) return false;
        return service.dependsOn().stream().allMatch(slot -> {
            var dependency = spec.services().stream().filter(candidate -> slot.equals(candidate.slot())).findFirst()
                .orElseThrow(() -> conflict("DEPENDENCY_SLOT_MISSING"));
            return foundationService(dependency, spec);
        });
    }
    private static boolean compatible(Map<String, String> existing, Map<String, String> candidate) {
        return existing.entrySet().stream().allMatch(entry -> Objects.equals(entry.getValue(), candidate.get(entry.getKey())));
    }
    private String plannedPublicUrl(ManagedDependencyState.Node node, CoolifyConnection connection) {
        String suffix = connection.config().defaultPublicDomainSuffix();
        if (suffix == null || suffix.isBlank()) fail("DEPENDENCY_PUBLIC_DOMAIN_REQUIRED");
        return "https://" + node.serviceRef + "." + suffix.replaceFirst("^\\.", "");
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
            graph.getReleaseId(),
            graph.getSpecFingerprint(), graph.getBindingFingerprint(), graph.getRevision(), graph.getStatus(), graph.getStep(),
            graph.getFailureCode(), graph.getAttempt(), state.configurationRevision, state.effectiveConfigurationRevision,
            state.nodes.stream().map(n -> new NodeSummary(n.slot, n.status, n.baseUrl, n.accountId, n.bindingId)).toList(),
            state.operators.stream().map(o -> new OperatorSummary(o.id, o.displayName, o.slot, o.status, o.credentialRevision, o.delivered, o.effectiveAt)).toList());
    }
    static PlatformManagedProductProvisioningService.OwnedDependencyResources resources(ManagedDependencyState.Node node) {
        return new PlatformManagedProductProvisioningService.OwnedDependencyResources(node.applicationUuid, node.databaseUuid,
            node.applicationCreateIntent, node.databaseCreateIntent, Map.copyOf(node.volumes),
            Map.copyOf(node.volumeReclamationPlan), Set.copyOf(node.reclaimedVolumeUuids));
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
