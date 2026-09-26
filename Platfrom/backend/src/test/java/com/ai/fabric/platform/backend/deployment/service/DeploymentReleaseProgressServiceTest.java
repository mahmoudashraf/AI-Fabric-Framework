package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentReleaseProgressServiceTest {

    @Test
    void trackerHeartbeatKeepsLongRunningProvisioningReleaseFresh() {
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        when(releaseRepository.touchUpdatedAt(eq("rel-heartbeat"), any()))
            .thenReturn(1);

        DeploymentReleaseProgressService service = new DeploymentReleaseProgressService(
            releaseRepository,
            new ObjectMapper()
        );

        service.tracker("rel-heartbeat").heartbeat();

        verify(releaseRepository).touchUpdatedAt(
            eq("rel-heartbeat"),
            any(Instant.class)
        );
    }
}
