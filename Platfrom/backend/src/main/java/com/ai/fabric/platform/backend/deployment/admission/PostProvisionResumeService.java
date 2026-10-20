package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.model.CustomerOfferContracts.Operation;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerOfferEntitlementService;
import com.ai.fabric.platform.backend.customerselfservice.service.CustomerSelfServiceOperationBoundary;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.deployment.service.CoolifyDeploymentProvider;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseExecutionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
import java.util.function.Consumer;
import static com.ai.fabric.platform.backend.deployment.admission.PostProvisionResumeContracts.*;
import static org.springframework.http.HttpStatus.*;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@PreAuthorize("hasRole('CUSTOMER_ADMIN')")
public class PostProvisionResumeService {
    private final AdmissionStore store;
    private final CustomerOfferEntitlementService entitlements;
    private final CustomerSelfServiceOperationBoundary boundary;
    private final DeploymentRepository deployments;
    private final DeploymentVersionRepository versions;
    private final CoolifyDeploymentProvider provider;
    private final DeploymentReleaseExecutionService execution;
    public PostProvisionResumeService(AdmissionStore store, CustomerOfferEntitlementService entitlements,
        CustomerSelfServiceOperationBoundary boundary, DeploymentRepository deployments,
        DeploymentVersionRepository versions, CoolifyDeploymentProvider provider, DeploymentReleaseExecutionService execution) {
        this.store = store; this.entitlements = entitlements; this.boundary = boundary;
        this.deployments = deployments; this.versions = versions; this.provider = provider; this.execution = execution;
    }
    public Plan plan(String offerId, String deploymentId, String reservationId, PlanRequest request) {
        if (request == null) throw new AdmissionFailure(BAD_REQUEST, "POST_PROVISION_RESUME_COMMAND_REQUIRED");
        var actor = entitlements.requireCustomerActor(); var r = store.require(reservationId);
        var command = new Command(r.fence(), request.releaseId(), request.operationKey(), request.reason(),
            request.acceptRemainingDependencyProvisioning(), null);
        var authorize = authorization(actor, offerId, deploymentId, command.operationKey());
        var candidate = store.postProvisionResumeCandidate(actor.customerId(), deploymentId, reservationId, command, authorize);
        var readback = readback(candidate, command.releaseId());
        return store.planPostProvisionResume(actor.customerId(), deploymentId, reservationId, command, candidate, readback, authorize);
    }
    public Summary resume(String offerId, String deploymentId, String reservationId, Command command) {
        if (command == null) throw new AdmissionFailure(BAD_REQUEST, "POST_PROVISION_RESUME_COMMAND_REQUIRED");
        var actor = entitlements.requireCustomerActor(); var authorize = authorization(actor, offerId, deploymentId, command.operationKey());
        var prior = store.recordedPostProvisionResume(actor.customerId(), deploymentId, reservationId, command, actor.userId(), authorize);
        if (prior != null) return prior;
        var candidate = store.postProvisionResumeCandidate(actor.customerId(), deploymentId, reservationId, command, authorize);
        var nativeReadback = readback(candidate, command.releaseId());
        var queued = store.queuePostProvisionResume(actor.customerId(), deploymentId, reservationId, command,
            candidate, nativeReadback, actor.userId(), authorize);
        execution.executeApply(deploymentId, store.context(candidate.reservation()).manifest().versionId(), command.releaseId());
        return queued;
    }
    private FailedApplyRetryContracts.NativeReadback readback(Candidate candidate, String releaseId) {
        var r = candidate.reservation();
        return provider.postProvisionResumeReadback(r.deploymentId(), r.targetProfileId(), releaseId,
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
    private AdmissionFailure denied() { return new AdmissionFailure(FORBIDDEN, "POST_PROVISION_RESUME_NOT_AUTHORIZED"); }
}
