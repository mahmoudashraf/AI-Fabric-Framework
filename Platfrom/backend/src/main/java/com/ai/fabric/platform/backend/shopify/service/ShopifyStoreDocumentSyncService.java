package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentVersionEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentVersionRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetRuntimeSyncClient;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetSyncService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreDocumentEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncDocument;
import com.ai.fabric.platform.backend.shopify.model.SyncShopifyStoreDocumentsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ShopifyStoreDocumentSyncService {

    private static final int MAX_DOCUMENTS_PER_SYNC = 5000;
    private static final String LEGACY_HANDLE_PREFIX = "shopify-store:";
    private static final String LEGACY_DATASET_PREFIX = "shopify-storefront:";
    private static final String SHOPIFY_CATALOG_DATASET_ID = "shopify-catalog";
    private static final String SHOPIFY_POLICIES_DATASET_ID = "shopify-policies";

    private final ShopifyStoreConnectionRepository storeRepository;
    private final ShopifyStoreDocumentRepository documentRepository;
    private final DeploymentRepository deploymentRepository;
    private final DeploymentVersionRepository deploymentVersionRepository;
    private final MarketplaceDatasetRuntimeSyncClient runtimeSyncClient;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreDocumentSyncService(ShopifyStoreConnectionRepository storeRepository,
                                           ShopifyStoreDocumentRepository documentRepository,
                                           DeploymentRepository deploymentRepository,
                                           DeploymentVersionRepository deploymentVersionRepository,
                                           MarketplaceDatasetRuntimeSyncClient runtimeSyncClient,
                                           ShopifyStoreConnectionService shopifyStoreConnectionService,
                                           ShopifyStoreSourcePreflightSupport support,
                                           PlatformAuditService platformAuditService) {
        this.storeRepository = storeRepository;
        this.documentRepository = documentRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentVersionRepository = deploymentVersionRepository;
        this.runtimeSyncClient = runtimeSyncClient;
        this.shopifyStoreConnectionService = shopifyStoreConnectionService;
        this.support = support;
        this.platformAuditService = platformAuditService;
    }

    @Transactional
    public ShopifyStoreConnectionSummary sync(String shopDomain, SyncShopifyStoreDocumentsRequest request) {
        ShopifyStoreConnectionEntity store = storeRepository.findByShopDomainIgnoreCase(normalizeShopDomain(shopDomain))
            .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Shopify store connection not found: " + shopDomain));
        if (request == null) {
            throw new ResponseStatusException(CONFLICT, "Shopify store sync request is required.");
        }
        List<ShopifyStoreSyncDocument> incoming = request.documents() == null ? List.of() : request.documents();
        if (incoming.size() > MAX_DOCUMENTS_PER_SYNC) {
            throw new ResponseStatusException(CONFLICT, "Shopify store sync exceeds the maximum supported document count.");
        }

        DeploymentEntity deployment = deploymentRepository.findById(store.getDeploymentId())
            .orElseThrow(() -> new ResponseStatusException(CONFLICT, "Shopify store sync requires a bootstrapped deployment."));
        if (!StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
            throw new ResponseStatusException(CONFLICT, "Shopify store sync requires an active deployment runtime URL.");
        }

        List<ShopifyStoreSyncDocument> documents = normalizeDocuments(incoming);
        String legacyDatasetId = LEGACY_DATASET_PREFIX + store.getShopDomain();
        String legacyHandleRef = LEGACY_HANDLE_PREFIX + store.getShopDomain();
        String documentHash = hashDocuments(documents);
        Map<String, ResolvedDatasetTarget> datasetTargets = resolveDatasetTargets(deployment, legacyDatasetId, legacyHandleRef, documentHash);

        Map<String, ShopifyStoreDocumentEntity> existing = documentRepository.findByStoreConnectionIdOrderByDocumentIdAsc(store.getId())
            .stream()
            .collect(Collectors.toMap(
                ShopifyStoreDocumentEntity::getDocumentId,
                entity -> entity,
                (left, right) -> right,
                LinkedHashMap::new
            ));

        try {
            syncEntityTypeGroups(deployment, datasetTargets, documents, existing.values());
            persistTrackedDocuments(store, documents, existing);
            updateSyncSuccess(store, request.mode(), documents.size(), documentHash);
        } catch (RuntimeException ex) {
            updateSyncFailure(store, request.mode(), ex);
            throw ex;
        }

        platformAuditService.record(
            "SHOPIFY_STORE_DOCUMENTS_SYNCED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", deployment.getId(),
                "documentCount", Integer.toString(documents.size()),
                "datasetHash", documentHash
            )
        );
        return shopifyStoreConnectionService.getConnection(store.getShopDomain());
    }

    @Transactional
    public DocumentCleanupResult cleanupTrackedDocuments(ShopifyStoreConnectionEntity store, String reason) {
        if (store == null || !hasText(store.getId())) {
            return new DocumentCleanupResult("NO_STORE", 0, "Shopify store cleanup skipped because no store connection was available.");
        }

        List<ShopifyStoreDocumentEntity> trackedDocuments = documentRepository.findByStoreConnectionIdOrderByDocumentIdAsc(store.getId());
        if (trackedDocuments.isEmpty()) {
            recordCleanupSyncStatus(store, "NO_DOCUMENTS", 0, normalizedCleanupMessage(reason, 0, false));
            return new DocumentCleanupResult("NO_DOCUMENTS", 0, "No synced Shopify documents remained to clear.");
        }

        DeploymentEntity deployment = hasText(store.getDeploymentId())
            ? deploymentRepository.findById(store.getDeploymentId()).orElse(null)
            : null;
        String legacyDatasetId = LEGACY_DATASET_PREFIX + store.getShopDomain();
        String legacyHandleRef = LEGACY_HANDLE_PREFIX + store.getShopDomain();
        String cleanupHash = DigestUtils.md5DigestAsHex((trimToNull(reason) == null ? "cleanup" : trimToNull(reason))
            .getBytes(StandardCharsets.UTF_8));

        try {
            if (deployment != null && StringUtils.hasText(deployment.getRuntimeBaseUrl())) {
                Map<String, ResolvedDatasetTarget> datasetTargets = resolveDatasetTargets(deployment, legacyDatasetId, legacyHandleRef, cleanupHash);
                Map<String, List<ShopifyStoreDocumentEntity>> existingByDataset = trackedDocuments.stream()
                    .collect(Collectors.groupingBy(
                        document -> datasetIdForDocument(document.getSourceCategory(), document.getEntityType()),
                        LinkedHashMap::new,
                        Collectors.toList()
                    ));
                for (Map.Entry<String, List<ShopifyStoreDocumentEntity>> entry : existingByDataset.entrySet()) {
                    ResolvedDatasetTarget target = datasetTargets.get(entry.getKey());
                    if (target == null) {
                        continue;
                    }
                    runtimeSyncClient.deleteDocuments(
                        deployment,
                        target.entityType(),
                        target.datasetId(),
                        target.handleRef(),
                        target.datasetHash(),
                        entry.getValue().stream()
                            .map(ShopifyStoreDocumentEntity::getDocumentId)
                            .toList()
                    );
                }
            }

            documentRepository.deleteByStoreConnectionIdAndDocumentIdIn(
                store.getId(),
                trackedDocuments.stream().map(ShopifyStoreDocumentEntity::getDocumentId).toList()
            );
            recordCleanupSyncStatus(store, "CLEARED", trackedDocuments.size(), normalizedCleanupMessage(reason, trackedDocuments.size(), deployment != null && StringUtils.hasText(deployment.getRuntimeBaseUrl())));
            platformAuditService.record(
                "SHOPIFY_STORE_DOCUMENTS_CLEARED",
                "SHOPIFY_STORE_CONNECTION",
                store.getShopDomain(),
                Map.of(
                    "shopDomain", store.getShopDomain(),
                    "deploymentId", blankToEmpty(store.getDeploymentId()),
                    "documentCount", Integer.toString(trackedDocuments.size()),
                    "reason", blankToEmpty(trimToNull(reason))
                )
            );
            return new DocumentCleanupResult("CLEARED", trackedDocuments.size(), "Cleared " + trackedDocuments.size() + " synced Shopify documents.");
        } catch (RuntimeException ex) {
            recordCleanupSyncStatus(store, "FAILED", trackedDocuments.size(), "Shopify cleanup could not remove synced documents: " + firstMessage(ex));
            platformAuditService.record(
                "SHOPIFY_STORE_DOCUMENT_CLEANUP_FAILED",
                "SHOPIFY_STORE_CONNECTION",
                store.getShopDomain(),
                Map.of(
                    "shopDomain", store.getShopDomain(),
                    "deploymentId", blankToEmpty(store.getDeploymentId()),
                    "documentCount", Integer.toString(trackedDocuments.size()),
                    "reason", blankToEmpty(trimToNull(reason)),
                    "message", blankToEmpty(firstMessage(ex))
                )
            );
            return new DocumentCleanupResult("FAILED", trackedDocuments.size(), firstMessage(ex));
        }
    }

    private void syncEntityTypeGroups(DeploymentEntity deployment,
                                      Map<String, ResolvedDatasetTarget> datasetTargets,
                                      List<ShopifyStoreSyncDocument> documents,
                                      Collection<ShopifyStoreDocumentEntity> existingDocuments) {
        Map<String, List<ShopifyStoreSyncDocument>> incomingByDataset = documents.stream()
            .collect(Collectors.groupingBy(
                document -> datasetIdForDocument(document.sourceCategory(), document.entityType()),
                LinkedHashMap::new,
                Collectors.toList()
            ));
        Map<String, List<ShopifyStoreDocumentEntity>> existingByDataset = existingDocuments.stream()
            .collect(Collectors.groupingBy(
                document -> datasetIdForDocument(document.getSourceCategory(), document.getEntityType()),
                LinkedHashMap::new,
                Collectors.toList()
            ));

        LinkedHashSet<String> datasetIds = new LinkedHashSet<>();
        datasetIds.addAll(existingByDataset.keySet());
        datasetIds.addAll(incomingByDataset.keySet());

        for (String datasetId : datasetIds) {
            ResolvedDatasetTarget target = datasetTargets.get(datasetId);
            if (target == null) {
                throw new ResponseStatusException(CONFLICT, "Shopify sync dataset target is not configured for datasetId: " + datasetId);
            }
            List<ShopifyStoreSyncDocument> incomingGroup = incomingByDataset.getOrDefault(datasetId, List.of());
            List<MarketplaceDatasetSyncService.DatasetDocument> upserts = incomingGroup.stream()
                .map(document -> new MarketplaceDatasetSyncService.DatasetDocument(
                    document.documentId(),
                    buildDocumentContent(document),
                    mergeMetadata(document.metadata(), Map.of(
                        "knowledgeSourceHandleRef", target.handleRef(),
                        "marketplaceDatasetId", target.datasetId(),
                        "marketplaceDatasetHash", target.datasetHash(),
                        "sourceCategory", document.sourceCategory(),
                        "shopifyDocumentTitle", document.title()
                    ))
                ))
                .toList();
            runtimeSyncClient.upsertDocuments(
                deployment,
                target.entityType(),
                target.datasetId(),
                target.handleRef(),
                target.datasetHash(),
                upserts
            );

            LinkedHashSet<String> incomingIds = incomingGroup.stream()
                .map(ShopifyStoreSyncDocument::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> staleIds = existingByDataset.getOrDefault(datasetId, List.of()).stream()
                .map(ShopifyStoreDocumentEntity::getDocumentId)
                .filter(existingId -> !incomingIds.contains(existingId))
                .toList();
            if (!staleIds.isEmpty()) {
                runtimeSyncClient.deleteDocuments(
                    deployment,
                    target.entityType(),
                    target.datasetId(),
                    target.handleRef(),
                    target.datasetHash(),
                    staleIds
                );
            }
        }
    }

    private void persistTrackedDocuments(ShopifyStoreConnectionEntity store,
                                         List<ShopifyStoreSyncDocument> documents,
                                         Map<String, ShopifyStoreDocumentEntity> existing) {
        Instant now = Instant.now();
        List<ShopifyStoreDocumentEntity> tracked = new ArrayList<>();
        LinkedHashSet<String> retainedIds = new LinkedHashSet<>();
        for (ShopifyStoreSyncDocument document : documents) {
            retainedIds.add(document.documentId());
            ShopifyStoreDocumentEntity entity = existing.get(document.documentId());
            if (entity == null) {
                entity = new ShopifyStoreDocumentEntity();
                entity.setId("shpd-" + UUID.randomUUID().toString().substring(0, 8));
                entity.setStoreConnectionId(store.getId());
                entity.setDocumentId(document.documentId());
                entity.setCreatedAt(now);
            }
            entity.setSourceCategory(document.sourceCategory());
            entity.setEntityType(document.entityType());
            entity.setContentFingerprint(DigestUtils.md5DigestAsHex(buildDocumentContent(document).getBytes(StandardCharsets.UTF_8)));
            entity.setMetadataJson(writeMetadata(document.metadata()));
            entity.setLastSyncedAt(now);
            entity.setUpdatedAt(now);
            tracked.add(entity);
        }
        if (!tracked.isEmpty()) {
            documentRepository.saveAll(tracked);
        }
        List<String> staleIds = existing.keySet().stream()
            .filter(documentId -> !retainedIds.contains(documentId))
            .toList();
        if (!staleIds.isEmpty()) {
            documentRepository.deleteByStoreConnectionIdAndDocumentIdIn(store.getId(), staleIds);
        }
    }

    private void updateSyncSuccess(ShopifyStoreConnectionEntity store,
                                   String mode,
                                   int documentCount,
                                   String datasetHash) {
        Instant now = Instant.now();
        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode sync = details.putObject("sync");
        sync.put("status", "SYNCED");
        sync.put("checkedAt", now.toString());
        sync.put("mode", normalizeMode(mode));
        sync.put("documentCount", documentCount);
        sync.put("datasetHash", datasetHash);
        sync.put("message", documentCount > 0
            ? "Shopify bridge synced " + documentCount + " documents into the deployment runtime."
            : "Shopify bridge synced zero documents for the current store selection.");
        store.setSyncStatus("SYNCED");
        store.setLastSyncAt(now);
        if ("BLOCKED".equalsIgnoreCase(store.getOnboardingStatus())) {
            store.setOnboardingStatus("PLATFORM_BOOTSTRAPPED");
        }
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        storeRepository.save(store);
    }

    private void updateSyncFailure(ShopifyStoreConnectionEntity store,
                                   String mode,
                                   RuntimeException ex) {
        Instant now = Instant.now();
        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode sync = details.putObject("sync");
        sync.put("status", "FAILED");
        sync.put("checkedAt", now.toString());
        sync.put("mode", normalizeMode(mode));
        sync.put("documentCount", 0);
        sync.put("message", firstMessage(ex));
        store.setSyncStatus("FAILED");
        store.setLastSyncAt(now);
        store.setOnboardingStatus("BLOCKED");
        store.setDetailsJson(support.writeJson(details));
        store.setUpdatedAt(now);
        storeRepository.save(store);
    }

    private void recordCleanupSyncStatus(ShopifyStoreConnectionEntity store,
                                         String status,
                                         int documentCount,
                                         String message) {
        Instant now = Instant.now();
        ObjectNode details = support.mutableDetails(store.getDetailsJson());
        ObjectNode sync = details.putObject("sync");
        sync.put("status", status);
        sync.put("checkedAt", now.toString());
        sync.put("mode", "UNINSTALL_CLEANUP");
        sync.put("documentCount", Math.max(documentCount, 0));
        sync.put("message", message);
        store.setDetailsJson(support.writeJson(details));
        store.setLastSyncAt(now);
        store.setUpdatedAt(now);
        storeRepository.save(store);
    }

    private List<ShopifyStoreSyncDocument> normalizeDocuments(List<ShopifyStoreSyncDocument> incoming) {
        LinkedHashMap<String, ShopifyStoreSyncDocument> byId = new LinkedHashMap<>();
        for (ShopifyStoreSyncDocument document : incoming) {
            if (document == null || !hasText(document.documentId()) || !hasText(document.sourceCategory())
                || !hasText(document.entityType()) || !hasText(document.content())) {
                throw new ResponseStatusException(CONFLICT, "Each Shopify sync document requires documentId, sourceCategory, entityType, and content.");
            }
            String documentId = document.documentId().trim();
            if (byId.containsKey(documentId)) {
                throw new ResponseStatusException(CONFLICT, "Shopify sync request contains duplicate documentId: " + documentId);
            }
            byId.put(documentId, new ShopifyStoreSyncDocument(
                documentId,
                document.sourceCategory().trim().toLowerCase(Locale.ROOT),
                document.entityType().trim().toLowerCase(Locale.ROOT),
                trimToNull(document.title()),
                document.content().trim(),
                document.metadata() == null ? Map.of() : document.metadata()
            ));
        }
        return List.copyOf(byId.values());
    }

    private String hashDocuments(List<ShopifyStoreSyncDocument> documents) {
        String payload = documents.stream()
            .map(document -> document.documentId() + "|" + document.entityType() + "|" + DigestUtils.md5DigestAsHex(buildDocumentContent(document).getBytes(StandardCharsets.UTF_8)))
            .sorted()
            .collect(Collectors.joining("\n"));
        return DigestUtils.md5DigestAsHex(payload.getBytes(StandardCharsets.UTF_8));
    }

    private String buildDocumentContent(ShopifyStoreSyncDocument document) {
        String title = trimToNull(document.title());
        if (!hasText(title)) {
            return document.content().trim();
        }
        return title + "\n\n" + document.content().trim();
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> left, Map<String, Object> right) {
        LinkedHashMap<String, Object> merged = new LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return merged;
    }

    private String writeMetadata(Map<String, Object> metadata) {
        ObjectNode root = support.mutableDetails(null);
        ArrayNode arrayNode = root.putArray("metadata");
        if (metadata != null) {
            metadata.forEach((key, value) -> {
                ObjectNode entry = arrayNode.addObject();
                entry.put("key", key);
                entry.put("value", value == null ? "" : value.toString());
            });
        }
        return support.writeJson(root);
    }

    private String normalizeShopDomain(String shopDomain) {
        if (!hasText(shopDomain)) {
            throw new ResponseStatusException(CONFLICT, "shopDomain is required.");
        }
        return shopDomain.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeMode(String mode) {
        return hasText(mode) ? mode.trim().toUpperCase(Locale.ROOT) : "FULL";
    }

    private Map<String, ResolvedDatasetTarget> resolveDatasetTargets(DeploymentEntity deployment,
                                                                     String legacyDatasetId,
                                                                     String legacyHandleRef,
                                                                     String legacyDatasetHash) {
        LinkedHashMap<String, ResolvedDatasetTarget> targets = new LinkedHashMap<>();
        DeploymentVersionEntity activeVersion = resolveActiveVersion(deployment);
        JsonNode root = activeVersion == null ? null : support.readJsonNode(activeVersion.getMarketplaceDatasetConfigJson());
        JsonNode datasets = root == null ? null : root.path("datasets");
        if (datasets != null && datasets.isArray()) {
            for (JsonNode dataset : datasets) {
                String datasetId = lowerTrimToNull(dataset.path("datasetId").asText(""));
                if (!SHOPIFY_CATALOG_DATASET_ID.equals(datasetId) && !SHOPIFY_POLICIES_DATASET_ID.equals(datasetId)) {
                    continue;
                }
                String handleRef = trimToNull(dataset.path("handleRef").asText(""));
                String datasetHash = trimToNull(dataset.path("datasetHash").asText(""));
                String entityType = trimToNull(dataset.path("entityType").asText(""));
                if (handleRef == null || datasetHash == null || entityType == null) {
                    continue;
                }
                targets.put(datasetId, new ResolvedDatasetTarget(datasetId, handleRef, datasetHash, entityType));
            }
        }
        targets.putIfAbsent(
            SHOPIFY_CATALOG_DATASET_ID,
            new ResolvedDatasetTarget(legacyDatasetId, legacyHandleRef, legacyDatasetHash, "product")
        );
        targets.putIfAbsent(
            SHOPIFY_POLICIES_DATASET_ID,
            new ResolvedDatasetTarget(legacyDatasetId, legacyHandleRef, legacyDatasetHash, "support-policy")
        );
        return Map.copyOf(targets);
    }

    private DeploymentVersionEntity resolveActiveVersion(DeploymentEntity deployment) {
        if (deployment == null) {
            return null;
        }
        if (hasText(deployment.getActiveVersionId())) {
            return deploymentVersionRepository.findById(deployment.getActiveVersionId()).orElse(null);
        }
        return deploymentVersionRepository.findByDeploymentIdOrderByPublishedAtDesc(deployment.getId()).stream()
            .findFirst()
            .orElse(null);
    }

    private String datasetIdForDocument(String sourceCategory, String entityType) {
        String normalizedCategory = lowerTrimToNull(sourceCategory);
        if ("products".equals(normalizedCategory) || "collections".equals(normalizedCategory)) {
            return SHOPIFY_CATALOG_DATASET_ID;
        }
        if ("policies".equals(normalizedCategory) || "pages".equals(normalizedCategory)) {
            return SHOPIFY_POLICIES_DATASET_ID;
        }
        String normalizedEntityType = lowerTrimToNull(entityType);
        return "product".equals(normalizedEntityType) ? SHOPIFY_CATALOG_DATASET_ID : SHOPIFY_POLICIES_DATASET_ID;
    }

    private String firstMessage(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException && hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason().trim();
        }
        return hasText(ex.getMessage()) ? ex.getMessage().trim() : "Shopify document sync failed.";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String normalizedCleanupMessage(String reason, int documentCount, boolean runtimeCleanupAttempted) {
        String baseReason = trimToNull(reason);
        if (documentCount <= 0) {
            return baseReason == null
                ? "No synced Shopify documents remained to clear."
                : baseReason + " No synced Shopify documents remained to clear.";
        }
        String action = runtimeCleanupAttempted
            ? "Removed " + documentCount + " synced Shopify documents from the runtime and local tracking."
            : "Removed " + documentCount + " locally tracked Shopify documents.";
        return baseReason == null ? action : baseReason + " " + action;
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String lowerTrimToNull(String value) {
        return hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : null;
    }

    private record ResolvedDatasetTarget(
        String datasetId,
        String handleRef,
        String datasetHash,
        String entityType
    ) {
    }

    public record DocumentCleanupResult(
        String status,
        int deletedDocumentCount,
        String message
    ) {
    }
}
