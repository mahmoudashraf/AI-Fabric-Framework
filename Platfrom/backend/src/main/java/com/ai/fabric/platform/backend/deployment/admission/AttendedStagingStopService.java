package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.config.CustomerSelfServiceProperties;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.TargetIntent;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyGraphStore;
import com.ai.fabric.platform.backend.deployment.dependency.ManagedDependencyRetirementAdapter;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentTargetProfileRepository;
import com.ai.fabric.platform.backend.deployment.retirement.CoolifyHostVolumeReclamationService;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementGateService;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementInventory;
import com.ai.fabric.platform.backend.deployment.service.CoolifyApiClient;
import com.ai.fabric.platform.backend.deployment.service.CoolifyConnection;
import com.ai.fabric.platform.backend.deployment.service.CoolifyTargetProfileResolver;
import com.ai.fabric.platform.backend.security.PlatformRole;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.admission.AdmissionColdTerminationReview.*;
import static com.ai.fabric.platform.backend.deployment.admission.AttendedStagingStopContracts.*;
import static org.springframework.http.HttpStatus.*;

/** No provider mutation: staff stop the frozen native resources, then this service verifies the host. */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class AttendedStagingStopService {
    private final AdmissionStore store;
    private final AdmissionJson json;
    private final JdbcTemplate jdbc;
    private final TargetRetirementInventory inventory;
    private final TargetRetirementGateService gates;
    private final TargetRetirementGateRepository gateRepository;
    private final ManagedDependencyGraphStore graphs;
    private final ManagedDependencyRetirementAdapter dependencies;
    private final DeploymentTargetProfileRepository targets;
    private final CoolifyTargetProfileResolver resolver;
    private final CoolifyApiClient client;
    private final CoolifyHostVolumeReclamationService host;
    private final CustomerSelfServiceProperties properties;

    public AttendedStagingStopService(AdmissionStore store, AdmissionJson json, JdbcTemplate jdbc,
        TargetRetirementInventory inventory, TargetRetirementGateService gates,
        TargetRetirementGateRepository gateRepository, ManagedDependencyGraphStore graphs,
        ManagedDependencyRetirementAdapter dependencies, DeploymentTargetProfileRepository targets,
        CoolifyTargetProfileResolver resolver, CoolifyApiClient client,
        CoolifyHostVolumeReclamationService host, CustomerSelfServiceProperties properties) {
        this.store = store; this.json = json; this.jdbc = jdbc; this.inventory = inventory;
        this.gates = gates; this.gateRepository = gateRepository; this.graphs = graphs;
        this.dependencies = dependencies; this.targets = targets; this.resolver = resolver;
        this.client = client; this.host = host; this.properties = properties;
    }

    public Summary plan(String reservationId, Request request) {
        String actor = actor();
        if (request == null || request.fence() == null || !reservationId.equals(request.fence().reservationId())
            || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000
            || !request.acceptDowntimeVolatileStateLossAndWaiveRecoveryWindow()) throw failure(BAD_REQUEST, "ATTENDED_STOP_EXPLICIT_ACCEPTANCE_REQUIRED");
        Reservation r = store.require(reservationId);
        if (!r.fence().equals(request.fence())) throw failure(CONFLICT, "STALE_RESERVATION_FENCE");
        requireUnusedFailedTarget(r);
        var connection = connection(r.targetProfileId());
        var trusted = properties.trustedServers().get(TargetIntent.CUSTOMER_STAGING);
        var server = client.getServer(connection, connection.config().serverUuid());
        if (trusted == null || !Objects.equals(trusted.uuid(), connection.config().serverUuid())
            || !Objects.equals(trusted.reportedAddress(), server.path("ip").asText())
            || !Objects.equals(trusted.uuid(), server.path("uuid").asText()))
            throw failure(PRECONDITION_FAILED, "ATTENDED_STOP_PLACEMENT_UNPROVEN");
        List<ReleaseBinding> releases = releaseBindings(r);
        var existingGate = jdbc.queryForList("SELECT epoch,state FROM platform_target_retirement_gates WHERE deployment_id=? AND target_profile_id=?",
            r.deploymentId(), r.targetProfileId());
        if (existingGate.size() > 1 || !existingGate.isEmpty() && !"AVAILABLE".equals(existingGate.getFirst().get("state")))
            throw failure(CONFLICT, "TARGET_RETIREMENT_FENCED");
        long epoch = existingGate.isEmpty() ? 0L : ((Number) existingGate.getFirst().get("epoch")).longValue();
        var snapshot = inventory.capture(r.deploymentId(), r.targetProfileId(), retirementRequest(r, releases), epoch);
        var resources = new ArrayList<NativeResource>();
        for (var resource : snapshot.resources()) if (!resource.preserved()) {
            resources.add(observe(connection, resource.resourceKind(), resource.providerResourceUuid(),
                resource.deploymentMarker(), false));
        }
        String graphId = null, graphFingerprint = null; long graphRevision = -1;
        var graph = graphs.find(r.deploymentId(), r.targetProfileId());
        if (graph.isPresent()) {
            var value = graph.get();
            if (!r.customerId().equals(value.getCustomerId()) || !r.tenantId().equals(value.getTenantId())
                || value.getWorkerToken() != null || !"READY".equals(value.getStatus()))
                throw failure(CONFLICT, "ATTENDED_STOP_DEPENDENCY_BUSY_OR_CHANGED");
            var plan = dependencies.planForTargetRetirement(r.deploymentId(), r.targetProfileId());
            if (!plan.blockers().isEmpty() || plan.resources().size() != 1
                || !"inbox".equals(plan.resources().getFirst().slot()))
                throw failure(PRECONDITION_FAILED, "ATTENDED_STOP_DEPENDENCY_TOPOLOGY_UNSUPPORTED");
            graphId = value.getId(); graphRevision = value.getRevision(); graphFingerprint = plan.fingerprint();
            for (var node : plan.resources()) {
                String marker = "ManagedDependency:" + graphId + ":" + node.slot();
                if (node.applicationUuid() != null) resources.add(observe(connection, "MANAGED_INBOX_APPLICATION", node.applicationUuid(), marker, false));
                if (node.databaseUuid() != null) resources.add(observe(connection, "MANAGED_INBOX_DATABASE", node.databaseUuid(), marker, false));
            }
        }
        resources.sort(Comparator.comparing(NativeResource::uuid));
        if (resources.isEmpty() || resources.stream().map(NativeResource::uuid).distinct().count() != resources.size())
            throw failure(PRECONDITION_FAILED, "ATTENDED_STOP_INVENTORY_INCOMPLETE");
        Instant now = Instant.now(), expiry = now.plusSeconds(900);
        String id = UUID.randomUUID().toString(), reference = "attended-staging-stop:" + id;
        var capture = new ServerCapture(reference, now, expiry, r, snapshot, releases,
            new Placement(trusted.uuid(), connection.config().destinationUuid(), trusted.address()),
            snapshot.resources().stream().filter(resource -> !resource.preserved()).findFirst().orElseThrow().projectUuid(),
            snapshot.resources().stream().filter(resource -> !resource.preserved()).findFirst().orElseThrow().environmentUuid(),
            EnumSet.allOf(Confirmation.class));
        var expected = new Expectation(r.fence(), r.customerId(), r.tenantId(), r.deploymentId(), r.targetProfileId(), r.workerToken());
        var acknowledgement = new Acknowledgement(reference, reference, actor, request.reason(), now, expiry,
            EnumSet.allOf(LossAcknowledgement.class));
        var reviewed = AdmissionColdTerminationReview.freeze(expected, capture, acknowledgement, now);
        var plan = new Plan(id, actor, reviewed.payload(), graphId, graphRevision, graphFingerprint, resources, expiry);
        store.reviewAttendedStop(plan, actor);
        return summary(plan, null, null);
    }

    public Summary claim(String reservationId, String planId, Command command) {
        Plan plan = requirePlan(reservationId, planId, command);
        recheckRemote(plan, false);
        Claim claim = store.claimAttendedStop(plan, actor(), () -> {
            recheckLocal(plan);
            var r = plan.review().capture().reservation();
            var gate = gates.lock(r.deploymentId(), r.targetProfileId());
            if (!"AVAILABLE".equals(gate.getState()) || gate.getEpoch() != plan.review().capture().snapshot().targetEpoch())
                throw failure(CONFLICT, "TARGET_GENERATION_CHANGED");
            // Keep the existing mutation barrier closed while storage remains. This is not RETIRED/drained.
            gate.setEpoch(gate.getEpoch() + 1); gate.setState("RETIRING"); gate.setOperationId(plan.id());
            gate.setUpdatedAt(Instant.now()); gateRepository.saveAndFlush(gate);
            return gate.getEpoch();
        });
        return summary(plan, claim, null);
    }

    public Summary settle(String reservationId, String planId, Command command) {
        Plan plan = requirePlan(reservationId, planId, command);
        var prior = optionalReceipt(planId);
        if (prior != null) return summary(plan, prior.claim(), prior);
        Claim claim = store.reviewed("ATTENDED_STOP_CLAIM", planId, Claim.class);
        if (!claim.actorId().equals(actor()) || !claim.planFingerprint().equals(json.fingerprint(plan)))
            throw failure(CONFLICT, "ATTENDED_STOP_COMMAND_CHANGED");
        recheckRemote(plan, true);
        for (var resource : plan.resources()) host.verifyNoRunningContainers(TargetIntent.CUSTOMER_STAGING,
            resource.serverUuid(), plan.id(), database(resource.kind()) ? "database" : "application", resource.uuid());
        var proof = new StoppedReadback(plan.id(), json.fingerprint(plan), claim.targetEpoch(), plan.resources(),
            Instant.now(), "provider-host-attended-stop:" + plan.id());
        var receipt = store.settleAttendedStop(claim, proof, actor(), () -> {
            recheckLocal(plan);
            var r = plan.review().capture().reservation();
            var gate = gates.lock(r.deploymentId(), r.targetProfileId());
            if (!"RETIRING".equals(gate.getState()) || !plan.id().equals(gate.getOperationId())
                || gate.getEpoch() != claim.targetEpoch()) throw failure(CONFLICT, "ATTENDED_STOP_TARGET_FENCE_CHANGED");
        });
        return summary(plan, claim, receipt);
    }

    private Plan requirePlan(String reservationId, String id, Command command) {
        String actor = actor();
        var plan = store.reviewed("ATTENDED_STOP_PLAN", id, Plan.class);
        if (!actor.equals(plan.actorId()) || !reservationId.equals(plan.review().expectation().fence().reservationId())
            || command == null || !json.fingerprint(plan).equals(command.fingerprint()))
            throw failure(CONFLICT, "ATTENDED_STOP_COMMAND_CHANGED");
        return plan;
    }
    private Receipt optionalReceipt(String id) {
        var rows = jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews WHERE kind='ATTENDED_STOP_RECEIPT' AND record_key=?", String.class, id);
        return rows.isEmpty() ? null : json.read(rows.getFirst(), Receipt.class);
    }
    private Summary summary(Plan plan, Claim claim, Receipt receipt) {
        return new Summary(plan.id(), json.fingerprint(plan), receipt != null ? "COMPUTE_STOPPED_STORAGE_RETAINED_NOT_DRAINED"
            : claim != null ? "CLAIMED_NATIVE_STOP_REQUIRED" : "REVIEWED_NO_EFFECT", plan.resources(), claim, receipt, plan.expiresAt());
    }
    private void recheckRemote(Plan plan, boolean stopped) {
        var r = plan.review().capture().reservation();
        var connection = connection(r.targetProfileId());
        inventory.recheck(plan.review().capture().snapshot(), retirementRequest(r, plan.review().capture().releaseBindings()));
        for (var resource : plan.resources()) if (!resource.equals(observe(connection, resource.kind(), resource.uuid(), resource.marker(), stopped)))
            throw failure(CONFLICT, "ATTENDED_STOP_NATIVE_IDENTITY_CHANGED");
    }
    private void recheckLocal(Plan plan) {
        var r = plan.review().capture().reservation();
        requireUnusedFailedTarget(r);
        inventory.recheckLocal(plan.review().capture().snapshot(), retirementRequest(r, plan.review().capture().releaseBindings()));
        var graph = graphs.find(r.deploymentId(), r.targetProfileId());
        if (plan.dependencyGraphId() == null) {
            if (graph.isPresent()) throw failure(CONFLICT, "ATTENDED_STOP_DEPENDENCY_CHANGED");
        } else {
            var value = graph.orElseThrow(() -> failure(CONFLICT, "ATTENDED_STOP_DEPENDENCY_CHANGED"));
            if (!plan.dependencyGraphId().equals(value.getId()) || value.getRevision() != plan.dependencyRevision()
                || value.getWorkerToken() != null || !plan.dependencyFingerprint().equals(
                    dependencies.planForTargetRetirement(r.deploymentId(), r.targetProfileId()).fingerprint()))
                throw failure(CONFLICT, "ATTENDED_STOP_DEPENDENCY_CHANGED");
        }
    }
    private void requireUnusedFailedTarget(Reservation r) {
        if (r.intent() != Intent.CUSTOMER_STAGING || r.retirementOperationId() != null
            || r.retentionSettlement() != null || r.expiryState() != ExpiryState.CLAIMED
            || !List.of(State.OUTCOME_UNKNOWN, State.CLEANUP_FAILED).contains(r.state()))
            throw failure(CONFLICT, "ATTENDED_STOP_FAILED_STAGING_ONLY");
        // activeVersionId identifies the published candidate, including failed applies; it is not activation proof.
        if (count("SELECT count(*) FROM platform_consumers WHERE bound_deployment_id=? AND bound_release_id IS NOT NULL", r.deploymentId()) != 0
            || count("SELECT count(*) FROM platform_consumer_binding_history WHERE to_deployment_id=? AND to_release_id IS NOT NULL", r.deploymentId()) != 0
            || count("SELECT count(*) FROM ai_workspace_installations i JOIN platform_consumers c ON c.id=i.consumer_entity_id WHERE c.bound_deployment_id=? AND (i.status='ACTIVE' OR i.activated_at IS NOT NULL)", r.deploymentId()) != 0
            || count("SELECT count(*) FROM platform_deployment_releases WHERE deployment_id=? AND (status='APPLIED_VERIFIED' OR verification_status='PASSED' OR provisioning_status IN ('QUEUED','PROVISIONING','VERIFYING'))", r.deploymentId()) != 0
            || count("SELECT count(*) FROM platform_target_retirement_operations WHERE deployment_id=? AND target_profile_id=?", r.deploymentId(), r.targetProfileId()) != 0)
            throw failure(CONFLICT, "ATTENDED_STOP_NEVER_ACTIVATED_IDLE_TARGET_REQUIRED");
        var current = store.require(r.id());
        for (String row : jdbc.queryForList("SELECT payload_json FROM deployment_admission_reviews WHERE kind='ART_CLAIM'", String.class)) {
            var claim = json.read(row, RuntimeArtifactRepairContracts.Claim.class);
            if (current.fence().equals(claim.reservationFence()) && Objects.equals(current.workerToken(), claim.workerToken())
                && claim.executionDeadline().isAfter(Instant.now())) throw failure(CONFLICT, "ATTENDED_STOP_REPAIR_ACTIVE");
        }
    }
    private int count(String sql, Object... parameters) { return Objects.requireNonNull(jdbc.queryForObject(sql, Integer.class, parameters)); }
    private List<ReleaseBinding> releaseBindings(Reservation r) {
        return jdbc.queryForList("SELECT id FROM platform_deployment_releases WHERE deployment_id=? AND target_profile_id=? ORDER BY id", String.class,
            r.deploymentId(), r.targetProfileId()).stream().map(store::releaseBinding).toList();
    }
    private TargetRetirementContracts.PlanRequest retirementRequest(Reservation r, List<ReleaseBinding> bindings) {
        var consumers = jdbc.query("SELECT id,binding_revision FROM platform_consumers WHERE bound_deployment_id=? ORDER BY id",
            (rs, row) -> new TargetRetirementContracts.ConsumerExpectation(rs.getString(1), rs.getLong(2)), r.deploymentId());
        return new TargetRetirementContracts.PlanRequest("attended-staging-stop:" + r.id(),
            bindings.stream().map(ReleaseBinding::releaseId).sorted().toList(), consumers, r.drainDeadline(),
            new TargetRetirementContracts.Retention(TargetRetirementContracts.RetentionDecision.RECLAIM, null, null,
                "STORAGE_RETAINED_NO_DELETE_NO_DRAIN"), "Staff-attended stop only; no resource or storage reclamation.");
    }
    private CoolifyConnection connection(String targetId) {
        var target = targets.findById(targetId).orElseThrow(() -> failure(NOT_FOUND, "TARGET_MISSING"));
        if (!target.isActive() || !"staging".equals(target.getEnvironmentName())) throw failure(FORBIDDEN, "STAGING_TARGET_REQUIRED");
        return resolver.requireConnection(target);
    }
    private NativeResource observe(CoolifyConnection connection, String kind, String uuid, String marker, boolean stopped) {
        JsonNode raw = database(kind) ? client.getDatabase(connection, uuid).orElseThrow(() -> failure(CONFLICT, "ATTENDED_STOP_NATIVE_MISSING")).raw()
            : client.getApplication(connection, uuid).orElseThrow(() -> failure(CONFLICT, "ATTENDED_STOP_NATIVE_MISSING")).raw();
        String server = raw.path("destination").path("server").path("uuid").asText();
        String destination = raw.path("destination").path("uuid").asText();
        if (!uuid.equals(raw.path("uuid").asText()) || !marker.equals(raw.path("description").asText())
            || !connection.config().serverUuid().equals(server) || !connection.config().destinationUuid().equals(destination))
            throw failure(CONFLICT, "ATTENDED_STOP_NATIVE_OWNERSHIP_CHANGED");
        if (!database(kind) && client.applicationDeploymentHistory(connection, uuid).stream().anyMatch(job ->
            !List.of("finished", "failed", "cancelled", "canceled", "skipped").contains(job.status())))
            throw failure(CONFLICT, "ATTENDED_STOP_NATIVE_DEPLOYMENT_PENDING");
        if (stopped && !List.of("exited", "stopped").contains(raw.path("status").asText().split(":", 2)[0]))
            throw failure(PRECONDITION_FAILED, "ATTENDED_STOP_NATIVE_STILL_RUNNING");
        return new NativeResource(kind, uuid, raw.path("name").asText(), marker, server, destination, raw.path("environment_id").asText());
    }
    private boolean database(String kind) { return List.of("RUNTIME_POSTGRES_DATABASE", "MANAGED_INBOX_DATABASE").contains(kind); }
    private String actor() {
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null || principal.role() != PlatformRole.PLATFORM_ADMIN) throw failure(FORBIDDEN, "ATTENDED_STOP_STAFF_ONLY");
        return principal.actorId();
    }
    private AdmissionFailure failure(org.springframework.http.HttpStatus status, String code) { return new AdmissionFailure(status, code); }
}
