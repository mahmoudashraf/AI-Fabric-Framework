package com.ai.fabric.platform.backend.marketplace.service;

import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentReleaseEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.marketplace.entity.DeploymentMarketplacePluginInstallEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetDocumentEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetHandleEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplaceDatasetSyncRunEntity;
import com.ai.fabric.platform.backend.marketplace.entity.MarketplacePluginDatasetEntity;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetDocumentRepository;
import com.ai.fabric.platform.backend.marketplace.repository.DeploymentMarketplacePluginInstallRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetHandleRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplaceDatasetSyncRunRepository;
import com.ai.fabric.platform.backend.marketplace.repository.MarketplacePluginDatasetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MarketplaceDatasetSyncService {

    private static final String DATASET_CONTRACT_VERSION = "MARKETPLACE_DATASET_CONFIG_V1";
    private static final String STATUS_READY = "READY";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_SYNCING = "SYNCING";
    private static final String STATUS_SKIPPED = "SKIPPED";
    private static final String CONNECTION_REF_PLATFORM_MARKETPLACE_DEMO_SQL = "platform-marketplace-demo-sql";

    private final MarketplaceDatasetHandleRepository datasetHandleRepository;
    private final MarketplaceDatasetDocumentRepository datasetDocumentRepository;
    private final MarketplaceDatasetSyncRunRepository syncRunRepository;
    private final DeploymentMarketplacePluginInstallRepository installRepository;
    private final MarketplacePluginDatasetRepository pluginDatasetRepository;
    private final MarketplaceDatasetRuntimeSyncClient runtimeSyncClient;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ResourcePatternResolver resourcePatternResolver;

    public MarketplaceDatasetSyncService(MarketplaceDatasetHandleRepository datasetHandleRepository,
                                         MarketplaceDatasetDocumentRepository datasetDocumentRepository,
                                         MarketplaceDatasetSyncRunRepository syncRunRepository,
                                         DeploymentMarketplacePluginInstallRepository installRepository,
                                         MarketplacePluginDatasetRepository pluginDatasetRepository,
                                         MarketplaceDatasetRuntimeSyncClient runtimeSyncClient,
                                         ObjectMapper objectMapper,
                                         JdbcTemplate jdbcTemplate,
                                         ResourcePatternResolver resourcePatternResolver) {
        this.datasetHandleRepository = datasetHandleRepository;
        this.datasetDocumentRepository = datasetDocumentRepository;
        this.syncRunRepository = syncRunRepository;
        this.installRepository = installRepository;
        this.pluginDatasetRepository = pluginDatasetRepository;
        this.runtimeSyncClient = runtimeSyncClient;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.resourcePatternResolver = resourcePatternResolver;
    }

    @Transactional
    public DatasetSyncSummary syncReleaseDatasets(DeploymentEntity deployment,
                                                  DeploymentVersionEntity version,
                                                  DeploymentReleaseEntity release) {
        return syncDatasets(deployment, version, release, SyncTrigger.APPLY, Duration.ZERO);
    }

    @Transactional
    public DatasetSyncSummary syncScheduledDatasets(DeploymentEntity deployment,
                                                    DeploymentVersionEntity version,
                                                    DeploymentReleaseEntity release,
                                                    Duration minimumInterval) {
        return syncDatasets(
            deployment,
            version,
            release,
            SyncTrigger.SCHEDULED,
            minimumInterval == null ? Duration.ofHours(1) : minimumInterval
        );
    }

    private DatasetSyncSummary syncDatasets(DeploymentEntity deployment,
                                            DeploymentVersionEntity version,
                                            DeploymentReleaseEntity release,
                                            SyncTrigger trigger,
                                            Duration minimumInterval) {
        ObjectNode config = readObject(version.getMarketplaceDatasetConfigJson());
        ArrayNode datasets = config.path("datasets") instanceof ArrayNode array ? array : objectMapper.createArrayNode();
        if (datasets.isEmpty()) {
            return new DatasetSyncSummary(0, 0, 0, List.of());
        }

        Instant evaluationTime = Instant.now();
        int eligibleDatasets = 0;
        int synced = 0;
        int skipped = 0;
        List<String> syncedHandles = new ArrayList<>();
        for (JsonNode datasetNode : datasets) {
            if (!(datasetNode instanceof ObjectNode dataset)) {
                continue;
            }
            if (!shouldProcessDataset(deployment, dataset, trigger, minimumInterval, evaluationTime)) {
                continue;
            }
            eligibleDatasets += 1;
            String handleRef = text(dataset, "handleRef");
            String datasetId = text(dataset, "datasetId");
            String status;
            try {
                status = syncDataset(deployment, version, release, dataset, trigger);
            } catch (RuntimeException ex) {
                recordFailure(deployment, release, dataset, ex);
                throw ex;
            }
            if (STATUS_SKIPPED.equals(status)) {
                skipped += 1;
            } else {
                synced += 1;
            }
            if (StringUtils.hasText(handleRef)) {
                syncedHandles.add(handleRef);
            } else if (StringUtils.hasText(datasetId)) {
                syncedHandles.add(datasetId);
            }
        }
        return new DatasetSyncSummary(eligibleDatasets, synced, skipped, List.copyOf(syncedHandles));
    }

    private String syncDataset(DeploymentEntity deployment,
                               DeploymentVersionEntity version,
                               DeploymentReleaseEntity release,
                               ObjectNode dataset,
                               SyncTrigger trigger) {
        String installId = requiredText(dataset, "marketplaceInstallId");
        String pluginId = requiredText(dataset, "marketplacePluginId");
        String datasetId = requiredText(dataset, "datasetId");
        String entityType = requiredText(dataset, "entityType");
        String handleRef = requiredText(dataset, "handleRef");
        String datasetHash = requiredText(dataset, "datasetHash");
        String ingestionMode = requiredText(dataset, "ingestionMode");

        DeploymentMarketplacePluginInstallEntity install = installRepository.findById(installId)
            .orElseThrow(() -> new IllegalStateException("Marketplace dataset references unknown install: " + installId));
        MarketplacePluginDatasetEntity pluginDataset = pluginDatasetRepository.findByPluginVersionIdAndDatasetId(install.getPluginVersionId(), datasetId)
            .orElseThrow(() -> new IllegalStateException("Marketplace dataset definition missing for install " + installId + " dataset " + datasetId));

        MarketplaceDatasetHandleEntity handle = datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId(pluginId, deployment.getTenantId(), datasetId)
            .orElseGet(() -> newHandle(deployment, install, pluginDataset));
        handle.setPluginVersionId(install.getPluginVersionId());
        handle.setDeploymentId(deployment.getId());
        handle.setCustomerId(deployment.getCustomerId());
        handle.setTenantId(deployment.getTenantId());
        handle.setStorageScope(requiredText(dataset, "storageScope"));
        handle.setSharingScope(requiredText(dataset, "sharingScope"));
        handle.setHandleRef(handleRef);
        handle.setEntityType(entityType);
        handle.setUpdatedAt(Instant.now());
        boolean unchangedReadyHandle = datasetHash.equals(handle.getDatasetHash())
            && STATUS_READY.equalsIgnoreCase(handle.getStatus())
            && handle.getLastSyncAt() != null;
        boolean allowHashSkip = SyncTrigger.APPLY.equals(trigger) || !isExternalSyncIngestionMode(ingestionMode);
        if (unchangedReadyHandle && allowHashSkip) {
            MarketplaceDatasetSyncRunEntity skippedRun = newRun(handle, deployment, release, dataset, "SKIPPED");
            skippedRun.setDocumentCount(0);
            skippedRun.setStartedAt(Instant.now());
            skippedRun.setCompletedAt(Instant.now());
            skippedRun.setDetailsJson(writeJson(Map.of(
                "reason", "dataset hash already synced",
                "trigger", trigger.name()
            )));
            syncRunRepository.save(skippedRun);
            datasetHandleRepository.save(handle);
            return STATUS_SKIPPED;
        }

        handle.setDatasetHash(datasetHash);
        handle.setStatus(STATUS_SYNCING);
        handle.setLastError(null);
        datasetHandleRepository.save(handle);

        MarketplaceDatasetSyncRunEntity run = newRun(handle, deployment, release, dataset, "RUNNING");
        run.setStartedAt(Instant.now());
        syncRunRepository.save(run);

        List<DatasetDocument> documents = loadDocuments(dataset);
        Map<String, MarketplaceDatasetDocumentEntity> existingDocuments = datasetDocumentRepository
            .findByDatasetHandleIdOrderByDocumentIdAsc(handle.getId())
            .stream()
            .filter(document -> StringUtils.hasText(document.getDocumentId()))
            .collect(Collectors.toMap(
                MarketplaceDatasetDocumentEntity::getDocumentId,
                document -> document,
                (left, right) -> right,
                LinkedHashMap::new
            ));
        int syncedDocuments = runtimeSyncClient.upsertDocuments(
            deployment,
            entityType,
            datasetId,
            handleRef,
            datasetHash,
            documents
        );
        List<String> staleDocumentIds = existingDocuments.keySet().stream()
            .filter(existingId -> documents.stream().noneMatch(document -> existingId.equals(document.id())))
            .toList();
        int deletedDocuments = 0;
        if (!staleDocumentIds.isEmpty()) {
            deletedDocuments = runtimeSyncClient.deleteDocuments(
                deployment,
                entityType,
                datasetId,
                handleRef,
                datasetHash,
                staleDocumentIds
            );
        }
        persistTrackedDocuments(handle, entityType, datasetHash, documents, staleDocumentIds);

        handle.setStatus(STATUS_READY);
        handle.setLastSyncAt(Instant.now());
        handle.setLastError(null);
        handle.setUpdatedAt(Instant.now());
        datasetHandleRepository.save(handle);

        run.setStatus("PASSED");
        run.setDocumentCount(syncedDocuments);
        run.setCompletedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        run.setDetailsJson(writeJson(Map.of(
            "handleRef", handleRef,
            "entityType", entityType,
            "documents", syncedDocuments,
            "deletedDocuments", deletedDocuments,
            "trigger", trigger.name()
        )));
        syncRunRepository.save(run);
        return STATUS_READY;
    }

    private void recordFailure(DeploymentEntity deployment,
                               DeploymentReleaseEntity release,
                               ObjectNode dataset,
                               RuntimeException ex) {
        String installId = text(dataset, "marketplaceInstallId");
        String datasetId = text(dataset, "datasetId");
        String pluginId = text(dataset, "marketplacePluginId");
        MarketplaceDatasetHandleEntity handle = datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId(
                pluginId,
                deployment.getTenantId(),
                datasetId
            )
            .orElseGet(() -> {
                MarketplaceDatasetHandleEntity created = new MarketplaceDatasetHandleEntity();
                created.setId("mdh-" + UUID.randomUUID().toString().substring(0, 8));
                created.setPluginId(pluginId);
                created.setPluginVersionId(resolvePluginVersionId(installId));
                created.setDatasetId(datasetId);
                created.setDeploymentId(deployment.getId());
                created.setCustomerId(deployment.getCustomerId());
                created.setTenantId(deployment.getTenantId());
                created.setStorageScope(text(dataset, "storageScope"));
                created.setSharingScope(text(dataset, "sharingScope"));
                created.setHandleRef(text(dataset, "handleRef"));
                created.setEntityType(text(dataset, "entityType"));
                created.setCreatedAt(Instant.now());
                return created;
            });
        handle.setStatus(STATUS_FAILED);
        handle.setDatasetHash(text(dataset, "datasetHash"));
        handle.setLastError(ex.getMessage());
        handle.setUpdatedAt(Instant.now());
        datasetHandleRepository.save(handle);

        MarketplaceDatasetSyncRunEntity run = newRun(handle, deployment, release, dataset, STATUS_FAILED);
        run.setDocumentCount(0);
        run.setStartedAt(Instant.now());
        run.setCompletedAt(Instant.now());
        run.setErrorMessage(ex.getMessage());
        run.setDetailsJson(writeJson(Map.of("datasetId", datasetId, "installId", installId)));
        syncRunRepository.save(run);
    }

    private MarketplaceDatasetHandleEntity newHandle(DeploymentEntity deployment,
                                                     DeploymentMarketplacePluginInstallEntity install,
                                                     MarketplacePluginDatasetEntity pluginDataset) {
        MarketplaceDatasetHandleEntity handle = new MarketplaceDatasetHandleEntity();
        handle.setId("mdh-" + UUID.randomUUID().toString().substring(0, 8));
        handle.setPluginId(pluginDataset.getPluginId());
        handle.setPluginVersionId(install.getPluginVersionId());
        handle.setDatasetId(pluginDataset.getDatasetId());
        handle.setDeploymentId(deployment.getId());
        handle.setCustomerId(deployment.getCustomerId());
        handle.setTenantId(deployment.getTenantId());
        handle.setStorageScope(pluginDataset.getStorageScope());
        handle.setSharingScope(pluginDataset.getSharingScope());
        handle.setEntityType(pluginDataset.getEntityType());
        handle.setStatus("PENDING");
        handle.setDatasetHash(pluginDataset.getDatasetHash());
        handle.setCreatedAt(Instant.now());
        handle.setUpdatedAt(Instant.now());
        return handle;
    }

    private MarketplaceDatasetSyncRunEntity newRun(MarketplaceDatasetHandleEntity handle,
                                                   DeploymentEntity deployment,
                                                   DeploymentReleaseEntity release,
                                                   ObjectNode dataset,
                                                   String status) {
        MarketplaceDatasetSyncRunEntity run = new MarketplaceDatasetSyncRunEntity();
        run.setId("mds-" + UUID.randomUUID().toString().substring(0, 8));
        run.setDatasetHandleId(handle.getId());
        run.setReleaseId(release.getId());
        run.setDeploymentId(deployment.getId());
        run.setSyncType(text(dataset, "ingestionMode"));
        run.setStatus(status);
        run.setDocumentCount(0);
        run.setCreatedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        return run;
    }

    private void persistTrackedDocuments(MarketplaceDatasetHandleEntity handle,
                                         String entityType,
                                         String datasetHash,
                                         List<DatasetDocument> documents,
                                         List<String> staleDocumentIds) {
        Instant now = Instant.now();
        Map<String, MarketplaceDatasetDocumentEntity> existingDocuments = datasetDocumentRepository
            .findByDatasetHandleIdOrderByDocumentIdAsc(handle.getId())
            .stream()
            .filter(document -> StringUtils.hasText(document.getDocumentId()))
            .collect(Collectors.toMap(
                MarketplaceDatasetDocumentEntity::getDocumentId,
                document -> document,
                (left, right) -> right,
                LinkedHashMap::new
            ));
        List<MarketplaceDatasetDocumentEntity> tracked = new ArrayList<>();
        for (DatasetDocument document : documents) {
            if (!StringUtils.hasText(document.id())) {
                continue;
            }
            MarketplaceDatasetDocumentEntity entity = existingDocuments.get(document.id());
            if (entity == null) {
                entity = new MarketplaceDatasetDocumentEntity();
                entity.setId("mdd-" + UUID.randomUUID().toString().substring(0, 8));
                entity.setDatasetHandleId(handle.getId());
                entity.setDocumentId(document.id());
                entity.setCreatedAt(now);
            }
            entity.setEntityType(entityType);
            entity.setContentFingerprint(document.contentFingerprint());
            entity.setDatasetHash(datasetHash);
            entity.setMetadataJson(writeJson(document.metadata() == null ? Map.of() : document.metadata()));
            entity.setLastSyncedAt(now);
            entity.setUpdatedAt(now);
            tracked.add(entity);
        }
        if (!tracked.isEmpty()) {
            datasetDocumentRepository.saveAll(tracked);
        }
        LinkedHashSet<String> staleIds = new LinkedHashSet<>();
        if (staleDocumentIds != null) {
            staleIds.addAll(staleDocumentIds);
        }
        if (!staleIds.isEmpty()) {
            datasetDocumentRepository.deleteByDatasetHandleIdAndDocumentIdIn(handle.getId(), staleIds);
        }
    }

    private List<DatasetDocument> loadDocuments(ObjectNode dataset) {
        String ingestionMode = requiredText(dataset, "ingestionMode");
        return switch (ingestionMode) {
            case "PACKAGED_SEED" -> loadPackagedSeed(dataset);
            case "EXTERNAL_SYNC_SQL" -> loadSqlDocuments(dataset);
            case "EXTERNAL_SYNC_FOLDER" -> loadFolderDocuments(dataset);
            default -> throw new IllegalStateException("Unsupported marketplace dataset ingestionMode: " + ingestionMode);
        };
    }

    private boolean shouldProcessDataset(DeploymentEntity deployment,
                                         ObjectNode dataset,
                                         SyncTrigger trigger,
                                         Duration minimumInterval,
                                         Instant evaluationTime) {
        if (SyncTrigger.APPLY.equals(trigger)) {
            return true;
        }
        String ingestionMode = requiredText(dataset, "ingestionMode");
        if (!isExternalSyncIngestionMode(ingestionMode)) {
            return false;
        }
        if (minimumInterval == null || minimumInterval.isZero() || minimumInterval.isNegative()) {
            return true;
        }
        Optional<MarketplaceDatasetHandleEntity> existingHandle = datasetHandleRepository.findByPluginIdAndTenantIdAndDatasetId(
            requiredText(dataset, "marketplacePluginId"),
            deployment.getTenantId(),
            requiredText(dataset, "datasetId")
        );
        if (existingHandle.isEmpty()) {
            return true;
        }
        MarketplaceDatasetHandleEntity handle = existingHandle.get();
        if (!STATUS_READY.equalsIgnoreCase(handle.getStatus()) || handle.getLastSyncAt() == null) {
            return true;
        }
        return !handle.getLastSyncAt().isAfter(evaluationTime.minus(minimumInterval));
    }

    private boolean isExternalSyncIngestionMode(String ingestionMode) {
        return "EXTERNAL_SYNC_SQL".equals(ingestionMode) || "EXTERNAL_SYNC_FOLDER".equals(ingestionMode);
    }

    private List<DatasetDocument> loadPackagedSeed(ObjectNode dataset) {
        String ref = requiredText(dataset, "seedDatasetRef");
        String resourcePath = ref.startsWith("classpath:") ? ref.substring("classpath:".length()) : ref;
        try {
            Resource resource = resourcePatternResolver.getResource("classpath:" + resourcePath);
            if (!resource.exists()) {
                throw new IllegalStateException("Missing packaged dataset resource: " + ref);
            }
            List<DatasetDocument> documents = new ArrayList<>();
            String scope = text(dataset.path("config"), "scope");
            String raw = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (String line : raw.split("\\R")) {
                if (!StringUtils.hasText(line)) {
                    continue;
                }
                JsonNode node = objectMapper.readTree(line);
                Map<String, Object> metadata = toMetadata(node.path("metadata"));
                if (StringUtils.hasText(scope)
                    && !"all".equalsIgnoreCase(scope)
                    && !scope.equalsIgnoreCase(String.valueOf(metadata.getOrDefault("scope", "")))) {
                    continue;
                }
                documents.add(new DatasetDocument(
                    requiredText(node, "id"),
                    requiredText(node, "content"),
                    metadata
                ));
            }
            return documents;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load packaged dataset " + ref + ": " + ex.getMessage(), ex);
        }
    }

    private List<DatasetDocument> loadSqlDocuments(ObjectNode dataset) {
        ObjectNode connector = readObject(dataset.path("syncConnector"));
        String connectionRef = requiredText(connector, "connectionRef");
        if (!CONNECTION_REF_PLATFORM_MARKETPLACE_DEMO_SQL.equals(connectionRef)) {
            throw new IllegalStateException("Unsupported marketplace SQL connectionRef: " + connectionRef);
        }
        String query = requiredText(connector, "query");
        String scope = text(dataset.path("config"), "scope");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(query);
        List<DatasetDocument> documents = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String rowScope = String.valueOf(row.getOrDefault("scope", ""));
            if (StringUtils.hasText(scope) && !"all".equalsIgnoreCase(scope) && !scope.equalsIgnoreCase(rowScope)) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("scope", rowScope);
            metadata.put("title", String.valueOf(row.getOrDefault("title", "")));
            metadata.putAll(readMetadataJson(row.get("metadata_json")));
            documents.add(new DatasetDocument(
                String.valueOf(row.get("id")),
                String.valueOf(row.get("content")),
                metadata
            ));
        }
        return documents;
    }

    private List<DatasetDocument> loadFolderDocuments(ObjectNode dataset) {
        ObjectNode connector = readObject(dataset.path("syncConnector"));
        String folderRef = requiredText(connector, "folderRef");
        try {
            Resource[] resources = resourcePatternResolver.getResources(folderRef);
            List<DatasetDocument> documents = new ArrayList<>();
            List<Resource> sortedResources = java.util.Arrays.stream(resources)
                .filter(Resource::exists)
                .sorted(Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
            for (Resource resource : sortedResources) {
                String filename = resource.getFilename();
                if (!StringUtils.hasText(filename)) {
                    continue;
                }
                String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (!StringUtils.hasText(content)) {
                    continue;
                }
                String id = filename.replaceAll("\\.[^.]+$", "");
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("fileName", filename);
                metadata.put("title", id.replace('-', ' '));
                metadata.put("scope", id);
                documents.add(new DatasetDocument(id, content, metadata));
            }
            return documents;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load marketplace folder dataset " + folderRef + ": " + ex.getMessage(), ex);
        }
    }

    private ObjectNode readObject(JsonNode node) {
        return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
    }

    private Map<String, Object> readMetadataJson(Object value) {
        if (!(value instanceof String text) || !StringUtils.hasText(text)) {
            return Map.of();
        }
        try {
            JsonNode node = objectMapper.readTree(text);
            return toMetadata(node);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private Map<String, Object> toMetadata(JsonNode node) {
        if (!(node instanceof ObjectNode objectNode)) {
            return Map.of();
        }
        return objectMapper.convertValue(objectNode, Map.class);
    }

    private String requiredText(JsonNode node, String fieldName) {
        String value = text(node, fieldName);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Marketplace dataset config is missing required field '" + fieldName + "'.");
        }
        return value;
    }

    private String text(JsonNode node, String fieldName) {
        return node == null ? null : (StringUtils.hasText(node.path(fieldName).asText("")) ? node.path(fieldName).asText("").trim() : null);
    }

    private String resolvePluginVersionId(String installId) {
        if (!StringUtils.hasText(installId)) {
            return null;
        }
        return installRepository.findById(installId).map(DeploymentMarketplacePluginInstallEntity::getPluginVersionId).orElse(null);
    }

    private ObjectNode readObject(String json) {
        try {
            if (!StringUtils.hasText(json)) {
                return objectMapper.createObjectNode();
            }
            JsonNode node = objectMapper.readTree(json);
            return node instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse marketplace dataset JSON.", ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? objectMapper.createObjectNode() : value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize marketplace dataset sync details.", ex);
        }
    }

    public record DatasetSyncSummary(
        int datasetsCount,
        int syncedDatasets,
        int skippedDatasets,
        List<String> handleRefs
    ) {
    }

    private enum SyncTrigger {
        APPLY,
        SCHEDULED
    }

    public record DatasetDocument(
        String id,
        String content,
        Map<String, Object> metadata
    ) {
        public String contentFingerprint() {
            return DigestUtils.md5DigestAsHex((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
        }
    }
}
