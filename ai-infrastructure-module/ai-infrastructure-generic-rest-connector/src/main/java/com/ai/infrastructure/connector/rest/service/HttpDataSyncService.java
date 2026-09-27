package com.ai.infrastructure.connector.rest.service;

import com.ai.infrastructure.connector.rest.config.RestRoutingConfig;
import com.ai.infrastructure.connector.rest.persistence.IntegrationStateRepository;
import com.ai.infrastructure.connector.rest.util.Hashing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

@Service
public class HttpDataSyncService {

    private final RestRoutingConfig config;
    private final ProviderHttpClient providerClient;
    private final ProtectedResourceService protectedResources;
    private final RuntimeDataSyncClient runtimeClient;
    private final IntegrationStateRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Set<String> running = ConcurrentHashMap.newKeySet();

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
        if (!running.add(sourceId)) {
            return repository.syncState(sourceId).orElseThrow();
        }
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
            for (RuntimeDataSyncClient.SyncRecord record : records) {
                repository.markRecordSeen(sourceId, record.id(), record.fingerprint(), runId);
            }
            deletes.forEach(id -> repository.markRecordDeleted(sourceId, id));
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
            running.remove(sourceId);
        }
    }

    public CompletableFuture<IntegrationStateRepository.SyncState> reconcileAsync(String sourceId) {
        return CompletableFuture.supplyAsync(() -> reconcile(sourceId));
    }

    @Scheduled(fixedDelayString = "${REST_CONNECTOR_SCHEDULER_TICK_MS:10000}")
    public void scheduledReconciliation() {
        Instant now = clock.instant();
        config.getDataSources().forEach((sourceId, source) -> {
            if (source == null || !source.isEnabled() || running.contains(sourceId)) {
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
}
