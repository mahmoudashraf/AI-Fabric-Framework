package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginDatasetEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginVersionEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginDatasetRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Service
public class MarketplacePluginDatasetDefinitionService {

    private final MarketplacePluginDatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;

    public MarketplacePluginDatasetDefinitionService(MarketplacePluginDatasetRepository datasetRepository,
                                                     ObjectMapper objectMapper) {
        this.datasetRepository = datasetRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncPluginVersionDatasets(MarketplacePluginEntity plugin,
                                          MarketplacePluginVersionEntity version,
                                          MarketplaceManifestService.ParsedMarketplaceManifest parsed) {
        if (plugin == null || version == null || parsed == null || parsed.datasets().isEmpty()) {
            return;
        }
        Set<String> retainedDatasetIds = new LinkedHashSet<>();
        Instant now = Instant.now();
        for (MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset : parsed.datasets()) {
            retainedDatasetIds.add(dataset.datasetId());
            MarketplacePluginDatasetEntity entity = datasetRepository.findByPluginVersionIdAndDatasetId(version.getId(), dataset.datasetId())
                .orElseGet(MarketplacePluginDatasetDefinitionService::newEntity);
            entity.setPluginId(plugin.getId());
            entity.setPluginVersionId(version.getId());
            entity.setDatasetId(dataset.datasetId());
            entity.setEntityType(dataset.entityType());
            entity.setStorageScope(dataset.storageScope());
            entity.setSharingScope(dataset.sharingScope());
            entity.setIngestionMode(dataset.ingestionMode());
            entity.setUpdateStrategy(dataset.updateStrategy());
            entity.setVectorizationProfile(dataset.vectorizationProfile());
            entity.setHandleTemplate(dataset.handleTemplate());
            entity.setSeedDatasetRef(dataset.seedDatasetRef());
            entity.setConnectorType(dataset.connectorType());
            entity.setConnectorConfigJson(writeJson(dataset.syncConnector()));
            entity.setDatasetHash(hashDatasetDefinition(dataset));
            entity.setUpdatedAt(now);
            datasetRepository.save(entity);
        }
        datasetRepository.findByPluginVersionIdOrderByDatasetIdAsc(version.getId()).stream()
            .filter(existing -> !retainedDatasetIds.contains(existing.getDatasetId()))
            .forEach(datasetRepository::delete);
    }

    private static MarketplacePluginDatasetEntity newEntity() {
        MarketplacePluginDatasetEntity entity = new MarketplacePluginDatasetEntity();
        entity.setId("mpd-" + UUID.randomUUID().toString().substring(0, 8));
        entity.setCreatedAt(Instant.now());
        return entity;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize marketplace dataset definition.", ex);
        }
    }

    private String hashDatasetDefinition(MarketplaceManifestService.ParsedMarketplaceDatasetDefinition dataset) {
        try {
            String payload = objectMapper.writeValueAsString(dataset);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash marketplace dataset definition.", ex);
        }
    }
}
