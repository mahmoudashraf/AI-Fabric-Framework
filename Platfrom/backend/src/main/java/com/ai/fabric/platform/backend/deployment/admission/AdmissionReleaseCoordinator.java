package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.customerselfservice.repository.CustomerOfferDeploymentRepository;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Binds customer authorization to a queued release before its worker can perform provider effects. */
@Service
public class AdmissionReleaseCoordinator {
    private static final Logger log = LoggerFactory.getLogger(AdmissionReleaseCoordinator.class);
    private static final String ACTOR = "system:admission-release";
    private final AdmissionStore store;
    private final CustomerOfferDeploymentRepository offers;
    private final DeploymentReleaseRepository releases;
    private final DeploymentVersionRepository versions;
    private final AdmissionEvidenceSource prerequisites;
    private final AdmissionResourceReadbackSource resources;
    private final java.time.Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public AdmissionReleaseCoordinator(AdmissionStore store, CustomerOfferDeploymentRepository offers,
        DeploymentReleaseRepository releases, DeploymentVersionRepository versions,
        AdmissionEvidenceSource prerequisites, AdmissionResourceReadbackSource resources) {
        this(store, offers, releases, versions, prerequisites, resources, java.time.Clock.systemUTC());
    }

    AdmissionReleaseCoordinator(AdmissionStore store, CustomerOfferDeploymentRepository offers,
        DeploymentReleaseRepository releases, DeploymentVersionRepository versions,
        AdmissionEvidenceSource prerequisites, AdmissionResourceReadbackSource resources, java.time.Clock clock) {
        this.store = store;
        this.offers = offers;
        this.releases = releases;
        this.versions = versions;
        this.prerequisites = prerequisites;
        this.resources = resources;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void bind(DeploymentEntity deployment, DeploymentVersionEntity version, DeploymentReleaseEntity release) {
        if (offers.existsById(deployment.getId())) store.bindRelease(deployment, version, release);
    }

    public void beginProvisioning(String releaseId) {
        ReleaseBinding binding = store.releaseBinding(releaseId);
        DeploymentReleaseEntity release = requireBoundRelease(releaseId, binding);
        if (binding == null) return;
        if (Set.of("NO_EFFECT", "SUPERSEDED").contains(binding.state())) throw failure("RELEASE_ALLOCATION_FENCED");
        Reservation reservation = store.require(binding.reservationId());
        requireGeneration(binding, reservation);
        AdmissionContext context = store.context(binding.customerId(), binding.tenantId(), binding.deploymentId(), reservation.manifestId());
        PrerequisiteReadback readback = prerequisites.read(context);
        store.beginReleaseProvisioning(binding, context, readback, ACTOR, clock.instant());
    }

    /** Called after the release transaction commits; a failed readback retains its full reservation. */
    public void observe(String releaseId) {
        ReleaseBinding binding = store.releaseBinding(releaseId);
        DeploymentReleaseEntity release = requireBoundRelease(releaseId, binding);
        if (binding == null) return;
        if (Set.of("NO_EFFECT", "SUPERSEDED").contains(binding.state())) return;
        Reservation reservation = store.require(binding.reservationId());
        requireGeneration(binding, reservation);
        if ("APPLIED_VERIFIED".equals(release.getStatus()) && "ACTIVE".equals(release.getProvisioningStatus())) {
            if (reservation.state() != State.ACTIVE || !"ACTIVE".equals(binding.state())) {
                FleetPolicy policy = store.reviewed("POLICY", reservation.policyId(), FleetPolicy.class);
                var readback = resources.read(new ReadbackContext(reservation, policy, reservation.workerToken()));
                store.reconcile(reservation.fence(), readback, false, ACTOR, clock.instant());
                store.releaseBindingState(releaseId, "ACTIVE");
            }
        } else if (Set.of("PRE_APPLY_BLOCKED", "FAILED").contains(release.getStatus()) && "QUEUED".equals(binding.state())) {
            store.releaseBindingState(releaseId, "NO_EFFECT");
        } else if (Set.of("FAILED", "APPLIED_VERIFICATION_FAILED", "PRE_APPLY_BLOCKED", "APPLIED_VERIFICATION_BLOCKED")
                .contains(release.getStatus()) || "AWAITING_CONFIRMATION".equals(release.getProvisioningStatus())) {
            if (reservation.state() != State.OUTCOME_UNKNOWN) {
                store.outcome(reservation.fence(), State.OUTCOME_UNKNOWN, "release-outcome:" + releaseId, ACTOR, clock.instant());
            }
            store.releaseBindingState(releaseId, "OUTCOME_UNKNOWN");
        }
    }

    @Scheduled(initialDelayString = "${platform.deployment.admission.release.initial-delay:30s}",
        fixedDelayString = "${platform.deployment.admission.release.poll-delay:30s}")
    void reconcile() {
        for (String releaseId : store.unsettledReleases(32)) {
            try { observe(releaseId); }
            catch (RuntimeException error) { log.warn("Admission release readback remains blocked for {}", releaseId); }
        }
    }

    private DeploymentReleaseEntity requireBoundRelease(String releaseId, ReleaseBinding binding) {
        DeploymentReleaseEntity release = releases.findById(releaseId).orElseThrow(() -> failure("RELEASE_MISSING"));
        if (binding == null) {
            if (offers.existsById(release.getDeploymentId())) throw failure("RELEASE_ALLOCATION_REQUIRED");
            return release;
        }
        if (!binding.deploymentId().equals(release.getDeploymentId()) || !binding.targetProfileId().equals(release.getTargetProfileId())
            || !binding.versionId().equals(release.getDeploymentVersionId())) throw failure("RELEASE_ALLOCATION_SCOPE_CHANGED");
        var version = versions.findById(binding.versionId()).orElseThrow(() -> failure("REVIEWED_VERSION_MISSING"));
        if (!binding.deploymentId().equals(version.getDeploymentId()) || !"PUBLISHED".equals(version.getStatus())
            || !binding.versionConfigHash().equals(version.getConfigHash())) throw failure("RELEASE_ALLOCATION_VERSION_CHANGED");
        return release;
    }

    private void requireGeneration(ReleaseBinding binding, Reservation reservation) {
        if (!binding.generation().equals(reservation.generation()) || !binding.customerId().equals(reservation.customerId())
            || !binding.tenantId().equals(reservation.tenantId()) || !binding.deploymentId().equals(reservation.deploymentId())
            || !binding.targetProfileId().equals(reservation.targetProfileId())
            || !binding.releaseId().equals(store.currentReleaseId(reservation))) {
            throw failure("RELEASE_ALLOCATION_GENERATION_CHANGED");
        }
    }

    private AdmissionFailure failure(String code) {
        return new AdmissionFailure(org.springframework.http.HttpStatus.PRECONDITION_FAILED, code);
    }
}
