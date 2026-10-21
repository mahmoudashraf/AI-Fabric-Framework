package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.generation.DeploymentRuntimeGenerationService;
import com.ai.fabric.platform.backend.deployment.generation.RuntimeGenerationTransitionContracts.Readback;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentProvider;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseExecutionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;
import java.util.function.Consumer;
import static com.ai.fabric.platform.backend.deployment.admission.FailedApplyRetryContracts.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class FailedApplyRetryService {
    private final AdmissionStore store;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceOperationBoundary boundary;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final CoolifyDeploymentProvider provider;
    private final DeploymentReleaseExecutionService execution;
    private final DeploymentReleaseRepository releases;
    private final DeploymentRuntimeGenerationService generations;

    public FailedApplyRetryService(AdmissionStore store, CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceOperationBoundary boundary, DeploymentRepository deployments,
        DeploymentVersionRepository versions, CoolifyDeploymentProvider provider, DeploymentReleaseExecutionService execution,
        DeploymentReleaseRepository releases, DeploymentRuntimeGenerationService generations) {
        this.store = store; this.entitlements = entitlements; this.boundary = boundary;
        this.deployments = deployments; this.versions = versions; this.provider = provider; this.execution = execution;
        this.releases = Objects.requireNonNull(releases); this.generations = Objects.requireNonNull(generations);
    }

    public Plan plan(String offerId, String deploymentId, String reservationId, PlanRequest request) {
        if (request == null) throw new AdmissionFailure(BAD_REQUEST, "FAILED_APPLY_RETRY_COMMAND_REQUIRED");
        var actor = entitlements.requireCustomerActor();
        var reservation = store.require(reservationId);
        var command = new Command(reservation.fence(), request.releaseId(), request.operationKey(), request.reason(),
            request.acceptOwnedServiceRedeploy(), null);
        var authorize = authorization(actor, offerId, deploymentId, command.operationKey());
        var candidate = store.failedApplyRetryCandidate(actor.customerId(), deploymentId, reservationId, command, authorize);
        var readback = readback(candidate, command.releaseId());
        return store.planFailedApplyRetry(actor.customerId(), deploymentId, reservationId, command, candidate, readback, authorize);
    }

    public Summary retry(String offerId, String deploymentId, String reservationId, Command command) {
        var actor = entitlements.requireCustomerActor();
        if (command == null) throw new AdmissionFailure(BAD_REQUEST, "FAILED_APPLY_RETRY_COMMAND_REQUIRED");
        var authorize = authorization(actor, offerId, deploymentId, command.operationKey());
        var prior = store.recordedFailedApplyRetry(actor.customerId(), deploymentId, reservationId, command, actor.userId(), authorize);
        if (prior != null) return prior;
        var candidate = store.failedApplyRetryCandidate(actor.customerId(), deploymentId, reservationId, command, authorize);
        var nativeReadback = readback(candidate, command.releaseId());
        var submitted = store.submitFailedApplyRetry(actor.customerId(), deploymentId, reservationId, command,
            candidate, nativeReadback, actor.userId(), authorize);
        if (submitted.queued()) {
            execution.executeApply(deploymentId, store.context(candidate.reservation()).manifest().versionId(), command.releaseId());
        }
        return submitted.summary();
    }

    private NativeReadback readback(Candidate candidate, String releaseId) {
        var r = candidate.reservation();
        // Provider reads run outside the admission mutex; the local snapshot is rechecked atomically afterwards.
        var reapply = store.reapplyRetryScope(candidate);
        if (reapply != null) {
            var deployment = deployments.findById(r.deploymentId()).orElseThrow(this::denied);
            var release = releases.findById(releaseId).orElseThrow(this::denied);
            var nativeReadback = provider.reapplyBeforeRuntimeStopReadback(r.deploymentId(), r.targetProfileId(), releaseId,
                reapply.priorReleaseId(), reapply.priorReleaseCreatedAt(), candidate.releaseCreatedAt(), candidate.failedAt());
            Readback generation;
            try {
                generation = generations.requireActiveForReapply(deployment, release, reapply.priorReleaseId(), reapply.priorRuntimeBaseUrl());
            } catch (IllegalStateException unproven) {
                throw new ResponseStatusException(CONFLICT,
                    "FAILED_APPLY_RETRY_GENERATION_READBACK_UNPROVEN", unproven);
            }
            return store.reapplyRetryReadback(candidate, nativeReadback, generation);
        }
        return provider.failedApplyRetryReadback(r.deploymentId(), r.targetProfileId(), releaseId,
            candidate.releaseCreatedAt(), candidate.failedAt());
    }

    private Consumer<AdmissionContracts.Reservation> authorization(CustomerOfferEntitlementService.CustomerActor actor,
        String offerId, String deploymentId, String operationKey) {
        return reservation -> {
            if (!actor.equals(entitlements.requireCustomerActor())) throw denied();
            var deployment = deployments.findById(deploymentId).orElseThrow(this::denied);
            if (!actor.customerId().equals(deployment.getCustomerId()) || !Objects.equals(reservation.tenantId(), deployment.getTenantId())) throw denied();
            var approved = boundary.prepareAdmission(deployment, reservation.targetProfileId(), Operation.APPLY, operationKey);
            if (approved == null || !offerId.equals(approved.context().offerId()) || !actor.customerId().equals(approved.context().customerId())) throw denied();
            var manifest = store.context(reservation).manifest();
            var version = versions.findById(manifest.versionId()).orElseThrow(this::denied);
            if (!deploymentId.equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())
                || !manifest.versionConfigHash().equals(version.getConfigHash())) throw denied();
            boundary.requireVersion(approved, version);
        };
    }
    private AdmissionFailure denied() { return new AdmissionFailure(FORBIDDEN, "FAILED_APPLY_RETRY_NOT_AUTHORIZED"); }
}
