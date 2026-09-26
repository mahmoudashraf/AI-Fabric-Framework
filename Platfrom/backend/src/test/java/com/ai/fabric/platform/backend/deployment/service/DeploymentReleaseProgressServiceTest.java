package com.ai.fabric.platform.backend.deployment.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeploymentReleaseProgressServiceTest {

    @Test
    void trackerHeartbeatKeepsLongRunningProvisioningReleaseFresh() {
        DeploymentReleaseRepository releaseRepository = mock(DeploymentReleaseRepository.class);
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-heartbeat");
        release.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        when(releaseRepository.findById(release.getId())).thenReturn(Optional.of(release));

        DeploymentReleaseProgressService service = new DeploymentReleaseProgressService(
            releaseRepository,
            new ObjectMapper()
        );

        service.tracker(release.getId()).heartbeat();

        assertThat(release.getUpdatedAt()).isAfter(Instant.parse("2026-01-01T00:00:00Z"));
        verify(releaseRepository).save(release);
    }
}
