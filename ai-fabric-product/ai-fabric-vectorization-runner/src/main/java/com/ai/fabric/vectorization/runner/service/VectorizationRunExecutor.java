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
import java.util.List;
import java.util.Map;

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
        VectorizationSourceAdapter adapter = sourceAdapterRegistry.resolve(bundle.connectionDescriptor().adapterType());
        List<String> entityScope = bundle.entityScope();
        VectorizationDiscoveryResult discovery = adapter.discover(bundle, entityScope);
        platformClient.reportDiscovery(sessionToken, bundle.sourceConnectionId(), discovery);

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
            for (String entityType : entityScope) {
                processEntity(sessionToken, bundle, adapter, entityType, progress, failureBuckets);
                syncBuckets(buckets, failureBuckets);
                VectorizationRunnerPlatformClient.HeartbeatDecision decision = platformClient.heartbeat(sessionToken, bundle.runId());
                if (decision.shouldCancel()) {
                    platformClient.completeRun(sessionToken, bundle.runId(), "CANCELLED", progress, errorSummary);
                    return;
                }
                if (decision.shouldPause()) {
                    platformClient.completeRun(sessionToken, bundle.runId(), "PAUSED", progress, errorSummary);
                    return;
                }
            }
            syncBuckets(buckets, failureBuckets);
            String finalStatus = progress.path("failedRecords").asInt(0) > 0 ? "FAILED" : "COMPLETED";
            platformClient.completeRun(sessionToken, bundle.runId(), finalStatus, progress, errorSummary);
        } catch (Exception ex) {
            log.error("Vectorization run failed: runId={}, message={}", bundle.runId(), ex.getMessage(), ex);
            errorSummary.put("exception", ex.getMessage());
            syncBuckets(buckets, failureBuckets);
            platformClient.completeRun(sessionToken, bundle.runId(), "FAILED", progress, errorSummary);
            throw ex;
        }
    }

    private void processEntity(String sessionToken,
                               VectorizationExecutionBundle bundle,
                               VectorizationSourceAdapter adapter,
                               String entityType,
                               ObjectNode progress,
                               Map<String, FailureBucket> failureBuckets) throws Exception {
        int batchSize = bundle.executionConfig().path("batchSize").asInt(bundle.executionConfig().path("pageSize").asInt(100));
        String cursor = null;
        boolean hasMore;
        int batchNumber = 0;
        do {
            VectorizationSourcePage page = adapter.fetchPage(bundle, entityType, cursor, batchSize);
            List<VectorizationMappedRecord> mapped = new ArrayList<>();
            for (JsonNode record : page.records()) {
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

            VectorizationTargetWriteResult writeResult = targetWriter.upsertBatch(bundle, entityType, mapped);
            progress.put("processedRecords", progress.path("processedRecords").asInt(0) + page.records().size());
            progress.put("succeededRecords", progress.path("succeededRecords").asInt(0) + writeResult.succeeded());
            progress.put("failedRecords", progress.path("failedRecords").asInt(0) + writeResult.failed());
            progress.put("currentEntityType", entityType);
            progress.put("batchNumber", ++batchNumber);

            ObjectNode details = objectMapper.createObjectNode();
            details.put("recordsInPage", page.records().size());
            details.put("mappedRecords", mapped.size());
            details.put("nextCursor", page.nextCursor());
            details.put("hasMore", page.hasMore());
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
            hasMore = page.hasMore();
        } while (hasMore);
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
}
