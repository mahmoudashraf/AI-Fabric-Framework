package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentReleaseRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.config.MarketplaceDatasetSyncProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Service
@ConditionalOnProperty(prefix = "platform.marketplace.dataset-sync", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MarketplaceDatasetScheduledSyncService {

    private static final Logger log = LoggerFactory.getLogger(MarketplaceDatasetScheduledSyncService.class);

    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final DeploymentReleaseRepository deploymentReleaseRepository;
    private final MarketplaceDatasetSyncService marketplaceDatasetSyncService;
    private final MarketplaceDatasetSyncProperties properties;

    public MarketplaceDatasetScheduledSyncService(DeploymentRepository deploymentRepository,
                                                  DeploymentVersionRepository deploymentVersionRepository,
                                                  DeploymentReleaseRepository deploymentReleaseRepository,
                                                  MarketplaceDatasetSyncService marketplaceDatasetSyncService,
                                                  MarketplaceDatasetSyncProperties properties) {
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.deploymentReleaseRepository = deploymentReleaseRepository;
        this.marketplaceDatasetSyncService = marketplaceDatasetSyncService;
        this.properties = properties;
    }

    @Scheduled(
        initialDelayString = "${platform.marketplace.dataset-sync.initial-delay-ms:300000}",
        fixedDelayString = "${platform.marketplace.dataset-sync.fixed-delay-ms:900000}"
    )
    public void syncActiveDeploymentDatasets() {
        for (DeploymentEntity deployment : deploymentRepository.findByArchivedAtIsNullOrderByCreatedAtDesc()) {
            syncDeploymentDatasets(deployment);
        }
    }

    void syncDeploymentDatasets(DeploymentEntity deployment) {
        if (deployment == null || !StringUtils.hasText(deployment.getActiveVersionId())) {
            return;
        }
        Optional<DeploymentVersionEntity> version = deploymentVersionRepository.findById(deployment.getActiveVersionId());
        if (version.isEmpty()) {
            return;
        }
        Optional<DeploymentReleaseEntity> release = deploymentReleaseRepository
            .findTopByDeploymentIdAndDeploymentVersionIdOrderByCreatedAtDesc(deployment.getId(), version.get().getId());
        if (release.isEmpty() || !"APPLIED_VERIFIED".equalsIgnoreCase(release.get().getStatus())) {
            return;
        }
        try {
            MarketplaceDatasetSyncService.DatasetSyncSummary summary = marketplaceDatasetSyncService.syncScheduledDatasets(
                deployment,
                version.get(),
                release.get(),
                properties.getMinimumInterval()
            );
            if (summary.datasetsCount() > 0) {
                log.info(
                    "Scheduled marketplace dataset sync completed: deploymentId={}, datasets={}, synced={}, skipped={}",
                    deployment.getId(),
                    summary.datasetsCount(),
                    summary.syncedDatasets(),
                    summary.skippedDatasets()
                );
            }
        } catch (RuntimeException ex) {
            log.error(
                "Scheduled marketplace dataset sync failed: deploymentId={}, activeVersionId={}, error={}",
                deployment.getId(),
                deployment.getActiveVersionId(),
                ex.getMessage(),
                ex
            );
        }
    }
}
