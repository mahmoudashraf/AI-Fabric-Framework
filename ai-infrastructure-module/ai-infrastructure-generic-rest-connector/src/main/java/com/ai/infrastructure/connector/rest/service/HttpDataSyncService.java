package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.util.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class HttpDataSyncService {

    private final RestRoutingConfig config;
    private final ProviderHttpClient providerClient;
    private final ProtectedResourceService protectedResources;
    private final RuntimeDataSyncClient runtimeClient;
    private final IntegrationStateRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, ReentrantLock> sourceLocks = new ConcurrentHashMap<>();

    public HttpDataSyncService(
        RestRoutingConfig config,
        ProviderHttpClient providerClient,
        ProtectedResourceService protectedResources,
        RuntimeDataSyncClient runtimeClient,
        IntegrationStateRepository repository,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.config = config;
        this.providerClient = providerClient;
        this.protectedResources = protectedResources;
        this.runtimeClient = runtimeClient;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public IntegrationStateRepository.SyncState reconcile(String sourceId) {
        RestRoutingConfig.HttpDataSource source = requireSource(sourceId);
        ReentrantLock sourceLock = sourceLock(sourceId);
        sourceLock.lock();
        String runId = UUID.randomUUID().toString();
        IntegrationStateRepository.SyncState previousState = repository.syncState(sourceId).orElse(null);
        String previousCursor = previousState != null
            && Objects.equals(previousState.sourceVersion(), source.getSourceVersion())
            ? previousState.cursor()
            : null;
        repository.startSync(sourceId, runId, source.getSourceVersion());
        try {
            FetchResult fetched = fetchAll(sourceId, source, previousCursor);
            List<RuntimeDataSyncClient.SyncRecord> records = fetched.records();
            Set<String> previousIds = repository.activeRecordIds(sourceId);
            Set<String> currentIds = new LinkedHashSet<>();
            records.forEach(record -> currentIds.add(record.id()));
            Set<String> deleteIds = new LinkedHashSet<>(fetched.explicitDeleteIds());
            if (deletesAbsent(source.getTombstonePolicy())) {
                previousIds.stream().filter(id -> !currentIds.contains(id)).forEach(deleteIds::add);
            }
            List<String> deletes = List.copyOf(deleteIds);

            RuntimeDataSyncClient.SyncOutcome outcome = runtimeClient.submit(
                sourceId,
                records,
                deletes,
                source.getVectorSpace()
            );
            IntegrationStateRepository.SyncCounts counts = new IntegrationStateRepository.SyncCounts(
                fetched.sourceCount(),
                records.size() + fetched.explicitDeleteIds().size(),
                outcome.completedUpserts(),
                outcome.completedDeletes(),
                outcome.accepted(),
                outcome.completed(),
                outcome.failed()
            );
            if (outcome.failed() > 0) {
                repository.failSync(
                    sourceId,
                    "INDEXING_WORK_FAILED",
                    "Runtime indexing did not complete every accepted operation.",
                    counts
                );
                throw new ProviderCallException(
                    ProviderErrorClass.SERVICE_UNAVAILABLE,
                    0,
                    "Runtime indexing did not complete every accepted operation."
                );
            }
            repository.applyProjectionChanges(
                sourceId,
                runId,
                records.stream().map(this::projectionRecord).toList(),
                Set.copyOf(deletes)
            );
            repository.completeSync(sourceId, runId, fetched.cursor(), counts);
            return repository.syncState(sourceId).orElseThrow();
        } catch (ProviderCallException ex) {
            repository.failSync(sourceId, ex.errorClass().name(), ex.getMessage(), null);
            throw ex;
        } catch (RuntimeException ex) {
            repository.failSync(
                sourceId,
                ProviderErrorClass.SERVICE_UNAVAILABLE.name(),
                "Integration reconciliation failed.",
                null
            );
            throw ex;
        } finally {
            sourceLock.unlock();
        }
    }

    public CompletableFuture<IntegrationStateRepository.SyncState> reconcileAsync(String sourceId) {
        return CompletableFuture.supplyAsync(() -> reconcile(sourceId));
    }

    public RecordReconcileResult reconcileRecord(String sourceId, String recordKey) {
        RestRoutingConfig.HttpDataSource source = requireSource(sourceId);
        RestRoutingConfig.TargetedRecordFetch targeted = source.getTargetedRecordFetch();
        if (targeted == null || !targeted.isEnabled()) {
            throw new ProviderCallException(
                ProviderErrorClass.BAD_REQUEST,
                0,
                "Targeted record reconciliation is not configured for this data source."
            );
        }
        String safeRecordKey = boundedRecordKey(recordKey);
        ReentrantLock sourceLock = sourceLock(sourceId);
        sourceLock.lock();
        try {
            TargetedFetchResult fetched = fetchRecord(sourceId, source, targeted, safeRecordKey);
            List<RuntimeDataSyncClient.SyncRecord> upserts = fetched.record() != null
                ? List.of(fetched.record())
                : List.of();
            List<String> deletes = fetched.delete() ? List.of(safeRecordKey) : List.of();
            RuntimeDataSyncClient.SyncOutcome outcome = runtimeClient.submit(
                sourceId,
                upserts,
                deletes,
                source.getVectorSpace()
            );
            if (outcome.failed() > 0) {
                throw new ProviderCallException(
                    ProviderErrorClass.SERVICE_UNAVAILABLE,
                    0,
                    "Runtime indexing did not complete the targeted provider operation."
                );
            }
            String runId = "record-" + UUID.randomUUID();
            if (fetched.record() != null) {
                repository.applyProjectionChanges(
                    sourceId,
                    runId,
                    List.of(projectionRecord(fetched.record())),
                    Set.of()
                );
            } else if (fetched.delete()) {
                repository.applyProjectionChanges(sourceId, runId, List.of(), Set.of(safeRecordKey));
            }
            return new RecordReconcileResult(
                sourceId,
                safeRecordKey,
                "COMPLETED",
                outcome.completedUpserts(),
                outcome.completedDeletes(),
                outcome.failed(),
                null
            );
        } finally {
            sourceLock.unlock();
        }
    }

    public CompletableFuture<RecordReconcileResult> reconcileRecordAsync(String sourceId, String recordKey) {
        return CompletableFuture.supplyAsync(() -> reconcileRecord(sourceId, recordKey));
    }

    @Scheduled(fixedDelayString = "${REST_CONNECTOR_SCHEDULER_TICK_MS:10000}")
    public void scheduledReconciliation() {
        Instant now = clock.instant();
        config.getDataSources().forEach((sourceId, source) -> {
            if (source == null || !source.isEnabled() || sourceBusy(sourceId)) {
                return;
            }
            Instant lastStarted = repository.syncState(sourceId).map(IntegrationStateRepository.SyncState::lastStartedAt).orElse(null);
            if (lastStarted == null || Duration.between(lastStarted, now).getSeconds() >= source.getScheduleSeconds()) {
                reconcileAsync(sourceId).exceptionally(ignored -> null);
            }
        });
    }

    public List<IntegrationStateRepository.SyncState> states() {
        return repository.syncStates();
    }

    public IntegrationStateRepository.SyncState state(String sourceId) {
        RestRoutingConfig.HttpDataSource source = requireSource(sourceId);
        return repository.syncState(sourceId).orElse(new IntegrationStateRepository.SyncState(
            sourceId,
            "NOT_RUN",
            null,
            source.getSourceVersion(),
            null,
            null,
            new IntegrationStateRepository.SyncCounts(0, 0, 0, 0, 0, 0, 0),
            null, null, null, null, null, null
        ));
    }

    private TargetedFetchResult fetchRecord(
        String sourceId,
        RestRoutingConfig.HttpDataSource source,
        RestRoutingConfig.TargetedRecordFetch targeted,
        String recordKey
    ) {
        String requestPath = StringUtils.hasText(targeted.getPath())
            ? targeted.getPath().trim()
            : source.getPath();
        Map<String, Object> query = new LinkedHashMap<>(source.getQuery());
        query.putAll(targeted.getQuery());
        Map<String, String> headers = new LinkedHashMap<>(source.getHeaders());
        headers.putAll(targeted.getHeaders());
        ProtectedResourceService.BoundRequest bound = protectedResources.apply(
            source.getProtectedResourceBindingRef(),
            source.getConnectionProfileRef(),
            source.getRequiredCapabilityGrants(),
            source.getTrustedResourcePlacements(),
            requestPath,
            query,
            headers,
            null,
            Map.of()
        );
        TargetedRequest targetedRequest = placeRecordKey(
            bound.path(),
            bound.query(),
            bound.headers(),
            targeted.getRecordKeyPlacement(),
            recordKey
        );
        ProviderHttpClient.ProviderResponse response = providerClient.execute(new ProviderHttpClient.ProviderRequest(
            source.getConnectionProfileRef(),
            targeted.getMethod(),
            targetedRequest.path(),
            targetedRequest.query(),
            targetedRequest.headers(),
            null,
            config.getRuntimeDataSync().getTimeoutMs(),
            source.getMapping().getMaxResponseBytes(),
            true
        ));
        if (targeted.getAbsentHttpStatuses().contains(response.status())) {
            return new TargetedFetchResult(null, true);
        }
        if (!response.successful()) {
            throw new ProviderCallException(
                providerClient.classify(source.getConnectionProfileRef(), response.status(), response.body()),
                response.status(),
                "Provider targeted record request failed."
            );
        }
        if (!targeted.getCompleteHttpStatuses().contains(response.status())) {
            throw new ProviderCallException(
                ProviderErrorClass.MALFORMED_RESPONSE,
                response.status(),
                "Provider targeted record request returned an undeclared successful HTTP status."
            );
        }
        response.correlationHeaders().entrySet().stream().findFirst().ifPresent(entry ->
            repository.recordProviderCorrelation(sourceId, entry.getKey(), entry.getValue())
        );
        JsonNode root = readJson(response.body());
        JsonNode recordNode = at(root, source.getMapping().getRecordsJsonPointer());
        if (!recordNode.isArray()) {
            throw new ProviderCallException(
                ProviderErrorClass.MALFORMED_RESPONSE,
                response.status(),
                "Provider targeted record selector did not resolve to a list."
            );
        }
        if (recordNode.isEmpty()) {
            return new TargetedFetchResult(null, true);
        }
        if (recordNode.size() != 1) {
            throw new ProviderCallException(
                ProviderErrorClass.MALFORMED_RESPONSE,
                response.status(),
                "Provider targeted record request did not resolve to exactly one current record."
            );
        }
        JsonNode record = recordNode.get(0);
        String providerRecordId = validateRecordBoundary(source, bound.binding(), record);
        if (!recordKey.equals(providerRecordId)) {
            throw new ProviderCallException(
                ProviderErrorClass.RESOURCE_ACCESS_DENIED,
                response.status(),
                "Provider targeted record identity did not match the authenticated event key."
            );
        }
        if (isExplicitTombstone(source.getTombstonePolicy(), record)) {
            return new TargetedFetchResult(null, true);
        }
        if (!isIncludedRecord(source.getMapping(), record)) {
            return new TargetedFetchResult(null, true);
        }
        return new TargetedFetchResult(
            mapRecord(sourceId, source, bound.binding(), providerRecordId, record),
            false
        );
    }

    private TargetedRequest placeRecordKey(
        String path,
        Map<String, Object> query,
        Map<String, String> headers,
        RestRoutingConfig.RecordKeyPlacement placement,
        String recordKey
    ) {
        Map<String, Object> safeQuery = new LinkedHashMap<>(query);
        Map<String, String> safeHeaders = new LinkedHashMap<>(headers);
        String safePath = path;
        switch (placement.getTarget()) {
            case QUERY -> safeQuery.put(placement.getField(), recordKey);
            case HEADER -> safeHeaders.put(placement.getField(), recordKey);
            case PATH -> safePath = safePath.replace(
                "{" + placement.getField() + "}",
                URLEncoder.encode(recordKey, StandardCharsets.UTF_8).replace("+", "%20")
            );
        }
        return new TargetedRequest(safePath, Map.copyOf(safeQuery), Map.copyOf(safeHeaders));
    }

    private String boundedRecordKey(String recordKey) {
        String normalized = StringUtils.hasText(recordKey) ? recordKey.trim() : "";
        if (!StringUtils.hasText(normalized) || normalized.length() > 500
            || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new ProviderCallException(
                ProviderErrorClass.BAD_REQUEST,
                0,
                "Targeted provider record key is missing or outside the supported boundary."
            );
        }
        return normalized;
    }

    private ReentrantLock sourceLock(String sourceId) {
        return sourceLocks.computeIfAbsent(sourceId, ignored -> new ReentrantLock(true));
    }

    private boolean sourceBusy(String sourceId) {
        ReentrantLock lock = sourceLocks.get(sourceId);
        return lock != null && (lock.isLocked() || lock.hasQueuedThreads());
    }

    private FetchResult fetchAll(
        String sourceId,
        RestRoutingConfig.HttpDataSource source,
        String previousCursor
    ) {
        Map<String, RuntimeDataSyncClient.SyncRecord> records = new LinkedHashMap<>();
        Set<String> explicitDeleteIds = new LinkedHashSet<>();
        int sourceCount = 0;
        RestRoutingConfig.Pagination pagination = source.getPagination() != null ? source.getPagination() : new RestRoutingConfig.Pagination();
        int page = pagination.getStartPage();
        String cursor = pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.CURSOR
            ? previousCursor
            : null;
        for (int pageIndex = 0; pageIndex < pagination.getMaxPages(); pageIndex++) {
            Map<String, Object> query = new LinkedHashMap<>(source.getQuery());
            if (pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.PAGE_SIZE) {
                query.put(pagination.getPageQuery(), page);
                query.put(pagination.getSizeQuery(), pagination.getPageSize());
            } else if (pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.CURSOR && StringUtils.hasText(cursor)) {
                query.put(pagination.getCursorQuery(), cursor);
            }
            ProtectedResourceService.BoundRequest bound = protectedResources.apply(
                source.getProtectedResourceBindingRef(),
                source.getConnectionProfileRef(),
                source.getRequiredCapabilityGrants(),
                source.getTrustedResourcePlacements(),
                source.getPath(),
                query,
                source.getHeaders(),
                null,
                Map.of()
            );
            ProviderHttpClient.ProviderResponse response = providerClient.execute(new ProviderHttpClient.ProviderRequest(
                source.getConnectionProfileRef(),
                source.getMethod(),
                bound.path(),
                bound.query(),
                bound.headers(),
                null,
                config.getRuntimeDataSync().getTimeoutMs(),
                source.getMapping().getMaxResponseBytes(),
                true
            ));
            if (!response.successful()) {
                throw new ProviderCallException(
                    providerClient.classify(source.getConnectionProfileRef(), response.status(), response.body()),
                    response.status(),
                    "Provider data source request failed."
                );
            }
            if (!source.getCompleteHttpStatuses().contains(response.status())) {
                throw new ProviderCallException(
                    ProviderErrorClass.MALFORMED_RESPONSE,
                    response.status(),
                    "Provider data source returned a successful HTTP status that is not declared as a complete response."
                );
            }
            response.correlationHeaders().entrySet().stream().findFirst().ifPresent(entry ->
                repository.recordProviderCorrelation(sourceId, entry.getKey(), entry.getValue())
            );
            JsonNode root = readJson(response.body());
            JsonNode recordNode = at(root, source.getMapping().getRecordsJsonPointer());
            if (!recordNode.isArray()) {
                throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, response.status(), "Provider record selector did not resolve to a list.");
            }
            int pageRecordCount = 0;
            for (JsonNode record : recordNode) {
                if (sourceCount >= source.getMapping().getMaxRecords()) {
                    throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, response.status(), "Provider data source exceeded the configured record limit.");
                }
                sourceCount++;
                pageRecordCount++;
                String recordId = validateRecordBoundary(source, bound.binding(), record);
                if (isExplicitTombstone(source.getTombstonePolicy(), record)) {
                    if (records.containsKey(recordId)) {
                        throw conflictingRecordOperation(recordId);
                    }
                    explicitDeleteIds.add(recordId);
                } else if (!isIncludedRecord(source.getMapping(), record)) {
                    if (records.containsKey(recordId)) {
                        throw conflictingRecordOperation(recordId);
                    }
                    explicitDeleteIds.add(recordId);
                } else {
                    if (explicitDeleteIds.contains(recordId)) {
                        throw conflictingRecordOperation(recordId);
                    }
                    records.put(recordId, mapRecord(sourceId, source, bound.binding(), recordId, record));
                }
            }
            if (pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.NONE) {
                break;
            }
            if (pagination.getStrategy() == RestRoutingConfig.Pagination.Strategy.PAGE_SIZE) {
                if (pageRecordCount < pagination.getPageSize()) {
                    break;
                }
                if (pageIndex == pagination.getMaxPages() - 1) {
                    throw new ProviderCallException(
                        ProviderErrorClass.MALFORMED_RESPONSE,
                        response.status(),
                        "Provider snapshot reached the configured page limit before completeness could be proven."
                    );
                }
                page++;
                continue;
            }
            JsonNode next = at(root, pagination.getNextCursorJsonPointer());
            String nextCursor = next.isValueNode() ? next.asText("").trim() : "";
            if (!StringUtils.hasText(nextCursor) || nextCursor.equals(cursor)) {
                break;
            }
            cursor = nextCursor;
        }
        return new FetchResult(List.copyOf(records.values()), Set.copyOf(explicitDeleteIds), sourceCount, cursor);
    }

    private RuntimeDataSyncClient.SyncRecord mapRecord(
        String sourceId,
        RestRoutingConfig.HttpDataSource source,
        RestRoutingConfig.ProtectedResourceBinding binding,
        String id,
        JsonNode record
    ) {
        RestRoutingConfig.RecordMapping mapping = source.getMapping();
        Map<String, Object> entity = project(record, mapping.getEntityFields());
        Map<String, Object> metadata = project(record, mapping.getMetadataFields());
        metadata.put("sourceId", sourceId);
        metadata.put("sourceRecordId", id);
        metadata.put("entityType", source.getEntityType());
        metadata.put("knowledgeSourceHandleRef", source.getKnowledgeSourceHandleRef());
        metadata.put("protectedResourceType", binding.getResourceType());
        metadata.put("protectedResourceFingerprint", protectedResources.fingerprint(binding));
        if (StringUtils.hasText(config.getRuntimeDataSync().getDeploymentId())) {
            metadata.put("deploymentId", config.getRuntimeDataSync().getDeploymentId());
        }
        if (StringUtils.hasText(config.getRuntimeDataSync().getTenantId())) {
            metadata.put("tenantId", config.getRuntimeDataSync().getTenantId());
        }
        String content = content(record, mapping.getContentFields());
        String fingerprint = Hashing.sha256Hex(canonical(entity) + "\n" + canonical(metadata) + "\n" + content);
        return new RuntimeDataSyncClient.SyncRecord(id, content, Map.copyOf(entity), Map.copyOf(metadata), fingerprint);
    }

    private IntegrationStateRepository.SourceProjectionRecord projectionRecord(
        RuntimeDataSyncClient.SyncRecord record
    ) {
        return new IntegrationStateRepository.SourceProjectionRecord(
            record.id(),
            record.fingerprint(),
            record.content(),
            record.entity(),
            record.metadata(),
            clock.instant()
        );
    }

    private String validateRecordBoundary(
        RestRoutingConfig.HttpDataSource source,
        RestRoutingConfig.ProtectedResourceBinding binding,
        JsonNode record
    ) {
        RestRoutingConfig.RecordMapping mapping = source.getMapping();
        String id = scalar(record, mapping.getIdJsonPointer());
        if (!StringUtils.hasText(id)) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider record has no stable identity.");
        }
        if (id.length() > 500) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider record identity exceeds the supported boundary.");
        }
        if (StringUtils.hasText(mapping.getResourceJsonPointer())) {
            String resourceId = scalar(record, mapping.getResourceJsonPointer());
            if (!binding.getResourceId().equals(resourceId)) {
                throw new ProviderCallException(ProviderErrorClass.RESOURCE_ACCESS_DENIED, 0, "Provider record belongs to a different protected resource.");
            }
        }
        return id;
    }

    private boolean isExplicitTombstone(RestRoutingConfig.TombstonePolicy policy, JsonNode record) {
        if (!deletesByField(policy)) {
            return false;
        }
        String operation = scalar(record, policy.getOperationJsonPointer());
        return policy.getDeleteValues().stream().anyMatch(value -> value.equalsIgnoreCase(operation));
    }

    private boolean isIncludedRecord(RestRoutingConfig.RecordMapping mapping, JsonNode record) {
        if (mapping == null || mapping.getInclusionConditions() == null
            || mapping.getInclusionConditions().isEmpty()) {
            return true;
        }
        return mapping.getInclusionConditions().stream().allMatch(condition -> {
            String value = scalar(record, condition.getJsonPointer());
            return condition.getAllowedValues().stream()
                .anyMatch(allowed -> allowed.equalsIgnoreCase(value));
        });
    }

    private boolean deletesAbsent(RestRoutingConfig.TombstonePolicy policy) {
        RestRoutingConfig.TombstonePolicy.Strategy strategy = tombstoneStrategy(policy);
        return strategy == RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_FROM_SNAPSHOT
            || strategy == RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_OR_FIELD_VALUE;
    }

    private boolean deletesByField(RestRoutingConfig.TombstonePolicy policy) {
        RestRoutingConfig.TombstonePolicy.Strategy strategy = tombstoneStrategy(policy);
        return strategy == RestRoutingConfig.TombstonePolicy.Strategy.FIELD_VALUE
            || strategy == RestRoutingConfig.TombstonePolicy.Strategy.ABSENT_OR_FIELD_VALUE;
    }

    private RestRoutingConfig.TombstonePolicy.Strategy tombstoneStrategy(RestRoutingConfig.TombstonePolicy policy) {
        return policy != null && policy.getStrategy() != null
            ? policy.getStrategy()
            : RestRoutingConfig.TombstonePolicy.Strategy.NONE;
    }

    private ProviderCallException conflictingRecordOperation(String recordId) {
        return new ProviderCallException(
            ProviderErrorClass.MALFORMED_RESPONSE,
            0,
            "Provider data source returned conflicting operations for record " + recordId + "."
        );
    }

    private Map<String, Object> project(JsonNode record, Map<String, String> fields) {
        Map<String, Object> out = new LinkedHashMap<>();
        fields.forEach((name, pointer) -> {
            JsonNode value = at(record, pointer);
            if (!value.isMissingNode() && !value.isNull() && (value.isValueNode() || value.isArray())) {
                out.put(name, objectMapper.convertValue(value, Object.class));
            }
        });
        return out;
    }

    private String content(JsonNode record, Map<String, String> fields) {
        List<String> values = new ArrayList<>();
        fields.forEach((label, pointer) -> {
            JsonNode value = at(record, pointer);
            if (value.isValueNode() && StringUtils.hasText(value.asText())) {
                values.add(label + ": " + value.asText().trim());
            } else if (value.isArray() && !value.isEmpty()) {
                values.add(label + ": " + value.toString());
            }
        });
        if (values.isEmpty()) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider record produced no approved searchable content.");
        }
        return String.join("\n", values);
    }

    private JsonNode readJson(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (Exception ex) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Provider returned malformed JSON.", ex);
        }
    }

    private JsonNode at(JsonNode root, String pointer) {
        return !StringUtils.hasText(pointer) ? root : root.at(pointer);
    }

    private String scalar(JsonNode root, String pointer) {
        JsonNode value = at(root, pointer);
        return value.isValueNode() ? value.asText("").trim() : "";
    }

    private String canonical(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new ProviderCallException(ProviderErrorClass.MALFORMED_RESPONSE, 0, "Failed to normalize provider record.", ex);
        }
    }

    private RestRoutingConfig.HttpDataSource requireSource(String sourceId) {
        RestRoutingConfig.HttpDataSource source = StringUtils.hasText(sourceId) ? config.getDataSources().get(sourceId.trim()) : null;
        if (source == null || !source.isEnabled()) {
            throw new ProviderCallException(ProviderErrorClass.BAD_REQUEST, 0, "HTTP data source is not configured.");
        }
        return source;
    }

    private record FetchResult(
        List<RuntimeDataSyncClient.SyncRecord> records,
        Set<String> explicitDeleteIds,
        int sourceCount,
        String cursor
    ) {
    }

    private record TargetedFetchResult(
        RuntimeDataSyncClient.SyncRecord record,
        boolean delete
    ) {
    }

    private record TargetedRequest(
        String path,
        Map<String, Object> query,
        Map<String, String> headers
    ) {
    }

    public record RecordReconcileResult(
        String sourceId,
        String recordKey,
        String status,
        int completedUpserts,
        int completedDeletes,
        int failedWorkCount,
        String errorClass
    ) {
    }
}
