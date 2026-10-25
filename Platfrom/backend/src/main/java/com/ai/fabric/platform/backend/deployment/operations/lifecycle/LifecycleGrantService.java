package com.ai.fabric.platform.backend.deployment.operations.lifecycle;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.operations.DeploymentOperationsClient;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentAccessService;
import com.ai.fabric.platform.backend.security.PlatformSecurityContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import static com.ai.fabric.platform.backend.deployment.operations.lifecycle.LifecycleContracts.*;
import static org.springframework.http.HttpStatus.*;

@Service
public class LifecycleGrantService {
    private final LifecycleCommandStore store;
    private final DeploymentRepository deployments;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final DeploymentAccessService access;
    private final CustomerSelfServiceOperationBoundary offers;
    private final LifecycleEffects effects;
    private final LifecycleGatewayProperties properties;
    private final DeploymentOperationsClient client;
    private final PlatformAuditService audit;

    public LifecycleGrantService(LifecycleCommandStore store, DeploymentRepository deployments,
        DeploymentReleaseRepository releases, DeploymentVersionRepository versions, DeploymentAccessService access,
        CustomerSelfServiceOperationBoundary offers, LifecycleEffects effects, LifecycleGatewayProperties properties,
        DeploymentOperationsClient client, PlatformAuditService audit) {
        this.store = store; this.deployments = deployments; this.releases = releases; this.versions = versions;
        this.access = access; this.offers = offers; this.effects = effects; this.properties = properties;
        this.client = client; this.audit = audit;
    }

    public Map<String, Object> create(String deploymentId, String targetId, CreateJob request) {
        if (!properties.isEnabled()) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "LIFECYCLE_GATEWAY_DISABLED");
        var deployment = deployments.findById(deploymentId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        access.requireDeploymentAdminAccess(deployment);
        if (!request.operationId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))
            throw new ResponseStatusException(BAD_REQUEST, "LIFECYCLE_OPERATION_ID_INVALID");
        offers.require(deployment, targetId, request.releaseId(),
            request.goal() == Goal.RECOVER || request.goal() == Goal.RECONCILE ? Operation.VERIFY : Operation.TARGET_RETIRE,
            request.operationId(), Map.of(), null, null, null);
        var principal = PlatformSecurityContext.currentPrincipal();
        if (principal == null) throw new ResponseStatusException(FORBIDDEN);
        String digest = store.digest(java.util.List.of(deploymentId, targetId, request));
        var existing = store.byOperation(request.operationId());
        LifecycleCommandStore.Grant grant;
        if (existing != null) {
            if (!deploymentId.equals(existing.scope().job().deploymentId())
                || !targetId.equals(existing.scope().job().targetProfileId())) throw new ResponseStatusException(CONFLICT, "LIFECYCLE_OPERATION_CONFLICT");
            grant = store.create(existing.scope(), digest, principal.actorId());
        } else {
            validateDates(request);
            if (request.goal() == Goal.RETIRE && deployment.isApprovalRequiredForDelete())
                throw new ResponseStatusException(CONFLICT, "EXPLICIT_RETIREMENT_APPROVAL_REQUIRED");
            if ((request.goal() == Goal.RECOVER || request.goal() == Goal.RECONCILE) && request.mode() != Mode.COLD)
                throw new ResponseStatusException(BAD_REQUEST, "RECOVERY_MODE_MUST_BE_COLD");
            if (request.reclaimOwnedData() && request.goal() != Goal.RETIRE)
                throw new ResponseStatusException(BAD_REQUEST, "DATA_RECLAMATION_REQUIRES_RETIREMENT");
            var release = releases.findById(request.releaseId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            if (!deploymentId.equals(release.getDeploymentId()) || !targetId.equals(release.getTargetProfileId()))
                throw new ResponseStatusException(CONFLICT, "LIFECYCLE_RELEASE_SCOPE_MISMATCH");
            var latest = releases.findByDeploymentIdAndTargetProfileIdOrderByCreatedAtDesc(deploymentId, targetId);
            if (latest.isEmpty() || !request.releaseId().equals(latest.getFirst().getId()))
                throw new ResponseStatusException(CONFLICT, "LIFECYCLE_RELEASE_SUPERSEDED");
            if (!Set.of("APPLIED_VERIFIED", "APPLIED_VERIFICATION_FAILED", "STOPPED").contains(release.getStatus()))
                throw new ResponseStatusException(CONFLICT, "LIFECYCLE_KNOWN_PROVISIONED_RELEASE_REQUIRED");
            var version = versions.findById(release.getDeploymentVersionId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
            if (!deploymentId.equals(version.getDeploymentId()) || version.getConfigHash() == null)
                throw new ResponseStatusException(CONFLICT, "LIFECYCLE_VERSION_SCOPE_INVALID");
            var snapshot = effects.freeze(deploymentId, targetId, release.getId());
            String scopeHash = store.digest(java.util.Arrays.asList(deploymentId, targetId, release.getId(),
                version.getId(), version.getConfigHash(), release.getRuntimeAuthorityFingerprint(), snapshot, request));
            var job = new JobRequest("1", request.operationId(), "lcg-" + UUID.randomUUID(),
                deployment.getCustomerId() == null ? "platform" : deployment.getCustomerId(), deployment.getTenantId(),
                deploymentId, targetId, release.getId(), scopeHash, request.goal(), request.mode(), request.policyRevision(),
                request.dueAt(), request.deadlineAt(), Instant.now());
            var scope = new LifecycleCommandStore.Scope(job, version.getId(), version.getConfigHash(),
                release.getRuntimeAuthorityFingerprint(), snapshot, request.reclaimOwnedData());
            grant = store.create(scope, digest, principal.actorId());
            audit.record("DEPLOYMENT_LIFECYCLE_AUTHORIZED", "DEPLOYMENT", deploymentId,
                Map.of("operationId", request.operationId(), "grantId", grant.id(), "releaseId", release.getId(),
                    "targetProfileId", targetId, "goal", request.goal().name(), "mode", request.mode().name(),
                    "reclaimOwnedData", request.reclaimOwnedData()));
        }
        JsonNode job;
        try { job = client.lifecycle("jobs", grant.scope().job(), false); }
        catch (RuntimeException unavailable) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "OPERATIONS_SERVICE_UNAVAILABLE_GRANT_RETAINED");
        }
        return Map.of("authorization", grant.scope().job(), "job", job);
    }

    public JsonNode job(String operationId, String command) {
        var grant = store.byOperation(operationId);
        if (grant == null) throw new ResponseStatusException(NOT_FOUND, "LIFECYCLE_OPERATION_NOT_FOUND");
        var deployment = deployments.findById(grant.scope().job().deploymentId()).orElseThrow(() -> new ResponseStatusException(NOT_FOUND));
        if (command == null || "events".equals(command)) access.requireDeploymentAccess(deployment);
        else access.requireDeploymentAdminAccess(deployment);
        if (command != null && !Set.of("advance", "cancel", "retry", "events").contains(command))
            throw new ResponseStatusException(BAD_REQUEST);
        try { return client.lifecycle("jobs/" + operationId + (command == null ? "" : "/" + command), Map.of(),
            command == null || "events".equals(command)); }
        catch (RuntimeException unavailable) { throw new ResponseStatusException(SERVICE_UNAVAILABLE, "OPERATIONS_SERVICE_UNAVAILABLE"); }
    }

    private static void validateDates(CreateJob request) {
        Instant now = Instant.now();
        if (request.dueAt().isBefore(now.minusSeconds(300)) || request.dueAt().isAfter(now.plus(Duration.ofDays(30)))
            || !request.deadlineAt().isAfter(request.dueAt()) || !request.deadlineAt().isAfter(now)
            || request.deadlineAt().isAfter(request.dueAt().plus(Duration.ofDays(7))))
            throw new ResponseStatusException(BAD_REQUEST, "LIFECYCLE_DATES_INVALID");
    }
}
