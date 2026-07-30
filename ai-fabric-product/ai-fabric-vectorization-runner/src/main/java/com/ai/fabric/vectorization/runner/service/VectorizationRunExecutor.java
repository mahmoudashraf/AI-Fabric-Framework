package com.ai.fabric.vectorization.runner.service;

import com.ai.fabric.vectorization.adapter.source.VectorizationSourceAdapter;
import com.ai.fabric.vectorization.identity.StableVectorizationIdentity;
import com.ai.fabric.vectorization.mapping.VectorizationRecordMapper;
import com.ai.fabric.vectorization.model.VectorizationDiscoveryResult;
import com.ai.fabric.vectorization.model.VectorizationExecutionBundle;
import com.ai.fabric.vectorization.model.VectorizationMappedRecord;
import com.ai.fabric.vectorization.model.VectorizationSourcePage;
import com.ai.fabric.vectorization.model.VectorizationTargetWriteResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class VectorizationRunExecutor {

    private static final Logger log = LoggerFactory.getLogger(VectorizationRunExecutor.class);

    private final ObjectMapper objectMapper;
    private final VectorizationRunnerPlatformClient platformClient;
    private final VectorizationSourceAdapterRegistry sourceAdapterRegistry;
    private final VectorizationRecordMapper recordMapper;
    private final ConnectorDataSyncTargetWriter targetWriter;

    public VectorizationRunExecutor(ObjectMapper objectMapper,
                                    VectorizationRunnerPlatformClient platformClient,
                                    VectorizationSourceAdapterRegistry sourceAdapterRegistry,
                                    VectorizationRecordMapper recordMapper,
                                    ConnectorDataSyncTargetWriter targetWriter) {
        this.objectMapper = objectMapper;
        this.platformClient = platformClient;
        this.sourceAdapterRegistry = sourceAdapterRegistry;
        this.recordMapper = recordMapper;
        this.targetWriter = targetWriter;
    }

    public void execute(String sessionToken, VectorizationRunnerPlatformClient.ClaimedRun claimedRun) throws Exception {
        VectorizationExecutionBundle bundle = platformClient.fetchExecutionBundle(sessionToken, claimedRun.runId());
        ObjectNode progress = objectMapper.createObjectNode();
        progress.put("reason", bundle.reason().name());
        progress.put("deploymentId", bundle.deploymentId());
        progress.put("runId", bundle.runId());
        progress.put("processedRecords", 0);
        progress.put("succeededRecords", 0);
        progress.put("failedRecords", 0);

        ObjectNode errorSummary = objectMapper.createObjectNode();
        ArrayNode buckets = errorSummary.putArray("failureBuckets");
        Map<String, FailureBucket> failureBuckets = new LinkedHashMap<>();

        try {
            Set<ReconciledIdentity> reconciledIdentities =
                reconcilePendingDataSyncWork(bundle);
            progress.put("reconciledRecords", reconciledIdentities.size());

            VectorizationSourceAdapter adapter = sourceAdapterRegistry.resolve(
                bundle.connectionDescriptor().adapterType()
            );
            List<String> entityScope = bundle.entityScope();
            VectorizationDiscoveryResult discovery = adapter.discover(
                bundle,
                entityScope
            );
            platformClient.reportDiscovery(
                sessionToken,
                bundle.sourceConnectionId(),
                discovery
            );

            if (bundle.reason() == com.ai.fabric.vectorization.model.VectorizationRunReason.DISCOVERY) {
                ObjectNode discoverySummary = progress.putObject("discoverySummary");
                ObjectNode counts = discoverySummary.putObject("countsByEntityType");
                discovery.countsByEntityType().forEach(counts::put);
                ObjectNode methods = discoverySummary.putObject("countMethodByEntityType");
                discovery.countMethodByEntityType().forEach((key, value) -> methods.put(key, value.name()));
                platformClient.completeRun(sessionToken, bundle.runId(), "COMPLETED", progress, errorSummary, buckets);
                return;
            }
            for (String entityType : entityScope) {
                processEntity(
                    sessionToken,
                    bundle,
                    adapter,
                    entityType,
                    progress,
                    failureBuckets,
                    reconciledIdentities
                );
                syncBuckets(buckets, failureBuckets);
                VectorizationRunnerPlatformClient.HeartbeatDecision decision = platformClient.heartbeat(sessionToken, bundle.runId());
                if (decision.shouldCancel()) {
                    platformClient.completeRun(sessionToken, bundle.runId(), "CANCELLED", progress, errorSummary, buckets);
                    return;
                }
                if (decision.shouldPause()) {
                    platformClient.completeRun(sessionToken, bundle.runId(), "PAUSED", progress, errorSummary, buckets);
                    return;
                }
            }
            syncBuckets(buckets, failureBuckets);
            String finalStatus = progress.path("failedRecords").asInt(0) > 0 ? "FAILED" : "COMPLETED";
            platformClient.completeRun(sessionToken, bundle.runId(), finalStatus, progress, errorSummary, buckets);
        } catch (DataSyncWorkReconciliationException ex) {
            log.warn(
                "Vectorization durable work reconciliation stopped the run: runId={}, workId={}, status={}, errorCode={}",
                bundle.runId(),
                ex.workId(),
                ex.indexingStatus(),
                ex.errorCode()
            );
            errorSummary.put("exception", ex.getMessage());
            ObjectNode dataSync = errorSummary.putObject("dataSync");
            dataSync.put("requiresWorkReconciliation", true);
            ObjectNode reconciliation = dataSync.putObject("reconciliation");
            reconciliation.put("httpStatus", ex.httpStatus());
            reconciliation.put("errorCode", ex.errorCode());
            putIfText(reconciliation, "workId", ex.workId());
            putIfText(
                reconciliation,
                "indexingStatus",
                ex.indexingStatus()
            );
            putIfText(reconciliation, "entityType", ex.entityType());
            putIfText(reconciliation, "entityId", ex.entityId());
            reconciliation.put(
                "nextAction",
                "RECONCILE_DURABLE_WORK_BEFORE_RESUBMIT"
            );
            bucketFailure(
                failureBuckets,
                ex.entityType() == null ? "unknown" : ex.entityType(),
                ex.errorCode(),
                ex.getMessage()
            );
            syncBuckets(buckets, failureBuckets);
            platformClient.completeRun(
                sessionToken,
                bundle.runId(),
                "FAILED",
                progress,
                errorSummary,
                buckets
            );
            throw ex;
        } catch (DataSyncTargetWriteException ex) {
            log.warn(
                "Vectorization Data Sync handoff failed: runId={}, httpStatus={}, providerRequestId={}, failures={}",
                bundle.runId(),
                ex.httpStatus(),
                ex.providerRequestId(),
                ex.failures().size()
            );
            progress.put(
                "processedRecords",
                progress.path("processedRecords").asInt(0)
                    + ex.succeededOperations()
                    + ex.failedOperations()
            );
            progress.put(
                "succeededRecords",
                progress.path("succeededRecords").asInt(0) + ex.succeededOperations()
            );
            progress.put(
                "failedRecords",
                progress.path("failedRecords").asInt(0) + ex.failedOperations()
            );
            errorSummary.put("exception", ex.getMessage());
            ObjectNode dataSync = errorSummary.putObject("dataSync");
            dataSync.put("httpStatus", ex.httpStatus());
            if (ex.providerRequestId() != null) {
                dataSync.put("providerRequestId", ex.providerRequestId());
            }
            dataSync.put("succeededOperations", ex.succeededOperations());
            dataSync.put("failedOperations", ex.failedOperations());
            dataSync.put("durableHandoffAccepted", ex.hasDurableHandoff());
            dataSync.put(
                "requiresWorkReconciliation",
                ex.failures().stream().anyMatch(
                    failure -> failure.retryDisposition()
                        == DataSyncRetryDisposition.RECONCILE_DURABLE_WORK
                )
            );
            dataSync.set("failures", objectMapper.valueToTree(ex.failures()));
            ex.failures().forEach(failure -> bucketFailure(
                failureBuckets,
                failure.vectorSpace() == null ? "unknown" : failure.vectorSpace(),
                failure.errorCode(),
                failure.message()
            ));
            syncBuckets(buckets, failureBuckets);
            platformClient.completeRun(sessionToken, bundle.runId(), "FAILED", progress, errorSummary, buckets);
            throw ex;
        } catch (Exception ex) {
            log.error("Vectorization run failed: runId={}, message={}", bundle.runId(), ex.getMessage(), ex);
            errorSummary.put("exception", ex.getMessage());
            syncBuckets(buckets, failureBuckets);
            platformClient.completeRun(sessionToken, bundle.runId(), "FAILED", progress, errorSummary, buckets);
            throw ex;
        }
    }

    private void processEntity(String sessionToken,
                               VectorizationExecutionBundle bundle,
                               VectorizationSourceAdapter adapter,
                               String entityType,
                               ObjectNode progress,
                               Map<String, FailureBucket> failureBuckets,
                               Set<ReconciledIdentity> reconciledIdentities) throws Exception {
        int configuredBatchSize = bundle.executionConfig().path("batchSize").asInt(bundle.executionConfig().path("pageSize").asInt(100));
        int maxPagesPerEntity = bundle.executionConfig().path("maxPagesPerEntity").asInt(Integer.MAX_VALUE);
        int maxRecordsPerEntity = bundle.executionConfig().path("maxRecordsPerEntity").asInt(Integer.MAX_VALUE);
        int remainingRecords = maxRecordsPerEntity > 0 ? maxRecordsPerEntity : Integer.MAX_VALUE;
        String cursor = null;
        boolean hasMore;
        int batchNumber = 0;
        do {
            if (batchNumber >= maxPagesPerEntity || remainingRecords <= 0) {
                break;
            }
            int requestedBatchSize = configuredBatchSize;
            if (remainingRecords != Integer.MAX_VALUE) {
                requestedBatchSize = Math.max(1, Math.min(configuredBatchSize, remainingRecords));
            }
            VectorizationSourcePage page = adapter.fetchPage(bundle, entityType, cursor, requestedBatchSize);
            List<JsonNode> pageRecords = page.records() == null ? List.of() : page.records();
            List<JsonNode> processedSourceRecords = pageRecords;
            if (remainingRecords != Integer.MAX_VALUE && pageRecords.size() > remainingRecords) {
                processedSourceRecords = pageRecords.subList(0, remainingRecords);
            }
            List<VectorizationMappedRecord> mapped = new ArrayList<>();
            for (JsonNode record : processedSourceRecords) {
                try {
                    VectorizationMappedRecord mappedRecord = recordMapper.map(entityType, bundle.mappingConfig(), record);
                    StableVectorizationIdentity identity = new StableVectorizationIdentity(
                        mappedRecord.logicalEntityId(),
                        mappedRecord.sourceRecordId(),
                        null
                    );
                    mapped.add(new VectorizationMappedRecord(
                        mappedRecord.entityType(),
                        identity.effectiveTargetId(),
                        mappedRecord.sourceRecordId(),
                        mappedRecord.sourceRecordVersion(),
                        mappedRecord.entity(),
                        mappedRecord.metadata()
                    ));
                } catch (Exception ex) {
                    bucketFailure(failureBuckets, entityType, "MAPPING_ERROR", ex.getMessage());
                    progress.put("failedRecords", progress.path("failedRecords").asInt(0) + 1);
                }
            }

            List<VectorizationMappedRecord> recordsToWrite = new ArrayList<>();
            int reconciledInPage = 0;
            for (VectorizationMappedRecord mappedRecord : mapped) {
                ReconciledIdentity identity = new ReconciledIdentity(
                    normalizedEntityType(mappedRecord, entityType),
                    mappedRecord.logicalEntityId()
                );
                if (reconciledIdentities.contains(identity)) {
                    reconciledInPage++;
                } else {
                    recordsToWrite.add(mappedRecord);
                }
            }
            VectorizationTargetWriteResult writeResult = recordsToWrite.isEmpty()
                ? new VectorizationTargetWriteResult(0, 0)
                : targetWriter.upsertBatch(
                    bundle,
                    entityType,
                    recordsToWrite
                );
            progress.put("processedRecords", progress.path("processedRecords").asInt(0) + processedSourceRecords.size());
            progress.put(
                "succeededRecords",
                progress.path("succeededRecords").asInt(0)
                    + writeResult.succeeded()
                    + reconciledInPage
            );
            progress.put("failedRecords", progress.path("failedRecords").asInt(0) + writeResult.failed());
            progress.put("currentEntityType", entityType);
            progress.put("batchNumber", ++batchNumber);
            if (remainingRecords != Integer.MAX_VALUE) {
                remainingRecords = Math.max(0, remainingRecords - processedSourceRecords.size());
            }

            ObjectNode details = objectMapper.createObjectNode();
            details.put("recordsInPage", processedSourceRecords.size());
            details.put("mappedRecords", mapped.size());
            details.put("submittedRecords", recordsToWrite.size());
            details.put("reconciledRecords", reconciledInPage);
            details.put("nextCursor", page.nextCursor());
            details.put("hasMore", page.hasMore() && batchNumber < maxPagesPerEntity && remainingRecords > 0);
            platformClient.reportCheckpoint(
                sessionToken,
                bundle.runId(),
                entityType,
                "PAGE",
                page.nextCursor(),
                progress,
                details
            );

            cursor = page.nextCursor();
            hasMore = page.hasMore() && batchNumber < maxPagesPerEntity && remainingRecords > 0;
        } while (hasMore);
    }

    private Set<ReconciledIdentity> reconcilePendingDataSyncWork(
        VectorizationExecutionBundle bundle
    ) throws Exception {
        JsonNode pendingWork = bundle.executionConfig()
            .path("pendingDataSyncWork");
        if (!pendingWork.isArray() || pendingWork.isEmpty()) {
            return Set.of();
        }

        Set<ReconciledIdentity> reconciled = new LinkedHashSet<>();
        for (JsonNode pending : pendingWork) {
            String workId = text(pending, "workId");
            String expectedEntityType = text(pending, "vectorSpace");
            String expectedEntityId = text(pending, "entityId");
            DataSyncWorkStatus status = targetWriter.readWorkStatus(
                bundle,
                workId
            );
            if (expectedEntityType != null
                && !expectedEntityType.equals(status.entityType())
                || expectedEntityId != null
                && !expectedEntityId.equals(status.entityId())) {
                throw new DataSyncWorkReconciliationException(
                    200,
                    "INDEXING_WORK_IDENTITY_MISMATCH",
                    "Indexing work identity does not match the failed operation.",
                    status.workId(),
                    status.status(),
                    status.entityType(),
                    status.entityId()
                );
            }
            if (status.isSuccessfulTerminal()) {
                reconciled.add(
                    new ReconciledIdentity(
                        status.entityType(),
                        status.entityId()
                    )
                );
                continue;
            }
            if (status.isInFlight()) {
                throw new DataSyncWorkReconciliationException(
                    200,
                    "INDEXING_WORK_IN_PROGRESS",
                    "Indexing work is still in progress.",
                    status.workId(),
                    status.status(),
                    status.entityType(),
                    status.entityId()
                );
            }
            if (status.isDeadLetter()) {
                throw new DataSyncWorkReconciliationException(
                    200,
                    "INDEXING_WORK_DEAD_LETTER",
                    "Indexing work requires operator review.",
                    status.workId(),
                    status.status(),
                    status.entityType(),
                    status.entityId()
                );
            }
            throw new DataSyncWorkReconciliationException(
                200,
                "INDEXING_WORK_STATUS_INVALID",
                "Indexing work returned an unsupported state.",
                status.workId(),
                status.status(),
                status.entityType(),
                status.entityId()
            );
        }
        return Set.copyOf(reconciled);
    }

    private String normalizedEntityType(
        VectorizationMappedRecord record,
        String fallbackEntityType
    ) {
        return record.entityType() == null || record.entityType().isBlank()
            ? fallbackEntityType
            : record.entityType().trim();
    }

    private String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        String value = node.path(fieldName).asText(null);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void putIfText(
        ObjectNode target,
        String fieldName,
        String value
    ) {
        if (value != null && !value.isBlank()) {
            target.put(fieldName, value);
        }
    }

    private void bucketFailure(Map<String, FailureBucket> buckets, String entityType, String errorCode, String message) {
        String key = entityType + "::" + errorCode + "::" + message;
        buckets.compute(key, (ignored, existing) -> existing == null
            ? new FailureBucket(entityType, errorCode, message, 1)
            : new FailureBucket(existing.entityType(), existing.errorCode(), existing.summary(), existing.occurrences() + 1));
    }

    private void syncBuckets(ArrayNode target, Map<String, FailureBucket> buckets) {
        target.removeAll();
        buckets.values().forEach(bucket -> {
            ObjectNode node = target.addObject();
            node.put("entityType", bucket.entityType());
            node.put("errorCode", bucket.errorCode());
            node.put("summary", bucket.summary());
            node.put("occurrences", bucket.occurrences());
        });
    }

    private record FailureBucket(String entityType, String errorCode, String summary, int occurrences) {
    }

    private record ReconciledIdentity(String entityType, String entityId) {
    }
}
