package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentProviderResourceHandleRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationEntity;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementOperationRepository;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementParticipation;
import com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementStore;
import com.ai.fabric.platform.backend.tenant.repository.PlatformConsumerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Comparator;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;
import static com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.*;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;

/** Bridges an admission expiry claim to one exact persisted R3 operation. */
@Service
final class AdmissionRetirementHandoffService {
    private static final String PRESERVED_SOURCE_BINDING = "EXTERNAL_DOCUMENT_STORAGE_BINDING";
    private static final String ACTOR = "system:admission-lifecycle";
    private final AdmissionStore admissions;
    private final DeploymentProviderResourceHandleRepository handles;
    private final DeploymentReleaseRepository releases;
    private final PlatformConsumerRepository consumers;
    private final TargetRetirementOperationRepository operations;
    private final TargetRetirementStore retirements;
    private final TargetRetirementParticipation participation;
    private final AdmissionLifecycleProperties properties;
    private final Clock clock;

    @Autowired
    AdmissionRetirementHandoffService(AdmissionStore admissions,
                                      DeploymentProviderResourceHandleRepository handles,
                                      DeploymentReleaseRepository releases,
                                      PlatformConsumerRepository consumers,
                                      TargetRetirementOperationRepository operations,
                                      TargetRetirementStore retirements,
                                      TargetRetirementParticipation participation,
                                      AdmissionLifecycleProperties properties) {
        this(admissions, handles, releases, consumers, operations, retirements, participation, properties,
            Clock.systemUTC());
    }

    AdmissionRetirementHandoffService(AdmissionStore admissions,
                                      DeploymentProviderResourceHandleRepository handles,
                                      DeploymentReleaseRepository releases,
                                      PlatformConsumerRepository consumers,
                                      TargetRetirementOperationRepository operations,
                                      TargetRetirementStore retirements,
                                      TargetRetirementParticipation participation,
                                      AdmissionLifecycleProperties properties,
                                      Clock clock) {
        this.admissions = admissions;
        this.handles = handles;
        this.releases = releases;
        this.consumers = consumers;
        this.operations = operations;
        this.retirements = retirements;
        this.participation = participation;
        this.properties = properties;
        this.clock = clock;
    }

    Reservation handoff(Reservation reservation) {
        if (!properties.retirementWorkerEnabled()) throw failure("RETIREMENT_WORKER_DISABLED");
        if (reservation == null || reservation.expiryState() != ExpiryState.CLAIMED) {
            throw failure("EXPIRY_NOT_CLAIMED");
        }
        ExpiryWork work = admissions.expiryWork(reservation);
        String operationKey = operationKey(reservation);
        TargetRetirementOperationEntity operation = operations
            .findByDeploymentIdAndTargetProfileIdAndOperationKey(
                reservation.deploymentId(), reservation.targetProfileId(), operationKey)
            .orElseGet(() -> plan(reservation, work, operationKey));
        requireOwned(reservation, operation);

        String evidence = "target-retirement:" + operation.getId() + ":" + operation.getRequestFingerprint();
        if (operation.getState() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.PLANNED) {
            if (!ready(operation)) return reservation;
            Reservation handedOff = admissions.handoff(work, operation.getId(), evidence, ACTOR, clock.instant());
            var summary = retirements.execute(operation.getId(), new Command(operation.getRequestFingerprint()));
            operation = retirements.require(summary.id());
            requireAccepted(operation);
            return handedOff;
        }
        requireAccepted(operation);
        return admissions.handoff(work, operation.getId(), evidence, ACTOR, clock.instant());
    }

    com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.Summary reconcile(Reservation reservation) {
        if (reservation == null || reservation.expiryState() != ExpiryState.HANDED_OFF
            || reservation.retirementOperationId() == null) throw failure("RETIREMENT_HANDOFF_REQUIRED");
        TargetRetirementOperationEntity operation = operations.findById(reservation.retirementOperationId())
            .orElseThrow(() -> failure("RETIREMENT_OPERATION_MISSING"));
        requireOwned(reservation, operation);
        var summary = retirements.summary(operation);
        if (summary.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.PLANNED) {
            if (!ready(operation)) return retirements.summary(operation);
            summary = retirements.execute(operation.getId(), new Command(operation.getRequestFingerprint()));
        }
        boolean recoveryWindowHeld = "RECOVERY_WINDOW_NOT_ELAPSED".equals(summary.errorCode())
            && reservation.recoveryUntil() != null && clock.instant().isBefore(reservation.recoveryUntil());
        if ((summary.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.BLOCKED
                || summary.state() == com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RETRYABLE_FAILURE)
            && !recoveryWindowHeld
            && summary.inFlightEffect() == null
            && (summary.nextRetryAt() == null || !summary.nextRetryAt().isAfter(clock.instant()))) {
            summary = retirements.retry(summary.id(), new Command(summary.requestFingerprint()), null);
        }
        return summary;
    }

    private boolean ready(TargetRetirementOperationEntity operation) {
        var context = retirements.context(operation);
        var inspection = participation.inspect(context);
        retirements.evidence(context, inspection.evidence(), inspection.interlockEvidence());
        List<String> blockers = executionBlockers(inspection);
        retirements.preview(operation.getId(), blockers);
        return blockers.isEmpty();
    }

    private List<String> executionBlockers(TargetRetirementParticipation.Inspection inspection) {
        var runtime = inspection.evidence().get(ParticipantKind.RUNTIME_DRAIN);
        boolean activePreDrain = runtime != null
            && !runtime.complete() && !runtime.ingressBlocked() && !runtime.inventoryComplete()
            && runtime.conversations() == null && runtime.confirmations() == null
            && runtime.humanWork() == null && runtime.backgroundWork() == null
            && runtime.blockers().equals(List.of("DRAIN_NOT_STARTED"));
        if (!activePreDrain) return inspection.blockers();
        return inspection.blockers().stream().filter(blocker -> !List.of(
            "DRAIN_NOT_STARTED", "DRAIN_WORK_UNKNOWN", "PARTICIPANT_INVENTORY_UNPROVEN_RUNTIME_DRAIN"
        ).contains(blocker)).toList();
    }

    private void requireAccepted(TargetRetirementOperationEntity operation) {
        if (!List.of(
            com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.QUEUED,
            com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RUNNING,
            com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.WAITING,
            com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.BLOCKED,
            com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.RETRYABLE_FAILURE,
            com.ai.fabric.platform.backend.deployment.retirement.TargetRetirementContracts.State.COMPLETE
        ).contains(operation.getState())) {
            throw failure("RETIREMENT_HANDOFF_REJECTED");
        }
    }

    private TargetRetirementOperationEntity plan(Reservation reservation, ExpiryWork work, String operationKey) {
        var ownedHandles = handles.findByDeploymentIdAndTargetProfileIdOrderByUpdatedAtDesc(
            reservation.deploymentId(), reservation.targetProfileId()).stream()
            .filter(handle -> !PRESERVED_SOURCE_BINDING.equals(handle.getResourceKind())).toList();
        if (ownedHandles.isEmpty()) throw failure("RETIREMENT_RESOURCE_INVENTORY_EMPTY");
        if (ownedHandles.stream().anyMatch(handle -> handle.getReleaseId() == null || handle.getReleaseId().isBlank())) {
            throw failure("RETIREMENT_RELEASE_INVENTORY_INCOMPLETE");
        }
        List<String> releaseIds = ownedHandles.stream().map(handle -> handle.getReleaseId()).distinct().sorted().toList();
        for (String releaseId : releaseIds) {
            var release = releases.findById(releaseId).orElseThrow(() -> failure("RETIREMENT_RELEASE_INVENTORY_INCOMPLETE"));
            if (!reservation.deploymentId().equals(release.getDeploymentId())
                || !reservation.targetProfileId().equals(release.getTargetProfileId())) {
                throw failure("RETIREMENT_RELEASE_INVENTORY_CHANGED");
            }
        }
        var deploymentConsumers = consumers.findAll().stream()
            .filter(consumer -> reservation.deploymentId().equals(consumer.getBoundDeploymentId()))
            .toList();
        List<ConsumerExpectation> expectations = deploymentConsumers.stream()
            .map(consumer -> new ConsumerExpectation(consumer.getId(), consumer.getBindingRevision()))
            .sorted(Comparator.comparing(ConsumerExpectation::consumerEntityId))
            .toList();
        FleetPolicy policy = admissions.reviewed("POLICY", reservation.policyId(), FleetPolicy.class);
        PlanRequest request = new PlanRequest(operationKey, releaseIds, expectations, work.drainDeadline(),
            new Retention(RetentionDecision.RECLAIM, null, null, policy.restoreEvidenceReference()), work.reason());
        var planned = retirements.planForAdmissionExpiry(reservation.deploymentId(), reservation.targetProfileId(),
            reservation.id(), reservation.generation(), request);
        return retirements.require(planned.id());
    }

    private void requireOwned(Reservation reservation, TargetRetirementOperationEntity operation) {
        if (!reservation.customerId().equals(operation.getCustomerId())
            || !reservation.deploymentId().equals(operation.getDeploymentId())
            || !reservation.targetProfileId().equals(operation.getTargetProfileId())
            || !operationKey(reservation).equals(operation.getOperationKey())) {
            throw new AdmissionFailure(CONFLICT, "RETIREMENT_OPERATION_OWNERSHIP_CHANGED");
        }
    }

    private String operationKey(Reservation reservation) {
        return "admission-expiry:" + reservation.id() + ":" + reservation.generation();
    }

    private AdmissionFailure failure(String code) {
        return new AdmissionFailure(PRECONDITION_FAILED, code);
    }
}
