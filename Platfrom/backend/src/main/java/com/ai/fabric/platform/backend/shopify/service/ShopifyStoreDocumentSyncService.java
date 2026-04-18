package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetRuntimeSyncClient;
import com.ai.fabric.platform.backend.marketplace.service.MarketplaceDatasetSyncService;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreDocumentEntity;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreConnectionSummary;
import com.ai.fabric.platform.backend.shopify.model.ShopifyStoreSyncDocument;
import com.ai.fabric.platform.backend.shopify.model.SyncShopifyStoreDocumentsRequest;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreDocumentRepository;
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
    private static final String HANDLE_PREFIX = "shopify-store:";
    private static final String DATASET_PREFIX = "shopify-storefront:";

    private final ShopifyStoreConnectionRepository storeRepository;
    private final ShopifyStoreDocumentRepository documentRepository;
    private final DeploymentRepository deploymentRepository;
    private final MarketplaceDatasetRuntimeSyncClient runtimeSyncClient;
    private final ShopifyStoreConnectionService shopifyStoreConnectionService;
    private final ShopifyStoreSourcePreflightSupport support;
    private final PlatformAuditService platformAuditService;

    public ShopifyStoreDocumentSyncService(ShopifyStoreConnectionRepository storeRepository,
                                           ShopifyStoreDocumentRepository documentRepository,
                                           DeploymentRepository deploymentRepository,
                                           MarketplaceDatasetRuntimeSyncClient runtimeSyncClient,
                                           ShopifyStoreConnectionService shopifyStoreConnectionService,
                                           ShopifyStoreSourcePreflightSupport support,
                                           PlatformAuditService platformAuditService) {
        this.storeRepository = storeRepository;
        this.documentRepository = documentRepository;
        this.deploymentRepository = deploymentRepository;
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
        String datasetId = DATASET_PREFIX + store.getShopDomain();
        String handleRef = HANDLE_PREFIX + store.getShopDomain();
        String datasetHash = hashDocuments(documents);

        Map<String, ShopifyStoreDocumentEntity> existing = documentRepository.findByStoreConnectionIdOrderByDocumentIdAsc(store.getId())
            .stream()
            .collect(Collectors.toMap(
                ShopifyStoreDocumentEntity::getDocumentId,
                entity -> entity,
                (left, right) -> right,
                LinkedHashMap::new
            ));

        try {
            syncEntityTypeGroups(deployment, datasetId, handleRef, datasetHash, documents, existing.values());
            persistTrackedDocuments(store, datasetHash, documents, existing);
            updateSyncSuccess(store, request.mode(), documents.size(), datasetHash);
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
                "datasetHash", datasetHash
            )
        );
        return shopifyStoreConnectionService.getConnection(store.getShopDomain());
    }

    private void syncEntityTypeGroups(DeploymentEntity deployment,
                                      String datasetId,
                                      String handleRef,
                                      String datasetHash,
                                      List<ShopifyStoreSyncDocument> documents,
                                      Collection<ShopifyStoreDocumentEntity> existingDocuments) {
        Map<String, List<ShopifyStoreSyncDocument>> incomingByType = documents.stream()
            .collect(Collectors.groupingBy(document -> document.entityType().trim().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ShopifyStoreDocumentEntity>> existingByType = existingDocuments.stream()
            .collect(Collectors.groupingBy(document -> document.getEntityType().trim().toLowerCase(Locale.ROOT), LinkedHashMap::new, Collectors.toList()));

        LinkedHashSet<String> entityTypes = new LinkedHashSet<>();
        entityTypes.addAll(existingByType.keySet());
        entityTypes.addAll(incomingByType.keySet());

        for (String entityType : entityTypes) {
            List<ShopifyStoreSyncDocument> incomingGroup = incomingByType.getOrDefault(entityType, List.of());
            List<MarketplaceDatasetSyncService.DatasetDocument> upserts = incomingGroup.stream()
                .map(document -> new MarketplaceDatasetSyncService.DatasetDocument(
                    document.documentId(),
                    buildDocumentContent(document),
                    mergeMetadata(document.metadata(), Map.of(
                        "knowledgeSourceHandleRef", handleRef,
                        "marketplaceDatasetId", datasetId,
                        "marketplaceDatasetHash", datasetHash,
                        "sourceCategory", document.sourceCategory(),
                        "shopifyDocumentTitle", document.title()
                    ))
                ))
                .toList();
            runtimeSyncClient.upsertDocuments(deployment, entityType, datasetId, handleRef, datasetHash, upserts);

            LinkedHashSet<String> incomingIds = incomingGroup.stream()
                .map(ShopifyStoreSyncDocument::documentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
            List<String> staleIds = existingByType.getOrDefault(entityType, List.of()).stream()
                .map(ShopifyStoreDocumentEntity::getDocumentId)
                .filter(existingId -> !incomingIds.contains(existingId))
                .toList();
            if (!staleIds.isEmpty()) {
                runtimeSyncClient.deleteDocuments(deployment, entityType, datasetId, handleRef, datasetHash, staleIds);
            }
        }
    }

    private void persistTrackedDocuments(ShopifyStoreConnectionEntity store,
                                         String datasetHash,
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

    private String firstMessage(RuntimeException ex) {
        if (ex instanceof ResponseStatusException responseStatusException && hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason().trim();
        }
        return hasText(ex.getMessage()) ? ex.getMessage().trim() : "Shopify document sync failed.";
    }

    private String trimToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
