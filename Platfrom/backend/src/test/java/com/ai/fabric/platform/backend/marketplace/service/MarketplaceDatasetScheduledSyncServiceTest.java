package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.config.MarketplaceDatasetSyncProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceDatasetScheduledSyncServiceTest {

    @Test
    void syncDeploymentDatasetsRunsForAppliedVerifiedActiveDeployment() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        MarketplaceDatasetSyncProperties properties = new MarketplaceDatasetSyncProperties();
        properties.setMinimumInterval(Duration.ofHours(2));

        MarketplaceDatasetScheduledSyncService service = new MarketplaceDatasetScheduledSyncService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            marketplaceDatasetSyncService,
            properties
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-1");
        deployment.setActiveVersionId("ver-1");
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-1");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-1");
        release.setStatus("APPLIED_VERIFIED");

        when(deploymentVersionRepository.findById("ver-1")).thenReturn(Optional.of(version));
        when(deploymentReleaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-1", "ver-1"))
            .thenReturn(Optional.of(release));
        when(marketplaceDatasetSyncService.syncScheduledDatasets(eq(deployment), eq(version), eq(release), eq(Duration.ofHours(2))))
            .thenReturn(new MarketplaceDatasetSyncService.DatasetSyncSummary(1, 1, 0, java.util.List.of("plugin/mkp-data-help-center/tenant/ten-1/help-center-seed")));

        service.syncDeploymentDatasets(deployment);

        verify(marketplaceDatasetSyncService).syncScheduledDatasets(
            eq(deployment),
            eq(version),
            eq(release),
            eq(Duration.ofHours(2))
        );
    }

    @Test
    void syncDeploymentDatasetsSkipsNonVerifiedRelease() {
        DeploymentRepository deploymentRepository = mock(DeploymentRepository.class);
        DeploymentVersionRepository deploymentVersionRepository = mock(DeploymentVersionRepository.class);
        DeploymentReleaseRepository deploymentReleaseRepository = mock(DeploymentReleaseRepository.class);
        MarketplaceDatasetSyncService marketplaceDatasetSyncService = mock(MarketplaceDatasetSyncService.class);
        MarketplaceDatasetSyncProperties properties = new MarketplaceDatasetSyncProperties();

        MarketplaceDatasetScheduledSyncService service = new MarketplaceDatasetScheduledSyncService(
            deploymentRepository,
            deploymentVersionRepository,
            deploymentReleaseRepository,
            marketplaceDatasetSyncService,
            properties
        );

        DeploymentEntity deployment = new DeploymentEntity();
        deployment.setId("dep-2");
        deployment.setActiveVersionId("ver-2");
        DeploymentVersionEntity version = new DeploymentVersionEntity();
        version.setId("ver-2");
        DeploymentReleaseEntity release = new DeploymentReleaseEntity();
        release.setId("rel-2");
        release.setStatus("PROVISIONING");

        when(deploymentVersionRepository.findById("ver-2")).thenReturn(Optional.of(version));
        when(deploymentReleaseRepository.findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc("dep-2", "ver-2"))
            .thenReturn(Optional.of(release));

        service.syncDeploymentDatasets(deployment);

        verify(marketplaceDatasetSyncService, never()).syncScheduledDatasets(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
