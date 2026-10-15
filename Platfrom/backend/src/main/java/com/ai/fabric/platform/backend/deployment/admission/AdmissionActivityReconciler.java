package com.ai.fabric.platform.backend.deployment.admission;

import com.ai.fabric.platform.backend.deployment.authority.DeploymentRuntimeAuthorityService;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseEndpointService;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.ai.fabric.platform.backend.deployment.admission.AdmissionContracts.*;

/** Private readback only, selected from a persisted allocation/release binding. */
@Service
public class AdmissionActivityReconciler {
    private final AdmissionStore store;
    private final DeploymentReleaseRepository releases;
    private final DeploymentReleaseEndpointService endpoints;
    private final DeploymentRuntimeAuthorityService authority;
    private final RuntimeAdmissionActivityClient client;

    public AdmissionActivityReconciler(AdmissionStore store, DeploymentReleaseRepository releases,
        DeploymentReleaseEndpointService endpoints, DeploymentRuntimeAuthorityService authority, RuntimeAdmissionActivityClient client) {
        this.store = store; this.releases = releases; this.endpoints = endpoints; this.authority = authority; this.client = client;
    }

    public Reservation reconcile(Reservation reservation) {
        String releaseId = store.currentReleaseId(reservation);
        if (releaseId == null || reservation.state() != State.ACTIVE) return reservation;
        ReleaseBinding binding = store.releaseBinding(releaseId);
        var release = releases.findById(releaseId).orElseThrow(() -> new IllegalStateException("ACTIVITY_RELEASE_MISSING"));
        if (!binding.deploymentId().equals(release.getDeploymentId()) || !binding.targetProfileId().equals(release.getTargetProfileId())
            || !binding.versionId().equals(release.getDeploymentVersionId())
            || !"APPLIED_VERIFIED".equals(release.getStatus()) || !"ACTIVE".equals(release.getProvisioningStatus())) {
            throw new IllegalStateException("ACTIVITY_RELEASE_NOT_CURRENT");
        }
        var headers = authority.issueTargetRetirementHeaders(binding.deploymentId(), binding.targetProfileId(), binding.releaseId(),
            release.getRuntimeAuthorityFingerprint(), "admission-activity-" + reservation.id(), List.of("runtime:admission-activity:read"));
        ActivityReadback readback = client.read(endpoints.runtimeBaseUrl(release), binding, headers);
        return store.acceptActivity(reservation.fence(), binding, readback);
    }
}
