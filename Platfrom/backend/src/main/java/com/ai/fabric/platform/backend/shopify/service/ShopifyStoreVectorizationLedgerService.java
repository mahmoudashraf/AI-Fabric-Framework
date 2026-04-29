package com.ai.fabric.platform.backend.shopify.service;

import com.ai.fabric.platform.backend.audit.service.PlatformAuditService;
import com.ai.fabric.platform.backend.deployment.entity.DeploymentEntity;
import com.ai.fabric.platform.backend.deployment.repository.DeploymentRepository;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreConnectionEntity;
import com.ai.fabric.platform.backend.shopify.entity.ShopifyStoreVectorizationLedgerEntity;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreConnectionRepository;
import com.ai.fabric.platform.backend.shopify.repository.ShopifyStoreVectorizationLedgerRepository;
import com.ai.fabric.platform.backend.vectorization.entity.VectorizationRunEntity;
import com.ai.fabric.platform.backend.vectorization.model.VectorizationOverviewSummary;
import com.ai.fabric.platform.backend.vectorization.service.VectorizationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShopifyStoreVectorizationLedgerService {

    private static final TypeReference<Map<String, String>> FIELD_FINGERPRINT_TYPE = new TypeReference<>() { };

    private final ShopifyStoreConnectionRepository storeRepository;
    private final DeploymentRepository deploymentRepository;
    private final ShopifyStoreVectorizationLedgerRepository ledgerRepository;
    private final ShopifyBridgeAdminClient bridgeAdminClient;
    private final ShopifyStoreVectorizationFingerprintService fingerprintService;
    private final VectorizationService vectorizationService;
    private final PlatformAuditService platformAuditService;
    private final ObjectMapper objectMapper;

    public ShopifyStoreVectorizationLedgerService(ShopifyStoreConnectionRepository storeRepository,
                                                  DeploymentRepository deploymentRepository,
                                                  ShopifyStoreVectorizationLedgerRepository ledgerRepository,
                                                  ShopifyBridgeAdminClient bridgeAdminClient,
                                                  ShopifyStoreVectorizationFingerprintService fingerprintService,
                                                  VectorizationService vectorizationService,
                                                  PlatformAuditService platformAuditService,
                                                  ObjectMapper objectMapper) {
        this.storeRepository = storeRepository;
        this.deploymentRepository = deploymentRepository;
        this.ledgerRepository = ledgerRepository;
        this.bridgeAdminClient = bridgeAdminClient;
        this.fingerprintService = fingerprintService;
        this.vectorizationService = vectorizationService;
        this.platformAuditService = platformAuditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void refreshForCompletedRun(VectorizationRunEntity run, List<String> entityScope) {
        if (run == null || entityScope == null || entityScope.isEmpty()) {
            return;
        }
        ShopifyStoreConnectionEntity store = storeRepository.findByDeploymentId(run.getDeploymentId()).orElse(null);
        if (store == null) {
            return;
        }
        DeploymentEntity deployment = deploymentRepository.findById(run.getDeploymentId()).orElse(null);
        if (deployment == null) {
            return;
        }
        VectorizationOverviewSummary overview = vectorizationService.getOverviewForTrustedCaller(deployment);
        String deploymentHash = overview == null || overview.plan() == null ? null : overview.plan().activeIndexedOutputHash();
        Instant now = Instant.now();

        for (String entityType : entityScope) {
            refreshEntityScope(store, run, entityType, overview, deploymentHash, now);
        }

        platformAuditService.record(
            "SHOPIFY_STORE_VECTORIZATION_LEDGER_REFRESHED",
            "SHOPIFY_STORE_CONNECTION",
            store.getShopDomain(),
            Map.of(
                "shopDomain", store.getShopDomain(),
                "deploymentId", run.getDeploymentId(),
                "runId", run.getId(),
                "entityScope", String.join(",", entityScope)
            )
        );
    }

    @Transactional(readOnly = true)
    public Map<String, String> readFieldFingerprints(ShopifyStoreVectorizationLedgerEntity entity) {
        if (entity == null || entity.getFieldFingerprintsJson() == null || entity.getFieldFingerprintsJson().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = objectMapper.readValue(entity.getFieldFingerprintsJson(), FIELD_FINGERPRINT_TYPE);
            return parsed == null ? Map.of() : Map.copyOf(parsed);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse Shopify vectorization ledger field fingerprints.", ex);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> aggregateStoredFingerprints(String deploymentId,
                                                           String entityType,
                                                           String sourceCategory) {
        List<ShopifyStoreVectorizationLedgerEntity> rows = ledgerRepository.findByDeploymentIdAndEntityTypeAndSourceCategory(
            deploymentId,
            entityType,
            sourceCategory
        ).stream()
            .filter(entity -> entity.getDeletedAt() == null)
            .sorted((left, right) -> left.getSourceObjectId().compareToIgnoreCase(right.getSourceObjectId()))
            .toList();
        Map<String, List<String>> combined = new LinkedHashMap<>();
        for (ShopifyStoreVectorizationLedgerEntity row : rows) {
            Map<String, String> fingerprints = readFieldFingerprints(row);
            fingerprints.forEach((fieldKey, fingerprint) -> combined.computeIfAbsent(fieldKey, ignored -> new ArrayList<>())
                .add(row.getSourceObjectId() + ":" + fingerprint));
        }
        return fingerprintService.fingerprintAggregatedValues(combined).fieldFingerprints();
    }

    private void refreshEntityScope(ShopifyStoreConnectionEntity store,
                                    VectorizationRunEntity run,
                                    String entityType,
                                    VectorizationOverviewSummary overview,
                                    String deploymentHash,
                                    Instant now) {
        Map<String, ShopifyStoreVectorizationSourceRecordSnapshot> seen = new HashMap<>();
        String cursor = null;
        do {
            ShopifyStoreVectorizationSourcePageSnapshot page = bridgeAdminClient.fetchPage(store, entityType, cursor, 250);
            for (ShopifyStoreVectorizationSourceRecordSnapshot item : page.items()) {
                seen.put(item.id(), item);
                upsertLedgerRow(store, run, item, overview, deploymentHash, now);
            }
            cursor = page.hasMore() ? page.nextCursor() : null;
        } while (cursor != null);

        List<ShopifyStoreVectorizationLedgerEntity> existingRows = ledgerRepository.findByDeploymentIdAndEntityType(run.getDeploymentId(), entityType);
        for (ShopifyStoreVectorizationLedgerEntity existing : existingRows) {
            if (!seen.containsKey(existing.getSourceObjectId())) {
                existing.setDeletedAt(now);
                existing.setLastSeenAt(now);
                existing.setUpdatedAt(now);
                ledgerRepository.save(existing);
            }
        }
    }

    private void upsertLedgerRow(ShopifyStoreConnectionEntity store,
                                 VectorizationRunEntity run,
                                 ShopifyStoreVectorizationSourceRecordSnapshot item,
                                 VectorizationOverviewSummary overview,
                                 String deploymentHash,
                                 Instant now) {
        ShopifyStoreVectorizationFingerprintService.FingerprintResult fingerprint = fingerprintService.fingerprintRecord(
            item.sourceCategory(),
            item.values(),
            overview
        );
        Optional<ShopifyStoreVectorizationLedgerEntity> existing = ledgerRepository.findByDeploymentIdAndEntityTypeAndSourceObjectId(
            run.getDeploymentId(),
            item.entityType(),
            item.id()
        );
        ShopifyStoreVectorizationLedgerEntity entity = existing.orElseGet(() -> {
            ShopifyStoreVectorizationLedgerEntity created = new ShopifyStoreVectorizationLedgerEntity();
            created.setId("svl-" + UUID.randomUUID().toString().substring(0, 8));
            created.setShopDomain(store.getShopDomain().trim().toLowerCase(Locale.ROOT));
            created.setDeploymentId(run.getDeploymentId());
            created.setEntityType(item.entityType());
            created.setSourceObjectId(item.id());
            created.setCreatedAt(now);
            created.setFirstIndexedAt(now);
            return created;
        });
        entity.setSourceCategory(item.sourceCategory());
        entity.setSourceRecordVersion(item.sourceRecordVersion());
        entity.setIndexedOutputFingerprint(fingerprint.fullFingerprint());
        entity.setFieldFingerprintsJson(writeFieldFingerprints(fingerprint.fieldFingerprints()));
        entity.setLastIndexedAt(now);
        entity.setLastSeenAt(now);
        entity.setLastIndexRunId(run.getId());
        entity.setLastIndexedDeploymentHash(deploymentHash);
        entity.setDeletedAt(null);
        entity.setUpdatedAt(now);
        ledgerRepository.save(entity);
    }

    private String writeFieldFingerprints(Map<String, String> fieldFingerprints) {
        try {
            return objectMapper.writeValueAsString(fieldFingerprints == null ? Map.of() : fieldFingerprints);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize Shopify vectorization ledger field fingerprints.", ex);
        }
    }
}
