package com.ai.fabric.platform.backend.deployment.admission;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Runs release readback only on control-plane instances explicitly assigned lifecycle work. */
@Service
@ConditionalOnProperty(
    name = "platform.deployment.admission.release.worker-enabled",
    havingValue = "true"
)
final class AdmissionReleaseReconciliationWorker {
    private final AdmissionReleaseCoordinator coordinator;

    AdmissionReleaseReconciliationWorker(AdmissionReleaseCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(initialDelayString = "${platform.deployment.admission.release.initial-delay:PT30S}",
        fixedDelayString = "${platform.deployment.admission.release.poll-delay:PT30S}")
    void reconcile() {
        coordinator.reconcile();
    }
}
