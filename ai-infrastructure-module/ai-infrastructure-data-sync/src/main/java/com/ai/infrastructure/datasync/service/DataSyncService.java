package com.ai.infrastructure.datasync.service;

import com.ai.infrastructure.access.AIAccessControlService;
import com.ai.infrastructure.config.AIEntityConfigurationLoader;
import com.ai.infrastructure.datasync.AIDataSyncProperties;
import com.ai.infrastructure.datasync.dto.DataSyncBatchRequest;
import com.ai.infrastructure.datasync.dto.DataSyncBatchResponse;
import com.ai.infrastructure.datasync.dto.DataSyncDeleteRequest;
import com.ai.infrastructure.datasync.dto.DataSyncIdentity;
import com.ai.infrastructure.datasync.dto.DataSyncOperation;
import com.ai.infrastructure.datasync.dto.DataSyncOperationResponse;
import com.ai.infrastructure.datasync.dto.DataSyncOperationType;
import com.ai.infrastructure.datasync.dto.DataSyncTrace;
import com.ai.infrastructure.datasync.dto.DataSyncUpsertRequest;
import com.ai.infrastructure.datasync.dto.DataSyncVerifiedAuthContext;
import com.ai.infrastructure.datasync.dto.DataSyncVectorSpacesResponse;
import com.ai.infrastructure.datasync.normalize.DataSyncEntityNormalizer;
import com.ai.infrastructure.dto.AIAccessControlRequest;
import com.ai.infrastructure.dto.AIAccessControlResponse;
import com.ai.infrastructure.dto.AIEmbeddingRequest;
import com.ai.infrastructure.dto.AIEmbeddingResponse;
import com.ai.infrastructure.dto.AIEntityConfig;
import com.ai.infrastructure.service.VectorManagementService;
import com.ai.infrastructure.core.AIEmbeddingService;
import com.ai.infrastructure.util.UlidGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Service implementing push-based data sync (ingestion) operations.
 */
public class DataSyncService {

    private static final Logger log = LoggerFactory.getLogger(DataSyncService.class);

    private static final String ERROR_INVALID_REQUEST = "INVALID_REQUEST";
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_VECTOR_SPACE_NOT_FOUND = "VECTOR_SPACE_NOT_FOUND";
    private static final String ERROR_VECTOR_SPACE_NOT_INDEXABLE = "VECTOR_SPACE_NOT_INDEXABLE";
    private static final String ERROR_BATCH_TOO_LARGE = "BATCH_TOO_LARGE";
    private static final String ERROR_EMBEDDING_FAILED = "EMBEDDING_FAILED";
    private static final String ERROR_VECTOR_STORE_FAILED = "VECTOR_STORE_FAILED";

    private static final String OPERATION_WRITE = "WRITE";
    private static final String OPERATION_DELETE = "DELETE";

    private static final String IDENTITY_SOURCE_RECORD_ID = "_dataSyncSourceRecordId";
    private static final String IDENTITY_SOURCE_RECORD_VERSION = "_dataSyncSourceRecordVersion";
    private static final String IDENTITY_TARGET_ID = "_dataSyncTargetId";
    private static final String IDENTITY_CHUNK_ID = "_dataSyncChunkId";
    private static final String IDENTITY_CHUNK_COUNT = "_dataSyncChunkCount";
    private static final String IDENTITY_CONTENT_FINGERPRINT = "_dataSyncContentFingerprint";
    private static final String IDENTITY_IDEMPOTENCY_KEY = "_dataSyncIdempotencyKey";

    private static final String RESOURCE_PREFIX = "vectorSpace:";

    private final AIDataSyncProperties properties;
    private final AIEntityConfigurationLoader entityConfigurationLoader;
    private final AIEmbeddingService embeddingService;
    private final VectorManagementService vectorManagementService;
    private final AIAccessControlService accessControlService;
    private final DataSyncEntityNormalizer normalizer;
    private final UlidGenerator ulidGenerator;
    private final Clock clock;

    public DataSyncService(AIDataSyncProperties properties,
                           AIEntityConfigurationLoader entityConfigurationLoader,
                           AIEmbeddingService embeddingService,
                           VectorManagementService vectorManagementService,
                           AIAccessControlService accessControlService,
                           DataSyncEntityNormalizer normalizer,
                           Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.entityConfigurationLoader = Objects.requireNonNull(entityConfigurationLoader, "entityConfigurationLoader is required");
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService is required");
        this.vectorManagementService = Objects.requireNonNull(vectorManagementService, "vectorManagementService is required");
        this.accessControlService = Objects.requireNonNull(accessControlService, "accessControlService is required");
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer is required");
        this.clock = clock != null ? clock : Clock.systemUTC();
        this.ulidGenerator = new UlidGenerator(this.clock);
    }

    public DataSyncVectorSpacesResponse listVectorSpaces() {
        Set<String> supported = entityConfigurationLoader.getSupportedEntityTypes();
        List<String> vectorSpaces = supported == null
            ? List.of()
            : supported.stream()
                .filter(StringUtils::hasText)
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .sorted()
                .toList();

        DataSyncVectorSpacesResponse response = new DataSyncVectorSpacesResponse();
        response.setSuccess(true);
        response.setMessage("OK");
        response.setVectorSpaces(vectorSpaces);
        return response;
    }

    public DataSyncOperationResponse upsert(DataSyncUpsertRequest request) {
        long startedAt = System.nanoTime();
        if (request == null) {
            return failure(DataSyncOperationType.UPSERT, ERROR_INVALID_REQUEST, "Request body is required.", null, null, null, startedAt);
        }

        DataSyncTrace trace = request.getTrace();
        String vectorSpace = safeText(request.getVectorSpace());
        String logicalId = safeText(request.getId());
        if (!StringUtils.hasText(vectorSpace) || !StringUtils.hasText(logicalId) || trace == null) {
            return failure(DataSyncOperationType.UPSERT, ERROR_INVALID_REQUEST, "vectorSpace, id, and trace are required.", vectorSpace, logicalId, null, startedAt);
        }
        String id = resolveEffectiveId(logicalId, request.getIdentity());

        AIEntityConfig entityConfig = entityConfigurationLoader.getEntityConfig(vectorSpace);
        if (entityConfig == null) {
            return failure(DataSyncOperationType.UPSERT, ERROR_VECTOR_SPACE_NOT_FOUND, "Unknown vectorSpace: " + vectorSpace, vectorSpace, id, null, startedAt);
        }
        if (!entityConfig.isIndexable()) {
            return failure(DataSyncOperationType.UPSERT, ERROR_VECTOR_SPACE_NOT_INDEXABLE, "Vector space is not indexable: " + vectorSpace, vectorSpace, id, null, startedAt);
        }

        AccessDecision access = checkAccess(trace, vectorSpace, id, OPERATION_WRITE);
        if (!access.granted()) {
            return failure(DataSyncOperationType.UPSERT, ERROR_ACCESS_DENIED, access.message(), vectorSpace, id, access.metadata(), startedAt);
        }

        DataSyncEntityNormalizer.NormalizedEntity normalized;
        try {
            normalized = normalizer.normalize(entityConfig, vectorSpace, id, request.getContent(), request.getEntity(), request.getMetadata());
        } catch (Exception ex) {
            return failure(DataSyncOperationType.UPSERT, ERROR_INVALID_REQUEST, ex.getMessage(), vectorSpace, id, null, startedAt);
        }
        Map<String, Object> identityMetadata = enrichIdentityMetadata(vectorSpace, logicalId, id, request.getIdentity(), normalized.metadata());

        AIEmbeddingResponse embedding;
        try {
            embedding = embeddingService.generateEmbedding(AIEmbeddingRequest.builder()
                .text(normalized.content())
                .entityType(vectorSpace)
                .entityId(id)
                .metadata(identityMetadata.toString())
                .build());
        } catch (Exception ex) {
            log.warn("Embedding generation failed for {}:{}: {}", vectorSpace, id, ex.getMessage());
            return failure(DataSyncOperationType.UPSERT, ERROR_EMBEDDING_FAILED, "Embedding generation failed.", vectorSpace, id, null, startedAt);
        }
        if (embedding == null || embedding.getEmbedding() == null || embedding.getEmbedding().isEmpty()) {
            return failure(DataSyncOperationType.UPSERT, ERROR_EMBEDDING_FAILED, "Embedding generation returned no embedding.", vectorSpace, id, null, startedAt);
        }

        String vectorId;
        try {
            vectorId = vectorManagementService.storeVector(
                vectorSpace,
                id,
                normalized.content(),
                embedding.getEmbedding(),
                identityMetadata
            );
        } catch (Exception ex) {
            log.warn("Vector store failed for {}:{}: {}", vectorSpace, id, ex.getMessage());
            return failure(DataSyncOperationType.UPSERT, ERROR_VECTOR_STORE_FAILED, "Vector store failed.", vectorSpace, id, null, startedAt);
        }

        long processingMs = nanosToMillis(System.nanoTime() - startedAt);
        DataSyncOperationResponse response = new DataSyncOperationResponse();
        response.setSuccess(true);
        response.setMessage("Upserted");
        response.setType(DataSyncOperationType.UPSERT);
        response.setVectorSpace(vectorSpace);
        response.setId(id);
        response.setVectorId(vectorId);
        response.setProcessingTimeMs(processingMs);
        response.setMetadata(mergeMetadata(access.metadata(), identityMetadata));
        return response;
    }

    public DataSyncOperationResponse delete(DataSyncDeleteRequest request) {
        long startedAt = System.nanoTime();
        if (request == null) {
            return failure(DataSyncOperationType.DELETE, ERROR_INVALID_REQUEST, "Request body is required.", null, null, null, startedAt);
        }

        DataSyncTrace trace = request.getTrace();
        String vectorSpace = safeText(request.getVectorSpace());
        String logicalId = safeText(request.getId());
        if (!StringUtils.hasText(vectorSpace) || !StringUtils.hasText(logicalId) || trace == null) {
            return failure(DataSyncOperationType.DELETE, ERROR_INVALID_REQUEST, "vectorSpace, id, and trace are required.", vectorSpace, logicalId, null, startedAt);
        }
        String id = resolveEffectiveId(logicalId, request.getIdentity());

        AIEntityConfig entityConfig = entityConfigurationLoader.getEntityConfig(vectorSpace);
        if (entityConfig == null) {
            return failure(DataSyncOperationType.DELETE, ERROR_VECTOR_SPACE_NOT_FOUND, "Unknown vectorSpace: " + vectorSpace, vectorSpace, id, null, startedAt);
        }

        AccessDecision access = checkAccess(trace, vectorSpace, id, OPERATION_DELETE);
        if (!access.granted()) {
            return failure(DataSyncOperationType.DELETE, ERROR_ACCESS_DENIED, access.message(), vectorSpace, id, access.metadata(), startedAt);
        }

        boolean removed;
        try {
            removed = vectorManagementService.removeVector(vectorSpace, id);
        } catch (Exception ex) {
            log.warn("Vector delete failed for {}:{}: {}", vectorSpace, id, ex.getMessage());
            return failure(DataSyncOperationType.DELETE, ERROR_VECTOR_STORE_FAILED, "Vector delete failed.", vectorSpace, id, null, startedAt);
        }

        long processingMs = nanosToMillis(System.nanoTime() - startedAt);
        DataSyncOperationResponse response = new DataSyncOperationResponse();
        response.setSuccess(true);
        response.setMessage(removed ? "Deleted" : "Not found");
        response.setType(DataSyncOperationType.DELETE);
        response.setVectorSpace(vectorSpace);
        response.setId(id);
        response.setProcessingTimeMs(processingMs);
        response.setMetadata(mergeMetadata(access.metadata(), identityMetadata(vectorSpace, logicalId, id, request.getIdentity())));
        return response;
    }

    public DataSyncBatchResponse batch(DataSyncBatchRequest request) {
        long startedAt = System.nanoTime();
        if (request == null) {
            return batchFailure(ERROR_INVALID_REQUEST, "Request body is required.", startedAt);
        }

        DataSyncTrace trace = request.getTrace();
        List<DataSyncOperation> ops = request.getOperations();
        if (trace == null || ops == null || ops.isEmpty()) {
            return batchFailure(ERROR_INVALID_REQUEST, "trace and operations are required.", startedAt);
        }

        int maxBatch = Math.max(1, properties.getMaxBatchSize());
        if (ops.size() > maxBatch) {
            return batchFailure(ERROR_BATCH_TOO_LARGE, "Batch size exceeds maxBatchSize=" + maxBatch, startedAt);
        }

        List<String> denied = new ArrayList<>();
        Map<String, AIEntityConfig> configBySpace = new LinkedHashMap<>();
        for (int i = 0; i < ops.size(); i++) {
            DataSyncOperation op = ops.get(i);
            if (op == null || op.getType() == null) {
                return batchFailure(ERROR_INVALID_REQUEST, "Operation at index " + i + " is missing type.", startedAt);
            }
            String vectorSpace = safeText(op.getVectorSpace());
            String logicalId = safeText(op.getId());
            if (!StringUtils.hasText(vectorSpace) || !StringUtils.hasText(logicalId)) {
                return batchFailure(ERROR_INVALID_REQUEST, "Operation at index " + i + " must include vectorSpace and id.", startedAt);
            }
            String id = resolveEffectiveId(logicalId, op.getIdentity());

            AIEntityConfig config = configBySpace.computeIfAbsent(vectorSpace, entityConfigurationLoader::getEntityConfig);
            if (config == null) {
                return batchFailure(ERROR_VECTOR_SPACE_NOT_FOUND, "Unknown vectorSpace: " + vectorSpace, startedAt);
            }
            if (op.getType() == DataSyncOperationType.UPSERT && !config.isIndexable()) {
                return batchFailure(ERROR_VECTOR_SPACE_NOT_INDEXABLE, "Vector space is not indexable: " + vectorSpace, startedAt);
            }
            if (op.getType() == DataSyncOperationType.UPSERT
                && !StringUtils.hasText(op.getContent())
                && (op.getEntity() == null || op.getEntity().isEmpty())) {
                return batchFailure(ERROR_INVALID_REQUEST, "Operation at index " + i + " must include content or entity for UPSERT.", startedAt);
            }

            String operationType = op.getType() == DataSyncOperationType.DELETE ? OPERATION_DELETE : OPERATION_WRITE;
            AccessDecision access = checkAccess(trace, vectorSpace, id, operationType);
            if (!access.granted()) {
                denied.add("index=" + i + " vectorSpace=" + vectorSpace + " id=" + id);
            }
        }

        if (!denied.isEmpty()) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("deniedOperations", List.copyOf(denied));

            DataSyncOperationResponse marker = new DataSyncOperationResponse();
            marker.setSuccess(false);
            marker.setErrorCode(ERROR_ACCESS_DENIED);
            marker.setMessage("Access denied for one or more operations.");
            marker.setMetadata(Collections.unmodifiableMap(meta));

            DataSyncBatchResponse response = new DataSyncBatchResponse();
            response.setSuccess(false);
            response.setErrorCode(ERROR_ACCESS_DENIED);
            response.setMessage("Access denied for one or more operations.");
            response.setTotalOperations(ops.size());
            response.setSucceededOperations(0);
            response.setFailedOperations(ops.size());
            response.setResults(List.of(marker));
            return response;
        }

        List<DataSyncOperationResponse> results = new ArrayList<>();
        int successCount = 0;
        for (DataSyncOperation op : ops) {
            if (op == null || op.getType() == null) {
                continue;
            }
            DataSyncOperationResponse result;
            if (op.getType() == DataSyncOperationType.DELETE) {
                DataSyncDeleteRequest deleteRequest = new DataSyncDeleteRequest();
                deleteRequest.setVectorSpace(op.getVectorSpace());
                deleteRequest.setId(op.getId());
                deleteRequest.setIdentity(op.getIdentity());
                deleteRequest.setTrace(trace);
                result = delete(deleteRequest);
            } else {
                DataSyncUpsertRequest upsertRequest = new DataSyncUpsertRequest();
                upsertRequest.setVectorSpace(op.getVectorSpace());
                upsertRequest.setId(op.getId());
                upsertRequest.setContent(op.getContent());
                upsertRequest.setEntity(op.getEntity());
                upsertRequest.setMetadata(op.getMetadata());
                upsertRequest.setIdentity(op.getIdentity());
                upsertRequest.setTrace(trace);
                result = upsert(upsertRequest);
            }

            results.add(result);
            if (Boolean.TRUE.equals(result.getSuccess())) {
                successCount++;
            }
        }

        int failed = results.size() - successCount;
        DataSyncBatchResponse response = new DataSyncBatchResponse();
        response.setSuccess(failed == 0);
        response.setMessage(failed == 0 ? "OK" : "Completed with failures");
        response.setTotalOperations(results.size());
        response.setSucceededOperations(successCount);
        response.setFailedOperations(failed);
        response.setResults(Collections.unmodifiableList(results));
        return response;
    }

    private AccessDecision checkAccess(DataSyncTrace trace, String vectorSpace, String id, String operationType) {
        DataSyncVerifiedAuthContext verifiedAuthContext = trace != null ? trace.getAuthContext() : null;
        String userId = safeText(verifiedAuthContext == null ? null : verifiedAuthContext.getSubjectId());
        if (!StringUtils.hasText(userId)) {
            userId = trace != null ? safeText(trace.getUserId()) : null;
        }
        String sessionId = safeText(verifiedAuthContext == null ? null : verifiedAuthContext.getSessionId());
        if (!StringUtils.hasText(sessionId)) {
            sessionId = trace != null ? safeText(trace.getSessionId()) : null;
        }
        String requestId = trace != null ? safeText(trace.getRequestId()) : null;
        if (!StringUtils.hasText(requestId)) {
            requestId = "sync_" + ulidGenerator.nextUlid();
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("vectorSpace", vectorSpace);
        meta.put("entityId", id);
        meta.put("operationType", operationType);
        meta.put("identitySource", verifiedAuthContext != null ? "verifiedAuthContext" : "legacyTrace");
        if (verifiedAuthContext != null) {
            Map<String, Object> authContextMeta = new LinkedHashMap<>();
            putIfText(authContextMeta, "subjectId", verifiedAuthContext.getSubjectId());
            putIfText(authContextMeta, "subjectType", verifiedAuthContext.getSubjectType());
            putIfText(authContextMeta, "authMode", verifiedAuthContext.getAuthMode());
            putIfText(authContextMeta, "callerType", verifiedAuthContext.getCallerType());
            putIfText(authContextMeta, "sessionId", verifiedAuthContext.getSessionId());
            putIfText(authContextMeta, "deploymentId", verifiedAuthContext.getDeploymentId());
            putIfText(authContextMeta, "customerId", verifiedAuthContext.getCustomerId());
            putIfText(authContextMeta, "tenantId", verifiedAuthContext.getTenantId());
            putIfText(authContextMeta, "issuer", verifiedAuthContext.getIssuer());
            if (verifiedAuthContext.getGrantedScopes() != null && !verifiedAuthContext.getGrantedScopes().isEmpty()) {
                authContextMeta.put("grantedScopes", List.copyOf(verifiedAuthContext.getGrantedScopes()));
            }
            if (!authContextMeta.isEmpty()) {
                meta.put("authContext", authContextMeta);
            }
        }
        if (trace != null && trace.getMetadata() != null && !trace.getMetadata().isEmpty()) {
            for (Map.Entry<String, Object> entry : trace.getMetadata().entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                meta.putIfAbsent(entry.getKey().trim(), entry.getValue());
            }
        }

        AIAccessControlRequest accessRequest = AIAccessControlRequest.builder()
            .requestId(requestId)
            .userId(userId)
            .sessionId(sessionId)
            .resourceId(RESOURCE_PREFIX + vectorSpace)
            .operationType(operationType)
            .context("vector sync")
            .metadata(Collections.unmodifiableMap(meta))
            .timestamp(LocalDateTime.now(clock))
            .build();

        try {
            AIAccessControlResponse response = accessControlService.checkAccess(accessRequest);
            boolean granted = response != null && Boolean.TRUE.equals(response.getAccessGranted());
            return new AccessDecision(granted, granted ? "OK" : "Access denied.", Collections.unmodifiableMap(meta));
        } catch (Exception ex) {
            log.warn("Access control evaluation failed for {}:{}: {}", vectorSpace, id, ex.getMessage());
            return new AccessDecision(false, "Access denied.", Collections.unmodifiableMap(meta));
        }
    }

    private void putIfText(Map<String, Object> target, String key, String value) {
        if (target == null || !StringUtils.hasText(key) || !StringUtils.hasText(value)) {
            return;
        }
        target.put(key.trim(), value.trim());
    }

    private DataSyncOperationResponse failure(DataSyncOperationType type,
                                              String errorCode,
                                              String message,
                                              String vectorSpace,
                                              String id,
                                              Map<String, Object> metadata,
                                              long startedAt) {
        long processingMs = nanosToMillis(System.nanoTime() - startedAt);
        DataSyncOperationResponse response = new DataSyncOperationResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setMessage(StringUtils.hasText(message) ? message : "Request failed.");
        response.setType(type);
        response.setVectorSpace(vectorSpace);
        response.setId(id);
        response.setMetadata(metadata);
        response.setProcessingTimeMs(processingMs);
        return response;
    }

    private DataSyncBatchResponse batchFailure(String errorCode, String message, long startedAt) {
        long processingMs = nanosToMillis(System.nanoTime() - startedAt);
        DataSyncOperationResponse marker = new DataSyncOperationResponse();
        marker.setSuccess(false);
        marker.setErrorCode(errorCode);
        marker.setMessage(StringUtils.hasText(message) ? message : "Batch request failed.");
        marker.setProcessingTimeMs(processingMs);

        DataSyncBatchResponse response = new DataSyncBatchResponse();
        response.setSuccess(false);
        response.setErrorCode(errorCode);
        response.setMessage(StringUtils.hasText(message) ? message : "Batch request failed.");
        response.setTotalOperations(0);
        response.setSucceededOperations(0);
        response.setFailedOperations(0);
        response.setResults(List.of(marker));
        return response;
    }

    private long nanosToMillis(long nanos) {
        return Math.max(0L, nanos / 1_000_000L);
    }

    private String safeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveEffectiveId(String logicalId, DataSyncIdentity identity) {
        String baseId = safeText(logicalId);
        if (!StringUtils.hasText(baseId)) {
            return baseId;
        }
        if (identity == null || !StringUtils.hasText(identity.getChunkId())) {
            return baseId;
        }
        String normalizedChunkId = normalizeChunkId(identity.getChunkId());
        if (!StringUtils.hasText(normalizedChunkId)) {
            throw new IllegalArgumentException("identity.chunkId must contain at least one safe character.");
        }
        String suffix = "::chunk:" + normalizedChunkId;
        return baseId.endsWith(suffix) ? baseId : baseId + suffix;
    }

    private Map<String, Object> enrichIdentityMetadata(String vectorSpace,
                                                       String logicalId,
                                                       String effectiveId,
                                                       DataSyncIdentity identity,
                                                       Map<String, Object> existingMetadata) {
        Map<String, Object> metadata = new LinkedHashMap<>(existingMetadata == null ? Map.of() : existingMetadata);
        metadata.putAll(identityMetadata(vectorSpace, logicalId, effectiveId, identity));
        return metadata;
    }

    private Map<String, Object> identityMetadata(String vectorSpace,
                                                 String logicalId,
                                                 String effectiveId,
                                                 DataSyncIdentity identity) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(IDENTITY_TARGET_ID, effectiveId);
        metadata.put(IDENTITY_IDEMPOTENCY_KEY, idempotencyKey(vectorSpace, effectiveId));
        if (identity == null) {
            return metadata;
        }
        if (StringUtils.hasText(identity.getSourceRecordId())) {
            metadata.put(IDENTITY_SOURCE_RECORD_ID, identity.getSourceRecordId().trim());
        } else {
            metadata.put(IDENTITY_SOURCE_RECORD_ID, logicalId);
        }
        if (StringUtils.hasText(identity.getSourceRecordVersion())) {
            metadata.put(IDENTITY_SOURCE_RECORD_VERSION, identity.getSourceRecordVersion().trim());
        }
        if (StringUtils.hasText(identity.getChunkId())) {
            metadata.put(IDENTITY_CHUNK_ID, normalizeChunkId(identity.getChunkId()));
        }
        if (identity.getChunkCount() != null && identity.getChunkCount() > 0) {
            metadata.put(IDENTITY_CHUNK_COUNT, identity.getChunkCount());
        }
        if (StringUtils.hasText(identity.getContentFingerprint())) {
            metadata.put(IDENTITY_CONTENT_FINGERPRINT, identity.getContentFingerprint().trim());
        }
        return metadata;
    }

    private Map<String, Object> mergeMetadata(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (left != null) {
            merged.putAll(left);
        }
        if (right != null) {
            merged.putAll(right);
        }
        return Collections.unmodifiableMap(merged);
    }

    private String normalizeChunkId(String chunkId) {
        return chunkId == null
            ? ""
            : chunkId.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("(^-|-$)", "");
    }

    private String idempotencyKey(String vectorSpace, String effectiveId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((vectorSpace + ":" + effectiveId).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) {
                out.append(String.format("%02x", b));
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to compute data sync idempotency key.", ex);
        }
    }

    private record AccessDecision(boolean granted, String message, Map<String, Object> metadata) { }
}
