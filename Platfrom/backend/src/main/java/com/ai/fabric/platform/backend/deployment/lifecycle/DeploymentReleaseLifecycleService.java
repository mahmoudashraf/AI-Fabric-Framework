package com.ai.fabric.platform.backend.deployment.lifecycle;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.service.DeploymentReleaseExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/** Records interrupted commands; restart never redispatches provider effects or resumes a repair. */
@Service
public class DeploymentReleaseLifecycleService {
    private static final Logger log = LoggerFactory.getLogger(DeploymentReleaseLifecycleService.class);
    private final DeploymentReleaseRepository releases;
    private final DeploymentReleaseExecutionService execution;

    public DeploymentReleaseLifecycleService(DeploymentReleaseRepository releases, DeploymentReleaseExecutionService execution) {
        this.releases = releases;
        this.execution = execution;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedReleasesOnStartup() {
        for (var release : releases.findByStatusIn(DeploymentTargetMutationService.ACTIVE_RELEASE_STATUSES)) {
            try {
                execution.markInterrupted(release.getId(), release.getDeploymentId());
            } catch (RuntimeException exception) {
                log.error("Cannot record interrupted release {} for deployment {}", release.getId(), release.getDeploymentId(), exception);
            }
        }
    }
}
